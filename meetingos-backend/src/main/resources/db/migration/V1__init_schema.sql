-- MeetingOS 当前完整初始化结构。
-- 注意：这是合并后的 Flyway 基线脚本，适用于新库或可重建的开发库。
-- 已经执行过拆分版 V1~V3 的数据库如遇本地迁移缺失或 checksum 校验失败，
-- 需要按 Flyway 规范评估 repair，或在开发/测试环境重建数据库。

create table app_users (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  username varchar(64) not null,
  display_name varchar(120) not null,
  password_hash varchar(255) not null,
  status varchar(32) not null,
  constraint uk_app_user_username unique (username)
);

create table app_user_roles (
  user_id bigint not null,
  role varchar(32) not null,
  constraint fk_app_user_roles_user foreign key (user_id) references app_users(id)
);

create table meetings (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  code varchar(64) not null,
  name varchar(160) not null,
  organizer varchar(160),
  co_organizer varchar(160),
  start_time datetime(6),
  end_time datetime(6),
  registration_start_time datetime(6),
  registration_end_time datetime(6),
  location varchar(255),
  venue_name varchar(160),
  capacity int,
  description text,
  agenda text,
  cover_image_url varchar(500),
  contact_name varchar(80),
  contact_phone varchar(40),
  dinner_venue varchar(160),
  dinner_time varchar(120),
  status varchar(40) not null,
  created_by varchar(64),
  seat_query_enabled boolean not null default false,
  room_query_enabled boolean not null default false,
  dinner_query_enabled boolean not null default false,
  checkin_enabled boolean not null default true,
  self_checkin_enabled boolean not null default true,
  public_query_fields varchar(255) not null default 'PHONE,NAME',
  public_query_match_mode varchar(20) not null default 'FUZZY',
  public_query_paused boolean not null default false,
  public_query_title varchar(160),
  public_query_description varchar(500),
  public_query_announcement text,
  public_query_publisher varchar(160),
  public_query_start_time datetime(6),
  public_query_end_time datetime(6),
  public_query_button_text varchar(40) not null default '立即查询',
  public_query_empty_message varchar(255) not null default '未查询到结果，请核对信息后重试',
  public_query_seat_notice text,
  public_query_room_notice text,
  public_query_dinner_notice text,
  public_query_page_content json null,
  public_query_theme_color varchar(20) not null default '#2563eb',
  public_query_logo_url varchar(500),
  public_query_background_url varchar(500),
  public_query_result_layout varchar(20) not null default 'CARD',
  public_query_visible_result_fields varchar(500) not null default 'ATTENDEE_NAME,SEAT_AREA,SEAT_ROW,SEAT_NO,SEAT_CODE,HOTEL_NAME,ROOM_TYPE,ROOM_NO,ROOM_REMARK,CHECKIN_TIPS,DINNER_TABLE_NO,DINNER_VENUE,DINNER_TIME,DINNER_REMARK',
  public_query_masked_result_fields varchar(500) not null default '',
  public_query_highlight_result_fields varchar(500) not null default 'SEAT_CODE,ROOM_NO,DINNER_TABLE_NO',
  public_query_seat_visible_result_fields varchar(500) not null default 'ATTENDEE_NAME,SEAT_AREA,SEAT_ROW,SEAT_NO,SEAT_CODE',
  public_query_seat_masked_result_fields varchar(500) not null default '',
  public_query_seat_highlight_result_fields varchar(500) not null default 'SEAT_CODE',
  public_query_room_visible_result_fields varchar(500) not null default 'ATTENDEE_NAME,HOTEL_NAME,ROOM_TYPE,ROOM_NO,ROOM_REMARK,CHECKIN_TIPS',
  public_query_room_masked_result_fields varchar(500) not null default '',
  public_query_room_highlight_result_fields varchar(500) not null default 'ROOM_NO',
  public_query_dinner_visible_result_fields varchar(500) not null default 'ATTENDEE_NAME,DINNER_TABLE_NO,DINNER_VENUE,DINNER_TIME,DINNER_REMARK',
  public_query_dinner_masked_result_fields varchar(500) not null default '',
  public_query_dinner_highlight_result_fields varchar(500) not null default 'DINNER_TABLE_NO',
  constraint uk_meeting_code unique (code)
);

