# MeetingOS 生产数据库数据字典

结构快照：2026-10-15。数据库：`meetingos`。MySQL：8.4.10。共 31 张表、433 个字段、134 个索引、45 条外键关系。包括 Flyway 迁移历史表，不包含 TDuck 独立数据库。

## 来源与取数口径

| 项目 | 说明 |
| --- | --- |
| 结构来源 | 2026-10-15 生产库 meetingos 的 information_schema；不含 TDuck 独立库和业务记录。 |
| 含义来源 | 服务器配套 Java 实体、状态枚举与业务服务。生产库表和字段中文注释均为空。 |
| 默认值 | “NULL / 未设置”表示元数据未返回默认值，不能据此区分显式 DEFAULT NULL 和未设置默认值。Java 初始化值不等于数据库默认值。 |
| 唯一性 | 联合唯一索引约束的是一组字段。attendees.phone 仅在同一 meeting_id 内唯一，NULL 可重复。 |
| 报名口径 | 报名参会人使用 attendees，现场未报名人员通过 unregistered=1 区分。审核状态另看 review_status。 |
| 提交口径 | tduck_submissions 是提交历史，一名参会人可能多次提交；不可将其条数直接当作报名人数。 |
| 签到口径 | 当前已签到人数按 attendees.checkin_status=CHECKED_IN；checkin_records 包含重复及失败尝试，成功操作按 result_status=SUCCESS 过滤。 |
| 房间与晚宴 | attendees 与 rooms 按 (meeting_id,room_no) 关联；dinner_assignments 与 dinner_tables 按 (meeting_id,table_no) 关联。两者均没有物理外键。 |
| 当前座位 | seats 包含禁用、过道和结构留白位置。可分配座位统计应排除 disabled=1、aisle=1 和 structural_blank=1。 |
| 归因口径 | registration_attributions.active=1 表示当前有效归因；来源分组使用 attribution_status，先区分直接报名、无效令牌与历史数据。 |
| 增量同步 | 多数表有 created_at/updated_at，可按 updated_at 与 id 组合水位并保留重叠窗口；物理删除无法仅凭更新时间捕获，需 CDC 或定期全量对账。app_user_roles 无时间字段。 |
| 时区 | MySQL 采集会话时区为 +08:00，系统时区为 CST。datetime(6) 不携带时区；跨系统同步须约定转换规则。 |
| JSON 扩展字段 | 原始报名快照、邀请函设计和看板配置为 JSON；其内部键不属于物理列，随表单或配置变化。 |
| 共享字段 | 字典含完整结构说明，不含任何业务记录或连接密码。账号具有整库 SELECT 权限，密码哈希、访问令牌和原始快照属于需要单独筛选的字段。 |

## 表总览

| 模块 | 表名 | 中文表名 | 数据粒度 | 字段数 | 主键 | 增量同步提示 |
| --- | --- | --- | --- | --- | --- | --- |
| 账户与权限 | app_user_roles | 后台用户角色 | 每行一个用户角色关联 | 2 | 无声明主键 | 全量或 CDC |
| 账户与权限 | app_users | 后台用户账户 | 每行一个后台登录账号 | 7 | id | updated_at + id，需处理物理删除 |
| 报名与签到 | attendees | 参会人主表 | 每行一场会议的一名参会人，包含现场未报名人员 | 30 | id | updated_at + id，需处理物理删除 |
| 报名与签到 | checkin_records | 签到操作记录 | 每行一次签到尝试，可能成功、重复或失败 | 12 | id | updated_at + id，需处理物理删除 |
| 住宿与晚宴 | dinner_assignments | 晚宴分桌结果 | 每行一名参会人在一场会议的桌号分配 | 10 | id | updated_at + id，需处理物理删除 |
| 住宿与晚宴 | dinner_tables | 晚宴桌配置 | 每行一场会议的一张晚宴桌 | 10 | id | updated_at + id，需处理物理删除 |
| 导入与导出 | export_tasks | 导出任务 | 每行一个导出任务及生成文件元数据 | 19 | id | updated_at + id，需处理物理删除 |
| 技术元数据 | flyway_schema_history | 数据库迁移历史 | 每行一次数据库迁移记录 | 10 | installed_rank | 技术表，通常不接入分析 |
| 导入与导出 | import_tasks | 导入任务 | 每行一个导入任务及处理进度 | 21 | id | updated_at + id，需处理物理删除 |
| 邀请函与归因 | invitation_assets | 邀请函素材 | 每行一个上传素材的文件元数据 | 12 | id | updated_at + id，需处理物理删除 |
| 邀请函与归因 | invitation_events | 邀请函访问事件 | 每行一次去重后的页面访问或按钮点击事件 | 15 | id | updated_at + id，需处理物理删除 |
| 邀请函与归因 | invitation_share_links | 邀请函渠道链接 | 每行一个分享渠道链接 | 12 | id | updated_at + id，需处理物理删除 |
| 会议与场地 | meeting_dashboard_configs | 会议看板配置 | 每场会议一份统计看板配置 | 5 | id | updated_at + id，需处理物理删除 |
| 邀请函与归因 | meeting_invitation_versions | 邀请函发布版本 | 每行一个邀请函的发布版本及会议快照 | 8 | id | updated_at + id，需处理物理删除 |
| 邀请函与归因 | meeting_invitations | 邀请函主表 | 每行一个邀请函，包含草稿和发布版本引用 | 13 | id | updated_at + id，需处理物理删除 |
| 账户与权限 | meeting_user_permissions | 会议用户权限 | 每行一个会议与用户的权限关联 | 7 | id | updated_at + id，需处理物理删除 |
| 会议与场地 | meetings | 会议主表 | 每行一场会议，包含公开查询和席签配置 | 65 | id | updated_at + id，需处理物理删除 |
| 通知与审计 | notification_records | 通知记录 | 每行一条通知配置或记录，不代表实际送达回执 | 10 | id | updated_at + id，需处理物理删除 |
| 通知与审计 | operation_logs | 操作日志 | 每行一次后台或公共业务操作审计 | 11 | id | updated_at + id，需处理物理删除 |
| 通知与审计 | public_query_history | 公开查询历史 | 每行一次公开查询结果记录 | 15 | id | updated_at + id，需处理物理删除 |
| 邀请函与归因 | registration_attributions | 报名来源归因 | 每行一次 TDuck 数据对应的报名归因 | 14 | id | updated_at + id，需处理物理删除 |
| 住宿与晚宴 | rooms | 住宿房间 | 每行一场会议的一个房间 | 10 | id | updated_at + id，需处理物理删除 |
| 会议与场地 | seat_assignments | 座位分配 | 每行一名参会人与一个座位的当前分配 | 8 | id | updated_at + id，需处理物理删除 |
| 会议与场地 | seat_layouts | 座位布局 | 每行一个会议座位分区布局 | 12 | id | updated_at + id，需处理物理删除 |
| 会议与场地 | seats | 座位明细 | 每行一个座位或布局网格位置，可能是过道或留白 | 14 | id | updated_at + id，需处理物理删除 |
| 账户与权限 | system_settings | 系统设置 | 每行一个设置键值 | 5 | id | updated_at + id，需处理物理删除 |
| TDuck 集成 | tduck_field_mappings | 报名字段映射 | 每行一个 TDuck 字段到参会人字段的映射 | 8 | id | updated_at + id，需处理物理删除 |
| TDuck 集成 | tduck_form_bindings | 报名表单绑定 | 每场会议一个绑定，表单标识全局唯一 | 11 | id | updated_at + id，需处理物理删除 |
| TDuck 集成 | tduck_pull_sync_tasks | TDuck 拉取任务 | 每行一次历史数据拉取任务 | 21 | id | updated_at + id，需处理物理删除 |
| TDuck 集成 | tduck_submissions | TDuck 提交历史 | 每行一场会议的一条 TDuck 提交，含重复资料裁决状态 | 23 | id | updated_at + id，需处理物理删除 |
| TDuck 集成 | tduck_sync_logs | TDuck 同步日志 | 每行一次同步事件处理记录，含失败和忽略事件 | 13 | id | updated_at + id，需处理物理删除 |

## 字段字典

### app_user_roles 后台用户角色

每行一个用户角色关联。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | user_id | 用户 ID | bigint | 否 | NULL / 未设置 |  | app_users.id |  |  |
| 2 | role | 用户角色 | varchar(32) | 否 | NULL / 未设置 |  |  | Role | 一个用户可有多个角色。本表无主键和更新时间，抽取需按用户与角色去重。 |

### app_users 后台用户账户

每行一个后台登录账号。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | username | 登录用户名 | varchar(64) | 否 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 5 | display_name | 用户显示名称 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | password_hash | 登录密码哈希 | varchar(255) | 否 | NULL / 未设置 |  |  |  | 访问凭证或密码派生信息 |
| 7 | status | 后台账号状态 | varchar(32) | 否 | NULL / 未设置 |  |  | UserStatus |  |

### attendees 参会人主表

