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

requires_environment_check() {
  # Compose 的全局参数位于子命令之前。跳过这些参数及其值后，首个普通参数才是
  # up/logs/down 等真正子命令；这样 `--profile tduck up` 也能被正确识别。
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --ansi|--env-file|-f|--file|--parallel|--profile|--progress|--project-directory|-p|--project-name)
        # 参数缺值时按需要检查处理；随后仍会由 Compose 输出准确的参数错误。
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
        # 未知全局参数可能来自新版 Compose。无法判断其是否携带参数时采用安全默认值。
        return 0
        ;;
      *)
        break
        ;;
    esac
  done

  [ "$#" -gt 0 ] || return 0
  case "$1" in
    up|start|restart|run|create)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

# 正式部署启动/创建容器前必须通过环境安全检查；logs、ps、config、down、stop
# 等观测或停止命令仍应在密钥配置有误时可用，便于故障排查和紧急止损。
if requires_environment_check "$@"; then
  "$SCRIPT_DIR/check-env.sh" "$ENV_FILE"
fi

exec docker compose \
  --project-directory "$DEPLOY_DIR" \
  --env-file "$ENV_FILE" \
  -f "$DEPLOY_DIR/docker-compose.yml" \
  "$@"
