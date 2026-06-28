#!/bin/sh
set -eu

# Docker 官方 MySQL 镜像会执行 docker-entrypoint-initdb.d 下的 .sh 文件。
# 这里使用环境变量创建 TDuck 独立 schema，避免把生产密码写死在 SQL 文件中。
# SQL 字符串需要转义单引号，防止密码中包含特殊字符时初始化失败。
sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

TDUCK_DB_NAME=$(sql_escape "${TDUCK_MYSQL_DATABASE:-tduck}")
TDUCK_DB_USER=$(sql_escape "${TDUCK_MYSQL_USER:-tduck}")
TDUCK_DB_PASSWORD=$(sql_escape "${TDUCK_MYSQL_PASSWORD}")

case "${TDUCK_MYSQL_DATABASE:-tduck}" in
  *[!A-Za-z0-9_]*|"")
    echo "Unsafe TDUCK_MYSQL_DATABASE: ${TDUCK_MYSQL_DATABASE:-}" >&2
    exit 1
    ;;
esac

case "${TDUCK_MYSQL_USER:-tduck}" in
  *[!A-Za-z0-9_]*|"")
    echo "Unsafe TDUCK_MYSQL_USER: ${TDUCK_MYSQL_USER:-}" >&2
    exit 1
    ;;
esac

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${TDUCK_DB_NAME}\`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS '${TDUCK_DB_USER}'@'%'
  IDENTIFIED BY '${TDUCK_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON \`${TDUCK_DB_NAME}\`.*
  TO '${TDUCK_DB_USER}'@'%';

FLUSH PRIVILEGES;
SQL
