alter table meetings
  add column public_query_dinner_visible_result_fields varchar(500) not null default 'ATTENDEE_NAME,DINNER_TABLE_NO,DINNER_VENUE,DINNER_TIME,DINNER_REMARK',
  add column public_query_dinner_masked_result_fields varchar(500) not null default '',
  add column public_query_dinner_highlight_result_fields varchar(500) not null default 'DINNER_TABLE_NO';

update meetings
set public_query_visible_result_fields = concat(public_query_visible_result_fields, ',DINNER_TABLE_NO,DINNER_VENUE,DINNER_TIME,DINNER_REMARK')
where public_query_visible_result_fields not like '%DINNER_TABLE_NO%';

update meetings
set public_query_highlight_result_fields = concat(public_query_highlight_result_fields, ',DINNER_TABLE_NO')
where public_query_highlight_result_fields not like '%DINNER_TABLE_NO%';
