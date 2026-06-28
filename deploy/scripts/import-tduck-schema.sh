#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
ENV_FILE="${1:-$ROOT_DIR/.env.example}"
SQL_FILE="$ROOT_DIR/vendor/tduck-survey-form/docker/init-db/tduck-v5.sql"

case "$ENV_FILE" in
  /*) ;;
  *) ENV_FILE="$PWD/$ENV_FILE" ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

if [ ! -f "$SQL_FILE" ]; then
  echo "TDuck init SQL not found: $SQL_FILE" >&2
  echo "Clone https://github.com/TDuckCloud/tduck-survey-form into vendor/tduck-survey-form first." >&2
  exit 1
fi

TDUCK_DB=$(
  awk -F= '
    $1 == "TDUCK_MYSQL_DATABASE" {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2)
      gsub(/^"|"$/, "", $2)
      print $2
      found=1
    }
    END {
      if (!found) {
        print "tduck"
      }
    }
  ' "$ENV_FILE"
)
TDUCK_USER=$(
  awk -F= '
    $1 == "TDUCK_MYSQL_USER" {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2)
      gsub(/^"|"$/, "", $2)
      print $2
      found=1
    }
    END {
      if (!found) {
        print "tduck"
      }
    }
  ' "$ENV_FILE"
)
TDUCK_PASSWORD=$(
  awk -F= '
    $1 == "TDUCK_MYSQL_PASSWORD" {
      sub(/^[^=]*=/, "")
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      gsub(/^"|"$/, "", $0)
      print $0
      found=1
    }
    END {
      if (!found) {
        print ""
      }
    }
  ' "$ENV_FILE"
)

if ! printf '%s' "$TDUCK_DB" | grep -Eq '^[A-Za-z0-9_]+$'; then
  echo "Unsafe TDuck database name: $TDUCK_DB" >&2
  exit 1
fi
if ! printf '%s' "$TDUCK_USER" | grep -Eq '^[A-Za-z0-9_]+$'; then
  echo "Unsafe TDuck database user: $TDUCK_USER" >&2
  exit 1
fi

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

TDUCK_PASSWORD_SQL=$(sql_escape "$TDUCK_PASSWORD")

cd "$ROOT_DIR/deploy"

echo "Ensuring TDuck schema '$TDUCK_DB' exists..."
docker compose --env-file "$ENV_FILE" exec -T mysql sh -lc '
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
    -e "CREATE DATABASE IF NOT EXISTS '"$TDUCK_DB"' DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
        CREATE USER IF NOT EXISTS '\'''"$TDUCK_USER"''\''@'\''%'\'' IDENTIFIED BY '\'''"$TDUCK_PASSWORD_SQL"''\'';
        GRANT ALL PRIVILEGES ON '"$TDUCK_DB"'.* TO '\'''"$TDUCK_USER"''\''@'\''%'\'';
        FLUSH PRIVILEGES;"
'

echo "Importing TDuck official schema from $SQL_FILE ..."
docker compose --env-file "$ENV_FILE" exec -T mysql sh -lc \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" '"$TDUCK_DB" < "$SQL_FILE"

echo "TDuck schema import complete."
