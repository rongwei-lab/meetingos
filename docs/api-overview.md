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
| `GET` | `/api/admin/tduck/sync-logs/{syncLogId}?meetingId={meetingId}` | 查询该次同步保存的完整表单快照，不返回 Webhook 原始载荷 |
| `GET` | `/api/admin/tduck/sync-conflicts?meetingId={meetingId}` | 查询同手机号、不同 `dataId` 的待处理报名，兼容 `/api/tduck/sync-conflicts` |
| `GET` | `/api/admin/tduck/sync-conflicts/{submissionId}?meetingId={meetingId}` | 查询一条待处理报名与当前参会人资料的完整对比 |
| `POST` | `/api/admin/tduck/sync-conflicts/{submissionId}/resolve` | 裁决重复报名；请求为 `meetingId`、`resolution`（`KEEP_EXISTING` 或 `ADOPT_INCOMING`）和可选 `note` |
| `POST` | `/api/admin/tduck/sync/manual` | 手工补偿同步单条 TDuck 报名数据 |
| `POST` | `/api/admin/tduck/sync/pull` | 使用 TDuck 管理接口按 dataId 拉取单条报名详情并补偿同步 |
| `POST` | `/api/admin/tduck/sync/pull/all` | 创建全量拉取同步任务，参数为 `meetingId`、可选 `limit` 和 `idempotencyKey` |
| `GET` | `/api/admin/tduck/sync/pull/tasks/{taskId}?meetingId={meetingId}` | 查询全量拉取同步任务进度和有限失败样例 |
| `POST` | `/api/integrations/tduck/webhook` | 接收 TDuck Webhook |

Webhook 优先校验基于原始 body 的 `X-TDuck-Signature`，兼容 `X-MeetingOS-Webhook-Token` 和仅内网 fallback 的 query token。校验失败不应返回具体密钥错误细节。
`form_data_delete` 以及删除/撤回类事件会按 `formKey + dataId` 定位原参会人并自动置为 `CANCELLED`，保留参会人记录和同步日志用于审计，不做物理删除。
补偿同步复用字段映射、手机号唯一和同步日志幂等规则；`manual` 适合导入 TDuck 导出的单条 payload，`pull` 会通过 `TDUCK_ADMIN_EMAIL`/`TDUCK_ADMIN_PASSWORD` 登录 TDuck 后读取 `/user/form/data/details/{dataId}`。`pull/all` 返回持久化任务，任务按会议互斥，使用幂等键、租约和独立有界执行器保证重试与服务重启后的恢复。

同一会议同一手机号出现不同 TDuck `dataId` 时，系统保留一个参会人和多条提交历史，不自动覆盖签到、座位、房间或晚宴分配。明显较早的提交标记为 `DUPLICATE_IGNORED`；新提交进入待处理队列。管理员选择 `ADOPT_INCOMING` 时只替换报名资料和当前 `dataId`，保留参会人 ID 及会务下游数据；选择 `KEEP_EXISTING` 时新提交保留为历史忽略记录。当前来源撤回时只会恢复曾被替代的 `SUPERSEDED` 提交。

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

## 邀请函与渠道归因

