export type MeetingStatus = "DRAFT" | "REGISTRATION_OPEN" | "REGISTRATION_CLOSED" | "READY" | "IN_PROGRESS" | "ENDED" | "ARCHIVED";
export type AuditStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
export type AttendeeType = "VIP" | "GUEST" | "MEDIA" | "STAFF" | "NORMAL";
export type SeatStatus = "EMPTY" | "ASSIGNED" | "CHECKED_IN" | "DISABLED";
export type SeatType = AttendeeType | "DISABLED";
export type RoomStatus = "AVAILABLE" | "ASSIGNED" | "DISABLED";
export type PublicQueryField = "NAME" | "PHONE" | "PHONE_LAST4" | "ATTENDEE_CODE" | "ORGANIZATION" | "TITLE" | "EMAIL";
export type PublicQueryMatchMode = "FUZZY" | "EXACT";
export type PublicQueryType = "SEAT" | "ROOM" | "CHECKIN";
export type PublicQueryStatus = "SUCCESS" | "FAILED";
export type PublicQueryResultField =
  | "ATTENDEE_NAME"
  | "ATTENDEE_PHONE"
  | "ORGANIZATION"
  | "TITLE"
  | "EMAIL"
  | "MEETING_NAME"
  | "SEAT_AREA"
  | "SEAT_ROW"
  | "SEAT_NO"
  | "SEAT_CODE"
  | "HOTEL_NAME"
  | "ROOM_TYPE"
  | "ROOM_NO"
  | "ROOM_REMARK"
  | "CHECKIN_TIPS";

export interface Meeting {
  id: string;
  code: string;
  name: string;
  organizer: string;
  coOrganizer?: string;
  venue: string;
  startTime: string;
  endTime: string;
  registrationStartTime?: string;
  registrationEndTime?: string;
  status: MeetingStatus;
  capacity: number;
  description?: string;
  agenda?: string;
  coverImageUrl?: string;
  contactName?: string;
  contactPhone?: string;
  registeredCount: number;
  approvedCount: number;
  seatedCount: number;
  roomedCount: number;
  checkedInCount: number;
  tduckFormKey: string;
  tduckFormName: string;
  registrationUrl: string;
  seatQueryEnabled: boolean;
  roomQueryEnabled: boolean;
  checkinEnabled: boolean;
  publicQueryFields: PublicQueryField[];
  publicQueryMatchMode: PublicQueryMatchMode;
}

export interface Attendee {
  id: string;
  meetingId: string;
  tduckDataId?: string;
  attendeeCode?: string;
  name: string;
  phone: string;
  company: string;
  title: string;
  email?: string;
  attendeeType: AttendeeType;
  tags: string[];
  auditStatus: AuditStatus;
  auditReason?: string;
  auditedAt?: string;
  registeredAt: string;
  seatLabel?: string;
  roomNo?: string;
  roomRemark?: string;
  roomId?: string;
  seatQueried?: boolean;
  roomQueried?: boolean;
  checkinStatus: "NOT_CHECKED_IN" | "CHECKED_IN";
  checkinTime?: string;
  formValues?: Record<string, unknown>;
}

export interface Seat {
  id: string;
  meetingId: string;
  areaName: string;
  areaSortOrder?: number;
  rowNo: number;
  seatNo: number;
  seatType: SeatType;
  status: SeatStatus;
  locked: boolean;
  attendeeId?: string;
  attendeeName?: string;
  attendeeTags?: string;
}

export interface Room {
  id: string;
  meetingId: string;
  roomNo: string;
  hotelName: string;
  building: string;
  floorNo: string;
  roomType: string;
  capacity: number;
  checkinTips: string;
  remark?: string;
  status: RoomStatus;
}

export interface QueryFieldOption {
  field: PublicQueryField;
  label: string;
}

export interface QueryResultFieldOption {
  field: PublicQueryResultField;
  label: string;
}

