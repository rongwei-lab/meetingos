#!/bin/sh
set -eu

ENV_FILE="${1:-.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi

read_env() {
  awk -F= -v key="$1" '
    $1 == key {
      sub(/^[^=]*=/, "", $0)
      print $0
      exit
    }
  ' "$ENV_FILE"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_strong_secret() {
  name="$1"
  value="$(read_env "$name")"
  [ -n "$value" ] || fail "$name is required"
  case "$value" in
    CHANGE_ME*|change_me*|change-this*|local-demo*|123456|admin123)
      fail "$name must be replaced with a strong random value"
      ;;
  esac
  [ "$(printf "%s" "$value" | wc -c | tr -d ' ')" -ge 32 ] || fail "$name must be at least 32 bytes"
}

require_no_latest_image() {
  name="$1"
  value="$(read_env "$name")"
  [ -n "$value" ] || fail "$name is required"
  case "$value" in
    *:latest)
      fail "$name must use a versioned tag or digest, not :latest"
      ;;
  esac
}

require_identifier() {
  name="$1"
  value="$(read_env "$name")"
  [ -n "$value" ] || fail "$name is required"
  printf "%s" "$value" | grep -Eq '^[A-Za-z0-9_]+$' || fail "$name must match ^[A-Za-z0-9_]+$"
}

require_strong_secret JWT_SECRET
require_strong_secret TDUCK_WEBHOOK_TOKEN
require_strong_secret MYSQL_ROOT_PASSWORD
require_strong_secret MYSQL_PASSWORD
require_strong_secret TDUCK_MYSQL_PASSWORD
require_strong_secret REDIS_PASSWORD
require_strong_secret MINIO_ROOT_PASSWORD
require_strong_secret MEETINGOS_INIT_ADMIN_PASSWORD

require_no_latest_image MEETINGOS_BACKEND_IMAGE
require_no_latest_image MEETINGOS_FRONTEND_IMAGE
require_no_latest_image TDUCK_BACKEND_IMAGE
require_no_latest_image TDUCK_FRONTEND_IMAGE

require_identifier MYSQL_DATABASE
require_identifier MYSQL_USER
require_identifier TDUCK_MYSQL_DATABASE
require_identifier TDUCK_MYSQL_USER

echo "Environment security checks passed: $ENV_FILE"
