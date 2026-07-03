alter table meetings
  add column public_combined_query_types varchar(80) not null default 'SEAT,ROOM,DINNER' after public_query_match_mode;