export interface MeetingQuerySettings {
  meetingId: string;
  meetingCode: string;
  meetingName: string;
  seatQueryEnabled: boolean;
  roomQueryEnabled: boolean;
  checkinEnabled: boolean;
  queryPaused: boolean;
  fields: PublicQueryField[];
  matchMode: PublicQueryMatchMode;
  pageTitle: string;
  pageDescription?: string;
  announcement?: string;
  publisher?: string;
  startTime?: string;
  endTime?: string;
  buttonText: string;
  emptyMessage: string;
  seatNotice: string;
  roomNotice: string;
  themeColor: string;
  logoUrl?: string;
  backgroundUrl?: string;
  resultLayout: "CARD" | "LIST" | "COMPACT";
  visibleResultFields: PublicQueryResultField[];
  maskedResultFields: PublicQueryResultField[];
  highlightResultFields: PublicQueryResultField[];
  seatVisibleResultFields: PublicQueryResultField[];
  seatMaskedResultFields: PublicQueryResultField[];
  seatHighlightResultFields: PublicQueryResultField[];
  roomVisibleResultFields: PublicQueryResultField[];
  roomMaskedResultFields: PublicQueryResultField[];
  roomHighlightResultFields: PublicQueryResultField[];
  captchaEnabled: boolean;
  fieldOptions: QueryFieldOption[];
  resultFieldOptions: QueryResultFieldOption[];
}

export interface SeatQueryResult {
  attendeeId: number;
  attendeeName: string;
  attendeePhone?: string;
  organization?: string;
  title?: string;
  email?: string;
  meetingName: string;
  areaName: string;
  rowNo: number;
  seatNo: number;
  seatCode: string;
  seatMap?: PublicSeatMap | null;
}

export interface PublicSeatMap {
  areaName: string;
  rowCount: number;
  seatsPerRow: number;
  currentRowNo: number;
  currentSeatNo: number;
}

export interface RoomQueryResult {
  attendeeId: number;
  attendeeName: string;
  attendeePhone?: string;
  organization?: string;
  title?: string;
  email?: string;
  meetingName: string;
  roomNo?: string;
  roomRemark?: string;
  hotelName?: string;
  roomType?: string;
  checkinTips?: string;
}

export type PublicCheckinResultStatus = "SUCCESS" | "DUPLICATE" | "NOT_APPROVED" | "NOT_FOUND";

export interface PublicCheckinResult {
  status: PublicCheckinResultStatus;
  attendeeId?: number;
  attendeeName?: string;
  phone?: string;
  checkedInAt?: string;
  entrance?: string;
  operator?: string;
  message?: string;
}

export interface AdminQueryCandidate {
  attendeeId: number;
  attendeeName: string;
  phone: string;
  organization?: string;
  title?: string;
  reviewStatus: AuditStatus;
  seatLabel?: string;
  roomNo?: string;
  roomRemark?: string;
}

export interface PublicQueryHistory {
  id: string;
  queryType: PublicQueryType;
  queryStatus: PublicQueryStatus;
  queryValues: Record<string, unknown>;
  errorMessage?: string;
  attendeeId?: string;
  attendeeName?: string;
  attendeePhone?: string;
  resultSummary?: string;
  clientIp?: string;
  userAgent?: string;
  createdAt?: string;
}

export interface PublicQuerySummary {
  groupKey: string;
  groupType: "ATTENDEE" | "UNRECOGNIZED";
  attendeeId?: string;
  attendeeName?: string;
  attendeePhone?: string;
  failureReason?: string;
  totalCount: number;
  successCount: number;
  failedCount: number;
  latestQueryAt?: string;
  latestResult?: string;
}

export interface SyncLog {
  id: string;
  meetingId: string;
  formKey: string;
  dataId: string;
  eventType: string;
  status: "SUCCESS" | "FAILED" | "PENDING";
  errorMessage?: string;
  createdAt: string;
}

export interface CheckinRecord {
  id: string;
  meetingId: string;
  attendeeId: string;
  attendeeName: string;
  method: "SCAN_CODE" | "SEARCH" | "MANUAL" | "SUPPLEMENT";
  entrance: string;
  operator: string;
  checkinTime: string;
}
