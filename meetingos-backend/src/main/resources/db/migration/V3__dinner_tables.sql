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
  constraint uk_dinner_table_meeting_no unique (meeting_id, table_no)
);

create index idx_dinner_table_meeting on dinner_tables (meeting_id, table_no);

insert into dinner_tables (
  created_at,
  updated_at,
  meeting_id,
  table_no,
  capacity,
  venue,
  dinner_time,
  remark,
  created_by
)
select
  current_timestamp(6),
  current_timestamp(6),
  meeting_id,
  table_no,
  greatest(count(*), 10),
  min(venue),
  min(dinner_time),
  null,
  'migration'
from dinner_assignments
group by meeting_id, table_no;
