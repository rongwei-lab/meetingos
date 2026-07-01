import type { Attendee, DinnerAssignment, Meeting, Room, Seat, SyncLog } from "./types";

export const mockMeetings: Meeting[] = [
  {
    id: "m-001",
    code: "NDEF2026",
    name: "2026年全国数字经济发展论坛",
    organizer: "数字经济产业联盟",
    venue: "北京国家会议中心",
    dinnerVenue: "北京会议中心三层宴会厅",
    dinnerTime: "2026-07-15 18:30",
    startTime: "2026-07-15 09:00",
    endTime: "2026-07-15 18:00",
    status: "REGISTRATION_OPEN",
    capacity: 500,
    registeredCount: 12,
    approvedCount: 9,
    seatedCount: 5,
    roomedCount: 4,
    dinneredCount: 6,
    checkedInCount: 2,
    tduckFormKey: "ndef2026",
    tduckFormName: "数字经济论坛报名表",
    registrationUrl: "https://tduck.example.com/s/ndef2026",
    seatQueryEnabled: true,
    roomQueryEnabled: true,
    dinnerQueryEnabled: true,
    checkinEnabled: true,
    selfCheckinEnabled: true,
    publicQueryFields: ["PHONE", "NAME"],
    publicQueryMatchMode: "FUZZY"
  },
  {
    id: "m-002",
    code: "AI2026",
    name: "第八届人工智能产业峰会",
    organizer: "AI 产业研究院",
    venue: "上海世博展览馆",
    startTime: "2026-07-20 08:30",
    endTime: "2026-07-20 17:30",
    status: "REGISTRATION_OPEN",
    capacity: 800,
    registeredCount: 567,
    approvedCount: 520,
    seatedCount: 320,
    roomedCount: 160,
    dinneredCount: 0,
    checkedInCount: 0,
    tduckFormKey: "ai-summit-2026",
    tduckFormName: "人工智能产业峰会报名表",
    registrationUrl: "https://tduck.example.com/s/ai-summit-2026",
    seatQueryEnabled: false,
    roomQueryEnabled: false,
    dinnerQueryEnabled: false,
    checkinEnabled: true,
    selfCheckinEnabled: true,
    publicQueryFields: ["PHONE", "NAME"],
    publicQueryMatchMode: "FUZZY"
  },
  {
    id: "m-003",
    code: "Q2ALL",
    name: "2026年Q2季度全员大会",
    organizer: "集团总部",
    venue: "集团总部16楼会议室",
    startTime: "2026-06-28 14:00",
    endTime: "2026-06-28 17:00",
    status: "IN_PROGRESS",
    capacity: 180,
    registeredCount: 138,
    approvedCount: 138,
    seatedCount: 138,
    roomedCount: 0,
    dinneredCount: 0,
    checkedInCount: 127,
    tduckFormKey: "q2-allhands",
    tduckFormName: "Q2 全员大会签到报名表",
    registrationUrl: "https://tduck.example.com/s/q2-allhands",
    seatQueryEnabled: false,
    roomQueryEnabled: false,
    dinnerQueryEnabled: false,
    checkinEnabled: true,
    selfCheckinEnabled: true,
    publicQueryFields: ["PHONE", "NAME"],
    publicQueryMatchMode: "FUZZY"
  }
];

const attendeeSeed = [
  ["a-001", "陈嘉宁", "13800000001", "未来科技集团", "副总裁", "VIP", "APPROVED", "VIP,特邀"],
  ["a-002", "王子墨", "13800000002", "北方数据院", "研究员", "GUEST", "APPROVED", "嘉宾"],
  ["a-003", "李若彤", "13800000003", "财经观察", "记者", "MEDIA", "PENDING", "媒体"],
  ["a-004", "周明远", "13800000004", "云川科技", "产品总监", "NORMAL", "APPROVED", "普通参会人"],
  ["a-005", "赵清扬", "13800000005", "会务组", "工作人员", "STAFF", "APPROVED", "工作人员"],
  ["a-006", "许知夏", "13800000006", "南方智造", "市场负责人", "NORMAL", "PENDING", "普通参会人"],
  ["a-007", "沈一舟", "13800000007", "星河资本", "合伙人", "VIP", "APPROVED", "VIP"],
  ["a-008", "林安琪", "13800000008", "行业协会", "秘书长", "GUEST", "APPROVED", "嘉宾"],
  ["a-009", "罗景行", "13800000009", "开源社区", "负责人", "NORMAL", "REJECTED", "普通参会人"],
  ["a-010", "何雨桐", "13800000010", "城市日报", "编辑", "MEDIA", "APPROVED", "媒体"],
  ["a-011", "唐亦辰", "13800000011", "东部云网", "架构师", "NORMAL", "PENDING", "普通参会人"],
  ["a-012", "宋知微", "13800000012", "未来科技集团", "法务", "NORMAL", "APPROVED", "普通参会人"]
] as const;

