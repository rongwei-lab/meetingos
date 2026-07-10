#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# MeetingOS/TDuck 备份入口。
#
# - MySQL 使用 single-transaction 逻辑备份，覆盖 MeetingOS 与 TDuck schema。
# - 文件类数据卷以只读方式归档；不存在的可选卷会跳过，绝不因备份动作创建空卷。
# - 所有产物先写入临时目录，校验通过后再原子改名，避免半成品被误当成可恢复备份。

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEPLOY_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$DEPLOY_DIR/.." && pwd)"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
ENV_FILE="${1:-$DEPLOY_DIR/../.env}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

absolute_file() {
  local path="$1"
  local dir
  dir="$(CDPATH= cd -- "$(dirname -- "$path")" 2>/dev/null && pwd)" || return 1
  printf '%s/%s\n' "$dir" "$(basename -- "$path")"
}

file_mode() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1" 2>/dev/null
}

read_env() {
  local name="$1"
  local count
  count="$(awk -F= -v key="$name" '$1 == key { count++ } END { print count + 0 }' "$ENV_FILE")"
  [ "$count" -le 1 ] || fail "$name must be defined at most once in the environment file"
  awk -F= -v key="$name" '
    $1 == key {
      sub(/^[^=]*=/, "", $0)
      sub(/\r$/, "", $0)
      print $0
      exit
    }
  ' "$ENV_FILE"
}

env_or_default() {
  local name="$1"
  local fallback="$2"
  local value="${!name-}"
  if [ -z "$value" ]; then
    value="$(read_env "$name")"
  fi
  printf '%s\n' "${value:-$fallback}"
}

require_identifier() {
  local name="$1"
  local value="$2"
  printf '%s' "$value" | grep -Eq '^[A-Za-z0-9_]+$' \
    || fail "$name must contain only letters, digits, and underscores"
}

require_project_name() {
  local value="$1"
  printf '%s' "$value" | grep -Eq '^[a-z0-9][a-z0-9_.-]*$' \
    || fail "COMPOSE_PROJECT_NAME contains unsupported characters"
}

is_container_running() {
  local container_id="$1"
  [ -n "$container_id" ] \
    && [ "$(docker inspect -f '{{.State.Running}}' "$container_id" 2>/dev/null || true)" = "true" ]
}

hash_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file"
  else
    shasum -a 256 "$file"
  fi
}

archive_volume_if_present() {
  local volume_key="$1"
  local artifact_name="$2"
  local volume_name="${COMPOSE_PROJECT_NAME}_${volume_key}"
  local target="$PARTIAL_DIR/$artifact_name"

  if ! docker volume inspect "$volume_name" >/dev/null 2>&1; then
    printf 'Skipping optional volume (not found): %s\n' "$volume_name"
    return 0
  fi

  printf 'Archiving volume: %s\n' "$volume_name"
  # 使用项目后端镜像作为只读 tar helper，避免临时拉取来源不明的工具镜像。
  # --network none 与只读根文件系统保证 helper 无法访问网络，也不能修改源卷。
  docker run --rm \
    --network none \
    --read-only \
    --security-opt no-new-privileges:true \
    --cap-drop ALL \
    --user 0:0 \
    --entrypoint /bin/tar \
    -v "$volume_name:/source:ro" \
    "$BACKUP_HELPER_IMAGE" \
    -C /source -czf - . >"$target"
  [ -s "$target" ] || fail "volume archive is empty: $artifact_name"
  tar -tzf "$target" >/dev/null
}

write_metadata() {
  {
    printf 'format=meetingos-backup-v1\n'
    printf 'created_at=%s\n' "$TIMESTAMP"
    printf 'compose_project=%s\n' "$COMPOSE_PROJECT_NAME"
    printf 'meetingos_database=%s\n' "$MEETINGOS_DATABASE"
    printf 'tduck_database=%s\n' "$TDUCK_DATABASE"
    printf 'mysql_consistency=single-transaction\n'
    printf 'file_volume_consistency=live-read-only-archive\n'
  } >"$PARTIAL_DIR/METADATA"
}

write_manifest() {
  local file
  (
    cd "$PARTIAL_DIR"
    for file in METADATA mysql.sql.gz *.tar.gz redis-snapshot.rdb.gz; do
      [ -f "$file" ] || continue
      hash_file "$file"
    done
  ) >"$PARTIAL_DIR/SHA256SUMS"
}