每行一场会议的一名参会人，包含现场未报名人员。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | name | 参会人姓名 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | phone | 手机号 | varchar(40) | 是 | NULL / 未设置 | 联合唯一索引成员 |  |  | 与 meeting_id 组成联合唯一索引，手机号不是全库唯一；NULL 可重复。 个人信息字段 |
| 7 | organization | 单位或组织 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | title | 职务 | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | identity_no | 证件号码 | varchar(80) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 10 | email | 电子邮箱 | varchar(160) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 11 | attendee_type | 参会人类型 | varchar(32) | 否 | NULL / 未设置 |  |  | AttendeeType |  |
| 12 | accommodation_required | 是否需要住宿 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 13 | room_no | 房间号 | varchar(80) | 是 | NULL / 未设置 |  |  |  | 按 (meeting_id, room_no) 关联 rooms；属于业务关联，没有声明外键。 |
| 14 | room_remark | 住宿备注 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 15 | remark | 备注 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 16 | tags | 标签文本 | varchar(300) | 是 | NULL / 未设置 |  |  |  | 物理类型为 varchar(300)，不是 JSON 列。 |
| 17 | review_status | 审核状态 | varchar(32) | 否 | NULL / 未设置 |  |  | ReviewStatus |  |
| 18 | review_reason | 审核原因 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 19 | review_remark | 审核备注 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 20 | reviewed_at | 审核时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 21 | reviewed_by | 审核人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 22 | registered_at | 报名时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 23 | checkin_status | 签到状态 | varchar(32) | 否 | NULL / 未设置 |  |  | CheckinStatus |  |
| 24 | checked_in_at | 签到时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 25 | checkin_entrance | 签到入口 | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |
| 26 | checkin_operator | 签到操作人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 27 | attendee_code | 参会人编码 | varchar(80) | 否 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 28 | tduck_data_id | TDuck 数据 ID | varchar(80) | 是 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 29 | raw_data_snapshot | 原始报名资料快照 | json | 是 | NULL / 未设置 |  |  |  | 包含扩展报名字段；具体 JSON 键由绑定表单及字段映射决定。 |
| 30 | unregistered | 是否为现场未报名人员 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 | 1 表示现场未报名入座人员，可无手机号和报名时间。统计线上报名时应单独过滤。 |

### checkin_records 签到操作记录

每行一次签到尝试，可能成功、重复或失败。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | attendee_id | 参会人 ID | bigint | 是 | NULL / 未设置 |  | attendees.id |  |  |
| 6 | checkin_type | 签到方式 | varchar(32) | 否 | NULL / 未设置 |  |  | CheckinType |  |
| 7 | result_status | 签到尝试结果 | varchar(32) | 否 | NULL / 未设置 |  |  | CheckinResultStatus | 重复扫码和未审核等也生成记录；操作次数不等于已签到人数。 |
| 8 | entrance | 签到入口 | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | operator | 操作人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 10 | checked_in_at | 签到时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 11 | message | 处理消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 12 | request_snapshot | 请求数据快照 | json | 是 | NULL / 未设置 |  |  |  |  |

### dinner_assignments 晚宴分桌结果

每行一名参会人在一场会议的桌号分配。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | attendee_id | 参会人 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | attendees.id |  |  |
| 6 | table_no | 晚宴桌号 | varchar(80) | 否 | NULL / 未设置 |  |  |  | 按 (meeting_id, table_no) 关联 dinner_tables；没有声明外键。 |
| 7 | venue | 场地名称 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | dinner_time | 晚宴时间文本 | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | remark | 备注 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 10 | assigned_by | 分配人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### dinner_tables 晚宴桌配置

每行一场会议的一张晚宴桌。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | table_no | 晚宴桌号 | varchar(80) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 6 | capacity | 容量上限 | int | 否 | 10 |  |  |  |  |
| 7 | venue | 场地名称 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | dinner_time | 晚宴时间文本 | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | remark | 备注 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 10 | created_by | 创建人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### export_tasks 导出任务

每行一个导出任务及生成文件元数据。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | export_type | 导出类型 | varchar(64) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | format | 导出文件格式 | varchar(16) | 否 | 'xlsx' |  |  | ExportFormat |  |
| 7 | start_date | 筛选开始日期 | date | 是 | NULL / 未设置 |  |  |  |  |
| 8 | end_date | 筛选结束日期 | date | 是 | NULL / 未设置 |  |  |  |  |
| 9 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | ExportTaskStatus |  |
| 10 | progress | 任务进度百分比 | int | 否 | 0 |  |  |  |  |
| 11 | filename | 文件名称 | varchar(160) | 否 | NULL / 未设置 |  |  |  |  |
| 12 | file_path | 生成文件路径 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 13 | file_size | 文件大小（字节） | bigint | 是 | NULL / 未设置 |  |  |  |  |
| 14 | checksum | 校验和 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 15 | requested_by | 请求人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 16 | completed_at | 完成时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 17 | generated_at | 文件生成时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 18 | expires_at | 过期时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 19 | message | 处理消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |

### flyway_schema_history 数据库迁移历史

每行一次数据库迁移记录。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | installed_rank | 迁移安装顺序 | int | 否 | NULL / 未设置 | 主键 |  |  |  |
| 2 | version | 迁移版本号 | varchar(50) | 是 | NULL / 未设置 |  |  |  |  |
| 3 | description | 描述 | varchar(200) | 否 | NULL / 未设置 |  |  |  |  |
| 4 | type | 迁移类型 | varchar(20) | 否 | NULL / 未设置 |  |  |  |  |
| 5 | script | 迁移脚本名称 | varchar(1000) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | checksum | 校验和 | int | 是 | NULL / 未设置 |  |  |  |  |
| 7 | installed_by | 安装人 | varchar(100) | 否 | NULL / 未设置 |  |  |  |  |
| 8 | installed_on | 安装时间 | timestamp | 否 | CURRENT_TIMESTAMP |  |  |  |  |
| 9 | execution_time | 执行耗时（毫秒） | int | 否 | NULL / 未设置 |  |  |  |  |
| 10 | success | 是否成功 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |

### import_tasks 导入任务

每行一个导入任务及处理进度。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | import_type | 导入类型 | varchar(32) | 否 | NULL / 未设置 | 联合唯一索引成员 |  | ImportTaskType |  |
| 6 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | ImportTaskStatus |  |
| 7 | original_filename | 导入原始文件名 | varchar(255) | 否 | NULL / 未设置 |  |  |  |  |
| 8 | input_file_path | 输入文件存储路径 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | failure_file_path | 失败明细文件路径 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 10 | idempotency_key | 幂等键 | varchar(128) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 11 | preflight | 是否预检查 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 12 | total_rows | 总记录数 | int | 否 | 0 |  |  |  |  |
| 13 | processed_rows | 已处理记录数 | int | 否 | 0 |  |  |  |  |
| 14 | imported_rows | 导入成功记录数 | int | 否 | 0 |  |  |  |  |
| 15 | failed_rows | 失败记录数 | int | 否 | 0 |  |  |  |  |
| 16 | progress | 任务进度百分比 | int | 否 | 0 |  |  |  |  |
| 17 | requested_by | 请求人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 18 | started_at | 开始处理时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 19 | completed_at | 完成时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 20 | expires_at | 过期时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 21 | message | 处理消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |

### invitation_assets 邀请函素材

每行一个上传素材的文件元数据。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | original_name | 上传原始文件名 | varchar(255) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | storage_name | 素材存储名称 | varchar(160) | 否 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 7 | public_url | 素材公开 URL | varchar(500) | 否 | NULL / 未设置 |  |  |  |  |
| 8 | content_type | 文件 MIME 类型 | varchar(80) | 否 | NULL / 未设置 |  |  |  |  |
| 9 | size_bytes | 文件大小（字节） | bigint | 否 | NULL / 未设置 |  |  |  |  |
| 10 | width_px | 图片宽度（像素） | int | 是 | NULL / 未设置 |  |  |  |  |
| 11 | height_px | 图片高度（像素） | int | 是 | NULL / 未设置 |  |  |  |  |
| 12 | created_by | 创建人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### invitation_events 邀请函访问事件

每行一次去重后的页面访问或按钮点击事件。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | invitation_id | 邀请函 ID | bigint | 否 | NULL / 未设置 |  | meeting_invitations.id |  |  |
| 6 | version_id | 邀请函版本 ID | bigint | 否 | NULL / 未设置 |  | meeting_invitation_versions.id |  |  |
| 7 | share_link_id | 渠道分享链接 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | invitation_share_links.id |  |  |
| 8 | event_type | 事件类型 | varchar(32) | 否 | NULL / 未设置 |  |  | InvitationEventType |  |
| 9 | client_event_id | 客户端事件去重 ID | varchar(80) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 10 | visitor_hash | 访客不可逆哈希标识 | char(64) | 否 | NULL / 未设置 |  |  |  |  |
| 11 | occurred_at | 事件发生时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 12 | click_public_id | 公开点击事件标识 | varchar(80) | 是 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 13 | click_expires_at | 点击标识过期时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 14 | device_type | 设备类型 | varchar(24) | 是 | NULL / 未设置 |  |  |  |  |
| 15 | referrer_host | 来源站点主机名 | varchar(255) | 是 | NULL / 未设置 |  |  |  |  |

### invitation_share_links 邀请函渠道链接

每行一个分享渠道链接。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | invitation_id | 邀请函 ID | bigint | 否 | NULL / 未设置 |  | meeting_invitations.id |  |  |
| 6 | current_version_id | 当前引用的发布版本 ID | bigint | 是 | NULL / 未设置 |  | meeting_invitation_versions.id |  |  |
| 7 | channel_type | 分享渠道类型 | varchar(32) | 否 | NULL / 未设置 |  |  | InvitationChannelType |  |
| 8 | channel_name | 分享渠道名称 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 9 | share_token | 分享链接访问令牌 | varchar(96) | 否 | NULL / 未设置 | 单字段唯一 |  |  | 访问凭证或密码派生信息 |
| 10 | enabled | 是否启用 | tinyint(1) | 否 | 1 |  |  | 0=否，1=是 |  |
| 11 | expires_at | 过期时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 12 | created_by | 创建人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### meeting_dashboard_configs 会议看板配置