export const mockAttendees: Attendee[] = attendeeSeed.map((item, index) => ({
  id: item[0],
  meetingId: "m-001",
  tduckDataId: `td-${index + 1}`,
  name: item[1],
  phone: item[2],
  company: item[3],
  title: item[4],
  email: `${item[0]}@example.com`,
  attendeeType: item[5] as Attendee["attendeeType"],
  auditStatus: item[6] as Attendee["auditStatus"],
  tags: item[7].split(","),
  auditedAt: item[6] === "APPROVED" ? "2026-06-23 10:30" : undefined,
  registeredAt: `2026-06-${String(10 + index).padStart(2, "0")} 09:20`,
  seatLabel: index < 5 ? `A区 ${index + 1}排${index + 1}座` : undefined,
  roomNo: index < 4 ? `18${String(index + 1).padStart(2, "0")}` : undefined,
  roomId: index < 4 ? `r-00${index + 1}` : undefined,
  checkinStatus: index < 2 ? "CHECKED_IN" : "NOT_CHECKED_IN",
  checkinTime: index < 2 ? "2026-07-15 08:45" : undefined
}));

export const mockSeats: Seat[] = Array.from({ length: 100 }).map((_, index) => {
  const rowNo = Math.floor(index / 10) + 1;
  const seatNo = (index % 10) + 1;
  const vip = rowNo <= 2;
  const guest = rowNo <= 4 && !vip;
  const disabled = rowNo === 10 && [4, 5, 6].includes(seatNo);
  return {
    id: `s-${String(index + 1).padStart(3, "0")}`,
    meetingId: "m-001",
    areaName: vip ? "VIP区" : guest ? "嘉宾区" : "A区",
    rowNo,
    seatNo,
    seatType: disabled ? "DISABLED" : vip ? "VIP" : guest ? "GUEST" : "NORMAL",
    status: disabled ? "DISABLED" : index < 5 ? "ASSIGNED" : "EMPTY",
    locked: index === 0,
    attendeeId: index < 5 ? `a-00${index + 1}` : undefined
  };
});

export const mockRooms: Room[] = [
  { id: "r-001", meetingId: "m-001", roomNo: "1801", hotelName: "北京会议中心酒店", building: "A座", floorNo: "18", roomType: "大床房", capacity: 1, checkinTips: "凭身份证到前台办理入住", status: "ASSIGNED" },
  { id: "r-002", meetingId: "m-001", roomNo: "1802", hotelName: "北京会议中心酒店", building: "A座", floorNo: "18", roomType: "双床房", capacity: 2, checkinTips: "14:00 后可入住", status: "ASSIGNED" },
  { id: "r-003", meetingId: "m-001", roomNo: "1803", hotelName: "北京会议中心酒店", building: "A座", floorNo: "18", roomType: "大床房", capacity: 1, checkinTips: "如需延住请联系会务组", status: "ASSIGNED" },
  { id: "r-004", meetingId: "m-001", roomNo: "1804", hotelName: "北京会议中心酒店", building: "A座", floorNo: "18", roomType: "套房", capacity: 2, checkinTips: "VIP 嘉宾专用通道", status: "ASSIGNED" },
  { id: "r-005", meetingId: "m-001", roomNo: "1901", hotelName: "北京会议中心酒店", building: "B座", floorNo: "19", roomType: "双床房", capacity: 2, checkinTips: "早餐在二楼", status: "AVAILABLE" }
];

export const mockDinnerAssignments: DinnerAssignment[] = mockAttendees.slice(0, 6).map((attendee, index) => ({
  id: `d-${String(index + 1).padStart(3, "0")}`,
  meetingId: attendee.meetingId,
  attendeeId: attendee.id,
  attendeeName: attendee.name,
  attendeePhone: attendee.phone,
  organization: attendee.company,
  title: attendee.title,
  attendeeType: attendee.attendeeType,
  tableNo: `${Math.floor(index / 2) + 1}号桌`,
  remark: index === 0 ? "主桌" : "",
  assignedBy: "demo"
}));

export const mockSyncLogs: SyncLog[] = [
  { id: "log-001", meetingId: "m-001", formKey: "ndef2026", dataId: "td-001", eventType: "form_data_add", status: "SUCCESS", createdAt: "2026-06-23 10:01" },
  { id: "log-002", meetingId: "m-001", formKey: "ndef2026", dataId: "td-013", eventType: "form_data_add", status: "FAILED", errorMessage: "手机号格式错误", createdAt: "2026-06-23 10:03" },
  { id: "log-003", meetingId: "m-001", formKey: "ndef2026", dataId: "td-014", eventType: "form_data_update", status: "PENDING", createdAt: "2026-06-23 10:05" }
];
