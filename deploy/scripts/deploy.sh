#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEPLOY_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$DEPLOY_DIR/.." && pwd)"
CALLER_DIR="$(pwd)"

ENV_FILE="${1:-$ROOT_DIR/.env}"
if [ "$#" -gt 0 ]; then
  shift
fi

case "$ENV_FILE" in
  /*) ;;
  *) ENV_FILE="$CALLER_DIR/$ENV_FILE" ;;
esac

if [ "$#" -eq 0 ]; then
  set -- --profile tduck up -d
fi

compose_command() {
  # Compose 的全局参数位于子命令之前。跳过这些参数及其值后，首个普通参数才是
  # up/logs/down 等真正子命令；这样 `--profile tduck up` 也能被正确识别。
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --ansi|--env-file|-f|--file|--parallel|--profile|--progress|--project-directory|-p|--project-name)
        [ "$#" -ge 2 ] || return 0
        shift 2
        ;;
      --ansi=*|--env-file=*|--file=*|--parallel=*|--profile=*|--progress=*|--project-directory=*|--project-name=*)
        shift
        ;;
      --all-resources|--compatibility|--dry-run)
        shift
        ;;
      --)
        shift
        break
        ;;
      -*)
        # 未知全局参数可能来自新版 Compose，交给 Compose 自己给出准确错误。
        return 0
        ;;
      *)
        break
        ;;
    esac
  done

  [ "$#" -gt 0 ] && printf '%s\n' "$1"
}

requires_environment_check() {
  case "$1" in
    up|run|create|"")
      # 空值表示命令无法可靠解析，采用安全默认值并先校验环境。
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

COMPOSE_COMMAND="$(compose_command "$@")"

case "$COMPOSE_COMMAND" in
  start|restart)
    echo "ERROR: 'docker compose $COMPOSE_COMMAND' reuses existing containers and does not reload changed environment or Compose configuration." >&2
    echo "Use 'up -d --force-recreate [services...]' through this deploy script instead." >&2
    exit 2
    ;;
  up)
    detached=0
    force_recreate=0
    for arg do
      case "$arg" in
        -d|--detach)
          detached=1
          ;;
        --force-recreate)
          force_recreate=1
          ;;
        --no-recreate)
          echo "ERROR: --no-recreate is not allowed for a configuration deployment." >&2
          echo "Use 'up -d --force-recreate [services...]' through this deploy script instead." >&2
          exit 2
          ;;
      esac
    done

    # 正式发布统一在后台强制重建容器，确保 .env、镜像和 Compose 配置全部生效。
    # Compose 支持把 up 选项放在服务名之后，因此追加参数不会破坏调用方的服务列表。
    if [ "$detached" -eq 0 ]; then
      set -- "$@" -d
    fi
    if [ "$force_recreate" -eq 0 ]; then
      set -- "$@" --force-recreate
    fi
    ;;
esac

# 正式部署启动/创建容器前必须通过环境安全检查；logs、ps、config、down、stop
# 等观测或停止命令仍应在密钥配置有误时可用，便于故障排查和紧急止损。
if requires_environment_check "$COMPOSE_COMMAND"; then
  "$SCRIPT_DIR/check-env.sh" "$ENV_FILE"
fi

exec docker compose \
  --project-directory "$DEPLOY_DIR" \
  --env-file "$ENV_FILE" \
  -f "$DEPLOY_DIR/docker-compose.yml" \
  "$@"