apply_retention() {
  local retention_days="$1"
  local candidate base
  [ "$retention_days" -gt 0 ] || return 0

  # 只删除本脚本固定命名且位于 BACKUP_ROOT 第一层的旧目录；任何异常路径都会被跳过。
  while IFS= read -r candidate; do
    [ -n "$candidate" ] || continue
    base="$(basename -- "$candidate")"
    [[ "$base" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || continue
    case "$candidate" in
      "$BACKUP_ROOT"/*) rm -rf -- "$candidate" ;;
      *) fail "refusing to remove backup outside backup root" ;;
    esac
  done < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '????????T??????Z' -mtime "+$retention_days" -print)
}

run_offsite_hook() {
  local hook="$1"
  [ -n "$hook" ] || {
    printf 'Offsite upload: not configured\n'
    return 0
  }
  [ -x "$hook" ] || fail "BACKUP_OFFSITE_HOOK is not executable: $hook"

  printf 'Running offsite hook...\n'
  if "$hook" "$FINAL_DIR"; then
    # 状态文件只描述本机执行结果，不在上传前伪造成功状态；远端完整性由独立校验确认。
    printf 'SUCCESS %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" >"$FINAL_DIR/OFFSITE_STATUS"
    printf 'Offsite hook completed successfully.\n'
    return 0
  fi

  printf 'FAILED %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" >"$FINAL_DIR/OFFSITE_STATUS"
  printf 'ERROR: offsite hook failed; local backup remains at %s\n' "$FINAL_DIR" >&2
  return 1
}

require_command awk
require_command docker
require_command gzip
require_command grep
require_command tar
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
  fail "sha256sum or shasum is required"
fi

[ -f "$ENV_FILE" ] || fail "environment file not found: $ENV_FILE"
ENV_FILE="$(absolute_file "$ENV_FILE")" || fail "cannot resolve environment file path"
case "$(file_mode "$ENV_FILE")" in
  400|600) ;;
  *) fail "environment file permissions must be 0600 or 0400" ;;
esac

MEETINGOS_DATABASE="$(env_or_default MYSQL_DATABASE meetingos)"
TDUCK_DATABASE="$(env_or_default TDUCK_MYSQL_DATABASE tduck)"
COMPOSE_PROJECT_NAME="$(env_or_default COMPOSE_PROJECT_NAME meetingos)"
BACKUP_HELPER_IMAGE="$(env_or_default MEETINGOS_BACKEND_IMAGE '')"
RETENTION_DAYS="$(env_or_default BACKUP_RETENTION_DAYS 14)"
OFFSITE_HOOK="$(env_or_default BACKUP_OFFSITE_HOOK '')"
# 默认放在用户主目录而不是 Git 工作区，降低备份中的个人信息被误提交的风险。
BACKUP_ROOT="${BACKUP_DIR:-${HOME:-/var/backups}/meetingos-backups}"

require_identifier MYSQL_DATABASE "$MEETINGOS_DATABASE"
require_identifier TDUCK_MYSQL_DATABASE "$TDUCK_DATABASE"
require_project_name "$COMPOSE_PROJECT_NAME"
[ -n "$BACKUP_HELPER_IMAGE" ] || fail "MEETINGOS_BACKEND_IMAGE is required for volume archives"
[[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || fail "BACKUP_RETENTION_DAYS must be a non-negative integer"

docker compose version >/dev/null
docker image inspect "$BACKUP_HELPER_IMAGE" >/dev/null 2>&1 \
  || fail "backup helper image is not available locally: $BACKUP_HELPER_IMAGE"

BACKUP_ROOT_PREEXISTED=false
[ -e "$BACKUP_ROOT" ] && BACKUP_ROOT_PREEXISTED=true
mkdir -p "$BACKUP_ROOT"
BACKUP_ROOT="$(CDPATH= cd -- "$BACKUP_ROOT" && pwd)"
case "$BACKUP_ROOT" in
  /|"${HOME:-/nonexistent}"|"$REPO_ROOT"|"$REPO_ROOT"/*)
    if [ "$BACKUP_ROOT_PREEXISTED" = false ]; then
      rmdir -- "$BACKUP_ROOT" 2>/dev/null || true
    fi
    fail "BACKUP_DIR must be a dedicated directory outside the Git workspace and cannot be / or HOME"
    ;;
esac
chmod 700 "$BACKUP_ROOT"
TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
FINAL_DIR="$BACKUP_ROOT/$TIMESTAMP"
PARTIAL_DIR="$BACKUP_ROOT/.${TIMESTAMP}.partial.$$"
[ ! -e "$FINAL_DIR" ] || fail "backup directory already exists: $FINAL_DIR"
mkdir -m 700 "$PARTIAL_DIR"

cleanup_partial() {
  if [ -n "${PARTIAL_DIR:-}" ] && [ -d "$PARTIAL_DIR" ]; then
    local partial_parent partial_base
    partial_parent="$(dirname -- "$PARTIAL_DIR")"
    partial_base="$(basename -- "$PARTIAL_DIR")"
    if [ "$partial_parent" = "$BACKUP_ROOT" ] \
      && [[ "$partial_base" =~ ^\.[0-9]{8}T[0-9]{6}Z\.partial\.[0-9]+$ ]]; then
      rm -rf -- "$PARTIAL_DIR"
    else
      printf 'ERROR: refusing to clean unsafe partial backup path\n' >&2
    fi
  fi
}
trap cleanup_partial EXIT INT TERM

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
"${COMPOSE[@]}" config >/dev/null
MYSQL_CONTAINER_ID="$("${COMPOSE[@]}" ps -q mysql)"
is_container_running "$MYSQL_CONTAINER_ID" || fail "mysql service is not running"

printf 'Creating consistent MySQL dump for two schemas...\n'
# 密码只从 MySQL 容器已有环境变量复制到 MYSQL_PWD；不会出现在宿主机或容器命令参数中。
"${COMPOSE[@]}" exec -T mysql sh -ec '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysqldump \
    --user=root \
    --single-transaction \
    --quick \
    --routines \
    --events \
    --triggers \
    --hex-blob \
    --no-tablespaces \
    --set-gtid-purged=OFF \
    --databases "$@"
' sh "$MEETINGOS_DATABASE" "$TDUCK_DATABASE" | gzip -9 >"$PARTIAL_DIR/mysql.sql.gz"
[ -s "$PARTIAL_DIR/mysql.sql.gz" ] || fail "MySQL dump is empty"
gzip -t "$PARTIAL_DIR/mysql.sql.gz"

# 文件卷归档是在线只读快照。高写入场景应先进入维护窗口，避免跨文件时点不一致。
archive_volume_if_present meetingos-upload-data meetingos-upload.tar.gz
archive_volume_if_present tduck-upload-data tduck-upload.tar.gz
archive_volume_if_present minio-data minio-data.tar.gz

REDIS_CONTAINER_ID="$("${COMPOSE[@]}" ps -q redis)"
if is_container_running "$REDIS_CONTAINER_ID"; then
  printf 'Creating Redis RDB snapshot...\n'
  "${COMPOSE[@]}" exec -T redis sh -ec '
    export REDISCLI_AUTH="$REDIS_PASSWORD"
    before="$(redis-cli --no-auth-warning --raw INFO persistence | awk -F: '\''$1 == "rdb_saves" { sub(/\r$/, "", $2); print $2 }'\'')"
    [ -n "$before" ] || { echo "Redis INFO does not expose rdb_saves" >&2; exit 1; }
    redis-cli --no-auth-warning BGSAVE SCHEDULE >/dev/null
    attempts=0
    while [ "$attempts" -lt 120 ]; do
      info="$(redis-cli --no-auth-warning --raw INFO persistence)"
      current="$(printf "%s\n" "$info" | awk -F: '\''$1 == "rdb_saves" { sub(/\r$/, "", $2); print $2 }'\'')"
      in_progress="$(printf "%s\n" "$info" | awk -F: '\''$1 == "rdb_bgsave_in_progress" { sub(/\r$/, "", $2); print $2 }'\'')"
      status="$(printf "%s\n" "$info" | awk -F: '\''$1 == "rdb_last_bgsave_status" { sub(/\r$/, "", $2); print $2 }'\'')"
      if [ "${current:-0}" -gt "$before" ] && [ "${in_progress:-1}" = "0" ] && [ "$status" = "ok" ]; then
        exit 0
      fi
      attempts=$((attempts + 1))
      sleep 1
    done
    echo "Redis background save timed out" >&2
    exit 1
  ' >/dev/null
  docker cp "$REDIS_CONTAINER_ID:/data/dump.rdb" "$PARTIAL_DIR/redis-snapshot.rdb" >/dev/null
  gzip -9 "$PARTIAL_DIR/redis-snapshot.rdb"
else
  printf 'Skipping Redis snapshot (service is not running).\n'
fi

write_metadata
write_manifest
"$SCRIPT_DIR/backup-verify.sh" "$PARTIAL_DIR"

mv "$PARTIAL_DIR" "$FINAL_DIR"
PARTIAL_DIR=""
printf 'Local backup completed: %s\n' "$FINAL_DIR"

if ! run_offsite_hook "$OFFSITE_HOOK"; then
  exit 1
fi
apply_retention "$RETENTION_DAYS"
printf 'Backup workflow completed successfully.\n'
