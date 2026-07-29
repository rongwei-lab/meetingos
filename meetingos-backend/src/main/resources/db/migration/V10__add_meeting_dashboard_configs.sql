-- 会议级默认统计看板。组件以 JSON 保存，方便后续增加布局、标题和筛选条件，
-- 但实际统计仍由服务端按字段目录和白名单聚合执行。
create table meeting_dashboard_configs (
  id bigint primary key auto_increment,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  meeting_id bigint not null,
  configuration json not null,
  constraint fk_dashboard_config_meeting foreign key (meeting_id) references meetings(id),
  constraint uk_dashboard_config_meeting unique (meeting_id)
);
