create table tduck_submissions (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  attendee_id bigint not null,
  form_key varchar(120) not null,
  tduck_data_id varchar(120) not null,
  status varchar(32) not null,
  active_attendee_id bigint,
  submitted_at datetime(6),
  resolved_at datetime(6),
  resolved_by varchar(64),
  resolution_note varchar(500),
  name varchar(120) not null,
  phone varchar(40) not null,
  organization varchar(160),
  title varchar(120),
  identity_no varchar(80),
  email varchar(160),
  attendee_type varchar(32) not null,
  accommodation_required boolean not null,
  remark varchar(500),
  raw_data_snapshot json,
  constraint fk_tduck_submission_meeting foreign key (meeting_id) references meetings(id) on delete cascade,
  constraint fk_tduck_submission_attendee foreign key (attendee_id) references attendees(id) on delete cascade,
  constraint uk_tduck_submission_meeting_data unique (meeting_id, tduck_data_id),
  constraint uk_tduck_submission_active_attendee unique (meeting_id, active_attendee_id),
  index idx_tduck_submission_conflict (meeting_id, status, created_at),
  index idx_tduck_submission_attendee_status (attendee_id, status)
);

-- 将已绑定的 dataId 回填为当前有效提交；报名原始快照用于后续管理员比较，不丢弃历史审计信息。
insert into tduck_submissions (
  created_at, updated_at, meeting_id, attendee_id, form_key, tduck_data_id, status, active_attendee_id, submitted_at,
  name, phone, organization, title, identity_no, email, attendee_type, accommodation_required, remark, raw_data_snapshot
)
select
  a.created_at, a.updated_at, a.meeting_id, a.id,
  coalesce((select b.form_key from tduck_form_bindings b where b.meeting_id = a.meeting_id), 'LEGACY'),
  a.tduck_data_id, 'ACTIVE', a.id, coalesce(a.registered_at, a.created_at),
  a.name, a.phone, a.organization, a.title, a.identity_no, a.email, a.attendee_type, a.accommodation_required, a.remark, a.raw_data_snapshot
from attendees a
where a.tduck_data_id is not null and trim(a.tduck_data_id) <> '';

-- 审核、采用新提交和撤回恢复都按会议与参会人回写归因状态，避免大会议逐次扫描归因全表。
create index idx_registration_attr_meeting_attendee_data
  on registration_attributions (meeting_id, attendee_id, tduck_data_id);

-- V9 上线前已有归因只认当时 attendees 上的当前 dataId。按新历史表重算一次，
-- 防止曾被手工调整 dataId 的旧归因继续被统计为有效报名。
update registration_attributions attribution
left join tduck_submissions submission
  on submission.meeting_id = attribution.meeting_id
  and submission.attendee_id = attribution.attendee_id
  and submission.tduck_data_id = attribution.tduck_data_id
  and submission.status = 'ACTIVE'
left join attendees attendee
  on attendee.id = attribution.attendee_id
set attribution.active = case
  when submission.id is not null and attendee.review_status not in ('REJECTED', 'CANCELLED') then true
  else false
end;
