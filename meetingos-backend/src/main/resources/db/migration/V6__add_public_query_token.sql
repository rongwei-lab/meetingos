set @meetingos_public_query_token_column_exists = (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'meetings'
    and column_name = 'public_query_token'
);

set @meetingos_add_public_query_token_column = if(
  @meetingos_public_query_token_column_exists = 0,
  'alter table meetings add column public_query_token varchar(80) null after code',
  'select 1'
);

prepare meetingos_public_query_token_stmt from @meetingos_add_public_query_token_column;
execute meetingos_public_query_token_stmt;
deallocate prepare meetingos_public_query_token_stmt;

update meetings
set public_query_token = concat('pq_', lower(replace(uuid(), '-', '')))
where public_query_token is null or public_query_token = '';

alter table meetings
  modify column public_query_token varchar(80) not null;

set @meetingos_public_query_token_index_exists = (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'meetings'
    and index_name = 'uk_meeting_public_query_token'
);

set @meetingos_add_public_query_token_index = if(
  @meetingos_public_query_token_index_exists = 0,
  'alter table meetings add constraint uk_meeting_public_query_token unique (public_query_token)',
  'select 1'
);

prepare meetingos_public_query_token_stmt from @meetingos_add_public_query_token_index;
execute meetingos_public_query_token_stmt;
deallocate prepare meetingos_public_query_token_stmt;
