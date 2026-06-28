# MeetingOS API 概览

本文档定义 MVP 阶段的 API 边界和主要接口。具体字段以 OpenAPI 文档和后端 DTO 为准。

## 通用约定

- 后台接口统一前缀：`/api/admin`
- 公开接口统一前缀：`/api/public`
- TDuck Webhook：推荐 `/api/integrations/tduck/webhook` + `X-TDuck-Signature`，兼容 `/api/public/tduck/webhook` 与 `/api/tduck/webhook`
- 认证方式：浏览器后台接口使用 HttpOnly Cookie；脚本/联调可兼容 `Authorization: Bearer <jwt>`。
- 时间格式：ISO 8601，服务端统一按 `Asia/Shanghai` 处理展示。
- 分页参数：`page` 从 0 开始，`size` 默认 20，最大值 200。
- 错误响应为 `ApiResponse` 包装，包含 `success`、`code`、`message`、`data`、`timestamp`。

## 会议接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/meetings` | 创建会议 |
| `PUT` | `/api/admin/meetings/{meetingId}` | 修改会议 |
| `GET` | `/api/admin/meetings` | 查询会议列表 |
| `GET` | `/api/admin/meetings/{meetingId}` | 查询会议详情 |
| `POST` | `/api/admin/meetings/{meetingId}/publish` | 发布会议 |
| `POST` | `/api/admin/meetings/{meetingId}/close-registration` | 截止报名 |
| `POST` | `/api/admin/meetings/{meetingId}/ready` | 标记待开始 |
| `POST` | `/api/admin/meetings/{meetingId}/start` | 开始会议 |
| `POST` | `/api/admin/meetings/{meetingId}/end` | 结束会议 |
| `POST` | `/api/admin/meetings/{meetingId}/archive` | 归档会议 |
| `POST` | `/api/admin/meetings/{meetingId}/status` | 通用状态流转 |
| `DELETE` | `/api/admin/meetings/{meetingId}` | 删除会议，MVP 建议软删除 |

关键状态：`DRAFT`、`REGISTRATION_OPEN`、`REGISTRATION_CLOSED`、`READY`、`IN_PROGRESS`、`ENDED`、`ARCHIVED`。

## TDuck 报名表接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/tduck/bindings` | 绑定 TDuck 表单，兼容 `/api/tduck/bindings` |
| `GET` | `/api/admin/tduck/bindings/{meetingId}` | 查询绑定状态，兼容 `/api/tduck/bindings/{meetingId}` |
| `PUT` | `/api/admin/tduck/mappings/{meetingId}` | 保存字段映射，兼容 `/api/tduck/mappings/{meetingId}` |
| `GET` | `/api/admin/tduck/mappings/{meetingId}` | 查询字段映射，兼容 `/api/tduck/mappings/{meetingId}` |
| `GET` | `/api/admin/tduck/sync-logs?meetingId={meetingId}` | 查询同步日志，兼容 `/api/tduck/sync-logs` |
| `POST` | `/api/admin/tduck/sync/manual` | 手工补偿同步单条 TDuck 报名数据 |
| `POST` | `/api/admin/tduck/sync/pull` | 使用 TDuck 管理接口按 dataId 拉取单条报名详情并补偿同步 |
| `POST` | `/api/integrations/tduck/webhook` | 接收 TDuck Webhook |

Webhook 优先校验基于原始 body 的 `X-TDuck-Signature`，兼容 `X-MeetingOS-Webhook-Token` 和仅内网 fallback 的 query token。校验失败不应返回具体密钥错误细节。
`form_data_delete` 以及删除/撤回类事件会按 `formKey + dataId` 定位原参会人并自动置为 `CANCELLED`，保留参会人记录和同步日志用于审计，不做物理删除。
补偿同步复用字段映射、手机号唯一和同步日志幂等规则；`manual` 适合导入 TDuck 导出的单条 payload，`pull` 会通过 `TDUCK_ADMIN_EMAIL`/`TDUCK_ADMIN_PASSWORD` 登录 TDuck 后读取 `/user/form/data/details/{dataId}`。

