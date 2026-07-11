alter table export_tasks
  add column format varchar(16) not null default 'xlsx' after export_type,
  add column start_date date null after format,
  add column end_date date null after start_date,
  add column file_path varchar(500) null after filename,
  add column file_size bigint null after file_path,
  add column checksum varchar(64) null after file_size,
  add column progress int not null default 0 after status,
  add column generated_at datetime(6) null after completed_at,
  add column expires_at datetime(6) null after generated_at;

update export_tasks
set format = case
    when lower(filename) like '%.csv' then 'csv'
    else 'xlsx'
  end,
  status = 'EXPIRED',
  progress = 100,
  message = '历史导出记录未保存固定文件，已过期'
where status in ('PENDING', 'RUNNING', 'APPROVED', 'COMPLETED');

create index idx_export_task_meeting_status on export_tasks (meeting_id, status);
create index idx_export_task_meeting_type_status on export_tasks (meeting_id, export_type, status);
create index idx_export_task_status_expires on export_tasks (status, expires_at);
create index idx_export_task_status_updated on export_tasks (status, updated_at);

create table import_tasks (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  import_type varchar(32) not null,
  status varchar(32) not null,
  original_filename varchar(255) not null,
  input_file_path varchar(500),
  failure_file_path varchar(500),
  idempotency_key varchar(128) not null,
  preflight boolean not null,
  total_rows int not null default 0,
  processed_rows int not null default 0,
  imported_rows int not null default 0,
  failed_rows int not null default 0,
  progress int not null default 0,
  requested_by varchar(64),
  started_at datetime(6),
  completed_at datetime(6),
  expires_at datetime(6),
  message varchar(500),
  constraint fk_import_task_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_import_task_idempotency unique (meeting_id, import_type, idempotency_key),
  index idx_import_task_meeting_created (meeting_id, created_at),
  index idx_import_task_status_expires (status, expires_at),
  index idx_import_task_status_updated (status, updated_at),
  index idx_import_task_meeting_type_status (meeting_id, import_type, status)
);
