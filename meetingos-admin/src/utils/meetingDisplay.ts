import type { AttendeeType, Meeting, SeatType } from "@/utils/types";

type MeetingDisplaySource = Pick<Meeting, "startTime" | "venue"> & Partial<Pick<Meeting, "code">>;

const PLACEHOLDER_VENUES = new Set(["", "-", "待定", "未定", "地点待定"]);

const attendeeTypeLabels: Record<AttendeeType, string> = {
  VIP: "VIP",
  GUEST: "嘉宾",
  MEDIA: "媒体",
  STAFF: "工作人员",
  NORMAL: "普通参会人"
};

const seatTypeLabels: Record<SeatType, string> = {
  ...attendeeTypeLabels,
  DISABLED: "禁用座"
};

const operationActionLabels: Record<string, string> = {
  AUTH_LOGIN: "登录",
  AUTH_LOGOUT: "退出登录",
  USER_CREATE: "新建用户",
  USER_UPDATE: "更新用户",
  USER_RESET_PASSWORD: "重置用户密码",
  MEETING_PERMISSION_GRANT: "授予会议权限",
  MEETING_PERMISSION_DELETE: "移除会议权限",
  MEETING_CREATE: "创建会议",
  MEETING_UPDATE: "更新会议",
  MEETING_QUERY_SETTINGS_UPDATE: "更新查询设置",
  MEETING_PRINT_SETTINGS_UPDATE: "更新打印设置",
  MEETING_STATUS_CHANGE: "修改会议状态",
  MEETING_PUBLISH: "发布会议",
  MEETING_RESTART: "重新开放会议",
  MEETING_CLOSE_REGISTRATION: "关闭报名",
  MEETING_READY: "会议就绪",
  MEETING_START: "开始会议",
  MEETING_END: "结束会议",
  MEETING_ARCHIVE: "归档会议",
  MEETING_DELETE_ARCHIVE: "删除归档会议",
  NOTIFICATION_CREATE: "创建通知",
  ATTENDEE_CREATE: "新增参会人",
  ATTENDEE_UPDATE: "更新参会人",
  ATTENDEE_REVIEW: "审核报名",
  ATTENDEE_BATCH_REVIEW: "批量审核报名",
  ATTENDEE_TAGS_UPDATE: "更新参会人标签",
  // 兼容早期版本误用的动作码，已有历史日志仍应展示中文名称。
  ATTENDEE_UPDATE_TAGS: "更新参会人标签",
  ATTENDEE_REMARK_UPDATE: "更新参会人备注",
  ATTENDEE_CANCEL: "取消参会人",
  ATTENDEE_IMPORT: "导入参会人",
  ATTENDEE_EXPORT: "导出参会人",
  CHECKIN: "签到",
  CHECKIN_SUPPLEMENT: "补签",
  PUBLIC_SELF_CHECKIN: "自助签到",
  SEATING_CREATE_LAYOUT: "生成座位图",
  SEATING_DELETE_AREA: "删除座位区域",
  SEATING_AUTO_ASSIGN: "自动排座",
  SEATING_MANUAL_ASSIGN: "手动分配座位",
  SEATING_CLEAR: "解除座位分配",
  SEATING_SWAP: "交换座位",
  SEATING_LOCK_SEAT: "锁定座位",
  SEATING_UPDATE_SEAT_TYPE: "修改座位类型",
  SEATING_TOGGLE_SEAT_DISABLED: "启停座位",
  ROOM_CREATE: "新增房间",
  ROOM_UPDATE: "更新房间",
  ROOM_ASSIGN: "分配房间",
  ROOM_CLEAR: "解除房间分配",
  ROOM_ASSIGNMENT_CLEAR: "解除房间分配",
  ROOM_ASSIGNMENT_CHANGE: "房间分配变更",
  ROOM_IMPORT: "导入房间",
  ROOM_ASSIGNMENT_IMPORT: "导入房间分配",
  ROOM_EXPORT: "导出房间",
  DINNER_SETTINGS_UPDATE: "更新晚宴设置",
  DINNER_TABLE_CREATE: "新增晚宴桌",
  DINNER_TABLE_UPDATE: "更新晚宴桌",
  DINNER_TABLE_DELETE: "删除晚宴桌",
  DINNER_ASSIGN: "分配晚宴桌",
  DINNER_ASSIGNMENT_CLEAR: "解除晚宴桌分配",
  DINNER_ASSIGNMENT_CHANGE: "晚宴桌分配变更",
  DINNER_ASSIGNMENT_IMPORT: "导入晚宴桌分配",
  DINNER_EXPORT: "导出晚宴安排",
  TDUCK_BIND_FORM: "绑定 TDuck 表单",
  TDUCK_REBIND_FORM: "更换 TDuck 表单",
  TDUCK_CREATE_FORM: "创建 TDuck 表单",
  TDUCK_UPDATE_FIELD_MAPPING: "更新 TDuck 字段映射",
  TDUCK_SYNC_FIELD_MAPPING: "同步 TDuck 字段映射",
  TDUCK_SYNC_FIELDS: "同步 TDuck 字段",
  TDUCK_MANUAL_SYNC: "手动同步 TDuck 报名",
  TDUCK_PULL_SYNC: "拉取同步 TDuck 报名",
  TDUCK_BATCH_PULL_SYNC: "批量拉取 TDuck 报名",
  TDUCK_REGISTRATION_TOGGLE: "启停报名入口",
  TDUCK_WEBHOOK_SYNC: "TDuck Webhook 同步",
  INVITATION_CREATE: "创建邀请函",
  INVITATION_COPY: "复制邀请函",
  INVITATION_DRAFT_SAVE: "保存邀请函草稿",
  INVITATION_PUBLISH: "发布邀请函",
  INVITATION_UNPUBLISH: "下线邀请函",
  INVITATION_ARCHIVE: "归档邀请函",
  INVITATION_SHARE_LINK_CREATE: "创建邀请函渠道链接",
  INVITATION_SHARE_LINK_UPDATE: "更新邀请函渠道链接",
  INVITATION_ASSET_UPLOAD: "上传邀请函素材",
  IMPORT_TASK_CREATE: "创建导入任务",
  IMPORT_TASK_COMMIT: "提交导入任务",
  IMPORT_FAILURE_DOWNLOAD: "下载导入失败记录",
  EXPORT_CREATE: "创建导出任务",
  EXPORT_DOWNLOAD: "下载导出文件",
  SYSTEM_SETTINGS_UPDATE: "更新系统设置"
};