## 公开报名中转

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/r/{meetingCode}` | 会议报名二维码入口，由前端或后端中转 |
| `GET` | `/api/public/meetings/{meetingCode}/registration` | 查询报名状态和 TDuck 跳转地址 |

推荐流程：

1. 用户访问 `/r/{meetingCode}`。
2. MeetingOS 查询会议状态、报名时间和绑定表单。
3. 若可报名，跳转 `/tduck/s/{formKey}`。
4. 若不可报名，展示报名未开始、已截止或会议不存在提示。

## 参会人接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/attendees` | 新增参会人 |
| `GET` | `/api/admin/attendees?meetingId={meetingId}` | 查询参会人列表，支持 `keyword`、`reviewStatus`、`checkinStatus`、`attendeeType`、`hasSeat`、`tags` |
| `GET` | `/api/admin/attendees/{attendeeId}` | 查询参会人详情 |
| `PUT` | `/api/admin/attendees/{attendeeId}` | 修改参会人 |
| `PUT` | `/api/admin/attendees/{attendeeId}/review` | 单个审核 |
| `PUT` | `/api/admin/attendees/batch-review` | 批量审核 |
| `PUT` | `/api/admin/attendees/{attendeeId}/tags` | 修改标签 |
| `DELETE` | `/api/admin/attendees/{attendeeId}` | 取消参会 |
| `POST` | `/api/admin/attendees/import?meetingId={meetingId}` | 导入参会人，multipart 字段名 `file` |
| `GET` | `/api/admin/attendees/export?meetingId={meetingId}&type=attendees` | 导出参会人 CSV |

导入统一使用 `.xlsx` 模板，可识别中文和英文字段名。失败行通过 `failedRows[{rowNumber, reason}]` 返回。
审核状态：`PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`。
后台列表和详情默认对手机号、证件号、邮箱脱敏；CSV 导出保留原始字段，但需要会议 `MANAGER` 权限并写操作日志。

## 座位接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/seating/layout` | 创建或重建座位布局 |
| `GET` | `/api/admin/seating/map/{meetingId}` | 查询座位图 |
| `POST` | `/api/admin/seating/auto-assign/{meetingId}` | 自动排座 |
| `POST` | `/api/admin/seating/manual-assign` | 手动分配座位 |
| `POST` | `/api/admin/seating/swap` | 交换座位 |
| `POST` | `/api/admin/seating/clear/{attendeeId}` | 清空参会人座位 |
| `POST` | `/api/admin/seating/lock/{seatId}` | 锁定或解锁座位 |
| `GET` | `/api/admin/exports/download?meetingId={meetingId}&type=seats` | 导出座位表 |
| `POST` | `/api/public/seat-query` | 个人座位查询，按会议查询配置校验字段 |
| `GET` | `/api/public/seat-query?meetingId={meetingId}&phone={phone}` | 个人座位查询旧接口兼容 |

自动排座不得覆盖已锁定座位和已有手动分配结果。

## 住宿接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/rooms` | 录入房间号 |
| `POST` | `/api/admin/rooms/import?meetingId={meetingId}` | 导入房间号，multipart 字段名 `file` |
| `GET` | `/api/admin/rooms?meetingId={meetingId}` | 查询房间列表 |
| `POST` | `/api/admin/rooms/assign` | 给嘉宾分配房间 |
| `POST` | `/api/admin/rooms/assignments/import?meetingId={meetingId}` | 导入房间分配 |
| `GET` | `/api/admin/rooms/export?meetingId={meetingId}` | 导出住宿安排 |
| `POST` | `/api/public/room-query` | 个人房间查询，按会议查询配置校验字段 |
| `GET` | `/api/public/room-query?meetingId={meetingId}&phone={phone}` | 个人房间查询旧接口兼容 |

房间分配导入支持按手机号、证件号、姓名+单位匹配参会人。MVP 允许多人共用同一房间，不做酒店房态容量校验。
房间手动分配和导入分配会写 `ROOM_ASSIGNMENT_CHANGE` 操作日志，包含原房间、新房间、参会人和来源。

