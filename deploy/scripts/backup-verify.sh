#!/usr/bin/env bash
set -Eeuo pipefail

# 只读验证备份产物：校验 SHA-256、gzip/tar 结构，以及 SQL 中是否包含两个目标 schema。
# 本脚本不会连接数据库，也不会执行恢复或修改任何 Docker volume。

BACKUP_DIR="${1:-}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

read_metadata() {
  local key="$1"
  awk -F= -v wanted="$key" '
    $1 == wanted {
      sub(/^[^=]*=/, "", $0)
      print $0
      exit
    }
  ' METADATA
}

[ -n "$BACKUP_DIR" ] || fail "usage: $0 <backup-directory>"
[ -d "$BACKUP_DIR" ] || fail "backup directory not found: $BACKUP_DIR"
cd "$BACKUP_DIR"

[ -f METADATA ] || fail "METADATA is missing"
[ -f SHA256SUMS ] || fail "SHA256SUMS is missing"
[ -f mysql.sql.gz ] || fail "mysql.sql.gz is missing"
[ "$(read_metadata format)" = "meetingos-backup-v1" ] || fail "unsupported backup format"

if find . -maxdepth 1 -type l -print | grep -q .; then
  fail "backup directory must not contain symbolic links"
fi

# 清单只允许引用当前目录中的受控文件名，防止对外部路径做误导性校验。
while IFS= read -r line; do
  file="${line#*  }"
  file="${file#\*}"
  case "$file" in
    ''|.*|*/*) fail "unsafe filename in SHA256SUMS" ;;
  esac
  [ -f "$file" ] || fail "manifest file is missing: $file"
done <SHA256SUMS

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c SHA256SUMS
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 -c SHA256SUMS
else
  fail "sha256sum or shasum is required"
fi

gzip -t mysql.sql.gz
MEETINGOS_DATABASE="$(read_metadata meetingos_database)"
TDUCK_DATABASE="$(read_metadata tduck_database)"
printf '%s' "$MEETINGOS_DATABASE" | grep -Eq '^[A-Za-z0-9_]+$' || fail "invalid MeetingOS database in metadata"
printf '%s' "$TDUCK_DATABASE" | grep -Eq '^[A-Za-z0-9_]+$' || fail "invalid TDuck database in metadata"

# awk 会消费完整解压流，不会因 grep -q 提前退出触发 pipefail/SIGPIPE 误报。
gzip -dc mysql.sql.gz | awk \
  -v first="Current Database: \`$MEETINGOS_DATABASE\`" \
  -v second="Current Database: \`$TDUCK_DATABASE\`" '
    index($0, first) { found_first = 1 }
    index($0, second) { found_second = 1 }
    END { exit !(found_first && found_second) }
' || fail "MySQL dump does not contain both configured schemas"

for archive in *.tar.gz; do
  [ -f "$archive" ] || continue
  tar -tzf "$archive" >/dev/null
done

if [ -f redis-snapshot.rdb.gz ]; then
  gzip -t redis-snapshot.rdb.gz
fi

printf 'Backup verification passed: %s\n' "$BACKUP_DIR"