每场会议一份统计看板配置。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 单字段唯一 | meetings.id |  |  |
| 5 | configuration | 统计看板配置 JSON | json | 否 | NULL / 未设置 |  |  |  |  |

### meeting_invitation_versions 邀请函发布版本

每行一个邀请函的发布版本及会议快照。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | invitation_id | 邀请函 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meeting_invitations.id |  |  |
| 5 | version_no | 发布版本序号 | int | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 6 | design_document | 已发布邀请函设计 JSON | json | 否 | NULL / 未设置 |  |  |  |  |
| 7 | meeting_snapshot | 发布时会议资料快照 | json | 否 | NULL / 未设置 |  |  |  |  |
| 8 | published_by | 发布人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### meeting_invitations 邀请函主表

每行一个邀请函，包含草稿和发布版本引用。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | name | 邀请函名称 | varchar(160) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | InvitationStatus |  |
| 7 | draft_revision | 草稿修订号 | bigint | 否 | 0 |  |  |  |  |
| 8 | draft_document | 当前草稿设计 JSON | json | 否 | NULL / 未设置 |  |  |  |  |
| 9 | latest_published_version_id | 最新已发布版本 ID | bigint | 是 | NULL / 未设置 |  | meeting_invitation_versions.id |  |  |
| 10 | created_by | 创建人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 11 | published_at | 发布时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 12 | unpublished_at | 下线时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 13 | archived_at | 归档时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |

### meeting_user_permissions 会议用户权限

每行一个会议与用户的权限关联。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | user_id | 用户 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | app_users.id |  |  |
| 6 | permission_role | 会议权限角色 | varchar(32) | 否 | NULL / 未设置 |  |  | MeetingPermissionRole |  |
| 7 | granted_by | 授权人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### meetings 会议主表

每行一场会议，包含公开查询和席签配置。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | code | 会议业务编号 | varchar(64) | 否 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 5 | public_query_token | 公开查询访问令牌 | varchar(80) | 否 | NULL / 未设置 | 单字段唯一 |  |  | 访问凭证或密码派生信息 |
| 6 | name | 会议名称 | varchar(160) | 否 | NULL / 未设置 |  |  |  |  |
| 7 | organizer | 主办方 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | co_organizer | 承办方 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | start_time | 会议开始时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 10 | end_time | 会议结束时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 11 | registration_start_time | 报名开始时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 12 | registration_end_time | 报名截止时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 13 | location | 会议地点 | varchar(255) | 是 | NULL / 未设置 |  |  |  |  |
| 14 | venue_name | 会场名称 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 15 | capacity | 容量上限 | int | 是 | NULL / 未设置 |  |  |  |  |
| 16 | description | 会议简介 | text | 是 | NULL / 未设置 |  |  |  |  |
| 17 | agenda | 会议议程 | text | 是 | NULL / 未设置 |  |  |  |  |
| 18 | cover_image_url | 会议封面图 URL | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 19 | contact_name | 会议联系人姓名 | varchar(80) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 20 | contact_phone | 会议联系人电话 | varchar(40) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 21 | dinner_venue | 晚宴地点 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 22 | dinner_time | 晚宴时间文本 | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 23 | status | 状态 | varchar(40) | 否 | NULL / 未设置 |  |  | MeetingStatus |  |
| 24 | created_by | 创建人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 25 | seat_query_enabled | 是否开放座位查询 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 |  |
| 26 | room_query_enabled | 是否开放住宿查询 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 |  |
| 27 | dinner_query_enabled | 是否开放晚宴查询 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 |  |
| 28 | checkin_enabled | 是否启用签到 | tinyint(1) | 否 | 1 |  |  | 0=否，1=是 |  |
| 29 | self_checkin_enabled | 是否允许公开自助签到 | tinyint(1) | 否 | 1 |  |  | 0=否，1=是 |  |
| 30 | public_query_fields | 公开查询可填写字段集合 | varchar(255) | 否 | 'PHONE,NAME' |  |  | PublicQueryField |  |
| 31 | public_query_required_fields | 公开查询必填字段集合 | varchar(255) | 否 | NULL / 未设置 |  |  | PublicQueryField |  |
| 32 | public_query_match_mode | 公开查询匹配模式 | varchar(20) | 否 | 'FUZZY' |  |  | PublicQueryMatchMode |  |
| 33 | public_query_allow_multiple_results | 是否允许查询返回多个匹配结果 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 |  |
| 34 | public_combined_query_types | 公开组合查询类型集合 | varchar(80) | 否 | 'SEAT,ROOM,DINNER' |  |  | PublicQueryType |  |
| 35 | public_query_paused | 是否暂停公开查询 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 |  |
| 36 | public_query_title | 公开查询页标题 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 37 | public_query_description | 公开查询页描述 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 38 | public_query_announcement | 公开查询公告 | text | 是 | NULL / 未设置 |  |  |  |  |
| 39 | public_query_publisher | 公开查询信息发布方 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 40 | public_query_start_time | 公开查询开放时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 41 | public_query_end_time | 公开查询截止时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 42 | public_query_button_text | 公开查询按钮文案 | varchar(40) | 否 | '立即查询' |  |  |  |  |
| 43 | public_query_empty_message | 公开查询无结果提示 | varchar(255) | 否 | '未查询到结果，请核对信息后重试' |  |  |  |  |
| 44 | public_query_seat_notice | 座位查询结果提示 | text | 是 | NULL / 未设置 |  |  |  |  |
| 45 | public_query_room_notice | 住宿查询结果提示 | text | 是 | NULL / 未设置 |  |  |  |  |
| 46 | public_query_dinner_notice | 晚宴查询结果提示 | text | 是 | NULL / 未设置 |  |  |  |  |
| 47 | public_query_page_content | 公开查询页内容配置 JSON | json | 是 | NULL / 未设置 |  |  |  |  |
| 48 | public_query_theme_color | 公开查询页主题颜色 | varchar(20) | 否 | '#2563eb' |  |  |  |  |
| 49 | public_query_logo_url | 公开查询页 Logo URL | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 50 | public_query_background_url | 公开查询页背景图 URL | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 51 | public_query_result_layout | 公开查询结果布局 | varchar(20) | 否 | 'CARD' |  |  |  |  |
| 52 | public_query_visible_result_fields | 通用结果可见字段集合 | varchar(500) | 否 | 'ATTENDEE_NAME,SEAT_AREA,SEAT_ROW,SEAT_NO,SEAT_CODE,HOTEL_NAME,ROOM_TYPE,ROOM_NO,ROOM_REMARK,CHECKIN_TIPS,DINNER_TABLE_NO,DINNER_VENUE,DINNER_TIME,DINNER_REMARK' |  |  | PublicQueryResultField |  |
| 53 | public_query_masked_result_fields | 通用结果脱敏字段集合 | varchar(500) | 否 | '' |  |  | PublicQueryResultField |  |
| 54 | public_query_highlight_result_fields | 通用结果高亮字段集合 | varchar(500) | 否 | 'SEAT_CODE,ROOM_NO,DINNER_TABLE_NO' |  |  | PublicQueryResultField |  |
| 55 | public_query_seat_visible_result_fields | 座位结果可见字段集合 | varchar(500) | 否 | 'ATTENDEE_NAME,SEAT_AREA,SEAT_ROW,SEAT_NO,SEAT_CODE' |  |  | PublicQueryResultField |  |
| 56 | public_query_seat_masked_result_fields | 座位结果脱敏字段集合 | varchar(500) | 否 | '' |  |  | PublicQueryResultField |  |
| 57 | public_query_seat_highlight_result_fields | 座位结果高亮字段集合 | varchar(500) | 否 | 'SEAT_CODE' |  |  | PublicQueryResultField |  |
| 58 | public_query_room_visible_result_fields | 住宿结果可见字段集合 | varchar(500) | 否 | 'ATTENDEE_NAME,HOTEL_NAME,ROOM_TYPE,ROOM_NO,ROOM_REMARK,CHECKIN_TIPS' |  |  | PublicQueryResultField |  |
| 59 | public_query_room_masked_result_fields | 住宿结果脱敏字段集合 | varchar(500) | 否 | '' |  |  | PublicQueryResultField |  |
| 60 | public_query_room_highlight_result_fields | 住宿结果高亮字段集合 | varchar(500) | 否 | 'ROOM_NO' |  |  | PublicQueryResultField |  |
| 61 | public_query_dinner_visible_result_fields | 晚宴结果可见字段集合 | varchar(500) | 否 | 'ATTENDEE_NAME,DINNER_TABLE_NO,DINNER_VENUE,DINNER_TIME,DINNER_REMARK' |  |  | PublicQueryResultField |  |
| 62 | public_query_dinner_masked_result_fields | 晚宴结果脱敏字段集合 | varchar(500) | 否 | '' |  |  | PublicQueryResultField |  |
| 63 | public_query_dinner_highlight_result_fields | 晚宴结果高亮字段集合 | varchar(500) | 否 | 'DINNER_TABLE_NO' |  |  | PublicQueryResultField |  |
| 64 | nameplate_template_url | 席签模板文件 URL | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 65 | nameplate_fields | 席签使用字段集合 | json | 是 | NULL / 未设置 |  |  |  |  |

