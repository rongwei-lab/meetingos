-- V1 已发布，不能删除其中的演示数据或修改 checksum。本迁移在全量建表后清理历史演示数据，
-- 让新库首次启动保持为空；同时只命中 V1 的固定会议标识，避免影响用户自行创建的会议。

-- 邀请函相关表存在版本、分享链接和事件的外键链路，必须先清理最末端记录。
delete from registration_attributions
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from invitation_events
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from invitation_share_links
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

update meeting_invitations
set latest_published_version_id = null
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from meeting_invitation_versions
where invitation_id in (
  select id from meeting_invitations
  where meeting_id in (
    select id from meetings
    where code = 'NDEF2026'
      and public_query_token = 'pq_seed_ndef2026'
      and name = '2026年全国数字经济发展论坛'
  )
);

delete from meeting_invitations
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from invitation_assets
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

-- 删除会议、参会人和座位的所有直接依赖，确保不会留下无效演示记录。
delete from meeting_dashboard_configs
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from tduck_pull_sync_tasks
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from import_tasks
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from export_tasks
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from notification_records
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from tduck_sync_logs
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from public_query_history
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from checkin_records
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from dinner_assignments
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from seat_assignments
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from tduck_submissions
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from seats
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from seat_layouts
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from rooms
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from dinner_tables
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from tduck_field_mappings
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from tduck_form_bindings
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from meeting_user_permissions
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from attendees
where meeting_id in (
  select id from meetings
  where code = 'NDEF2026'
    and public_query_token = 'pq_seed_ndef2026'
    and name = '2026年全国数字经济发展论坛'
);

delete from meetings
where code = 'NDEF2026'
  and public_query_token = 'pq_seed_ndef2026'
  and name = '2026年全国数字经济发展论坛';

-- V1 同时写入了一个已停用的弱密码占位账号。已被启动器升级为真实管理员的账号不匹配此条件。
delete from app_user_roles
where user_id in (
  select id from app_users
  where username = 'admin'
    and display_name = '超级管理员'
    and password_hash = '{noop}admin123'
    and status = 'DISABLED'
    and not exists (
      select 1 from meeting_user_permissions
      where meeting_user_permissions.user_id = app_users.id
    )
);

delete from app_users
where username = 'admin'
  and display_name = '超级管理员'
  and password_hash = '{noop}admin123'
  and status = 'DISABLED'
  and not exists (
    select 1 from meeting_user_permissions
    where meeting_user_permissions.user_id = app_users.id
  );