create table attendees (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  name varchar(120) not null,
  phone varchar(40) not null,
  organization varchar(160),
  title varchar(120),
  identity_no varchar(80),
  email varchar(160),
  attendee_type varchar(32) not null,
  accommodation_required boolean not null,
  room_no varchar(80),
  room_remark varchar(160),
  remark varchar(500),
  tags varchar(300),
  review_status varchar(32) not null,
  review_reason varchar(500),
  review_remark varchar(500),
  reviewed_at datetime(6),
  reviewed_by varchar(64),
  registered_at datetime(6),
  checkin_status varchar(32) not null,
  checked_in_at datetime(6),
  checkin_entrance varchar(80),
  checkin_operator varchar(64),
  attendee_code varchar(80) not null,
  tduck_data_id varchar(80),
  raw_data_snapshot json,
  constraint fk_attendee_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_attendee_meeting_phone unique (meeting_id, phone),
  constraint uk_attendee_code unique (attendee_code)
);

create table tduck_form_bindings (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  form_key varchar(120) not null,
  form_name varchar(160) not null,
  registration_url varchar(500) not null,
  binding_status varchar(32) not null,
  mapping_status varchar(32) not null,
  webhook_status varchar(32) not null,
  constraint fk_tduck_binding_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_tduck_binding_meeting unique (meeting_id),
  constraint uk_tduck_binding_form_key unique (form_key)
);

create table tduck_field_mappings (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  attendee_field varchar(48),
  tduck_field_key varchar(120) not null,
  tduck_field_label varchar(160),
  required_mapping boolean not null,
  constraint fk_tduck_mapping_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_mapping_meeting_target unique (meeting_id, attendee_field),
  constraint uk_mapping_meeting_tduck_field unique (meeting_id, tduck_field_key)
);

create table tduck_sync_logs (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint,
  form_key varchar(120) not null,
  event_id varchar(120),
  event_type varchar(80) not null,
  status varchar(32) not null,
  message varchar(500),
  attendee_id bigint,
  tduck_data_id varchar(120),
  raw_payload json,
  constraint fk_tduck_sync_meeting foreign key (meeting_id) references meetings(id),
  index idx_tduck_sync_event_id (event_id)
);

create table seat_layouts (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  venue_name varchar(160) not null,
  area_name varchar(80) not null,
  row_count int not null,
  seats_per_row int not null,
  sort_order int not null default 0,
  numbering_rule varchar(160),
  aisle_settings varchar(160),
  stage_direction varchar(80),
  constraint fk_seat_layout_meeting foreign key (meeting_id) references meetings(id)
);

create table seats (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  layout_id bigint,
  area_name varchar(80) not null,
  row_no int not null,
  seat_no int not null,
  seat_code varchar(80) not null,
  seat_type varchar(32) not null,
  disabled boolean not null,
  locked boolean not null,
  constraint fk_seat_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_seat_layout foreign key (layout_id) references seat_layouts(id),
  constraint uk_seat_meeting_area_row_no unique (meeting_id, area_name, row_no, seat_no)
);

create table seat_assignments (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  attendee_id bigint not null,
  seat_id bigint not null,
  manual_assigned boolean not null,
  assigned_by varchar(64),
  constraint fk_seat_assignment_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_seat_assignment_attendee foreign key (attendee_id) references attendees(id),
  constraint fk_seat_assignment_seat foreign key (seat_id) references seats(id),
  constraint uk_assignment_attendee unique (attendee_id),
  constraint uk_assignment_seat unique (seat_id)
);

create table rooms (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  room_no varchar(80) not null,
  room_type varchar(80),
  hotel_name varchar(160),
  remark varchar(500),
  capacity int not null default 1,
  status varchar(32) not null,
  constraint fk_room_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_room_meeting_no unique (meeting_id, room_no)
);

create table dinner_assignments (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  attendee_id bigint not null,
  table_no varchar(80) not null,
  venue varchar(160),
  dinner_time varchar(120),
  remark varchar(500),
  assigned_by varchar(64),
  constraint fk_dinner_assignment_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_dinner_assignment_attendee foreign key (attendee_id) references attendees(id),
  constraint uk_dinner_assignment_attendee unique (meeting_id, attendee_id),
  index idx_dinner_assignment_meeting_table (meeting_id, table_no)
);

create table dinner_tables (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  table_no varchar(80) not null,
  capacity int not null default 10,
  venue varchar(160),
  dinner_time varchar(120),
  remark varchar(500),
  created_by varchar(64),
  constraint fk_dinner_table_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_dinner_table_meeting_no unique (meeting_id, table_no),
  index idx_dinner_table_meeting (meeting_id, table_no)
);

