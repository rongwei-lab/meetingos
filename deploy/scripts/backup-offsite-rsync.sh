#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# backup.sh 的可选异机钩子。认证应使用 SSH agent 或受限专用密钥，禁止把密码写入参数。
# 调用示例：
#   BACKUP_RSYNC_TARGET='backup@example.com:/srv/meetingos' \
#   BACKUP_OFFSITE_HOOK="$PWD/scripts/backup-offsite-rsync.sh" \
#   ./scripts/backup.sh ../.env

BACKUP_DIR="${1:-}"
TARGET="${BACKUP_RSYNC_TARGET:-}"
AGE_RECIPIENTS_FILE="${BACKUP_AGE_RECIPIENTS_FILE:-}"
ALLOW_PLAINTEXT="${BACKUP_ALLOW_PLAINTEXT_OFFSITE:-false}"
ENCRYPTED_DIR=""

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

[ -n "$BACKUP_DIR" ] || fail "backup directory argument is required"
[ -d "$BACKUP_DIR" ] || fail "backup directory not found"
[ -n "$TARGET" ] || fail "BACKUP_RSYNC_TARGET is required"
case "$TARGET" in
  -*|*[!A-Za-z0-9_@./:~-]*) fail "BACKUP_RSYNC_TARGET contains unsafe characters" ;;
esac
case "$ALLOW_PLAINTEXT" in
  true|false) ;;
  *) fail "BACKUP_ALLOW_PLAINTEXT_OFFSITE must be true or false" ;;
esac
command -v rsync >/dev/null 2>&1 || fail "rsync is required"

RUN_NAME="$(basename -- "$BACKUP_DIR")"
[[ "$RUN_NAME" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || fail "unexpected backup directory name"

cleanup() {
  if [ -n "$ENCRYPTED_DIR" ] && [ -d "$ENCRYPTED_DIR" ]; then
    rm -rf -- "$ENCRYPTED_DIR"
  fi
}
trap cleanup EXIT INT TERM

UPLOAD_SOURCE="$BACKUP_DIR"
if [ -n "$AGE_RECIPIENTS_FILE" ]; then
  [ -f "$AGE_RECIPIENTS_FILE" ] || fail "BACKUP_AGE_RECIPIENTS_FILE is not a regular file"
  command -v age >/dev/null 2>&1 || fail "age is required when BACKUP_AGE_RECIPIENTS_FILE is configured"
  command -v tar >/dev/null 2>&1 || fail "tar is required for age encrypted bundles"

  ENCRYPTED_DIR="$(mktemp -d "${TMPDIR:-/tmp}/meetingos-age.XXXXXX")"
  chmod 700 "$ENCRYPTED_DIR"
  printf 'Age encryption: enabled with public recipients file.\n'
  # 未加密 tar 只走管道，不落地；远端仅收到 age 密文、密文哈希和非敏感格式说明。
  tar -C "$(dirname -- "$BACKUP_DIR")" -cf - "$RUN_NAME" | \
    age --encrypt \
      --recipients-file "$AGE_RECIPIENTS_FILE" \
      --output "$ENCRYPTED_DIR/$RUN_NAME.tar.age"
  {
    printf 'format=meetingos-age-offsite-v1\n'
    printf 'source_backup=%s\n' "$RUN_NAME"
    printf 'cipher=age\n'
  } >"$ENCRYPTED_DIR/METADATA"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$ENCRYPTED_DIR" && sha256sum METADATA "$RUN_NAME.tar.age") >"$ENCRYPTED_DIR/SHA256SUMS"
  elif command -v shasum >/dev/null 2>&1; then
    (cd "$ENCRYPTED_DIR" && shasum -a 256 METADATA "$RUN_NAME.tar.age") >"$ENCRYPTED_DIR/SHA256SUMS"
  else
    fail "sha256sum or shasum is required for encrypted offsite bundles"
  fi
  UPLOAD_SOURCE="$ENCRYPTED_DIR"
else
  [ "$ALLOW_PLAINTEXT" = true ] \
    || fail "refusing plaintext offsite backup; configure BACKUP_AGE_RECIPIENTS_FILE or explicitly set BACKUP_ALLOW_PLAINTEXT_OFFSITE=true"
  printf 'WARNING: age encryption is explicitly disabled; uploading plaintext backup.\n' >&2
fi

# rsync 返回成功仅表示传输完成；远端仍应独立运行 backup-verify.sh 并监控结果。
rsync \
  --archive \
  --partial \
  --human-readable \
  --chmod=Du=rwx,Dgo=,Fu=rw,Fgo= \
  -- \
  "$UPLOAD_SOURCE/" "${TARGET%/}/$RUN_NAME/"
