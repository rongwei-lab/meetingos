-- 先将既有值规范化为应用当前的写入口径。空串不代表有效业务主键，应按缺失值处理。
update attendees
set tduck_data_id = null
where tduck_data_id is not null and trim(tduck_data_id) = '';

update attendees
set tduck_data_id = trim(tduck_data_id)
where tduck_data_id is not null;

-- 迁移故意不清理历史重复报名：重复记录需要人工核对，静默删除会破坏业务数据和审计链。
-- 规范化后若仍存在重复的非 NULL 值，唯一约束会明确失败并阻止带病升级。
alter table attendees
  add constraint uk_attendee_meeting_tduck_data
  unique (meeting_id, tduck_data_id);

-- 失败事件必须允许使用相同 event_id 重试，因此不能直接唯一约束 event_id。
-- 仅 SUCCESS 行写入 success_event_id；MySQL/H2 均允许唯一列存在多个 NULL。
alter table tduck_sync_logs
  add column success_event_id varchar(120);

-- 历史上“先查后写”可能已经产生重复 SUCCESS。保留全部日志，只让每个规范化 eventId
-- 最早的一条占用幂等键；其余行保持 NULL，不会因为补约束而破坏升级。
update tduck_sync_logs
set success_event_id = trim(event_id)
where id in (
  select first_success_id
  from (
    select min(id) as first_success_id
    from tduck_sync_logs
    where status = 'SUCCESS' and event_id is not null and trim(event_id) <> ''
    group by trim(event_id)
  ) first_success
);

alter table tduck_sync_logs
  add constraint uk_tduck_sync_success_event
  unique (success_event_id);
