export type MeetingStatus = '草稿' | '报名中' | '报名截止' | '待开始' | '进行中' | '已结束' | '已归档'

export type AuditStatus = '待审核' | '已通过' | '已拒绝' | '已取消'

export type AttendeeType = 'VIP' | '嘉宾' | '媒体' | '工作人员' | '普通参会人'

export type SeatType = 'VIP' | '嘉宾' | '媒体' | '工作人员' | '普通座' | '禁用座'

export type SeatStatus = '空座' | '已分配' | '已签到' | '禁用'

export type ExportStatus = '可导出' | '生成中' | '已完成'

export interface Meeting {
  id: string
  code: string
  name: string
  organizer: string
  coOrganizer: string
  startTime: string
  endTime: string
  registrationStartTime: string
  registrationEndTime: string
  location: string
  venueName: string
  capacity: number
  description: string
  agenda: string
  coverUrl: string
  contactName: string
  contactPhone: string
  status: MeetingStatus
  createdBy: string
}

export interface RegistrationFormBinding {
  id: string
  meetingId: string
  formKey: string
  formName: string
  registrationUrl: string
  published: boolean
  mappingReady: boolean
  webhookEnabled: boolean
}

export interface TduckField {
  id: string
  name: string
  type: string
}

export interface FieldMapping {
  id: string
  tduckFieldId: string
  tduckFieldName: string
  required: boolean
}

export interface Attendee {
  id: string
  meetingId: string
  tduckDataId: string
  name: string
  phone: string
  company: string
  title: string
  idNumber: string
  email: string
  attendeeType: AttendeeType
  tags: string[]
  roomNumber: string
  roomRemark: string
  auditStatus: AuditStatus
  auditReason: string
  auditor: string
  auditedAt: string
  registeredAt: string
  seatId?: string
  checkinTime?: string
  checkinEntrance?: string
  checkinOperator?: string
}

export interface Seat {
  id: string
  meetingId: string
  areaName: string
  rowNo: number
  seatNo: number
  seatLabel: string
  seatType: SeatType
  status: SeatStatus
  locked: boolean
  attendeeId?: string
}

export interface Room {
  id: string
  meetingId: string
  roomNo: string
  hotelName: string
  hotelAddress: string
  building: string
  floorNo: string
  roomType: string
  checkinTips: string
  remark: string
  attendeeId?: string
}

export interface CheckinRecord {
  id: string
  meetingId: string
  attendeeId: string
  checkinTime: string
  checkinMethod: string
  entrance: string
  operatorName: string
  deviceId: string
  remark: string
  duplicate?: boolean
}

export interface OperationLog {
  id: string
  operator: string
  time: string
  object: string
  type: string
  before: string
  after: string
  ip: string
  remark: string
}

export interface SyncLog {
  id: string
  meetingId: string
  formKey: string
  dataId: string
  eventType: string
  status: '成功' | '失败' | '待处理'
  errorMessage: string
  payload: string
  createdAt: string
}

export interface ExportTask {
  id: string
  name: string
  scope: string
  sensitive: boolean
  status: ExportStatus
  updatedAt: string
}