### notification_records 通知记录

每行一条通知配置或记录，不代表实际送达回执。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | title | 通知标题 | varchar(160) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | content | 通知内容 | varchar(1000) | 否 | NULL / 未设置 |  |  |  |  |
| 7 | channel | 通知渠道 | varchar(40) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | scope | 通知对象范围 | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | status | 通知记录状态 | varchar(40) | 否 | NULL / 未设置 |  |  |  | String 类型，服务在未传值时设置 DRAFT；代码没有封闭状态枚举。 |
| 10 | expected_count | 预计通知人数 | int | 是 | NULL / 未设置 |  |  |  |  |

### operation_logs 操作日志

每行一次后台或公共业务操作审计。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | actor | 操作人标识 | varchar(64) | 否 | NULL / 未设置 |  |  |  |  |
| 5 | action | 操作动作代码 | varchar(80) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | target_type | 操作对象类型 | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |
| 7 | target_id | 操作对象 ID | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | client_ip | 客户端 IP | varchar(45) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | success | 是否成功 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 10 | message | 处理消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 11 | details | 操作详情 JSON | json | 是 | NULL / 未设置 |  |  |  | JSON 详情可能含业务操作前后信息。 |

### public_query_history 公开查询历史

每行一次公开查询结果记录。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | query_type | 公开查询类型 | varchar(20) | 否 | NULL / 未设置 |  |  | PublicQueryType |  |
| 6 | query_status | 公开查询结果状态 | varchar(20) | 否 | NULL / 未设置 |  |  | PublicQueryStatus |  |
| 7 | query_values | 公开查询条件 JSON | json | 是 | NULL / 未设置 |  |  |  | JSON 条件可能含手机号、姓名等实际输入。 |
| 8 | query_text | 公开查询条件摘要 | varchar(1000) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | error_message | 错误消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 10 | attendee_id | 参会人 ID | bigint | 是 | NULL / 未设置 |  | attendees.id |  |  |
| 11 | attendee_name | 查询命中的参会人姓名 | varchar(120) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 12 | attendee_phone | 查询命中的参会人手机号 | varchar(40) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 13 | result_summary | 查询结果摘要 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 14 | client_ip | 客户端 IP | varchar(45) | 是 | NULL / 未设置 |  |  |  |  |
| 15 | user_agent | 客户端 User-Agent | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |

### registration_attributions 报名来源归因

每行一次 TDuck 数据对应的报名归因。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | invitation_id | 邀请函 ID | bigint | 是 | NULL / 未设置 |  | meeting_invitations.id |  |  |
| 6 | version_id | 邀请函版本 ID | bigint | 是 | NULL / 未设置 |  | meeting_invitation_versions.id |  |  |
| 7 | share_link_id | 渠道分享链接 ID | bigint | 是 | NULL / 未设置 |  | invitation_share_links.id |  |  |
| 8 | click_event_id | 邀请函点击事件 ID | bigint | 是 | NULL / 未设置 |  | invitation_events.id |  |  |
| 9 | attendee_id | 参会人 ID | bigint | 是 | NULL / 未设置 |  | attendees.id |  |  |
| 10 | attribution_status | 报名来源归因状态 | varchar(32) | 否 | NULL / 未设置 |  |  | RegistrationAttributionStatus |  |
| 11 | form_key | TDuck 表单标识 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 12 | tduck_data_id | TDuck 数据 ID | varchar(120) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 13 | active | 是否为当前有效归因 | tinyint(1) | 否 | 1 |  |  | 0=否，1=是 | 做当前渠道归因统计时仅保留 active=1。 |
| 14 | attributed_at | 归因处理时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |

### rooms 住宿房间

每行一场会议的一个房间。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | room_no | 房间号 | varchar(80) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 6 | room_type | 房型 | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |
| 7 | hotel_name | 酒店名称 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | remark | 备注 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 9 | capacity | 容量上限 | int | 否 | 1 |  |  |  |  |
| 10 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | RoomStatus |  |

### seat_assignments 座位分配

每行一名参会人与一个座位的当前分配。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | attendee_id | 参会人 ID | bigint | 否 | NULL / 未设置 | 单字段唯一 | attendees.id |  | 单字段唯一索引，一名参会人只有一条当前座位分配。 |
| 6 | seat_id | 座位 ID | bigint | 否 | NULL / 未设置 | 单字段唯一 | seats.id |  | 单字段唯一索引，一个座位只有一条当前分配。 |
| 7 | manual_assigned | 是否手动分配 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 8 | assigned_by | 分配人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |

### seat_layouts 座位布局

每行一个会议座位分区布局。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | venue_name | 会场名称 | varchar(160) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | area_name | 座位区域名称 | varchar(80) | 否 | NULL / 未设置 |  |  |  |  |
| 7 | row_count | 布局排数 | int | 否 | NULL / 未设置 |  |  |  |  |
| 8 | seats_per_row | 布局每排座位数 | int | 否 | NULL / 未设置 |  |  |  |  |
| 9 | sort_order | 布局排序号 | int | 否 | 0 |  |  |  |  |
| 10 | numbering_rule | 座位编号规则 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 11 | aisle_settings | 过道设置序列化文本 | varchar(512) | 是 | NULL / 未设置 |  |  |  |  |
| 12 | stage_direction | 舞台方向 | varchar(80) | 是 | NULL / 未设置 |  |  |  |  |

### seats 座位明细

每行一个座位或布局网格位置，可能是过道或留白。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | layout_id | 座位布局 ID | bigint | 是 | NULL / 未设置 |  | seat_layouts.id |  |  |
| 6 | area_name | 座位区域名称 | varchar(80) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 7 | row_no | 排号 | int | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 8 | seat_no | 座号 | int | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 9 | seat_code | 座位业务编码 | varchar(80) | 否 | NULL / 未设置 |  |  |  |  |
| 10 | seat_type | 座位类型 | varchar(32) | 否 | NULL / 未设置 |  |  | SeatType |  |
| 11 | disabled | 是否禁用座位 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 12 | locked | 是否锁定座位 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 13 | structural_blank | 是否为布局结构留白 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 | 占布局位置但不可分配，容量统计应排除。 |
| 14 | aisle | 是否为过道 | tinyint(1) | 否 | 0 |  |  | 0=否，1=是 | 过道占布局位置但不可分配，容量统计应排除。 |

### system_settings 系统设置

每行一个设置键值。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | setting_key | 系统设置键 | varchar(80) | 否 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 5 | setting_value | 系统设置值 | text | 是 | NULL / 未设置 |  |  |  | 根据设置键存储文本或序列化内容，可能含敏感配置。 |

### tduck_field_mappings 报名字段映射

每行一个 TDuck 字段到参会人字段的映射。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | attendee_field | 映射的参会人标准字段 | varchar(48) | 是 | NULL / 未设置 | 联合唯一索引成员 |  | AttendeeField | 扩展标准字段可保存在原始快照中，不保证在 attendees 有同名物理列。 |
| 6 | tduck_field_key | TDuck 字段标识 | varchar(120) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 7 | tduck_field_label | TDuck 字段显示名称 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 8 | required_mapping | 是否为必需映射 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |

### tduck_form_bindings 报名表单绑定

每场会议一个绑定，表单标识全局唯一。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 单字段唯一 | meetings.id |  |  |
| 5 | form_key | TDuck 表单标识 | varchar(120) | 否 | NULL / 未设置 | 单字段唯一 |  |  |  |
| 6 | form_name | TDuck 表单名称 | varchar(160) | 否 | NULL / 未设置 |  |  |  |  |
| 7 | registration_url | TDuck 报名页 URL | varchar(500) | 否 | NULL / 未设置 |  |  |  |  |
| 8 | registration_enabled | 是否允许报名 | tinyint(1) | 否 | 1 |  |  | 0=否，1=是 |  |
| 9 | binding_status | 表单绑定状态 | varchar(32) | 否 | NULL / 未设置 |  |  | TduckBindingStatus |  |
| 10 | mapping_status | 字段映射状态 | varchar(32) | 否 | NULL / 未设置 |  |  | MappingStatus |  |
| 11 | webhook_status | Webhook 配置状态 | varchar(32) | 否 | NULL / 未设置 |  |  | WebhookStatus |  |

### tduck_pull_sync_tasks TDuck 拉取任务

每行一次历史数据拉取任务。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | form_key | TDuck 表单标识 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | requested_limit | 请求拉取条数上限 | int | 否 | NULL / 未设置 |  |  |  |  |
| 7 | idempotency_key | 幂等键 | varchar(128) | 是 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 8 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | TduckPullSyncTaskStatus |  |
| 9 | total_rows | 总记录数 | int | 否 | 0 |  |  |  |  |
| 10 | processed_rows | 已处理记录数 | int | 否 | 0 |  |  |  |  |
| 11 | success_rows | 成功记录数 | int | 否 | 0 |  |  |  |  |
| 12 | duplicate_rows | 重复记录数 | int | 否 | 0 |  |  |  |  |
| 13 | failed_rows | 失败记录数 | int | 否 | 0 |  |  |  |  |
| 14 | progress | 任务进度百分比 | int | 否 | 0 |  |  |  |  |
| 15 | failure_details | 失败详情序列化文本 | text | 是 | NULL / 未设置 |  |  |  |  |
| 16 | message | 处理消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 17 | requested_by | 请求人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 18 | started_at | 开始处理时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 19 | completed_at | 完成时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 20 | lease_until | 任务租约截止时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 21 | run_token | 任务执行令牌 | varchar(64) | 是 | NULL / 未设置 |  |  |  | 访问凭证或密码派生信息 |

