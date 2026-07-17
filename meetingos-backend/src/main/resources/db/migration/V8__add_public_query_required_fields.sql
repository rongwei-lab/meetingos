alter table meetings add column public_query_required_fields varchar(255) null after public_query_fields;

update meetings
set public_query_required_fields = public_query_fields
where public_query_required_fields is null or trim(public_query_required_fields) = '';

alter table meetings modify column public_query_required_fields varchar(255) not null;
