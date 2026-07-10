#!/bin/sh
set -eu

ENV_FILE="${1:-.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

file_mode() {
  stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE" 2>/dev/null
}

require_secure_file_permissions() {
  mode="$(file_mode)" || fail "cannot read permissions for environment file"
  case "$mode" in
    400|600)
      ;;
    *)
      fail "environment file permissions must be 0600 or 0400 (run: chmod 600 <env-file>)"
      ;;
  esac
}

read_env() {
  name="$1"
  count="$(awk -F= -v key="$name" '$1 == key { count++ } END { print count + 0 }' "$ENV_FILE")"
  [ "$count" -le 1 ] || fail "$name must be defined exactly once"
  awk -F= -v key="$name" '
    $1 == key {
      sub(/^[^=]*=/, "", $0)
      sub(/\r$/, "", $0)
      print $0
      exit
    }
  ' "$ENV_FILE"
}

require_strong_secret() {
  name="$1"
  min_bytes="$2"
  value="$(read_env "$name")"
  [ -n "$value" ] || fail "$name is required"
  case "$value" in
    *CHANGE_ME*|*change_me*|*change-this*|*local-demo*|123456|admin123|password|Password123)
      fail "$name must be replaced with a strong random value"
      ;;
  esac
  [ "$(printf "%s" "$value" | wc -c | tr -d ' ')" -ge "$min_bytes" ] || fail "$name must be at least $min_bytes bytes"
}

require_versioned_image() {
  name="$1"
  value="$(read_env "$name")"
  [ -n "$value" ] || fail "$name is required"
  case "$value" in
    *@sha256:*)
      ;;
    *:latest|*:LATEST)
      fail "$name must use a versioned tag or digest, not :latest"
      ;;
    *)
      image_name="${value##*/}"
      case "$image_name" in
        *:*) ;;
        *) fail "$name must use a versioned tag or sha256 digest" ;;
      esac
      ;;
  esac
}

require_identifier() {
  name="$1"
  value="$(read_env "$name")"
  [ -n "$value" ] || fail "$name is required"
  printf "%s" "$value" | grep -Eq '^[A-Za-z0-9_]+$' || fail "$name must match ^[A-Za-z0-9_]+$"
}

require_matching_values() {
  left_name="$1"
  right_name="$2"
  left_value="$(read_env "$left_name")"
  right_value="$(read_env "$right_name")"
  [ "$left_value" = "$right_value" ] || fail "$left_name and $right_name must match"
}

require_secure_file_permissions

require_strong_secret JWT_SECRET 32
require_strong_secret TDUCK_WEBHOOK_TOKEN 32
require_strong_secret MYSQL_ROOT_PASSWORD 32
require_strong_secret MYSQL_PASSWORD 32
require_strong_secret SPRING_DATASOURCE_PASSWORD 32
require_strong_secret TDUCK_MYSQL_PASSWORD 32
require_strong_secret REDIS_PASSWORD 32
require_strong_secret MINIO_ROOT_PASSWORD 32
require_strong_secret MINIO_SECRET_KEY 32
require_strong_secret MEETINGOS_INIT_ADMIN_PASSWORD 32
require_strong_secret TDUCK_ADMIN_PASSWORD 16

# Compose 同时向基础服务和应用传入这些别名；部署前确保它们没有因手工编辑而漂移。
require_matching_values MYSQL_PASSWORD SPRING_DATASOURCE_PASSWORD
require_matching_values MINIO_ROOT_PASSWORD MINIO_SECRET_KEY

require_versioned_image MEETINGOS_BACKEND_IMAGE
require_versioned_image MEETINGOS_FRONTEND_IMAGE
require_versioned_image TDUCK_BACKEND_IMAGE
require_versioned_image TDUCK_FRONTEND_IMAGE

require_identifier MYSQL_DATABASE
require_identifier MYSQL_USER
require_identifier TDUCK_MYSQL_DATABASE
require_identifier TDUCK_MYSQL_USER

echo "Environment security checks passed: $ENV_FILE"