## 签到接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/checkin` | 扫码、手机号或补签入口 |
| `GET` | `/api/admin/checkin/records?meetingId={meetingId}` | 查询签到记录 |
| `GET` | `/api/admin/statistics/overview?meetingId={meetingId}` | 查询签到统计概览 |
| `GET` | `/api/admin/exports/download?meetingId={meetingId}&type=checkin-records` | 导出签到数据 |

签到码格式：

```text
MEETINGOS-CHECKIN:{meetingId}:{attendeeCode}
/public/checkin-code?type=checkin&meetingId={meetingId}&attendeeCode={attendeeCode}#MEETINGOS-CHECKIN:{meetingId}:{attendeeCode}
```

重复签到必须返回首次签到时间、入口和操作人，不覆盖首次签到记录。
当 `checkinType=SUPPLEMENT` 时，`remark` 必填，用于补签审计。

## 统计和日志接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/statistics/overview?meetingId={meetingId}` | 报名、座位、住宿、签到概览 |
| `POST` | `/api/admin/exports?meetingId={meetingId}&type={type}` | 创建导出任务，MVP 同步完成 |
| `GET` | `/api/admin/exports?meetingId={meetingId}` | 查询已创建导出任务 |
| `GET` | `/api/admin/exports/download?meetingId={meetingId}&type={type}` | 下载 CSV |
| `GET` | `/api/admin/operation-logs` | 操作日志 |
| `GET` | `/api/admin/settings` | 读取系统设置，不返回 Webhook Token 明文 |
| `PUT` | `/api/admin/settings` | 保存系统设置，Webhook Token 留空表示不轮换 |
| `GET` | `/api/admin/meetings/{meetingId}/query-settings` | 查询会议公开座位/住宿查询配置 |
| `PUT` | `/api/admin/meetings/{meetingId}/query-settings` | 保存会议公开座位/住宿查询配置 |
| `POST` | `/api/admin/meetings/{meetingId}/queries/seat` | 后台座位查询候选，最多返回 20 条 |
| `POST` | `/api/admin/meetings/{meetingId}/queries/room` | 后台住宿查询候选，最多返回 20 条 |

导出类型：`attendees`、`approved`、`seats`、`rooms`、`checkin-records`、`no-checkin`、`room-inventory`。
操作日志暂仅 `SUPER_ADMIN` 可查看；日志 details 会递归脱敏手机号、证件号、邮箱、Token、密码和密钥类字段。

## 用户与会议授权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/users` | 查询用户列表，不返回密码哈希 |
| `POST` | `/api/admin/users` | 创建用户并设置角色 |
| `PUT` | `/api/admin/users/{userId}` | 修改显示名、状态和角色 |
| `POST` | `/api/admin/users/{userId}/reset-password` | 重置用户密码 |
| `GET` | `/api/admin/permissions/meeting-users?meetingId={meetingId}` | 查询会议授权 |
| `POST` | `/api/admin/permissions/meeting-users` | 授予用户会议角色 |
| `DELETE` | `/api/admin/permissions/meeting-users/{permissionId}` | 删除会议授权 |

会议授权角色：`MANAGER`、`CHECKIN`、`ROOM`、`VIEWER`。`SUPER_ADMIN` 可访问全部会议；其他账号必须同时具备全局角色和会议授权，才能访问对应会议数据。会议管理员创建会议后会自动获得该会议 `MANAGER` 授权。

## 公共查询安全

- `/api/public/seat-query` 和 `/api/public/room-query` 使用本机滑动窗口限流。
- 默认单 IP 每分钟 30 次，可通过 `PUBLIC_QUERY_RATE_LIMIT_PER_MINUTE` 调整。
- `GET /api/public/query-settings?meetingId=...|meetingCode=...` 返回会议查询开关、查询字段和匹配方式。
- 公开查询仅返回唯一匹配、审核通过且已安排座位/房间的参会人信息；模糊命中多人时只提示补充信息，不返回候选名单。
