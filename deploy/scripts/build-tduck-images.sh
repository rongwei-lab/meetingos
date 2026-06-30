#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TDUCK_FRONT_DIR="$ROOT_DIR/vendor/tduck-front"
: "${TDUCK_FRONTEND_IMAGE:?Set TDUCK_FRONTEND_IMAGE to a versioned tag or digest, for example meetingos-tduck-front:1.0.0}"

case "$TDUCK_FRONTEND_IMAGE" in
  *:latest)
    echo "TDUCK_FRONTEND_IMAGE must use a versioned tag or digest, not :latest" >&2
    exit 1
    ;;
esac

if [ ! -f "$TDUCK_FRONT_DIR/package.json" ]; then
  echo "TDuck front repo not found: $TDUCK_FRONT_DIR" >&2
  echo "Clone https://github.com/TDuckCloud/tduck-front into vendor/tduck-front first." >&2
  exit 1
fi

docker build \
  -f "$ROOT_DIR/deploy/tduck-front/Dockerfile" \
  -t "$TDUCK_FRONTEND_IMAGE" \
  "$ROOT_DIR"