### tduck_submissions TDuck 提交历史

每行一场会议的一条 TDuck 提交，含重复资料裁决状态。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 否 | NULL / 未设置 | 联合唯一索引成员 | meetings.id |  |  |
| 5 | attendee_id | 参会人 ID | bigint | 否 | NULL / 未设置 |  | attendees.id |  | 冲突提交也关联既有参会人，不表示该条提交资料已被采用。 |
| 6 | form_key | TDuck 表单标识 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 7 | tduck_data_id | TDuck 数据 ID | varchar(120) | 否 | NULL / 未设置 | 联合唯一索引成员 |  |  |  |
| 8 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | TduckSubmissionStatus |  |
| 9 | active_attendee_id | 当前采用来源的参会人占位 ID | bigint | 是 | NULL / 未设置 | 联合唯一索引成员 |  |  | 仅 ACTIVE 状态写入参会人 ID，其他状态为 NULL；配合 meeting_id 联合唯一模拟部分唯一约束，无声明外键。 |
| 10 | submitted_at | TDuck 提交时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 11 | resolved_at | 提交裁决时间 | datetime(6) | 是 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 12 | resolved_by | 裁决人标识 | varchar(64) | 是 | NULL / 未设置 |  |  |  |  |
| 13 | resolution_note | 提交裁决说明 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 14 | name | 提交快照中的姓名 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 15 | phone | 手机号 | varchar(40) | 否 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 16 | organization | 单位或组织 | varchar(160) | 是 | NULL / 未设置 |  |  |  |  |
| 17 | title | 职务 | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 18 | identity_no | 证件号码 | varchar(80) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 19 | email | 电子邮箱 | varchar(160) | 是 | NULL / 未设置 |  |  |  | 个人信息字段 |
| 20 | attendee_type | 参会人类型 | varchar(32) | 否 | NULL / 未设置 |  |  | AttendeeType |  |
| 21 | accommodation_required | 是否需要住宿 | tinyint(1) | 否 | NULL / 未设置 |  |  | 0=否，1=是 |  |
| 22 | remark | 备注 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 23 | raw_data_snapshot | 原始报名资料快照 | json | 是 | NULL / 未设置 |  |  |  |  |

### tduck_sync_logs TDuck 同步日志

每行一次同步事件处理记录，含失败和忽略事件。

| 序号 | 字段名 | 中文含义 | 类型 | 可空 | 数据库默认值 | 唯一性 | 物理外键目标 | 枚举或编码 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | id | 记录主键 | bigint | 否 | NULL / 未设置 | 主键 |  |  | 自增 |
| 2 | created_at | 创建时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 3 | updated_at | 最后更新时间 | datetime(6) | 否 | NULL / 未设置 |  |  |  | 无时区日期时间 |
| 4 | meeting_id | 会议 ID | bigint | 是 | NULL / 未设置 |  | meetings.id |  |  |
| 5 | form_key | TDuck 表单标识 | varchar(120) | 否 | NULL / 未设置 |  |  |  |  |
| 6 | event_id | 外部事件 ID | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 7 | event_type | 事件类型 | varchar(80) | 否 | NULL / 未设置 |  |  |  |  |
| 8 | status | 状态 | varchar(32) | 否 | NULL / 未设置 |  |  | SyncStatus |  |
| 9 | message | 处理消息 | varchar(500) | 是 | NULL / 未设置 |  |  |  |  |
| 10 | attendee_id | 参会人 ID | bigint | 是 | NULL / 未设置 |  |  |  | 可逻辑关联 attendees.id；数据库未声明外键。 |
| 11 | tduck_data_id | TDuck 数据 ID | varchar(120) | 是 | NULL / 未设置 |  |  |  |  |
| 12 | raw_payload | TDuck 原始事件 JSON | json | 是 | NULL / 未设置 |  |  |  |  |
| 13 | success_event_id | 成功处理事件的去重标识 | varchar(120) | 是 | NULL / 未设置 | 单字段唯一 |  |  | 仅成功事件占位以保证幂等；NULL 允许多条记录。 |

## 状态与字段枚举

以下为配套源码声明的取值，不是对现有业务数据做的去重采样。`notification_records.status` 为开放字符串，仅确认应用默认值为 `DRAFT`。布尔列统一按 0/1 表示。

