alter table meetings
  add column public_query_allow_multiple_results boolean not null default false after public_query_match_mode;
