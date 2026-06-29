alter table meetings
  add column dinner_query_enabled boolean not null default false,
  add column public_query_dinner_notice text;

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
  constraint uk_dinner_assignment_attendee unique (meeting_id, attendee_id)
);

create index idx_dinner_assignment_meeting_table on dinner_assignments (meeting_id, table_no);
