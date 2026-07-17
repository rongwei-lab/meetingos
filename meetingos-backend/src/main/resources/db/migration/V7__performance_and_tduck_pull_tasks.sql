-- 公共查询历史按创建时间分批清理，并加速会议内按参会人选择最近一条记录。
create index idx_public_query_history_created_id
  on public_query_history (created_at, id);
create index idx_public_query_history_meeting_attendee_created
  on public_query_history (meeting_id, attendee_id, created_at, id);

-- 邀请统计支持整场会议和指定邀请函两个维度，归因明细按最近时间读取。
create index idx_invitation_event_invitation_time_type
  on invitation_events (meeting_id, invitation_id, occurred_at, event_type);
create index idx_registration_attr_invitation_time_status
  on registration_attributions (meeting_id, invitation_id, attributed_at, attribution_status, active);
create index idx_registration_attr_detail_time
  on registration_attributions (invitation_id, attributed_at, id);

-- 精确公开查询和住宿/晚宴导入按业务标识定位参会人，避免逐行扫描整场会议。
create index idx_attendee_meeting_identity_no
  on attendees (meeting_id, identity_no);
create index idx_attendee_meeting_name_org
  on attendees (meeting_id, name, organization);
create index idx_attendee_meeting_email
  on attendees (meeting_id, email);
create index idx_attendee_meeting_organization
  on attendees (meeting_id, organization);
create index idx_attendee_meeting_title
  on attendees (meeting_id, title);

-- 增长型同步日志按会议倒序分页，复合索引避免每页 filesort。
create index idx_tduck_sync_meeting_created
  on tduck_sync_logs (meeting_id, created_at, id);

-- 两个普通索引与现有唯一索引列序完全相同，可直接删除而不改变可用访问路径。
drop index idx_attendee_meeting_tduck_data on attendees;
drop index idx_dinner_table_meeting on dinner_tables;

-- 其余三个索引是更长索引的严格前缀。先设为不可见以保留快速回退能力，
-- 观察生产执行计划后再由后续迁移物理删除，当前阶段不冒险改变优化器选择。
alter table meeting_user_permissions alter index idx_meeting_user_permission_meeting invisible;
alter table public_query_history alter index idx_public_query_history_meeting_type invisible;
alter table tduck_sync_logs alter index idx_tduck_sync_event_id invisible;

-- TDuck 拉取同步改为可恢复的后台任务。run_token 防止租约接管后旧 worker 覆盖新进度。
create table tduck_pull_sync_tasks (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  form_key varchar(120) not null,
  requested_limit int not null,
  idempotency_key varchar(128),
  status varchar(32) not null,
  total_rows int not null default 0,
  processed_rows int not null default 0,
  success_rows int not null default 0,
  duplicate_rows int not null default 0,
  failed_rows int not null default 0,
  progress int not null default 0,
  failure_details text,
  message varchar(500),
  requested_by varchar(64),
  started_at datetime(6),
  completed_at datetime(6),
  lease_until datetime(6),
  run_token varchar(64),
  constraint fk_tduck_pull_task_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_tduck_pull_task_idempotency unique (meeting_id, idempotency_key),
  index idx_tduck_pull_task_meeting_created (meeting_id, created_at),
  index idx_tduck_pull_task_meeting_status (meeting_id, status),
  index idx_tduck_pull_task_recovery (status, lease_until)
);
