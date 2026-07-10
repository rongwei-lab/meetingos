# MeetingOS 部署说明

本目录提供 MeetingOS MVP 的 Docker Compose 和 Nginx 反向代理模板。

## 文件结构

```text
deploy/
  docker-compose.yml
  mysql/init/01-create-tduck-schema.sh
  nginx/meetingos.conf
  minio/create-minio-app-user.sh
  scripts/backup.sh
  scripts/backup-verify.sh
  scripts/backup-offsite-rsync.sh
  scripts/build-tduck-images.sh
  scripts/check-env.sh
  scripts/deploy.sh
  scripts/e2e-tduck-meetingos.sh
  scripts/import-tduck-schema.sh
  scripts/rotate-minio-app-credentials.sh
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
./scripts/deploy.sh ../.env up -d --force-recreate \
  mysql meetingos-backend meetingos-frontend nginx
```

`deploy.sh` 是正式部署的唯一启动入口。执行 `up` 时，它会先运行 `check-env.sh`，拒绝权限宽于
`0600/0400`、重复变量、占位/弱密钥、密码别名不一致以及未固定版本的镜像；随后自动补齐
`-d --force-recreate`，确保 `.env`、镜像和 Compose 配置全部进入新容器。`start`、`restart` 和
`--no-recreate` 会被明确拒绝，因为它们会复用旧容器。`run`、`create` 等创建容器的高级命令
同样会先检查环境，但不应作为正式发布入口。检查过程只输出变量名和错误原因，不输出变量值。
`logs`、`ps`、`config`、`down`、`stop` 等观测或停止命令不会被密钥检查阻断，便于故障排查和
紧急停止服务。不要在生产环境绕过该入口直接执行 `compose up`。

### MeetingOS 数据库初始化

- MeetingOS 后端使用 Flyway 自动建表和升级，不需要手工导入 MeetingOS SQL。
- 全新服务器、全新 MySQL 数据卷第一次启动时，Flyway 会先执行 `V1__init_schema.sql` 创建包含公开查询、报名开关、`publicQueryToken` 和性能索引的基础结构，再继续执行后续增量迁移。
- `V1` 是已经发布且不可修改的基线，生产 Flyway checksum 为 `-317070242`；后续业务变化只能新增更高版本迁移，不能修改、重命名或删除已有 SQL，也不能用 `flyway repair` 掩盖漂移。测试中的 SHA-256 清单和 MySQL 8.4 空库迁移会共同保护这条规则。
- 如果 MySQL volume 已存在，数据库初始化不会重跑；新服务器演练完整初始化时请使用新的空数据卷或新的空数据库。

5. 如需同机部署 TDuck：

```bash
cd deploy
./scripts/build-tduck-images.sh
./scripts/import-tduck-schema.sh ../.env
./scripts/deploy.sh ../.env --profile tduck up -d --force-recreate
```

不传额外参数时，`./scripts/deploy.sh ../.env` 等价于上面的全量 TDuck profile 启动命令。
TDuck 当前使用本地 Ehcache，默认 `tduck` profile 不会启动 Redis 或 MinIO。

6. 只有明确需要对应能力时，才单独启动 Redis 或 MinIO：

```bash
cd deploy
./scripts/deploy.sh ../.env --profile redis up -d --force-recreate redis
./scripts/deploy.sh ../.env --profile minio up -d --force-recreate minio minio-init
```

MeetingOS 当前使用本机内存限流，Redis 仅为后续多副本缓存等能力预留。MinIO 仅在接入 S3/异步
导出文件存储后启用；未启用时，MeetingOS 与 TDuck 继续使用各自的本地持久卷。

### MinIO 凭据隔离与轮换