| 枚举 | 存储值 | 中文含义 | 使用字段 | 来源文件 |
| --- | --- | --- | --- | --- |
| AttendeeField | NAME | 姓名 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | PHONE | 手机号 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ORGANIZATION | 单位 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | TITLE | 职务 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | IDENTITY_NO | 证件号 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | EMAIL | 电子邮箱 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ATTENDEE_TYPE | 参会人类型 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ACCOMMODATION_REQUIRED | 是否住宿 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ROOM_NO | 房间号 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ROOM_REMARK | 住宿备注 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | REMARK | 备注 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | GENDER | 性别 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | COUNTRY_REGION | 国家或地区 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | PROVINCE | 省份 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | CITY | 城市 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | INDUSTRY | 行业 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ORGANIZATION_TYPE | 组织类型 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | DEPARTMENT | 部门 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ADDRESS | 地址 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | DIETARY_REQUIREMENT | 饮食要求 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ARRIVAL_DATE | 抵达日期 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ARRIVAL_TRANSPORT | 抵达交通方式 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | ARRIVAL_FLIGHT_TRAIN | 抵达航班或车次 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | DEPARTURE_DATE | 离开日期 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | DEPARTURE_TRANSPORT | 离开交通方式 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | DEPARTURE_FLIGHT_TRAIN | 离开航班或车次 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | EMERGENCY_CONTACT | 紧急联系人 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | EMERGENCY_PHONE | 紧急联系电话 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | COMPANION_COUNT | 同行人数 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | INVOICE_TITLE | 发票抬头 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | TAX_NO | 税号 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | INVOICE_EMAIL | 发票邮箱 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | PAYMENT_STATUS | 付款状态 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | SIGNUP_SOURCE | 报名来源 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | INVITER | 邀请人 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | CONSENT_PHOTO | 肖像授权 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeField | CONSENT_PRIVACY | 隐私授权 | tduck_field_mappings.attendee_field | AttendeeField.java |
| AttendeeType | VIP | VIP | attendees.attendee_type；tduck_submissions.attendee_type | AttendeeType.java |
| AttendeeType | GUEST | 嘉宾 | attendees.attendee_type；tduck_submissions.attendee_type | AttendeeType.java |
| AttendeeType | MEDIA | 媒体 | attendees.attendee_type；tduck_submissions.attendee_type | AttendeeType.java |
| AttendeeType | STAFF | 工作人员 | attendees.attendee_type；tduck_submissions.attendee_type | AttendeeType.java |
| AttendeeType | NORMAL | 普通 | attendees.attendee_type；tduck_submissions.attendee_type | AttendeeType.java |
| CheckinResultStatus | SUCCESS | 成功 | checkin_records.result_status | CheckinResultStatus.java |
| CheckinResultStatus | DUPLICATE | 重复签到 | checkin_records.result_status | CheckinResultStatus.java |
| CheckinResultStatus | NOT_APPROVED | 审核未通过 | checkin_records.result_status | CheckinResultStatus.java |
| CheckinResultStatus | NOT_FOUND | 未找到参会人 | checkin_records.result_status | CheckinResultStatus.java |
| CheckinStatus | NOT_CHECKED_IN | 未签到 | attendees.checkin_status | CheckinStatus.java |
| CheckinStatus | CHECKED_IN | 已签到 | attendees.checkin_status | CheckinStatus.java |
| CheckinType | SCAN | 扫码 | checkin_records.checkin_type | CheckinType.java |
| CheckinType | SEARCH | 搜索 | checkin_records.checkin_type | CheckinType.java |
| CheckinType | MANUAL | 手动 | checkin_records.checkin_type | CheckinType.java |
| CheckinType | SUPPLEMENT | 补签 | checkin_records.checkin_type | CheckinType.java |
| ExportFormat | csv | 逗号分隔文本 | export_tasks.format | ExportFormat.java |
| ExportFormat | xlsx | Excel 工作簿 | export_tasks.format | ExportFormat.java |
| ExportTaskStatus | PENDING | 待处理 | export_tasks.status | ExportTaskStatus.java |
| ExportTaskStatus | RUNNING | 运行中 | export_tasks.status | ExportTaskStatus.java |
| ExportTaskStatus | APPROVED | 已通过 | export_tasks.status | ExportTaskStatus.java |
| ExportTaskStatus | REJECTED | 已拒绝 | export_tasks.status | ExportTaskStatus.java |
| ExportTaskStatus | COMPLETED | 已完成 | export_tasks.status | ExportTaskStatus.java |
| ExportTaskStatus | FAILED | 失败 | export_tasks.status | ExportTaskStatus.java |
| ExportTaskStatus | EXPIRED | 已过期 | export_tasks.status | ExportTaskStatus.java |
| ImportTaskStatus | PENDING | 待处理 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskStatus | PREFLIGHTING | 预检查中 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskStatus | PREVIEW_READY | 预览已就绪 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskStatus | RUNNING | 运行中 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskStatus | COMPLETED | 已完成 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskStatus | FAILED | 失败 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskStatus | EXPIRED | 已过期 | import_tasks.status | ImportTaskStatus.java |
| ImportTaskType | attendees | attendees | import_tasks.import_type | ImportTaskType.java |
| ImportTaskType | rooms | rooms | import_tasks.import_type | ImportTaskType.java |
| ImportTaskType | room-assignments | room-assignments | import_tasks.import_type | ImportTaskType.java |
| ImportTaskType | dinner | dinner | import_tasks.import_type | ImportTaskType.java |
| InvitationChannelType | WECHAT_FRIENDS | 微信好友 | invitation_share_links.channel_type | InvitationChannelType.java |
| InvitationChannelType | WECHAT_MOMENTS | 微信朋友圈 | invitation_share_links.channel_type | InvitationChannelType.java |
| InvitationChannelType | EMAIL | 邮件 | invitation_share_links.channel_type | InvitationChannelType.java |
| InvitationChannelType | OFFLINE_POSTER | 线下海报 | invitation_share_links.channel_type | InvitationChannelType.java |
| InvitationChannelType | OTHER | 其他 | invitation_share_links.channel_type | InvitationChannelType.java |
| InvitationEventType | PAGE_VIEW | 页面访问 | invitation_events.event_type | InvitationEventType.java |
| InvitationEventType | CTA_CLICK | 按钮点击 | invitation_events.event_type | InvitationEventType.java |
| InvitationStatus | DRAFT | 草稿 | meeting_invitations.status | InvitationStatus.java |
| InvitationStatus | PUBLISHED | 已发布 | meeting_invitations.status | InvitationStatus.java |
| InvitationStatus | UNPUBLISHED | 已下线 | meeting_invitations.status | InvitationStatus.java |
| InvitationStatus | ARCHIVED | 已归档 | meeting_invitations.status | InvitationStatus.java |
| MappingStatus | NOT_CONFIGURED | 未配置 | tduck_form_bindings.mapping_status | MappingStatus.java |
| MappingStatus | VALID | 有效 | tduck_form_bindings.mapping_status | MappingStatus.java |
| MappingStatus | INVALID | 无效 | tduck_form_bindings.mapping_status | MappingStatus.java |
| MeetingPermissionRole | MANAGER | 会议管理 | meeting_user_permissions.permission_role | MeetingPermissionRole.java |
| MeetingPermissionRole | CHECKIN | 签到 | meeting_user_permissions.permission_role | MeetingPermissionRole.java |
| MeetingPermissionRole | ROOM | 住宿 | meeting_user_permissions.permission_role | MeetingPermissionRole.java |
| MeetingPermissionRole | VIEWER | 只读查看 | meeting_user_permissions.permission_role | MeetingPermissionRole.java |
| MeetingStatus | DRAFT | 草稿 | meetings.status | MeetingStatus.java |
| MeetingStatus | REGISTRATION_OPEN | 报名开放 | meetings.status | MeetingStatus.java |
| MeetingStatus | REGISTRATION_CLOSED | 报名关闭 | meetings.status | MeetingStatus.java |
| MeetingStatus | READY | 准备就绪 | meetings.status | MeetingStatus.java |
| MeetingStatus | IN_PROGRESS | 会议进行中 | meetings.status | MeetingStatus.java |
| MeetingStatus | ENDED | 已结束 | meetings.status | MeetingStatus.java |
| MeetingStatus | ARCHIVED | 已归档 | meetings.status | MeetingStatus.java |
| PublicQueryField | NAME | 姓名 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryField | PHONE | 手机号 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryField | PHONE_LAST4 | 手机号后四位 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryField | ATTENDEE_CODE | 参会码 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryField | ORGANIZATION | 单位 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryField | TITLE | 职务 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryField | EMAIL | 邮箱 | meetings.public_query_fields；meetings.public_query_required_fields | PublicQueryField.java |
| PublicQueryMatchMode | FUZZY | 模糊匹配 | meetings.public_query_match_mode | PublicQueryMatchMode.java |
| PublicQueryMatchMode | EXACT | 精确匹配 | meetings.public_query_match_mode | PublicQueryMatchMode.java |
| PublicQueryResultField | ATTENDEE_NAME | 姓名 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | ATTENDEE_PHONE | 手机号 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | ORGANIZATION | 单位 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | TITLE | 职务 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | EMAIL | 邮箱 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | MEETING_NAME | 会议名称 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | SEAT_AREA | 座位区域 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | SEAT_ROW | 排号 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | SEAT_NO | 座号 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | SEAT_CODE | 座位号 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | HOTEL_NAME | 酒店 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | ROOM_TYPE | 房型 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | ROOM_NO | 房间号 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | ROOM_REMARK | 房间备注 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | CHECKIN_TIPS | 入住提示 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | DINNER_TABLE_NO | 晚宴桌号 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | DINNER_VENUE | 晚宴地点 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | DINNER_TIME | 晚宴时间 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryResultField | DINNER_REMARK | 晚宴备注 | meetings.public_query_visible_result_fields；meetings.public_query_masked_result_fields；meetings.public_query_highlight_result_fields；meetings.public_query_seat_visible_result_fields；meetings.public_query_seat_masked_result_fields；meetings.public_query_seat_highlight_result_fields；meetings.public_query_room_visible_result_fields；meetings.public_query_room_masked_result_fields；meetings.public_query_room_highlight_result_fields；meetings.public_query_dinner_visible_result_fields；meetings.public_query_dinner_masked_result_fields；meetings.public_query_dinner_highlight_result_fields | PublicQueryResultField.java |
| PublicQueryStatus | SUCCESS | 成功 | public_query_history.query_status | PublicQueryStatus.java |
| PublicQueryStatus | FAILED | 失败 | public_query_history.query_status | PublicQueryStatus.java |
| PublicQueryType | SEAT | 座位 | meetings.public_combined_query_types；public_query_history.query_type | PublicQueryType.java |
| PublicQueryType | ROOM | 住宿 | meetings.public_combined_query_types；public_query_history.query_type | PublicQueryType.java |
| PublicQueryType | DINNER | 晚宴 | meetings.public_combined_query_types；public_query_history.query_type | PublicQueryType.java |
| PublicQueryType | CHECKIN | 签到 | meetings.public_combined_query_types；public_query_history.query_type | PublicQueryType.java |
| RegistrationAttributionStatus | ATTRIBUTED | 已归因 | registration_attributions.attribution_status | RegistrationAttributionStatus.java |
| RegistrationAttributionStatus | DIRECT_UNATTRIBUTED | 直接报名未归因 | registration_attributions.attribution_status | RegistrationAttributionStatus.java |
| RegistrationAttributionStatus | INVALID_TOKEN | 令牌无效未归因 | registration_attributions.attribution_status | RegistrationAttributionStatus.java |
| RegistrationAttributionStatus | LEGACY_UNATTRIBUTED | 历史报名未归因 | registration_attributions.attribution_status | RegistrationAttributionStatus.java |
| ReviewStatus | PENDING | 待处理 | attendees.review_status | ReviewStatus.java |
| ReviewStatus | APPROVED | 已通过 | attendees.review_status | ReviewStatus.java |
| ReviewStatus | REJECTED | 已拒绝 | attendees.review_status | ReviewStatus.java |
| ReviewStatus | CANCELLED | 已取消 | attendees.review_status | ReviewStatus.java |
| Role | SUPER_ADMIN | 超级管理员 | app_user_roles.role | Role.java |
| Role | MEETING_ADMIN | 会议管理员 | app_user_roles.role | Role.java |
| Role | CHECKIN_STAFF | 签到工作人员 | app_user_roles.role | Role.java |
| Role | ROOM_STAFF | 住宿工作人员 | app_user_roles.role | Role.java |
| Role | VIEWER | 只读查看 | app_user_roles.role | Role.java |
| RoomStatus | AVAILABLE | 可用 | rooms.status | RoomStatus.java |
| RoomStatus | ASSIGNED | 已分配 | rooms.status | RoomStatus.java |
| RoomStatus | DISABLED | 禁用 | rooms.status | RoomStatus.java |
| SeatType | NORMAL | 普通 | seats.seat_type | SeatType.java |
| SeatType | VIP | VIP | seats.seat_type | SeatType.java |
| SeatType | GUEST | 嘉宾 | seats.seat_type | SeatType.java |
| SeatType | MEDIA | 媒体 | seats.seat_type | SeatType.java |
| SeatType | STAFF | 工作人员 | seats.seat_type | SeatType.java |
| SeatType | DISABLED | 禁用 | seats.seat_type | SeatType.java |
| SyncStatus | SUCCESS | 成功 | tduck_sync_logs.status | SyncStatus.java |
| SyncStatus | FAILED | 失败 | tduck_sync_logs.status | SyncStatus.java |
| SyncStatus | DUPLICATE_IGNORED | 重复事件已忽略 | tduck_sync_logs.status | SyncStatus.java |
| TduckBindingStatus | DRAFT | 草稿 | tduck_form_bindings.binding_status | TduckBindingStatus.java |
| TduckBindingStatus | BOUND | 已绑定 | tduck_form_bindings.binding_status | TduckBindingStatus.java |
| TduckBindingStatus | PUBLISHED | 已发布 | tduck_form_bindings.binding_status | TduckBindingStatus.java |
| TduckBindingStatus | DISABLED | 禁用 | tduck_form_bindings.binding_status | TduckBindingStatus.java |
| TduckPullSyncTaskStatus | PENDING | 待处理 | tduck_pull_sync_tasks.status | TduckPullSyncTaskStatus.java |
| TduckPullSyncTaskStatus | RUNNING | 运行中 | tduck_pull_sync_tasks.status | TduckPullSyncTaskStatus.java |
| TduckPullSyncTaskStatus | COMPLETED | 已完成 | tduck_pull_sync_tasks.status | TduckPullSyncTaskStatus.java |
| TduckPullSyncTaskStatus | FAILED | 失败 | tduck_pull_sync_tasks.status | TduckPullSyncTaskStatus.java |
| TduckSubmissionStatus | ACTIVE | 当前采用的提交 | tduck_submissions.status | TduckSubmissionStatus.java |
| TduckSubmissionStatus | SUPERSEDED | 已被其他提交替代 | tduck_submissions.status | TduckSubmissionStatus.java |
| TduckSubmissionStatus | IGNORED | 已忽略 | tduck_submissions.status | TduckSubmissionStatus.java |
| TduckSubmissionStatus | PENDING_CONFLICT | 待裁决冲突 | tduck_submissions.status | TduckSubmissionStatus.java |
| TduckSubmissionStatus | CANCELLED | 已取消 | tduck_submissions.status | TduckSubmissionStatus.java |
| UserStatus | ACTIVE | 启用 | app_users.status | UserStatus.java |
| UserStatus | DISABLED | 禁用 | app_users.status | UserStatus.java |
| WebhookStatus | NOT_CONFIGURED | 未配置 | tduck_form_bindings.webhook_status | WebhookStatus.java |
| WebhookStatus | CONFIGURED | 已配置 | tduck_form_bindings.webhook_status | WebhookStatus.java |
| WebhookStatus | VERIFIED | 已验证 | tduck_form_bindings.webhook_status | WebhookStatus.java |

