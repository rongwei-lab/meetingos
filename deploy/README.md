# MeetingOS 部署说明

本目录提供 MeetingOS MVP 的 Docker Compose 和 Nginx 反向代理模板。

## 文件结构

```text
deploy/
  docker-compose.yml
  mysql/init/01-create-tduck-schema.sh
  nginx/meetingos.conf
  scripts/build-tduck-images.sh
  scripts/check-env.sh
  scripts/deploy.sh
  scripts/e2e-tduck-meetingos.sh
  scripts/import-tduck-schema.sh
  tduck-front/Dockerfile
```

## 启动顺序

1. 在根目录复制环境变量：

```bash
cp .env.example .env
chmod 600 .env
```

2. 修改 `.env` 中所有 `CHANGE_ME`、域名、镜像名、JWT Secret、`TDUCK_WEBHOOK_TOKEN`、数据库密码和管理员初始密码。

3. 构建或拉取 MeetingOS 后端、MeetingOS 前端镜像。

4. 启动 MeetingOS 基础服务：

```bash
cd deploy
./scripts/deploy.sh ../.env up -d mysql redis minio minio-init meetingos-backend meetingos-frontend nginx
```

`deploy.sh` 是正式部署的唯一启动入口。执行 `up`、`start`、`restart`、`run` 或 `create` 前，
它会先运行 `check-env.sh`，拒绝权限宽于 `0600/0400`、重复变量、占位/弱密钥、密码别名不一致
以及未固定版本的镜像。检查过程只输出变量名和错误原因，不输出变量值。`logs`、`ps`、`config`、
`down`、`stop` 等观测或停止命令不会被密钥检查阻断，便于故障排查和紧急停止服务。
不要在生产环境绕过该入口直接执行 `compose up`。

### MeetingOS 数据库初始化

- MeetingOS 后端使用 Flyway 自动建表和升级，不需要手工导入 MeetingOS SQL。
- 全新服务器、全新 MySQL 数据卷第一次启动时，Flyway 会先执行 `V1__init_schema.sql` 创建包含公开查询、报名开关、`publicQueryToken` 和性能索引的基础结构，再继续执行后续增量迁移。
- 当前仓库以合并后的 `V1` 作为新库基线，并保留 `V2`、`V3`、`V4` 等后续增量迁移；已有数据库如果已经执行过旧版 `V1~V7`，不要直接替换迁移文件启动，否则可能因 Flyway 历史不一致无法启动，应重建库或单独处理 `flyway_schema_history`。
- 如果 MySQL volume 已存在，数据库初始化不会重跑；新服务器演练完整初始化时请使用新的空数据卷或新的空数据库。

5. 如需同机部署 TDuck：

```bash
cd deploy
./scripts/build-tduck-images.sh
./scripts/import-tduck-schema.sh ../.env
./scripts/deploy.sh ../.env --profile tduck up -d
```

不传额外参数时，`./scripts/deploy.sh ../.env` 等价于上面的全量 TDuck profile 启动命令。

### 容器运行保护

- MeetingOS 后端固定以 UID/GID `10001:10001` 运行。`meetingos-upload-init` 仅在启动前以最小权限修复旧上传卷属主，业务后端本身不以 root 运行。
- MySQL、Redis、MinIO、MeetingOS 前后端、边界 Nginx 和 TDuck 服务均设置健康检查、CPU/内存/PID 上限。
- 所有服务使用 `json-file` 日志轮转，默认单文件 `10m`、保留 `5` 个；可用 `DOCKER_LOG_MAX_SIZE`、`DOCKER_LOG_MAX_FILES` 覆盖。
- 资源上限可通过 `*_MEMORY_LIMIT`、`*_CPU_LIMIT`、`*_PIDS_LIMIT` 覆盖；变量名以 `docker-compose.yml` 中各服务前缀为准。
- Compose 不把后端端口发布到宿主机，边界 Nginx 会把 `X-Forwarded-For` 覆盖为真实 `$remote_addr`，因此模板默认启用 `PUBLIC_QUERY_TRUST_FORWARD_HEADERS=true`。若另行开放后端公网直连或绕过该 Nginx，必须改为 `false`。

6. TDuck 和 MeetingOS 都启动后，可以运行真实联调脚本。脚本会通过 TDuck API 创建表单、创建字段、发布、配置 Webhook，绑定 MeetingOS 示例会议，再提交一条公开报名并确认 MeetingOS 已入库：

```bash
cd deploy
./scripts/e2e-tduck-meetingos.sh ../.env
```

本地演示环境没有 `.env` 时可传 `../.env.example`。如果已修改默认管理员密码，请通过环境变量覆盖：

```bash
MEETINGOS_ADMIN_PASSWORD=your-password \
TDUCK_ADMIN_PASSWORD=your-password \
./scripts/e2e-tduck-meetingos.sh ../.env
```

## TDuck 注意事项

