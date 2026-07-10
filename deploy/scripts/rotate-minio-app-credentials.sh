#!/bin/sh
set -eu
umask 077

# 只轮换受限 MinIO 应用账号，不修改 root 凭据。新 secret 仅保存在 shell 变量和
# 权限为 0600 的临时文件中，不输出到终端，也不作为子进程参数传递。
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env}"
NEW_ACCESS_KEY="${MINIO_NEW_ACCESS_KEY:-meetingos_app}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

case "$NEW_ACCESS_KEY" in
  ''|*[!A-Za-z0-9_]*) fail "MINIO_NEW_ACCESS_KEY must contain only letters, digits, and underscores" ;;
esac

[ -f "$ENV_FILE" ] || fail "environment file not found: $ENV_FILE"
command -v openssl >/dev/null 2>&1 || fail "openssl is required"

mode="$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE" 2>/dev/null)"
case "$mode" in
  400|600) ;;
  *) fail "environment file permissions must be 0600 or 0400" ;;
esac

NEW_SECRET="$(openssl rand -hex 32)"
[ "${#NEW_SECRET}" -eq 64 ] || fail "failed to generate MinIO application secret"

TMP_FILE="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
cleanup() {
  [ -n "${TMP_FILE:-}" ] && rm -f -- "$TMP_FILE"
}
trap cleanup EXIT INT TERM

access_count=0
secret_count=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    MINIO_ACCESS_KEY=*)
      access_count=$((access_count + 1))
      printf 'MINIO_ACCESS_KEY=%s\n' "$NEW_ACCESS_KEY" >>"$TMP_FILE"
      ;;
    MINIO_SECRET_KEY=*)
      secret_count=$((secret_count + 1))
      printf 'MINIO_SECRET_KEY=%s\n' "$NEW_SECRET" >>"$TMP_FILE"
      ;;
    *)
      printf '%s\n' "$line" >>"$TMP_FILE"
      ;;
  esac
done <"$ENV_FILE"

[ "$access_count" -eq 1 ] || fail "MINIO_ACCESS_KEY must be defined exactly once"
[ "$secret_count" -eq 1 ] || fail "MINIO_SECRET_KEY must be defined exactly once"

chmod 600 "$TMP_FILE"
mv -f -- "$TMP_FILE" "$ENV_FILE"
TMP_FILE=""
echo "MinIO application credentials rotated in $ENV_FILE (values not printed)."
echo "Run the minio profile once to apply the restricted user and bucket policy."