create table checkin_records (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  attendee_id bigint,
  checkin_type varchar(32) not null,
  result_status varchar(32) not null,
  entrance varchar(80),
  operator varchar(64),
  checked_in_at datetime(6),
  message varchar(500),
  request_snapshot json,
  constraint fk_checkin_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_checkin_attendee foreign key (attendee_id) references attendees(id)
);

create table operation_logs (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  actor varchar(64) not null,
  action varchar(80) not null,
  target_type varchar(80),
  target_id varchar(80),
  client_ip varchar(45),
  success boolean not null,
  message varchar(500),
  details json
);

create index idx_operation_logs_created_at on operation_logs (created_at);
create index idx_operation_logs_action_created_at on operation_logs (action, created_at);
create index idx_operation_logs_actor_created_at on operation_logs (actor, created_at);
create index idx_operation_logs_success_created_at on operation_logs (success, created_at);

create table notification_records (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  title varchar(160) not null,
  content varchar(1000) not null,
  channel varchar(40),
  scope varchar(80),
  status varchar(40) not null,
  expected_count int,
  constraint fk_notification_meeting foreign key (meeting_id) references meetings(id),
  index idx_notification_meeting_created (meeting_id, created_at)
);

create table meeting_user_permissions (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  user_id bigint not null,
  permission_role varchar(32) not null,
  granted_by varchar(64),
  constraint fk_meeting_user_permission_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_meeting_user_permission_user foreign key (user_id) references app_users(id),
  constraint uk_meeting_user_permission unique (meeting_id, user_id),
  index idx_meeting_user_permission_meeting (meeting_id),
  index idx_meeting_user_permission_user (user_id)
);

create table export_tasks (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  export_type varchar(64) not null,
  status varchar(32) not null,
  filename varchar(160) not null,
  requested_by varchar(64),
  completed_at datetime(6),
  message varchar(500),
  constraint fk_export_task_meeting foreign key (meeting_id) references meetings(id),
  index idx_export_task_meeting_created (meeting_id, created_at)
);

create table system_settings (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  setting_key varchar(80) not null,
  setting_value text,
  constraint uk_system_setting_key unique (setting_key)
);

create table public_query_history (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  query_type varchar(20) not null,
  query_status varchar(20) not null,
  query_values json,
  query_text varchar(1000),
  error_message varchar(500),
  attendee_id bigint,
  attendee_name varchar(120),
  attendee_phone varchar(40),
  result_summary varchar(500),
  client_ip varchar(45),
  user_agent varchar(500),
  constraint fk_public_query_history_meeting foreign key (meeting_id) references meetings(id),
  constraint fk_public_query_history_attendee foreign key (attendee_id) references attendees(id),
  index idx_public_query_history_meeting_created (meeting_id, created_at),
  index idx_public_query_history_meeting_status (meeting_id, query_status),
  index idx_public_query_history_meeting_type (meeting_id, query_type)
);

-- 保留旧演示管理员账号但直接置为禁用；启动器会要求通过环境变量创建强密码管理员。
insert into app_users (id, created_at, updated_at, username, display_name, password_hash, status)
values (1, now(6), now(6), 'admin', '超级管理员', '{noop}admin123', 'DISABLED');

insert into app_user_roles (user_id, role) values (1, 'SUPER_ADMIN'), (1, 'MEETING_ADMIN');

insert into meetings (
  id, created_at, updated_at, code, name, organizer, start_time, end_time,
  registration_start_time, registration_end_time, location, venue_name, capacity,
  description, contact_name, contact_phone, dinner_venue, dinner_time, status, created_by,
  public_query_seat_notice, public_query_room_notice, public_query_dinner_notice
) values (
  1, now(6), now(6), 'NDEF2026', '2026年全国数字经济发展论坛', '数字经济产业联盟',
  '2026-07-15 09:00:00', '2026-07-15 18:00:00',
  '2026-06-01 09:00:00', '2026-07-10 18:00:00',
  '北京国家会议中心', '主会场', 500,
  '覆盖报名、排座、住宿、晚宴和签到的演示会议', '会务组', '400-000-2026',
  '北京国家会议中心三层宴会厅', '2026-07-15 18:30',
  'REGISTRATION_OPEN', 'admin',
  '请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。',
  '凭会议证件及身份证办理入住，接待台位于酒店大堂。',
  '请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。'
);