- TDuck 社区版默认管理员常见为 `admin@tduckcloud.com / 123456`，生产上线前必须修改。
- MeetingOS 后端会使用 `TDUCK_ADMIN_EMAIL`、`TDUCK_ADMIN_PASSWORD` 登录 TDuck 拉取表单列表并做保存前校验；这两个变量只配置在后端环境里，不会暴露给前端。
- `TDUCK_API_PUBLIC_URL` 是浏览器访问 TDuck API 的公开地址，`TDUCK_ADMIN_API_URL` 是 MeetingOS 后端访问 TDuck API 的内部地址。Docker Compose 本地部署时，公开地址通常是 `http://127.0.0.1/tduck-api`，内部地址应为 `http://tduck-backend:8999`，否则后端容器会把 `127.0.0.1` 解析到自己身上。
- `TDUCK_WEBHOOK_TOKEN` 是 MeetingOS 校验 TDuck 回调的唯一密钥来源，只在 `.env` 中配置，不在后台系统设置页展示或轮换。轮换时修改 `.env` 后重启 MeetingOS 后端，并确保 TDuck Webhook 回调继续携带同一个 token。
- Webhook 回调优先使用 `X-TDuck-Signature` 或 `X-MeetingOS-Webhook-Token`。如果 TDuck 社区版无法设置请求头，只能把 query token 写入 `MEETINGOS_WEBHOOK_INTERNAL_URL`，并且应限制为容器内网地址，避免公网代理日志记录密钥。
- TDuck 前端挂在 `/tduck/` 子路径下，`tduck-front/Dockerfile` 会在镜像构建阶段把 `publicPath`、Vue Router `base` 和 `VUE_APP_API_ROOT` 调整为 `/tduck/`、`/tduck/`、`/tduck-api`，不修改 `vendor/tduck-front` 源码。只改 `publicPath` 会导致 `/tduck/s/{formKey}` 进入 TDuck SPA 后匹配不到 `/s/:key` 路由，从而显示 TDuck 404。
- TDuck 后端使用官方镜像 `tduckcloud/tduck-platform`，通过 `spring.datasource.*` 环境变量连接本 compose 的 MySQL。
- 如果 MySQL volume 已存在，`mysql/init` 不会再次执行；请用 `scripts/import-tduck-schema.sh` 显式导入 TDuck 官方初始化 SQL。
- MeetingOS 不直接写 TDuck 业务表，只保存表单绑定、字段映射、同步快照和同步日志。
- 通过 TDuck API 自动创建表单字段时，字段 `scheme` 不能传 `{}`。TDuck 后端会接受空结构，但公开填写页渲染器依赖 `scheme.config.span`、`scheme.config.formId`、`scheme.vModel`、`scheme.placeholder` 等配置；`scripts/e2e-tduck-meetingos.sh` 已按 TDuck 官方 `INPUT` 组件结构生成完整 `scheme`。

### TDuck 表单页排障

- 访问 `/tduck/s/{formKey}` 显示 TDuck 404：通常是 TDuck 前端没有按 `/tduck/` 子路径构建。重新执行 `scripts/build-tduck-images.sh`，并重启 `tduck-frontend` 与 `nginx`。
- 页面只显示“支持微信或浏览器扫码”，控制台出现 `Cannot set properties of undefined (setting 'span')`：通常是该表单字段的 `scheme` 为空或缺少 `config`。新表单请使用 `scripts/e2e-tduck-meetingos.sh` 的字段创建方式；已有演示表单可通过 TDuck `/user/form/item/update` 接口补齐字段 `scheme`，不建议改 TDuck 源码。
- `/r/{meetingCode}` 没有进入预期表单：先检查 `GET /api/public/meetings/{meetingCode}/registration` 返回的 `formKey` 和 `registrationUrl`，再检查 TDuck 对应 `webhook_config.source_id` 是否启用。

## Nginx 路由

| 入口 | 目标服务 | 说明 |
| --- | --- | --- |
| `/` | MeetingOS 前端 | 后台和公开查询页 |
| `/api/` | MeetingOS 后端 | 后端 API |
| `/actuator/health` | MeetingOS 后端 | 健康检查 |
| `/r/{meetingCode}` | MeetingOS 前端或后端 | 报名二维码中转入口 |
| `/tduck/` | TDuck 前端 | TDuck 后台和表单页 |
| `/tduck-api/` | TDuck 后端 | TDuck API |

## 生产上线前检查

- 已替换所有默认密码和弱密钥。
- TDuck 默认管理员密码已修改。
- `TDUCK_WEBHOOK_TOKEN` 已启用并保持强随机值，TDuck 回调配置已同步；Nginx/后端日志中避免明文输出。
- MySQL 数据卷已纳入备份；Redis/MinIO 当前主要供 TDuck 和后续 MeetingOS 多副本限流、异步导出文件存储预留，启用相关能力后也必须纳入备份。
- 导出接口已做权限控制和敏感字段脱敏。
- Nginx 已配置 HTTPS 证书、上传大小限制和必要限流。
- `.env` 权限为 `0600`，并且 `./scripts/check-env.sh ../.env` 已通过。
- 正式启动使用 `./scripts/deploy.sh`，没有绕过环境安全检查直接执行 `compose up`。
