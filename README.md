# MeetingOS

MeetingOS 是面向会议、论坛、培训、发布会等场景的会议管理 MVP。系统覆盖会议管理、TDuck 报名表集成、参会人审核、座位安排、住宿安排、现场签到、统计导出和基础权限。

## Monorepo 结构

```text
rooms/
  meetingos-backend/   # Spring Boot 后端，当前由后端 Agent 负责
  meetingos-admin/     # Vue 3 + Naive UI 前端，当前由前端 Agent 负责
  deploy/              # Docker Compose、Nginx 和部署模板
  docs/                # API、数据模型、验收、测试文档
  .env.example         # 环境变量样例
```

TDuck 不并入本仓库，按独立前后端服务部署，再通过 Nginx 反向代理集成。

## 本地开发

1. 复制环境变量：

```bash
cp .env.example .env
```

2. 修改 `.env` 中的数据库、JWT、`TDUCK_WEBHOOK_TOKEN` 和管理员初始密码；Redis/MinIO 当前主要供 TDuck 和后续多副本限流、异步导出存储预留。

3. 启动基础依赖：

```bash
cd deploy
docker compose --env-file ../.env up -d mysql redis minio minio-init
```

4. 分别启动后端和前端开发服务。实际命令以 `meetingos-backend/`、`meetingos-admin/` 内的项目脚本为准。

5. 如需联调 TDuck，可启动 TDuck profile：

```bash
cd deploy
docker compose --env-file ../.env --profile tduck up -d
```

## 生产部署

1. 构建并推送 `meetingos-backend`、`meetingos-admin` 镜像。
2. 在服务器上复制 `.env.example` 为 `.env`，替换所有 `CHANGE_ME`。
3. 配置域名、HTTPS 证书、Nginx 上传大小和日志策略。
4. 启动完整服务：

```bash
cd deploy
docker compose --env-file ../.env --profile tduck up -d
```

5. 首次登录后立刻修改 MeetingOS 初始管理员密码和 TDuck 默认管理员密码。

## TDuck 集成步骤

1. 部署 TDuck 后端和前端，推荐同域暴露为 `/tduck/` 与 `/tduck-api/`。
2. 在 TDuck 后台创建报名表，确认表单发布页可访问 `/tduck/s/{formKey}`。
3. 配置 MeetingOS 后端的 `TDUCK_ADMIN_EMAIL`、`TDUCK_ADMIN_PASSWORD`，容器部署时同时配置后端可访问的 `TDUCK_ADMIN_API_URL`。
4. 在 MeetingOS「报名配置」中从 TDuck 表单下拉列表选择已发布表单；后端会保存前校验表单真实存在、已发布且字段可读。
5. 报名 URL 由 MeetingOS 根据 `TDUCK_PUBLIC_URL + /s/{formKey}` 自动生成，不在前端手填。
6. 在 MeetingOS 字段映射页拉取或录入 TDuck 字段，映射姓名、手机号等标准字段。
7. 保存绑定时 MeetingOS 会自动在 TDuck 配置 Webhook；如需内网回调，配置 `MEETINGOS_WEBHOOK_INTERNAL_URL`。
8. 使用 `/r/{meetingCode}` 作为报名二维码内容，由 MeetingOS 中转到 TDuck 表单。
9. TDuck 地址可在 MeetingOS「系统设置」中保存；Webhook Token 仅通过 `.env` 的 `TDUCK_WEBHOOK_TOKEN` 管理，轮换时修改环境变量、重启后端，并同步确认 TDuck Webhook 回调携带同一个 token。

> 容器部署时不要把浏览器访问的 `http://127.0.0.1/tduck-api` 当作后端内部访问地址；在后端容器里 `127.0.0.1` 指向容器自己。`TDUCK_ADMIN_API_URL` 应指向容器网络里的 TDuck 后端，例如 `http://tduck-backend:8999`。`MEETINGOS_WEBHOOK_INTERNAL_URL` 也同理，本地 Docker Compose 建议使用 `http://nginx/api/integrations/tduck/webhook`。

## Webhook 配置

推荐 TDuck Webhook 地址：

```text
POST https://meeting.example.com/api/integrations/tduck/webhook
X-TDuck-Signature: sha256=<HMAC_SHA256(raw_body, TDUCK_WEBHOOK_TOKEN)>
```

MeetingOS 保存 TDuck 绑定时会自动调用 TDuck `/webhook/config/save` 写入该地址；管理员通常不需要再去 TDuck 后台手工配置。

兼容请求头：

```text
X-MeetingOS-Webhook-Token: ${TDUCK_WEBHOOK_TOKEN}
Content-Type: application/json
```