const operationTargetLabels: Record<string, string> = {
  APP_USER: "用户",
  AUTH_SESSION: "登录会话",
  MEETING_USER_PERMISSION: "会议用户权限",
  MEETING: "会议",
  NOTIFICATION: "通知",
  ATTENDEE: "参会人",
  CHECKIN: "签到记录",
  SEAT: "座位",
  SEAT_LAYOUT: "座位布局",
  SEAT_ASSIGNMENT: "座位分配",
  ROOM: "房间",
  DINNER: "晚宴安排",
  DINNER_TABLE: "晚宴桌",
  TDUCK_FORM: "TDuck 表单",
  TDUCK_MAPPING: "TDuck 字段映射",
  TDUCK_DATA: "TDuck 报名数据",
  TDUCK_SYNC: "TDuck 同步",
  MEETING_INVITATION: "邀请函",
  INVITATION_SHARE_LINK: "邀请函渠道链接",
  INVITATION_ASSET: "邀请函素材",
  IMPORT_TASK: "导入任务",
  EXPORT_TASK: "导出任务",
  SYSTEM_SETTINGS: "系统设置"
};

export const operationLogCategories = [
  "ACCOUNT_PERMISSION",
  "MEETING_CONFIGURATION",
  "ATTENDEE_CHECKIN",
  "VENUE_SEATING",
  "REGISTRATION_TDUCK",
  "INVITATION",
  "IMPORT_EXPORT",
  "SYSTEM"
] as const;

export type OperationLogCategory = typeof operationLogCategories[number];

const operationLogCategoryLabels: Record<OperationLogCategory, string> = {
  ACCOUNT_PERMISSION: "账户与权限",
  MEETING_CONFIGURATION: "会议与配置",
  ATTENDEE_CHECKIN: "参会人与签到",
  VENUE_SEATING: "场地与排座",
  REGISTRATION_TDUCK: "报名与 TDuck",
  INVITATION: "邀请函",
  IMPORT_EXPORT: "导入导出",
  SYSTEM: "系统"
};

export function meetingTimeLabel(startTime?: string | null) {
  const text = startTime?.trim();
  if (!text) return "时间待定";
  return formatDateTimeLabel(text);
}

