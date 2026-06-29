alter table meetings add column dinner_venue varchar(160);
alter table meetings add column dinner_time varchar(120);

update meetings m
set dinner_venue = coalesce(
  (
    select min(nullif(da.venue, ''))
    from dinner_assignments da
    where da.meeting_id = m.id
      and nullif(da.venue, '') is not null
  ),
  (
    select min(nullif(dt.venue, ''))
    from dinner_tables dt
    where dt.meeting_id = m.id
      and nullif(dt.venue, '') is not null
  )
)
where dinner_venue is null
  and (
    exists (
      select 1
      from dinner_assignments da
      where da.meeting_id = m.id
        and nullif(da.venue, '') is not null
    )
    or exists (
      select 1
      from dinner_tables dt
      where dt.meeting_id = m.id
        and nullif(dt.venue, '') is not null
    )
  );

update meetings m
set dinner_time = coalesce(
  (
    select min(nullif(da.dinner_time, ''))
    from dinner_assignments da
    where da.meeting_id = m.id
      and nullif(da.dinner_time, '') is not null
  ),
  (
    select min(nullif(dt.dinner_time, ''))
    from dinner_tables dt
    where dt.meeting_id = m.id
      and nullif(dt.dinner_time, '') is not null
  )
)
where dinner_time is null
  and (
    exists (
      select 1
      from dinner_assignments da
      where da.meeting_id = m.id
        and nullif(da.dinner_time, '') is not null
    )
    or exists (
      select 1
      from dinner_tables dt
      where dt.meeting_id = m.id
        and nullif(dt.dinner_time, '') is not null
    )
  );