- `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD` 只提供给 MinIO 服务和一次性 `minio-init`，不应传给任何业务应用。`minio-init` 使用 root 权限创建 bucket、IAM 用户和最小读写策略。
- 接入 S3 的业务应用必须使用独立的 `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。该账号只能访问 `MINIO_BUCKET`，且必须与 root 凭据不同。
- 应用凭据轮换时，脚本只更新权限为 `0600/0400` 的 `.env`，不会在终端输出新 secret。随后必须重新运行 `minio-init` 才会把新凭据应用到 MinIO：

```bash
cd deploy
./scripts/rotate-minio-app-credentials.sh ../.env
./scripts/deploy.sh ../.env --profile minio up -d --force-recreate minio minio-init
```

### 容器运行保护

- MeetingOS 后端固定以 UID/GID `10001:10001` 运行。`meetingos-upload-init` 仅在启动前以最小权限修复旧上传卷属主，业务后端本身不以 root 运行。
- MySQL、Redis、MinIO、MeetingOS 前后端、边界 Nginx 和 TDuck 服务均设置健康检查、CPU/内存/PID 上限。
- 所有服务使用 `json-file` 日志轮转，默认单文件 `10m`、保留 `5` 个；可用 `DOCKER_LOG_MAX_SIZE`、`DOCKER_LOG_MAX_FILES` 覆盖。
- 资源上限可通过 `*_MEMORY_LIMIT`、`*_CPU_LIMIT`、`*_PIDS_LIMIT` 覆盖；变量名以 `docker-compose.yml` 中各服务前缀为准。
- Compose 不把后端端口发布到宿主机，边界 Nginx 会把 `X-Forwarded-For` 覆盖为真实 `$remote_addr`，因此模板默认启用 `PUBLIC_QUERY_TRUST_FORWARD_HEADERS=true`。若另行开放后端公网直连或绕过该 Nginx，必须改为 `false`。

7. TDuck 和 MeetingOS 都启动后，可以运行真实联调脚本。脚本会通过 TDuck API 创建表单、创建字段、发布、配置 Webhook，绑定 MeetingOS 示例会议，再提交一条公开报名并确认 MeetingOS 已入库：

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
- MySQL 和两个本地上传数据卷已纳入备份；可选 Redis/MinIO 启用后，其数据卷也必须纳入备份。
- 导出接口已做权限控制和敏感字段脱敏。
- Nginx 已配置 HTTPS 证书、上传大小限制和必要限流。
- `.env` 权限为 `0600`，并且 `./scripts/check-env.sh ../.env` 已通过。
- 正式启动使用 `./scripts/deploy.sh`，没有绕过环境安全检查直接执行 `compose up`。

## 备份与恢复演练

仓库提供 `scripts/backup.sh`，默认生成以下本地备份：

- `mysql.sql.gz`：使用 `--single-transaction` 同时导出 MeetingOS 与 TDuck schema，并包含存储过程、事件和触发器。
- `meetingos-upload.tar.gz`、`tduck-upload.tar.gz`、`minio-data.tar.gz`：对应卷存在时才做只读归档，不存在时跳过且不会创建空卷。
- `redis-snapshot.rdb.gz`：Redis 正在运行时触发后台 `BGSAVE` 并等待完成，再复制稳定的 RDB 快照。
- `METADATA` 与 `SHA256SUMS`：记录非敏感恢复上下文，并为全部数据产物生成 SHA-256 清单。

备份脚本不会 `source` `.env`，只按变量名读取所需值；MySQL 和 Redis 密码由容器内已有环境变量传给客户端，不出现在宿主机/容器命令参数或脚本日志中。`.env` 权限必须为 `0600` 或 `0400`。

```bash
cd deploy
chmod 600 ../.env
./scripts/backup.sh ../.env
./scripts/backup-verify.sh ~/meetingos-backups/20260710T120000Z
```

默认输出到 `~/meetingos-backups`，避免把含个人信息的备份写进 Git 工作区；默认保留 14 天完整备份。以下选项通过命令环境显式传入，不需要把运维路径写进应用 `.env`：

```bash
# 自定义输出目录；BACKUP_RETENTION_DAYS=0 可关闭自动清理。
BACKUP_DIR=/srv/meetingos-backups \
BACKUP_RETENTION_DAYS=30 \
./scripts/backup.sh ../.env

