-- 邀请函方案保存可编辑草稿，发布版本不可变。方案归档后仍保留渠道和转化历史。
create table meeting_invitations (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  name varchar(160) not null,
  status varchar(32) not null,
  draft_revision bigint not null default 0,
  draft_document json not null,
  latest_published_version_id bigint,
  created_by varchar(64),
  published_at datetime(6),
  unpublished_at datetime(6),
  archived_at datetime(6),
  constraint fk_invitation_meeting foreign key (meeting_id) references meetings(id),
  index idx_invitation_meeting_status (meeting_id, status, updated_at)
);

create table meeting_invitation_versions (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  invitation_id bigint not null,
  version_no int not null,
  design_document json not null,
  meeting_snapshot json not null,
  published_by varchar(64),
  constraint fk_invitation_version_invitation foreign key (invitation_id) references meeting_invitations(id),
  constraint uk_invitation_version_no unique (invitation_id, version_no),
  index idx_invitation_version_created (invitation_id, created_at)
);

alter table meeting_invitations
  add constraint fk_invitation_latest_version
  foreign key (latest_published_version_id) references meeting_invitation_versions(id);

create table invitation_assets (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  original_name varchar(255) not null,
  storage_name varchar(160) not null,
  public_url varchar(500) not null,
  content_type varchar(80) not null,
  size_bytes bigint not null,
  width_px int,
  height_px int,
  created_by varchar(64),
  constraint fk_invitation_asset_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_invitation_asset_storage unique (storage_name),
  index idx_invitation_asset_meeting_created (meeting_id, created_at)
);

create table invitation_share_links (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  invitation_id bigint not null,
  current_version_id bigint,
  channel_type varchar(32) not null,
  channel_name varchar(120) not null,
  share_token varchar(96) not null,
  enabled boolean not null default true,
  expires_at datetime(6),
  created_by varchar(64),
  constraint fk_invitation_link_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_invitation_link_invitation foreign key (invitation_id) references meeting_invitations(id),
  constraint fk_invitation_link_version foreign key (current_version_id) references meeting_invitation_versions(id),
  constraint uk_invitation_share_token unique (share_token),
  index idx_invitation_link_invitation (invitation_id, enabled, created_at),
  index idx_invitation_link_meeting (meeting_id, created_at)
);

-- 只保存不可逆访客 HMAC，不保存原始 Cookie、IP 或完整 User-Agent。
create table invitation_events (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  invitation_id bigint not null,
  version_id bigint not null,
  share_link_id bigint not null,
  event_type varchar(32) not null,
  client_event_id varchar(80) not null,
  visitor_hash char(64) not null,
  occurred_at datetime(6) not null,
  click_public_id varchar(80),
  click_expires_at datetime(6),
  device_type varchar(24),
  referrer_host varchar(255),
  constraint fk_invitation_event_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_invitation_event_invitation foreign key (invitation_id) references meeting_invitations(id),
  constraint fk_invitation_event_version foreign key (version_id) references meeting_invitation_versions(id),
  constraint fk_invitation_event_link foreign key (share_link_id) references invitation_share_links(id),
  constraint uk_invitation_event_client unique (share_link_id, client_event_id),
  constraint uk_invitation_event_click unique (click_public_id),
  index idx_invitation_event_meeting_time (meeting_id, occurred_at, event_type),
  index idx_invitation_event_link_type_time (share_link_id, event_type, occurred_at),
  index idx_invitation_event_visitor (share_link_id, visitor_hash, event_type)
);

create table registration_attributions (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  invitation_id bigint,
  version_id bigint,
  share_link_id bigint,
  click_event_id bigint,
  attendee_id bigint,
  attribution_status varchar(32) not null,
  form_key varchar(120) not null,
  tduck_data_id varchar(120) not null,
  active boolean not null default true,
  attributed_at datetime(6) not null,
  constraint fk_registration_attr_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_registration_attr_invitation foreign key (invitation_id) references meeting_invitations(id),
  constraint fk_registration_attr_version foreign key (version_id) references meeting_invitation_versions(id),
  constraint fk_registration_attr_link foreign key (share_link_id) references invitation_share_links(id),
  -- 访问/点击事件会在会议结束 180 天后清理，永久归因记录必须能独立保留。
  constraint fk_registration_attr_click foreign key (click_event_id) references invitation_events(id) on delete set null,
  constraint fk_registration_attr_attendee foreign key (attendee_id) references attendees(id) on delete set null,
  constraint uk_registration_attr_tduck unique (meeting_id, tduck_data_id),
  index idx_registration_attr_meeting_time (meeting_id, attributed_at),
  index idx_registration_attr_invitation (invitation_id, share_link_id)
);

-- V6 上线前已经同步的 TDuck 报名没有邀请来源，显式标为历史未归因，避免与新产生的直接报名混淆。
insert into registration_attributions (
  created_at, updated_at, meeting_id, invitation_id, version_id, share_link_id, click_event_id,
  attendee_id, attribution_status, form_key, tduck_data_id, active, attributed_at
)
select
  coalesce(a.registered_at, a.created_at), coalesce(a.registered_at, a.created_at), a.meeting_id,
  null, null, null, null, a.id, 'LEGACY_UNATTRIBUTED', b.form_key, a.tduck_data_id,
  case when a.review_status in ('CANCELLED', 'REJECTED') then false else true end,
  coalesce(a.registered_at, a.created_at)
from attendees a
join tduck_form_bindings b on b.meeting_id = a.meeting_id
where a.tduck_data_id is not null;