insert into tduck_form_bindings (
  id, created_at, updated_at, meeting_id, form_key, form_name, registration_url,
  binding_status, mapping_status, webhook_status
) values (
  1, now(6), now(6), 1, 'ndef2026', '数字经济论坛报名表',
  'http://localhost:8888/s/ndef2026', 'BOUND', 'VALID', 'CONFIGURED'
);

insert into tduck_field_mappings (created_at, updated_at, meeting_id, attendee_field, tduck_field_key, tduck_field_label, required_mapping)
values
  (now(6), now(6), 1, 'NAME', 'name', '姓名', true),
  (now(6), now(6), 1, 'PHONE', 'phone', '手机号', true),
  (now(6), now(6), 1, 'ORGANIZATION', 'organization', '单位', false),
  (now(6), now(6), 1, 'TITLE', 'title', '职务', false),
  (now(6), now(6), 1, 'ATTENDEE_TYPE', 'attendeeType', '参会类型', false);

-- 种子参会人只用于演示报名列表，不预置住宿、标签或座位分配，避免新库呈现“自动排好”的假象。
insert into attendees (
  id, created_at, updated_at, meeting_id, name, phone, organization, title, attendee_type,
  accommodation_required, room_no, room_remark, tags, review_status, reviewed_at, reviewed_by,
  registered_at, checkin_status, attendee_code, raw_data_snapshot
) values
  (1, now(6), now(6), 1, '陈嘉宁', '13800000001', '未来科技集团', '副总裁', 'VIP', false, null, null, null, 'APPROVED', now(6), 'admin', now(6), 'NOT_CHECKED_IN', 'NDEF2026-0001', json_object('source', 'seed')),
  (2, now(6), now(6), 1, '王子墨', '13800000002', '北方数据院', '研究员', 'GUEST', false, null, null, null, 'APPROVED', now(6), 'admin', now(6), 'NOT_CHECKED_IN', 'NDEF2026-0002', json_object('source', 'seed')),
  (3, now(6), now(6), 1, '李若彤', '13800000003', '财经观察', '记者', 'MEDIA', false, null, null, null, 'PENDING', null, null, now(6), 'NOT_CHECKED_IN', 'NDEF2026-0003', json_object('source', 'seed')),
  (4, now(6), now(6), 1, '周明远', '13800000004', '云川科技', '产品总监', 'NORMAL', false, null, null, null, 'APPROVED', now(6), 'admin', now(6), 'NOT_CHECKED_IN', 'NDEF2026-0004', json_object('source', 'seed'));

insert into rooms (id, created_at, updated_at, meeting_id, room_no, room_type, hotel_name, remark, capacity, status)
values
  (1, now(6), now(6), 1, '1801', '大床房', '北京会议中心酒店', '凭身份证办理入住', 1, 'AVAILABLE'),
  (2, now(6), now(6), 1, '1802', '双床房', '北京会议中心酒店', '14:00 后可入住', 2, 'AVAILABLE'),
  (3, now(6), now(6), 1, '1803', '双床房', '北京会议中心酒店', '早餐在二楼', 2, 'AVAILABLE');

insert into dinner_tables (id, created_at, updated_at, meeting_id, table_no, capacity, venue, dinner_time, remark, created_by)
values
  (1, now(6), now(6), 1, 'A1', 10, '北京国家会议中心三层宴会厅', '2026-07-15 18:30', '主桌预留', 'admin'),
  (2, now(6), now(6), 1, 'A2', 10, '北京国家会议中心三层宴会厅', '2026-07-15 18:30', null, 'admin'),
  (3, now(6), now(6), 1, 'A3', 10, '北京国家会议中心三层宴会厅', '2026-07-15 18:30', null, 'admin');

insert into seat_layouts (id, created_at, updated_at, meeting_id, venue_name, area_name, row_count, seats_per_row, sort_order, numbering_rule, aisle_settings, stage_direction)
values (1, now(6), now(6), 1, '主会场', 'A区', 4, 6, 0, 'row-seat', '3号座后过道', '北');

insert into seats (created_at, updated_at, meeting_id, layout_id, area_name, row_no, seat_no, seat_code, seat_type, disabled, locked)
select now(6), now(6), 1, 1, 'A区', r.n, s.n, concat('A区-', r.n, '-', s.n),
       case when r.n = 1 then 'VIP' when r.n = 2 then 'GUEST' else 'NORMAL' end,
       false, false
from (select 1 n union all select 2 union all select 3 union all select 4) r
cross join (select 1 n union all select 2 union all select 3 union all select 4 union all select 5 union all select 6) s;