# rsync 异机副本默认强制 age 公钥加密。使用受限 SSH 专用密钥或 SSH agent，
# recipients 文件只包含公钥，不要把 age 私钥放在应用主机。
BACKUP_RSYNC_TARGET='backup@example.com:/srv/meetingos' \
BACKUP_AGE_RECIPIENTS_FILE=/etc/meetingos/backup-age-recipients.txt \
BACKUP_OFFSITE_HOOK="$PWD/scripts/backup-offsite-rsync.sh" \
./scripts/backup.sh ../.env
```

未配置 `BACKUP_OFFSITE_HOOK` 时，脚本会明确输出“未配置异机上传”；已配置但执行失败时会返回非零、在本地写入 `OFFSITE_STATUS=FAILED` 并保留本地备份，不会假装上传成功。成功状态同样只记录在本地；远端应再次校验，因为 rsync 成功只代表传输完成。

`backup-offsite-rsync.sh` 默认拒绝未加密的异机上传；只有在已评估专线/目标磁盘加密等保护后显式设置 `BACKUP_ALLOW_PLAINTEXT_OFFSITE=true` 才允许明文。age 模式不会在磁盘生成未加密的中间 tar，远端只收到 `*.tar.age`、密文 `SHA256SUMS` 和非敏感 `METADATA`。私钥应离线或保存在恢复主机，不得放进仓库、应用 `.env` 或生产容器。远端恢复时先校验密文，再解密并复用本地验证脚本：

```bash
cd /srv/meetingos/20260710T120000Z
sha256sum -c SHA256SUMS  # macOS 可使用 shasum -a 256 -c SHA256SUMS
age --decrypt \
  --identity /secure/offline/meetingos-backup-age-key.txt \
  --output 20260710T120000Z.tar \
  20260710T120000Z.tar.age
mkdir restored && tar -C restored -xf 20260710T120000Z.tar
./scripts/backup-verify.sh restored/20260710T120000Z
```

文件卷使用在线只读归档，不会暂停写流量。低写入部署可直接执行；表单附件或 MinIO 持续高频写入时，应先进入维护窗口，避免一个归档中包含跨时点文件。脚本始终不停止服务、不删除业务卷，也不会向正在运行的卷执行恢复。

### 隔离恢复演练

至少每月在隔离主机或临时容器完成一次恢复演练。不要把 SQL 或 tar 直接恢复到生产容器/生产卷。

1. 先运行 `backup-verify.sh`，确认 SHA-256、gzip/tar 结构和两个 schema 标记均通过。
2. 启动不挂载生产卷、也不发布端口的临时 MySQL，并使用原 `.env` 提供恢复所需密码：

```bash
docker run -d --rm --name meetingos-restore-drill \
  --env-file ../.env \
  mysql:8.4 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci

until docker exec meetingos-restore-drill sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin -uroot ping --silent'; do
  sleep 2
done

gzip -dc ~/meetingos-backups/20260710T120000Z/mysql.sql.gz | \
  docker exec -i meetingos-restore-drill sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'

docker exec meetingos-restore-drill sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e \
  "SELECT table_schema, COUNT(*) FROM information_schema.tables
   WHERE table_schema IN (\"${MYSQL_DATABASE:-meetingos}\", \"${TDUCK_MYSQL_DATABASE:-tduck}\")
   GROUP BY table_schema;"'
```

3. 对每个 `.tar.gz` 先解压到空临时目录并检查文件数量、属主和关键附件；MinIO 还应使用独立临时卷启动同版本 MinIO，通过 `mc ls` 抽查对象可读。TDuck/MeetingOS 上传卷应抽查实际图片或附件，而不只检查 tar 能否列目录。
4. 记录备份时间、恢复耗时、两库表数、抽查对象、执行人和结果。演练结束只删除明确命名的临时容器/临时卷：

```bash
docker rm -f meetingos-restore-drill
```

生产恢复前必须先停止写流量、再次备份现状、使用新卷/新数据库恢复并验证，再切换服务。不要对生产数据执行盲目覆盖，也不要用 `flyway repair` 代替迁移历史核对。

Nginx access log 使用 `meetingos_safe` 格式，只记录 `$uri` 而不记录 query string；公开查询 token 和通过 query 兼容传入的 Webhook token 不会进入访问日志。`Referrer-Policy: strict-origin` 同时阻止浏览器把公开页路径/query 作为同源 Referer 继续传播。日志仍保留请求方法、状态码、请求/上游时延、上游状态和 `request_id` 供排障使用。