管理接口统一使用会议级权限：查看和统计要求 `canView`，创建、设计、发布、渠道和素材操作要求
`canManage`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` / `POST` | `/api/admin/meetings/{meetingId}/invitations` | 查询或创建邀请方案 |
| `GET` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}` | 查询草稿和最新发布版本 |
| `PUT` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/draft` | 按 `expectedRevision` 乐观锁保存草稿 |
| `POST` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/copy` | 复制为新草稿 |
| `POST` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/publish` | 创建不可变发布版本 |
| `POST` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/unpublish` | 下线全部公开链接 |
| `POST` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/archive` | 归档并保留历史数据 |
| `GET` / `POST` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/share-links` | 查询或新增渠道链接 |
| `PUT` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/share-links/{shareLinkId}` | 修改渠道、启停或失效时间 |
| `GET` / `POST` | `/api/admin/meetings/{meetingId}/invitations/assets` | 查询或上传会议级图片素材 |
| `GET` | `/api/admin/meetings/{meetingId}/invitations/analytics` | 按日期和方案查询 PV、UV、点击与报名转化 |
| `GET` | `/api/admin/meetings/{meetingId}/invitations/{invitationId}/attributions` | 查询脱敏报名归因明细 |
| `GET` | `/api/public/invitations/{shareToken}` | 获取当前有效发布版本、引用素材和实时 `publicQueryToken` |
| `POST` | `/api/public/invitations/{shareToken}/views` | 页面可见后按客户端 UUID 幂等记录访问 |
| `POST` | `/api/public/invitations/{shareToken}/registration-intents` | 记录报名点击并生成限时归因 token |

公开分享入口为 `/i/{shareToken}`。每个邀请方案可以同时维护多个预置或自定义渠道，渠道停用后旧链接
不可访问，但历史事件和报名归因不删除。草稿绑定会议最新字段；发布时保存会议快照，后续会议资料变更
必须重新发布才会影响公开内容。

报名按钮继续经过 `/r/{meetingCode}` 的报名开放校验。归因 token 使用 URL fragment `mos_at` 传给
TDuck 前端，部署补丁将其放入答卷 `extValue`，Webhook 和补偿拉取再写入 MeetingOS 归因表。token
无效、过期或会议/表单不匹配只影响归因，不能阻断报名同步。管理员复制链接或下载二维码只代表生成
分享材料，不计作真实触达。

H5 还可配置独立签到按钮和单目标查询按钮。签到进入 `/public/checkin`，查询可选择公开入口、综合、座位、
住宿或晚宴页面，均使用响应中的随机 `publicQueryToken`，不会调用报名意图接口或计入报名 CTA。二维码
同样可选择报名或上述查询用途；报名二维码携带渠道 `shareToken` 到 `/r/{meetingCode}` 换取 `mos_at`，
查询二维码不参与报名归因。设计 JSON 只保存用途枚举，不保存查询 token 或任意 URL。

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
| `POST` | `/api/admin/attendees/import?meetingId={meetingId}` | 兼容导入参会人，multipart 字段名 `file`；新界面使用异步导入任务 |
| `GET` | `/api/admin/attendees/export?meetingId={meetingId}&type=attendees` | 导出参会人 XLSX；显式 `format=csv` 兼容旧调用 |

导入统一使用 `.xlsx` 模板，可识别中文和英文字段名。异步导入任务会生成 XLSX 失败明细。
审核状态：`PENDING`、`APPROVED`、`REJECTED`、`CANCELLED`。
后台列表、详情和导出默认对手机号、证件号、邮箱脱敏；关闭脱敏需要会议管理权限，下载会写操作日志。

## 异步导入任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/imports?meetingId={meetingId}&type={type}&preflight=true&idempotencyKey={key}` | 上传 XLSX 并创建文件格式检查任务，multipart 字段名 `file` |
| `GET` | `/api/admin/imports/{taskId}?meetingId={meetingId}` | 查询导入任务状态和进度 |
| `GET` | `/api/admin/imports?meetingId={meetingId}&page=0&size=20` | 分页查询导入任务记录 |
| `POST` | `/api/admin/imports/{taskId}/commit?meetingId={meetingId}` | 文件格式检查通过后启动正式导入 |
| `GET` | `/api/admin/imports/{taskId}/failures?meetingId={meetingId}` | 下载 XLSX 失败明细 |

`type` 支持 `attendees`、`rooms`、`room-assignments`、`dinner`。同一幂等键的已完成任务直接返回原结果；失败或文件过期后可安全替换输入并重试。文件格式检查只验证 XLSX、表头和文本格式，业务行规则在正式导入时逐行校验并写入失败明细。

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

## 晚宴接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/admin/dinner?meetingId={meetingId}` | 查询晚宴安排列表 |
| `GET` | `/api/admin/dinner/tables?meetingId={meetingId}` | 查询晚宴桌号列表，返回可就餐人数、已安排人数、还缺人数和超员人数 |
| `GET` | `/api/admin/dinner/settings?meetingId={meetingId}` | 查询整场晚宴地点和时间 |
| `PUT` | `/api/admin/dinner/settings` | 保存整场晚宴地点和时间 |
| `POST` | `/api/admin/dinner/tables` | 新增晚宴桌号，配置每桌可就餐人数和备注 |
| `PUT` | `/api/admin/dinner/tables/{tableId}` | 修改晚宴桌号；调整容量时不能小于当前已安排人数 |
| `DELETE` | `/api/admin/dinner/tables/{tableId}?meetingId={meetingId}` | 删除晚宴桌号；已有就餐人员时禁止删除 |
| `POST` | `/api/admin/dinner/assign` | 手动保存某参会人的晚宴桌号 |
| `POST` | `/api/admin/dinner/clear?attendeeId={attendeeId}` | 清空某参会人的晚宴安排 |
| `POST` | `/api/admin/dinner/import?meetingId={meetingId}` | 导入晚宴分配 `.xlsx`，multipart 字段名 `file` |
| `GET` | `/api/admin/dinner/export?meetingId={meetingId}` | 导出晚宴安排 `.xlsx` |
| `POST` | `/api/public/dinner-query` | 个人晚宴桌号查询，按会议公开查询配置校验字段 |