## 索引

联合索引的列按数据库实际顺序列出，唯一性约束覆盖整组字段。

| 表名 | 索引名 | 唯一 | 有序字段 | 类型 | 可见 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| app_user_roles | fk_app_user_roles_user | 否 | user_id | BTREE | 是 | 单字段索引 |
| app_users | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| app_users | uk_app_user_username | 是 | username | BTREE | 是 | 单字段索引 |
| attendees | idx_attendee_meeting_accommodation_room | 否 | meeting_id, accommodation_required, room_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_checkin_status | 否 | meeting_id, checkin_status | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_email | 否 | meeting_id, email | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_identity_no | 否 | meeting_id, identity_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_name_org | 否 | meeting_id, name, organization | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_organization | 否 | meeting_id, organization | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_registered_at | 否 | meeting_id, registered_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_review_status | 否 | meeting_id, review_status | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_room_no | 否 | meeting_id, room_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_title | 否 | meeting_id, title | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_type | 否 | meeting_id, attendee_type | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | idx_attendee_meeting_unregistered | 否 | meeting_id, unregistered | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| attendees | uk_attendee_code | 是 | attendee_code | BTREE | 是 | 单字段索引 |
| attendees | uk_attendee_meeting_phone | 是 | meeting_id, phone | BTREE | 是 | 联合索引，字段顺序有意义 |
| attendees | uk_attendee_meeting_tduck_data | 是 | meeting_id, tduck_data_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| checkin_records | fk_checkin_attendee | 否 | attendee_id | BTREE | 是 | 单字段索引 |
| checkin_records | idx_checkin_record_meeting_status_time | 否 | meeting_id, result_status, checked_in_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| checkin_records | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| dinner_assignments | fk_dinner_assignment_attendee | 否 | attendee_id | BTREE | 是 | 单字段索引 |
| dinner_assignments | idx_dinner_assignment_meeting_table | 否 | meeting_id, table_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| dinner_assignments | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| dinner_assignments | uk_dinner_assignment_attendee | 是 | meeting_id, attendee_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| dinner_tables | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| dinner_tables | uk_dinner_table_meeting_no | 是 | meeting_id, table_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| export_tasks | idx_export_task_meeting_created | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| export_tasks | idx_export_task_meeting_status | 否 | meeting_id, status | BTREE | 是 | 联合索引，字段顺序有意义 |
| export_tasks | idx_export_task_meeting_type_status | 否 | meeting_id, export_type, status | BTREE | 是 | 联合索引，字段顺序有意义 |
| export_tasks | idx_export_task_status_expires | 否 | status, expires_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| export_tasks | idx_export_task_status_updated | 否 | status, updated_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| export_tasks | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| flyway_schema_history | flyway_schema_history_s_idx | 否 | success | BTREE | 是 | 单字段索引 |
| flyway_schema_history | PRIMARY | 是 | installed_rank | BTREE | 是 | 单字段索引 |
| import_tasks | idx_import_task_meeting_created | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| import_tasks | idx_import_task_meeting_type_status | 否 | meeting_id, import_type, status | BTREE | 是 | 联合索引，字段顺序有意义 |
| import_tasks | idx_import_task_status_expires | 否 | status, expires_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| import_tasks | idx_import_task_status_updated | 否 | status, updated_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| import_tasks | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| import_tasks | uk_import_task_idempotency | 是 | meeting_id, import_type, idempotency_key | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_assets | idx_invitation_asset_meeting_created | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_assets | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| invitation_assets | uk_invitation_asset_storage | 是 | storage_name | BTREE | 是 | 单字段索引 |
| invitation_events | fk_invitation_event_invitation | 否 | invitation_id | BTREE | 是 | 单字段索引 |
| invitation_events | fk_invitation_event_version | 否 | version_id | BTREE | 是 | 单字段索引 |
| invitation_events | idx_invitation_event_invitation_time_type | 否 | meeting_id, invitation_id, occurred_at, event_type | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_events | idx_invitation_event_link_type_time | 否 | share_link_id, event_type, occurred_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_events | idx_invitation_event_meeting_time | 否 | meeting_id, occurred_at, event_type | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_events | idx_invitation_event_visitor | 否 | share_link_id, visitor_hash, event_type | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_events | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| invitation_events | uk_invitation_event_click | 是 | click_public_id | BTREE | 是 | 单字段索引 |
| invitation_events | uk_invitation_event_client | 是 | share_link_id, client_event_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_share_links | fk_invitation_link_version | 否 | current_version_id | BTREE | 是 | 单字段索引 |
| invitation_share_links | idx_invitation_link_invitation | 否 | invitation_id, enabled, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_share_links | idx_invitation_link_meeting | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| invitation_share_links | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| invitation_share_links | uk_invitation_share_token | 是 | share_token | BTREE | 是 | 单字段索引 |
| meeting_dashboard_configs | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| meeting_dashboard_configs | uk_dashboard_config_meeting | 是 | meeting_id | BTREE | 是 | 单字段索引 |
| meeting_invitation_versions | idx_invitation_version_created | 否 | invitation_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| meeting_invitation_versions | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| meeting_invitation_versions | uk_invitation_version_no | 是 | invitation_id, version_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| meeting_invitations | fk_invitation_latest_version | 否 | latest_published_version_id | BTREE | 是 | 单字段索引 |
| meeting_invitations | idx_invitation_meeting_status | 否 | meeting_id, status, updated_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| meeting_invitations | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| meeting_user_permissions | idx_meeting_user_permission_meeting | 否 | meeting_id | BTREE | 否 | 单字段索引 |
| meeting_user_permissions | idx_meeting_user_permission_user | 否 | user_id | BTREE | 是 | 单字段索引 |
| meeting_user_permissions | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| meeting_user_permissions | uk_meeting_user_permission | 是 | meeting_id, user_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| meetings | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| meetings | uk_meeting_code | 是 | code | BTREE | 是 | 单字段索引 |
| meetings | uk_meeting_public_query_token | 是 | public_query_token | BTREE | 是 | 单字段索引 |
| notification_records | idx_notification_meeting_created | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| notification_records | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| operation_logs | idx_operation_logs_action_created_at | 否 | action, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| operation_logs | idx_operation_logs_actor_created_at | 否 | actor, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| operation_logs | idx_operation_logs_created_at | 否 | created_at | BTREE | 是 | 单字段索引 |
| operation_logs | idx_operation_logs_success_created_at | 否 | success, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| operation_logs | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| public_query_history | idx_public_query_history_attendee_type | 否 | attendee_id, query_type | BTREE | 是 | 联合索引，字段顺序有意义 |
| public_query_history | idx_public_query_history_created_id | 否 | created_at, id | BTREE | 是 | 联合索引，字段顺序有意义 |
| public_query_history | idx_public_query_history_meeting_attendee_created | 否 | meeting_id, attendee_id, created_at, id | BTREE | 是 | 联合索引，字段顺序有意义 |
| public_query_history | idx_public_query_history_meeting_created | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| public_query_history | idx_public_query_history_meeting_status | 否 | meeting_id, query_status | BTREE | 是 | 联合索引，字段顺序有意义 |
| public_query_history | idx_public_query_history_meeting_type | 否 | meeting_id, query_type | BTREE | 否 | 联合索引，字段顺序有意义 |
| public_query_history | idx_public_query_history_meeting_type_attendee | 否 | meeting_id, query_type, attendee_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| public_query_history | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| registration_attributions | fk_registration_attr_attendee | 否 | attendee_id | BTREE | 是 | 单字段索引 |
| registration_attributions | fk_registration_attr_click | 否 | click_event_id | BTREE | 是 | 单字段索引 |
| registration_attributions | fk_registration_attr_link | 否 | share_link_id | BTREE | 是 | 单字段索引 |
| registration_attributions | fk_registration_attr_version | 否 | version_id | BTREE | 是 | 单字段索引 |
| registration_attributions | idx_registration_attr_detail_time | 否 | invitation_id, attributed_at, id | BTREE | 是 | 联合索引，字段顺序有意义 |
| registration_attributions | idx_registration_attr_invitation | 否 | invitation_id, share_link_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| registration_attributions | idx_registration_attr_invitation_time_status | 否 | meeting_id, invitation_id, attributed_at, attribution_status, active | BTREE | 是 | 联合索引，字段顺序有意义 |
| registration_attributions | idx_registration_attr_meeting_attendee_data | 否 | meeting_id, attendee_id, tduck_data_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| registration_attributions | idx_registration_attr_meeting_time | 否 | meeting_id, attributed_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| registration_attributions | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| registration_attributions | uk_registration_attr_tduck | 是 | meeting_id, tduck_data_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| rooms | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| rooms | uk_room_meeting_no | 是 | meeting_id, room_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| seat_assignments | idx_seat_assignment_meeting_attendee | 否 | meeting_id, attendee_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| seat_assignments | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| seat_assignments | uk_assignment_attendee | 是 | attendee_id | BTREE | 是 | 单字段索引 |
| seat_assignments | uk_assignment_seat | 是 | seat_id | BTREE | 是 | 单字段索引 |
| seat_layouts | fk_seat_layout_meeting | 否 | meeting_id | BTREE | 是 | 单字段索引 |
| seat_layouts | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| seats | fk_seat_layout | 否 | layout_id | BTREE | 是 | 单字段索引 |
| seats | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| seats | uk_seat_meeting_area_row_no | 是 | meeting_id, area_name, row_no, seat_no | BTREE | 是 | 联合索引，字段顺序有意义 |
| system_settings | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| system_settings | uk_system_setting_key | 是 | setting_key | BTREE | 是 | 单字段索引 |
| tduck_field_mappings | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| tduck_field_mappings | uk_mapping_meeting_target | 是 | meeting_id, attendee_field | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_field_mappings | uk_mapping_meeting_tduck_field | 是 | meeting_id, tduck_field_key | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_form_bindings | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| tduck_form_bindings | uk_tduck_binding_form_key | 是 | form_key | BTREE | 是 | 单字段索引 |
| tduck_form_bindings | uk_tduck_binding_meeting | 是 | meeting_id | BTREE | 是 | 单字段索引 |
| tduck_pull_sync_tasks | idx_tduck_pull_task_meeting_created | 否 | meeting_id, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_pull_sync_tasks | idx_tduck_pull_task_meeting_status | 否 | meeting_id, status | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_pull_sync_tasks | idx_tduck_pull_task_recovery | 否 | status, lease_until | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_pull_sync_tasks | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| tduck_pull_sync_tasks | uk_tduck_pull_task_idempotency | 是 | meeting_id, idempotency_key | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_submissions | idx_tduck_submission_attendee_status | 否 | attendee_id, status | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_submissions | idx_tduck_submission_conflict | 否 | meeting_id, status, created_at | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_submissions | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| tduck_submissions | uk_tduck_submission_active_attendee | 是 | meeting_id, active_attendee_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_submissions | uk_tduck_submission_meeting_data | 是 | meeting_id, tduck_data_id | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_sync_logs | idx_tduck_sync_event_id | 否 | event_id | BTREE | 否 | 单字段索引 |
| tduck_sync_logs | idx_tduck_sync_event_status_id | 否 | event_id, status, id | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_sync_logs | idx_tduck_sync_meeting_created | 否 | meeting_id, created_at, id | BTREE | 是 | 联合索引，字段顺序有意义 |
| tduck_sync_logs | PRIMARY | 是 | id | BTREE | 是 | 单字段索引 |
| tduck_sync_logs | uk_tduck_sync_success_event | 是 | success_event_id | BTREE | 是 | 单字段索引 |

