# MeetingOS 数据模型

本文档描述 MVP 阶段核心数据对象。实际建表可以根据后端 ORM 习惯补充索引、逻辑删除和审计字段。

## 设计原则

- MeetingOS 与 TDuck 数据隔离，不直接写 TDuck 业务表。
- MeetingOS 保存 TDuck 表单绑定、字段映射、同步快照和同步日志。
- 手机号、证件号、邮箱等敏感字段展示时脱敏，导出时走权限控制。
- 关键业务变更必须写操作日志，尤其是审核、排座、房间分配、签到和导出。

## 核心实体

### meeting

会议主表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_code` | varchar(64) | 会议编号，对外报名中转使用 |
| `name` | varchar(200) | 会议名称 |
| `organizer` | varchar(200) | 主办方 |
| `co_organizer` | varchar(200) | 承办方 |
| `start_time` | datetime | 会议开始时间 |
| `end_time` | datetime | 会议结束时间 |
| `registration_start_time` | datetime | 报名开始时间 |
| `registration_end_time` | datetime | 报名截止时间 |
| `location` | varchar(300) | 会议地点 |
| `venue_name` | varchar(200) | 会场名称 |
| `dinner_venue` | varchar(160) | 晚宴地点，整场晚宴共用 |
| `dinner_time` | varchar(120) | 晚宴时间，整场晚宴共用 |
| `capacity` | int | 人数上限 |
| `status` | varchar(32) | 会议状态 |
| `description` | text | 会议简介 |
| `agenda` | text | 会议议程 |
| `cover_url` | varchar(500) | 封面图 |
| `contact_name` | varchar(100) | 联系人 |
| `contact_phone` | varchar(50) | 联系电话 |
| `created_by` | bigint | 创建人 |
| `dinner_query_enabled` | tinyint | 是否开放晚宴查询 |
| `public_query_dinner_notice` | text | 晚宴查询结果提示 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议索引：`uk_meeting_code`、`idx_status_start_time`、`idx_created_by`。

### meeting_registration_form

会议与 TDuck 表单绑定关系。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `tduck_form_key` | varchar(128) | TDuck 表单 key |
| `tduck_form_name` | varchar(200) | TDuck 表单名称 |
| `registration_url` | varchar(500) | TDuck 报名页 URL |
| `qr_code_url` | varchar(500) | MeetingOS 中转二维码图片 |
| `webhook_enabled` | tinyint | 是否启用 Webhook |
| `status` | varchar(32) | 绑定状态 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议索引：`uk_meeting_id`、`idx_tduck_form_key`。

### form_field_mapping

TDuck 字段到 MeetingOS 标准字段的映射。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `tduck_form_key` | varchar(128) | TDuck 表单 key |
| `tduck_field_id` | varchar(128) | TDuck 字段 ID |
| `tduck_field_name` | varchar(200) | TDuck 字段名称 |
| `system_field` | varchar(64) | MeetingOS 标准字段 |
| `required` | tinyint | 是否必填 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议唯一约束：`meeting_id + system_field`，避免一个系统字段被重复映射。

### attendee

参会人主表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `tduck_data_id` | varchar(128) | TDuck 数据 ID |
| `name` | varchar(100) | 姓名 |
| `phone` | varchar(50) | 手机号 |
| `company` | varchar(200) | 单位 |
| `title` | varchar(200) | 职务 |
| `id_number` | varchar(100) | 证件号，建议加密或脱敏存储 |
| `email` | varchar(200) | 邮箱 |
| `attendee_type` | varchar(64) | 参会类型 |
| `tags` | json | 标签数组 |
| `need_room` | tinyint | 是否需要住宿 |
| `audit_status` | varchar(32) | 审核状态 |
| `audit_reason` | varchar(500) | 审核备注或拒绝原因 |
| `auditor` | bigint | 审核人 |
| `audited_at` | datetime | 审核时间 |
| `raw_registration_data` | json | TDuck 原始报名快照 |
| `registered_at` | datetime | 报名时间 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议索引：`idx_meeting_phone`、`idx_meeting_audit_status`、`idx_meeting_attendee_type`。

### venue_seat

座位表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `area_name` | varchar(100) | 区域名称 |
| `row_no` | varchar(50) | 排号 |
| `seat_no` | varchar(50) | 座号 |
| `seat_label` | varchar(100) | 展示座位号 |
| `seat_type` | varchar(32) | 普通、VIP、嘉宾、媒体、工作人员、禁用 |
| `status` | varchar(32) | 空座、已分配、已签到、禁用 |
| `locked` | tinyint | 是否锁定 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议唯一约束：`meeting_id + area_name + row_no + seat_no`。

### seat_assignment

座位分配表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `attendee_id` | bigint | 参会人 ID |
| `seat_id` | bigint | 座位 ID |
| `assigned_by` | bigint | 分配人 |
| `assigned_type` | varchar(32) | 自动或手动 |
| `assigned_at` | datetime | 分配时间 |

建议唯一约束：`meeting_id + attendee_id`、`meeting_id + seat_id`。

### meeting_room

会议房间号表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `room_no` | varchar(100) | 房间号 |
| `hotel_name` | varchar(200) | 酒店名称 |
| `hotel_address` | varchar(300) | 酒店地址 |
| `building` | varchar(100) | 楼栋 |
| `floor_no` | varchar(50) | 楼层 |
| `room_type` | varchar(100) | 房型 |
| `capacity` | int | 可入住人数 |
| `checkin_tips` | varchar(500) | 入住提示 |
| `remark` | varchar(500) | 备注 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议唯一约束：`meeting_id + hotel_name + room_no`。

### room_assignment

房间分配表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `attendee_id` | bigint | 参会人 ID |
| `room_id` | bigint | 房间 ID |
| `assigned_by` | bigint | 分配人 |
| `assigned_type` | varchar(32) | 手动或导入 |
| `assigned_at` | datetime | 分配时间 |
| `remark` | varchar(500) | 备注 |

房间变更必须记录操作日志，包含原房间、新房间、操作人和修改原因。

### dinner_assignments

晚宴桌号分配表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `attendee_id` | bigint | 参会人 ID |
| `table_no` | varchar(80) | 晚宴桌号 |
| `remark` | varchar(500) | 备注 |
| `assigned_by` | varchar(64) | 最近分配人 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

建议唯一约束：`meeting_id + attendee_id`，确保同一会议同一参会人只有一条晚宴安排。晚宴地点和时间保存在会议主表，避免同一场晚宴在不同桌号或参会人记录里出现不一致。晚宴桌号变更必须记录操作日志，包含原桌号、新桌号、参会人和来源；重复导入同一参会人应更新原记录，不创建重复记录。

### dinner_tables

晚宴桌号容量表，用于维护每桌可就餐人数，并支撑“已安排人数 / 还缺人数 / 超员人数”统计。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `table_no` | varchar(80) | 晚宴桌号 |
| `capacity` | int | 可就餐人数，最小为 1 |
| `remark` | varchar(500) | 桌号备注 |
| `created_by` | varchar(64) | 创建人 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

唯一约束：`meeting_id + table_no`。桌号容量不能小于当前已安排到该桌的人数；删除桌号前必须先清空或调整该桌的晚宴安排。为兼容历史导入数据，`dinner_assignments.table_no` 仍保存桌号字符串，不强制外键关联；导入或手动安排到新桌号时会自动补建默认 10 人桌。

### checkin_record

签到记录表。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `attendee_id` | bigint | 参会人 ID |
| `checkin_time` | datetime | 签到时间 |
| `checkin_method` | varchar(32) | 扫码、搜索、手动、补签 |
| `entrance` | varchar(100) | 签到入口 |
| `operator_id` | bigint | 操作人 |
| `device_id` | varchar(100) | 设备 ID |
| `remark` | varchar(500) | 备注 |
| `created_at` | datetime | 创建时间 |

建议唯一约束：`meeting_id + attendee_id`，用于高并发重复签到拦截。

### checkin_attempt_log

重复扫码和失败签到日志，便于现场追溯。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID |
| `attendee_id` | bigint | 参会人 ID，可为空 |
| `raw_code` | varchar(1000) | 原始扫码内容，注意脱敏 |
| `status` | varchar(32) | 成功、重复、失败 |
| `error_message` | varchar(500) | 失败原因 |
| `operator_id` | bigint | 操作人 |
| `created_at` | datetime | 创建时间 |

### tduck_sync_log

TDuck 同步日志。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `meeting_id` | bigint | 会议 ID，可为空 |
| `tduck_form_key` | varchar(128) | TDuck 表单 key |
| `tduck_data_id` | varchar(128) | TDuck 数据 ID |
| `event_type` | varchar(64) | 事件类型，如 `form_data_add` |
| `payload` | json | 推送原始数据 |
| `status` | varchar(32) | 成功、失败、待处理、忽略 |
| `error_message` | varchar(1000) | 错误信息 |
| `attendee_id` | bigint | 关联参会人 |
| `created_at` | datetime | 创建时间 |

### operation_log

操作日志。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `operator_id` | bigint | 操作人 |
| `operation_type` | varchar(64) | 操作类型 |
| `target_type` | varchar(64) | 对象类型 |
| `target_id` | varchar(64) | 对象 ID |
| `before_data` | json | 操作前数据，注意脱敏 |
| `after_data` | json | 操作后数据，注意脱敏 |
| `ip_address` | varchar(64) | IP 地址 |
| `remark` | varchar(500) | 备注 |
| `created_at` | datetime | 创建时间 |

## 权限模型

MVP 可以从角色权限起步：

- `super_admin`：系统配置、全部会议和用户管理。
- `meeting_admin`：被授权会议的业务管理。
- `checkin_staff`：被授权会议的签到工作台。
- `room_staff`：被授权会议的住宿维护。
- `readonly`：被授权会议只读查看。

用户与会议之间建议使用授权表，例如 `meeting_user_permission`，避免会议管理员看到无关会议。

## 数据安全要求

- 列表默认脱敏：手机号保留前三后四，证件号和邮箱按规则隐藏。
- 导出需检查权限，并写 `operation_log`。
- Webhook payload 可保存原文，但后台展示应脱敏，日志不要输出完整 Token。
- 备份应覆盖 MySQL、Redis 持久化数据和 MinIO 文件。