晚宴粒度固定为桌号，不提供席位号、多餐次或自动分桌。晚宴地点和时间是会议级信息，由 `/api/admin/dinner/settings` 维护，不跟随桌号或参会人分配。桌号容量由 `dinner_tables` 管理，手动安排和导入分配会校验目标桌是否已满；导入旧格式分配表时如果桌号尚未维护，会自动补一张默认 10 人桌，管理员可随后调整容量。导入分配支持按手机号、证件号、姓名+单位匹配参会人；同一会议同一参会人重复导入时更新原安排。晚宴专用导出固定为 `.xlsx`，表头包含姓名、手机号、单位、职务、参会类型、桌号、晚宴地点、晚宴时间、备注。
公开晚宴查询复用座位/住宿查询的身份字段、验证码、开放时间、限流和审核通过校验；仅在参会人审核通过且已安排晚宴时返回姓名、会议名、晚宴地点、晚宴时间、桌号和备注。

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
| `GET` | `/api/admin/statistics/overview?meetingId={meetingId}` | 报名、座位、住宿、晚宴、签到概览 |
| `POST` | `/api/admin/exports?meetingId={meetingId}&type={type}` | 创建异步 XLSX 导出任务 |
| `GET` | `/api/admin/exports?meetingId={meetingId}` | 分页查询导出任务和进度 |
| `GET` | `/api/admin/exports/{taskId}/download?meetingId={meetingId}` | 下载任务生成的固定 XLSX 文件 |
| `GET` | `/api/admin/exports/download?meetingId={meetingId}&type={type}` | 兼容同步下载，默认 XLSX；显式 `format=csv` 兼容旧调用 |
| `GET` | `/api/admin/operation-logs?category={category}&success={true|false}&fromDate={yyyy-MM-dd}&toDate={yyyy-MM-dd}&keyword={keyword}&includeDraftSaves={true|false}&page={page}&size={size}` | 操作日志；响应头 `X-MeetingOS-Log-Snapshot` 用于稳定分页 |
| `GET` | `/api/admin/settings` | 读取系统设置，Webhook Token 仅返回脱敏状态 |
| `PUT` | `/api/admin/settings` | 保存系统设置，忽略请求中的 Webhook Token，密钥仅由 `TDUCK_WEBHOOK_TOKEN` 管理 |
| `GET` | `/api/admin/meetings/{meetingId}/query-settings` | 查询会议公开座位/住宿/晚宴/签到配置 |
| `PUT` | `/api/admin/meetings/{meetingId}/query-settings` | 保存会议公开座位/住宿/晚宴/签到配置 |
| `POST` | `/api/admin/meetings/{meetingId}/queries/seat` | 后台座位查询候选，最多返回 20 条 |
| `POST` | `/api/admin/meetings/{meetingId}/queries/room` | 后台住宿查询候选，最多返回 20 条 |

导出类型：`attendees`、`approved`、`seats`、`rooms`、`checkin-records`、`no-checkin`、`room-inventory`、`statistics`。晚宴管理页使用专用 `/api/admin/dinner/export` 下载 `.xlsx`。
操作日志暂仅 `SUPER_ADMIN` 可查看；列表响应不包含客户端 IP 和 details。分类由 action 实时推导，默认不展示邀请函草稿保存；调用方应在后续分页请求中回传首次响应的 `X-MeetingOS-Log-Snapshot`。日志 details 会递归脱敏手机号、证件号、邮箱、Token、密码和密钥类字段。

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

- `/api/public/seat-query`、`/api/public/room-query`、`/api/public/dinner-query`、`/api/public/checkin` 和 `/api/public/checkin-preview` 使用本机滑动窗口限流。
- 默认单 IP 每分钟 30 次，可通过 `PUBLIC_QUERY_RATE_LIMIT_PER_MINUTE` 调整。
- `GET /api/public/query-settings?meetingId=...|meetingCode=...` 返回会议查询开关、查询字段、必填字段和匹配方式；未填写的可选字段不会参与查询，填写后会与必填字段一起参与匹配。
- 公开查询仅返回唯一匹配、审核通过且已安排座位/房间/晚宴的参会人信息；模糊命中多人时只提示补充信息，不返回候选名单。
- 会议查询配置包含 `dinnerQueryEnabled` 和 `dinnerNotice`。`dinnerNotice` 默认值为“请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。”。
- 查询历史 `queryType` 支持 `SEAT`、`ROOM`、`DINNER`、`CHECKIN`，会记录晚宴查询成功/失败；默认保留 180 天，后端按配置分批清理过期记录。
- `POST /api/public/checkin-preview` 仅在关闭“查询后立即签到”时用于核验身份，沿用限流与验证码校验，不写入签到状态；前端确认后仍调用 `/api/public/checkin` 完成签到。