## 物理外键关系

以下 45 条来自数据库声明的外键。房间号、晚宴桌号等逻辑关联见取数口径，不混入此表。

| 子表 | 子字段 | 父表 | 父字段 | 约束名 | 更新规则 | 删除规则 |
| --- | --- | --- | --- | --- | --- | --- |
| app_user_roles | user_id | app_users | id | fk_app_user_roles_user | NO ACTION | NO ACTION |
| attendees | meeting_id | meetings | id | fk_attendee_meeting | NO ACTION | NO ACTION |
| checkin_records | attendee_id | attendees | id | fk_checkin_attendee | NO ACTION | NO ACTION |
| checkin_records | meeting_id | meetings | id | fk_checkin_meeting | NO ACTION | NO ACTION |
| dinner_assignments | attendee_id | attendees | id | fk_dinner_assignment_attendee | NO ACTION | NO ACTION |
| dinner_assignments | meeting_id | meetings | id | fk_dinner_assignment_meeting | NO ACTION | NO ACTION |
| dinner_tables | meeting_id | meetings | id | fk_dinner_table_meeting | NO ACTION | NO ACTION |
| export_tasks | meeting_id | meetings | id | fk_export_task_meeting | NO ACTION | NO ACTION |
| import_tasks | meeting_id | meetings | id | fk_import_task_meeting | NO ACTION | NO ACTION |
| invitation_assets | meeting_id | meetings | id | fk_invitation_asset_meeting | NO ACTION | NO ACTION |
| invitation_events | invitation_id | meeting_invitations | id | fk_invitation_event_invitation | NO ACTION | NO ACTION |
| invitation_events | share_link_id | invitation_share_links | id | fk_invitation_event_link | NO ACTION | NO ACTION |
| invitation_events | meeting_id | meetings | id | fk_invitation_event_meeting | NO ACTION | NO ACTION |
| invitation_events | version_id | meeting_invitation_versions | id | fk_invitation_event_version | NO ACTION | NO ACTION |
| invitation_share_links | invitation_id | meeting_invitations | id | fk_invitation_link_invitation | NO ACTION | NO ACTION |
| invitation_share_links | meeting_id | meetings | id | fk_invitation_link_meeting | NO ACTION | NO ACTION |
| invitation_share_links | current_version_id | meeting_invitation_versions | id | fk_invitation_link_version | NO ACTION | NO ACTION |
| meeting_dashboard_configs | meeting_id | meetings | id | fk_dashboard_config_meeting | NO ACTION | NO ACTION |
| meeting_invitation_versions | invitation_id | meeting_invitations | id | fk_invitation_version_invitation | NO ACTION | NO ACTION |
| meeting_invitations | latest_published_version_id | meeting_invitation_versions | id | fk_invitation_latest_version | NO ACTION | NO ACTION |
| meeting_invitations | meeting_id | meetings | id | fk_invitation_meeting | NO ACTION | NO ACTION |
| meeting_user_permissions | meeting_id | meetings | id | fk_meeting_user_permission_meeting | NO ACTION | NO ACTION |
| meeting_user_permissions | user_id | app_users | id | fk_meeting_user_permission_user | NO ACTION | NO ACTION |
| notification_records | meeting_id | meetings | id | fk_notification_meeting | NO ACTION | NO ACTION |
| public_query_history | attendee_id | attendees | id | fk_public_query_history_attendee | NO ACTION | NO ACTION |
| public_query_history | meeting_id | meetings | id | fk_public_query_history_meeting | NO ACTION | NO ACTION |
| registration_attributions | attendee_id | attendees | id | fk_registration_attr_attendee | NO ACTION | SET NULL |
| registration_attributions | click_event_id | invitation_events | id | fk_registration_attr_click | NO ACTION | SET NULL |
| registration_attributions | invitation_id | meeting_invitations | id | fk_registration_attr_invitation | NO ACTION | NO ACTION |
| registration_attributions | share_link_id | invitation_share_links | id | fk_registration_attr_link | NO ACTION | NO ACTION |
| registration_attributions | meeting_id | meetings | id | fk_registration_attr_meeting | NO ACTION | NO ACTION |
| registration_attributions | version_id | meeting_invitation_versions | id | fk_registration_attr_version | NO ACTION | NO ACTION |
| rooms | meeting_id | meetings | id | fk_room_meeting | NO ACTION | NO ACTION |
| seat_assignments | attendee_id | attendees | id | fk_seat_assignment_attendee | NO ACTION | NO ACTION |
| seat_assignments | meeting_id | meetings | id | fk_seat_assignment_meeting | NO ACTION | NO ACTION |
| seat_assignments | seat_id | seats | id | fk_seat_assignment_seat | NO ACTION | NO ACTION |
| seat_layouts | meeting_id | meetings | id | fk_seat_layout_meeting | NO ACTION | NO ACTION |
| seats | layout_id | seat_layouts | id | fk_seat_layout | NO ACTION | NO ACTION |
| seats | meeting_id | meetings | id | fk_seat_meeting | NO ACTION | NO ACTION |
| tduck_field_mappings | meeting_id | meetings | id | fk_tduck_mapping_meeting | NO ACTION | NO ACTION |
| tduck_form_bindings | meeting_id | meetings | id | fk_tduck_binding_meeting | NO ACTION | NO ACTION |
| tduck_pull_sync_tasks | meeting_id | meetings | id | fk_tduck_pull_task_meeting | NO ACTION | NO ACTION |
| tduck_submissions | attendee_id | attendees | id | fk_tduck_submission_attendee | NO ACTION | CASCADE |
| tduck_submissions | meeting_id | meetings | id | fk_tduck_submission_meeting | NO ACTION | CASCADE |
| tduck_sync_logs | meeting_id | meetings | id | fk_tduck_sync_meeting | NO ACTION | NO ACTION |