TDuck 社区版若无法配置自定义请求头或 HMAC，可把 `MEETINGOS_WEBHOOK_INTERNAL_URL` 显式配置为仅容器内网可访问的
`http://nginx/api/integrations/tduck/webhook?token=...`。不推荐在公网 URL 使用 query token，因为代理和访问日志容易记录完整 URL。

后端处理规则：

- 优先校验 `X-TDuck-Signature`，兼容 `X-MeetingOS-Webhook-Token` 和内网 query token；不通过则拒绝并记录同步日志。
- 根据 `formKey` 查找会议绑定关系。
- 按字段映射提取参会人标准字段。
- 同一会议内默认以手机号做重复报名校验。
- 保存 TDuck 原始 payload 快照和同步日志。
- 必填缺失、字段映射缺失、手机号异常、重复报名等情况不直接丢弃，进入同步日志待人工处理。

## 字段映射

MVP 至少建议映射：

| MeetingOS 字段 | 含义 | 必填 |
| --- | --- | --- |
| `name` | 姓名 | 是 |
| `phone` | 手机号，重复报名校验依据 | 是 |
| `company` | 单位 | 否 |
| `title` | 职务 | 否 |
| `id_number` | 证件号 | 否 |
| `email` | 邮箱 | 否 |
| `attendee_type` | 嘉宾、媒体、普通参会人等 | 否 |
| `need_room` | 是否安排住宿 | 否 |
| `room_number` | 房间号 | 否 |
| `room_remark` | 房型或入住备注 | 否 |
| `remark` | 备注 | 否 |

字段映射变更后，只影响后续同步；历史数据如需重算，应通过补偿同步或人工修复流程执行，并保留操作日志。

## 常用命令

```bash
# 查看 Compose 解析结果
cd deploy && docker compose --env-file ../.env.example config

# 启动基础依赖
cd deploy && docker compose --env-file ../.env up -d mysql redis minio minio-init

# 启动全量服务
cd deploy && docker compose --env-file ../.env --profile tduck up -d

# 查看后端日志
cd deploy && docker compose --env-file ../.env logs -f meetingos-backend

# 停止服务但保留数据卷
cd deploy && docker compose --env-file ../.env down
```

## 验收清单

- 能创建会议、发布会议并生成 `/r/{meetingCode}` 报名二维码。
- 能绑定 TDuck 表单并保存字段映射。
- TDuck 新增报名后，MeetingOS 能生成参会人并记录同步日志。
- 能审核报名、筛选参会人、导入导出名单。
- 能生成座位布局，自动排座不覆盖锁定或手动座位。
- 能手动调座、查询个人座位。
- 能录入或导入房间号，给嘉宾分配房间并查询。
- 能扫码签到、搜索签到、补签，并拦截重复签到。
- 能查看报名、座位、住宿、签到统计。
- 导出、敏感字段查看和系统配置受权限控制。

更完整的验收项见 [MVP 验收清单](/Users/wei/Documents/code/rooms/docs/mvp-acceptance-checklist.md)。

## 安全与上线风险

- 默认密码：MeetingOS 初始管理员和 TDuck 默认管理员必须在首次登录后修改；TDuck 禁止继续使用 `123456`。
- `TDUCK_WEBHOOK_TOKEN`：必须使用高强度随机值，并避免在前端、日志和错误响应中泄露。
- 敏感信息：手机号、证件号、邮箱在列表和日志中应脱敏；导出需要独立权限。
- 系统设置：只维护域名和 TDuck 地址等运行配置；Webhook Token 不在前端展示或运行期轮换。
- 导出权限：住宿名单、签到记录、全量报名数据属于敏感导出；生产应开启导出审批并记录操作日志。
- 部署边界：MySQL、Redis、MinIO 不应暴露到公网；Nginx 若开放 443 必须有真实 TLS 配置。
- 镜像供应链：生产镜像应固定版本 tag 或 digest，不使用 `latest`。
- 备份：MySQL、Redis 持久化数据和 MinIO 文件必须纳入备份；上线前验证恢复流程。
- TDuck 边界：MeetingOS 不直接修改 TDuck 表；通过 Webhook/API 同步并保留原始快照。
- 公共查询：座位和房间查询会涉及个人信息，应增加限流、验证码或短信校验。

## 后续版本

- 短信、邮件、微信公众号和小程序订阅通知。
- 胸牌打印、现场大屏、物料领取。
- 多会场、多场次、复杂排座规则。
- 离线签到、身份证识别、人脸或闸机对接。
- 自动分房、酒店房态、外部预订系统对接。
- 更完整的审计、备份、监控和告警体系。