export function formatDateTimeLabel(value?: string | null, fallback = "-") {
  const text = value == null ? "" : String(value).trim();
  if (!text) return fallback;
  /*
   * Spring 返回的 LocalDateTime 常见形态是 2026-06-24T16:07:19.081，不带时区。
   * 这里按本地会议时间直接格式化，避免 Date 自动时区换算导致会议时间被挪动。
   */
  const localDateTime = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2}))?)?/);
  if (localDateTime) {
    const [, year, month, day, hour, minute, second] = localDateTime;
    const dateLabel = `${year}年${Number(month)}月${Number(day)}日`;
    if (hour && minute) {
      return second ? `${dateLabel} ${hour}:${minute}:${second}` : `${dateLabel} ${hour}:${minute}`;
    }
    return dateLabel;
  }

  const parsed = new Date(text);
  if (!Number.isNaN(parsed.getTime())) {
    return new Intl.DateTimeFormat("zh-CN", {
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false
    }).format(parsed);
  }

  return text;
}

export function formatDateTimeSecondLabel(value?: string | null, fallback = "-") {
  const text = value == null ? "" : String(value).trim();
  if (!text) return fallback;
  /*
   * H5 结果页需要短而明确的秒级时间。后端 LocalDateTime 可能带纳秒：
   * 2026-06-27T23:44:48.795806，这里保留到秒并去掉 T，避免直接暴露接口原始格式。
   */
  const localDateTime = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?)?/);
  if (localDateTime) {
    const [, year, month, day, hour, minute, second] = localDateTime;
    if (hour && minute) {
      return `${year}-${month}-${day} ${hour}:${minute}:${second || "00"}`;
    }
    return `${year}-${month}-${day}`;
  }

  const parsed = new Date(text);
  if (!Number.isNaN(parsed.getTime())) {
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ${pad(parsed.getHours())}:${pad(parsed.getMinutes())}:${pad(parsed.getSeconds())}`;
  }

  return text;
}

export function formatDateLikeValue(value: string) {
  const text = value.trim();
  /*
   * 动态 TDuck 字段没有固定字段名，不能只靠 createdAt/checkinTime 识别。
   * 这里只处理明确长得像日期/日期时间的字符串，避免把普通数字、编号误转成日期。
   */
  if (/^\d{4}-\d{2}-\d{2}(?:[T\s]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?)?$/.test(text)) {
    return formatDateTimeLabel(text, text);
  }
  return value;
}

export function attendeeTypeLabel(value?: string | null) {
  const key = value as AttendeeType;
  return key && attendeeTypeLabels[key] ? attendeeTypeLabels[key] : value || "-";
}

export function seatTypeLabel(value?: string | null) {
  const key = value as SeatType;
  return key && seatTypeLabels[key] ? seatTypeLabels[key] : value || "-";
}

export function operationActionLabel(value?: string | null) {
  const text = value?.trim();
  return text && operationActionLabels[text] ? operationActionLabels[text] : text || "-";
}

export function operationLogCategoryLabel(value?: string | null) {
  const text = value?.trim();
  return text && text in operationLogCategoryLabels
    ? operationLogCategoryLabels[text as OperationLogCategory]
    : text || operationLogCategoryLabels.SYSTEM;
}

export function operationTargetLabel(targetType?: string | null, targetId?: string | null) {
  const typeText = targetType?.trim();
  const idText = targetId?.trim();
  const label = typeText && operationTargetLabels[typeText] ? operationTargetLabels[typeText] : typeText || "";
  if (label && idText) return `${label}：${idText}`;
  return label || idText || "-";
}

export function operationMessageLabel(value?: string | null) {
  const text = value?.trim();
  if (!text) return "-";
  if (text === "OK") return "成功";
  return text;
}

export function meetingVenueLabel(venue?: string | null) {
  const text = venue?.trim() || "";
  return PLACEHOLDER_VENUES.has(text) ? "地点待定" : text;
}

export function meetingSummaryLabel(meeting: MeetingDisplaySource, options: { includeCode?: boolean } = {}) {
  const parts = [meetingTimeLabel(meeting.startTime), meetingVenueLabel(meeting.venue)];
  if (options.includeCode && meeting.code) {
    parts.push(meeting.code);
  }
  return parts.join(" · ");
}
