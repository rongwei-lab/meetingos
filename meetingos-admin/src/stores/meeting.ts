import { defineStore } from "pinia";
import { canUseDemoFallback, extractErrorMessage, http, type PagePayload, isAuthFailure, isHttpStatus, isRequestCanceled, unwrapApi } from "@/api/http";
import { mockAttendees, mockDinnerAssignments, mockMeetings, mockRooms, mockSeats, mockSyncLogs } from "@/utils/mockData";
import { updateMeetingCounters } from "@/utils/meetingCounters";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";
import { DEFAULT_NEW_MEETING_STATUS } from "@/utils/meetingLifecycle";
import { RequestRuntime, type LatestRequestToken } from "@/utils/requestControl";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import { mapWithConcurrency } from "@/utils/async";
import { CURRENT_MEETING_KEY } from "@/utils/currentMeeting";
import { pollFileTask } from "@/utils/fileTasks";
import type {
  AdminQueryCandidate,
  Attendee,
  CheckinRecord,
  CombinedQueryType,
  DinnerAssignment,
  DinnerTable,
  DinnerQueryResult,
  Meeting,
  MeetingQuerySettings,
  PublicPageContent,
  PublicPageKey,
  PublicCheckinPreview,
  PublicCheckinSource,
  PublicCheckinResult,
  PublicQueryField,
  PublicQueryHistory,
  PublicQueryMatchMode,
  PublicQueryResponse,
  PublicQueryResultField,
  PublicQueryStatus,
  PublicQuerySummary,
  PublicQueryType,
  Room,
  RoomQueryResult,
  Seat,
  SeatQueryResult,
  SyncLog
} from "@/utils/types";

interface MeetingSummaryDto {
  id: number;
  code: string;
  publicQueryToken?: string;
  name: string;
  startTime?: string;
  endTime?: string;
  location?: string;
  dinnerVenue?: string;
  dinnerTime?: string;
  registrationCount?: number;
  approvedCount?: number;
  seatedCount?: number;
  roomAssignedCount?: number;
  dinnerAssignedCount?: number;
  checkedInCount?: number;
  status: Meeting["status"];
  seatQueryEnabled?: boolean;
  roomQueryEnabled?: boolean;
  dinnerQueryEnabled?: boolean;
  checkinEnabled?: boolean;
  selfCheckinEnabled?: boolean;
  publicQueryFields?: string;
  publicQueryRequiredFields?: string;
  publicQueryMatchMode?: PublicQueryMatchMode;
}

interface MeetingDetailDto {
  meeting: {
    id: number;
    code: string;
    publicQueryToken?: string;
    name: string;
    organizer?: string;
    coOrganizer?: string;
    startTime?: string;
    endTime?: string;
    registrationStartTime?: string;
    registrationEndTime?: string;
    location?: string;
    venueName?: string;
    dinnerVenue?: string;
    dinnerTime?: string;
    capacity?: number;
    description?: string;
    agenda?: string;
    coverImageUrl?: string;
    contactName?: string;
    contactPhone?: string;
    status: Meeting["status"];
    seatQueryEnabled?: boolean;
    roomQueryEnabled?: boolean;
    dinnerQueryEnabled?: boolean;
    checkinEnabled?: boolean;
    selfCheckinEnabled?: boolean;
    publicQueryFields?: string;
    publicQueryRequiredFields?: string;
    publicQueryMatchMode?: PublicQueryMatchMode;
  };
  registrationCount?: number;
  approvedCount?: number;
  seatedCount?: number;
  roomAssignedCount?: number;
  dinnerAssignedCount?: number;
  checkedInCount?: number;
}

interface AttendeeDto {
  id: number;
  name: string;
  phone: string;
  organization?: string;
  title?: string;
  identityNo?: string;
  email?: string;
  attendeeType?: Attendee["attendeeType"];
  accommodationRequired?: boolean;
  tags?: string;
  reviewStatus?: Attendee["auditStatus"];
  reviewReason?: string;
  reviewedAt?: string;
  registeredAt?: string;
  roomNo?: string;
  roomRemark?: string;
  seatLabel?: string;
  remark?: string | null;
  seatAssigned?: boolean;
  roomAssigned?: boolean;
  dinnerAssigned?: boolean;
  seatQueried?: boolean;
  roomQueried?: boolean;
  dinnerQueried?: boolean;
  checkinStatus?: Attendee["checkinStatus"];
  checkedInAt?: string;
  attendeeCode?: string;
  tduckDataId?: string;
  formValues?: Record<string, unknown>;
}

interface DinnerAssignmentDto {
  id: number;
  meetingId: number;
  attendeeId: number;
  attendeeName: string;
  attendeePhone?: string;
  organization?: string;
  title?: string;
  attendeeType?: string;
  tableNo: string;
  venue?: string;
  dinnerTime?: string;
  remark?: string;
  assignedBy?: string;
}

interface DinnerSettingsDto {
  meetingId: number;
  venue?: string;
  dinnerTime?: string;
}

interface DinnerTableDto {
  id: number;
  meetingId: number;
  tableNo: string;
  capacity: number;
  occupiedCount: number;
  remainingCount: number;
  overCapacityCount: number;
  remark?: string;
  createdBy?: string;
}

interface SeatViewDto {
  seatId: number;
  areaName: string;
  areaSortOrder?: number;
  rowNo: number;
  seatNo: number;
  seatCode: string;
  seatType: Seat["seatType"];
  status: Seat["status"];
  locked: boolean;
  attendeeId?: number;
  attendeeName?: string;
  attendeeTags?: string;
}

export interface SeatLayoutPayload {
  originalAreaName?: string;
  areaName: string;
  rowCount: number;
  seatsPerRow: number;
  sortOrder?: number;
  venueName?: string;
  defaultSeatType?: Exclude<Seat["seatType"], "DISABLED">;
}

interface RoomDto {
  id: number;
  roomNo: string;
  roomType?: string;
  hotelName?: string;
  remark?: string;
  capacity?: number;
  status?: Room["status"];
}

interface MeetingQuerySettingsDto {
  meetingId: number;
  meetingCode: string;
  publicQueryToken?: string;
  meetingName: string;
  seatQueryEnabled: boolean;
  roomQueryEnabled: boolean;
  dinnerQueryEnabled?: boolean;
  checkinEnabled?: boolean;
  selfCheckinEnabled?: boolean;
  queryPaused?: boolean;
  fields: PublicQueryField[];
  requiredFields?: PublicQueryField[];
  matchMode: PublicQueryMatchMode;
  allowMultipleResults?: boolean;
  combinedQueryTypes?: CombinedQueryType[];
  pageTitle?: string;
  pageDescription?: string;
  announcement?: string;
  publisher?: string;
  startTime?: string;
  endTime?: string;
  buttonText?: string;
  emptyMessage?: string;
  seatNotice?: string;
  roomNotice?: string;
  dinnerNotice?: string;
  pageContents?: Partial<Record<PublicPageKey, PublicPageContent>>;
  themeColor?: string;
  logoUrl?: string;
  backgroundUrl?: string;
  resultLayout?: MeetingQuerySettings["resultLayout"];
  visibleResultFields?: PublicQueryResultField[];
  maskedResultFields?: PublicQueryResultField[];
  highlightResultFields?: PublicQueryResultField[];
  seatVisibleResultFields?: PublicQueryResultField[];
  seatMaskedResultFields?: PublicQueryResultField[];
  seatHighlightResultFields?: PublicQueryResultField[];
  roomVisibleResultFields?: PublicQueryResultField[];
  roomMaskedResultFields?: PublicQueryResultField[];
  roomHighlightResultFields?: PublicQueryResultField[];
  dinnerVisibleResultFields?: PublicQueryResultField[];
  dinnerMaskedResultFields?: PublicQueryResultField[];
  dinnerHighlightResultFields?: PublicQueryResultField[];
  captchaEnabled?: boolean;
  fieldOptions: Array<{ field: PublicQueryField; label: string }>;
  resultFieldOptions?: Array<{ field: PublicQueryResultField; label: string }>;
}

interface AssetUploadDto {
  url: string;
  filename: string;
  size: number;
  contentType: string;
}

export interface MeetingPrintSettingsDto {
  meetingId: string;
  nameplateTemplateUrl?: string | null;
  nameplateFields?: MeetingPrintFieldSettingsDto[];
}

export interface MeetingPrintFieldSettingsDto {
  fieldKey: string;
  fontSizePt?: number | null;
  color?: string | null;
  letterSpacingEm?: number | null;
  bold?: boolean | null;
}

export interface PublicCaptchaDto {
  enabled: boolean;
  captchaId?: string | null;
  question?: string | null;
  expiresInSeconds?: number;
}

export interface PublicCombinedQueryDto {
  seat?: PublicQueryResponse<SeatQueryResult> | null;
  seatError?: string | null;
  room?: PublicQueryResponse<RoomQueryResult> | null;
  roomError?: string | null;
  dinner?: PublicQueryResponse<DinnerQueryResult> | null;
  dinnerError?: string | null;
}

interface PublicQueryHistoryDto {
  id: number;
  queryType: PublicQueryType;
  queryStatus: PublicQueryStatus;
  queryValues?: Record<string, unknown>;
  errorMessage?: string;
  attendeeId?: number;
  attendeeName?: string;
  attendeePhone?: string;
  resultSummary?: string;
  clientIp?: string;
  userAgent?: string;
  createdAt?: string;
}

interface PublicQuerySummaryDto {
  groupKey: string;
  groupType: "ATTENDEE" | "UNRECOGNIZED";
  attendeeId?: number;
  attendeeName?: string;
  attendeePhone?: string;
  failureReason?: string;
  totalCount: number;
  successCount: number;
  failedCount: number;
  latestQueryAt?: string;
  latestResult?: string;
}

export interface PublicQueryHistoryFilters {
  page?: number;
  size?: number;
  type?: PublicQueryType | null;
  status?: PublicQueryStatus | null;
  keyword?: string;
}

export interface CreateRoomPayload {
  roomNo: string;
  roomType?: string;
  hotelName?: string;
  remark?: string;
  capacity?: number;
  status?: Room["status"];
}

export interface DinnerAssignPayload {
  attendeeId: string;
  tableNo: string;
  remark?: string;
}

export interface DinnerTablePayload {
  id?: string;
  tableNo: string;
  capacity: number;
  remark?: string;
}

export interface DinnerSettingsPayload {
  venue?: string;
  dinnerTime?: string;
}

interface SyncLogDto {
  id: number;
  formKey?: string;
  eventId?: string;
  eventType?: string;
  status: SyncLog["status"];
  message?: string;
  attendeeId?: number;
  tduckDataId?: string;
  createdAt?: string;
}

export interface TduckSyncLogDetailDto {
  id: string;
  meetingId: string;
  formKey: string;
  eventType: string;
  status: SyncLog["status"];
  message?: string;
  dataId: string;
  createdAt: string;
  formValues: Record<string, unknown>;
}

export type TduckSubmissionStatus = "ACTIVE" | "SUPERSEDED" | "IGNORED" | "PENDING_CONFLICT" | "CANCELLED";
export type TduckSubmissionResolution = "KEEP_EXISTING" | "ADOPT_INCOMING";

export interface TduckSubmissionParticipantDto {
  name?: string;
  phone?: string;
  organization?: string;
  title?: string;
  identityNo?: string;
  email?: string;
  attendeeType?: string;
  accommodationRequired?: boolean;
  remark?: string;
  formValues?: Record<string, unknown>;
}

export interface TduckSubmissionConflictDto {
  id: string;
  meetingId: string;
  attendeeId: string;
  currentTduckDataId?: string;
  incomingTduckDataId?: string;
  status: TduckSubmissionStatus;
  submittedAt?: string;
  createdAt?: string;
  existing: TduckSubmissionParticipantDto;
  incoming: TduckSubmissionParticipantDto;
}

interface TduckSubmissionConflictWireDto {
  id: number | string;
  meetingId: number | string;
  attendeeId: number | string;
  currentTduckDataId?: string;
  incomingTduckDataId?: string;
  status: TduckSubmissionStatus;
  submittedAt?: string;
  createdAt?: string;
  existing?: TduckSubmissionParticipantDto;
  incoming?: TduckSubmissionParticipantDto;
}

interface CheckinRecordDto {
  id: number;
  attendeeId?: number;
  attendeeName?: string;
  checkinType?: string;
  resultStatus?: string;
  entrance?: string;
  operator?: string;
  checkedInAt?: string;
  createdAt?: string;
  requestSnapshot?: Record<string, unknown>;
}

export interface TduckBindingDto {
  formKey: string;
  formName: string;
  registrationUrl: string;
  registrationEnabled?: boolean;
  tduckPublished?: boolean;
  bindingStatus?: "BOUND" | "UNBOUND" | string;
  webhookStatus?: "CONFIGURED" | "NOT_CONFIGURED" | string;
  validationStatus?: "VALID" | "INVALID" | "UNKNOWN";
  validationMessage?: string;
  rebindingLocked?: boolean;
  rebindingLockedReason?: string;
}

export interface TduckFormOptionDto {
  formKey: string;
  formName: string;
  status?: string;
  published: boolean;
  itemCount: number;
  registrationUrl: string;
}

export interface TduckFieldSyncItemDto {
  tduckFieldKey: string;
  tduckFieldLabel: string;
  tduckFieldType?: string;
  tduckRequired: boolean;
  attendeeField?: string | null;
  requiredMapping: boolean;
  status: "NOT_CONFIGURED" | "VALID" | "INVALID";
}

export interface TduckFieldMappingDto {
  id?: number;
  attendeeField?: string | null;
  tduckFieldKey: string;
  tduckFieldLabel?: string;
  tduckFieldType?: string;
  tduckRequired?: boolean;
  requiredMapping: boolean;
  status?: "NOT_CONFIGURED" | "VALID" | "INVALID";
}

export interface TduckFieldSyncResponseDto {
  formKey: string;
  formName: string;
  fieldCount: number;
  mappedCount: number;
  mappingStatus: "NOT_CONFIGURED" | "VALID" | "INVALID";
  fields: TduckFieldSyncItemDto[];
}

export interface TduckSyncResultDto {
  status: "SUCCESS" | "FAILED" | "DUPLICATE_IGNORED";
  syncLogId?: number;
  attendeeId?: number;
  message?: string;
}

export interface TduckBatchSyncFailureDto {
  dataId: string;
  message: string;
}

export interface TduckBatchSyncResultDto {
  taskId?: string | number;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  progress?: number;
  total?: number;
  scanned: number;
  success: number;
  duplicateIgnored: number;
  failed: number;
  failures: TduckBatchSyncFailureDto[];
  message: string;
  startedAt?: string;
  completedAt?: string;
  createdAt?: string;
}

export interface TduckBrowserSessionDto {
  token: string;
  userInfo: Record<string, unknown>;
}

export interface StatisticsTrendPoint {
  date: string;
  count: number;
}

export interface StatisticsOverviewDto {
  registrationCount: number;
  pendingCount?: number;
  approvedCount: number;
  rejectedCount?: number;
  todayRegistrationCount?: number;
  totalSeatCount?: number;
  availableSeatCount?: number;
  seatedCount: number;
  emptySeatCount?: number;
  disabledSeatCount?: number;
  seatUsageRate?: number;
  roomCount?: number;
  accommodationRequiredCount?: number;
  roomAssignedRequiredCount?: number;
  roomUnassignedRequiredCount?: number;
  roomAssignedCount: number;
  dinnerAssignedCount?: number;
  roomUnassignedCount?: number;
  checkedInCount: number;
  notCheckedInCount?: number;
  checkinRate: number;
  rangeRegistrationCount?: number;
  rangeCheckinCount?: number;
  registrationTrend?: Record<string, number>;
  checkinTrend?: Record<string, number>;
  reviewStatusDistribution?: Record<string, number>;
  attendeeTypeDistribution?: Record<string, number>;
  organizationDistribution?: Record<string, number>;
  hotelDistribution?: Record<string, number>;
  roomTypeDistribution?: Record<string, number>;
  checkinEntranceDistribution?: Record<string, number>;
  checkinTypeDistribution?: Record<string, number>;
}

export type ImportTaskType = "attendees" | "rooms" | "room-assignments" | "dinner";
export type ImportTaskStatus =
  | "PENDING"
  | "PREFLIGHTING"
  | "PREVIEW_READY"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "EXPIRED"
  | string;

export interface ImportTaskDto {
  id: string;
  meetingId: number | string;
  type: ImportTaskType;
  status: ImportTaskStatus;
  originalFilename: string;
  totalRows: number;
  processedRows: number;
  importedRows: number;
  failedRows: number;
  progress: number;
  message?: string;
  failureFileAvailable: boolean;
  startedAt?: string;
  createdAt?: string;
  completedAt?: string;
  expiresAt?: string;
}

interface ImportTaskWireDto extends Partial<Omit<ImportTaskDto, "id" | "meetingId" | "type">> {
  id?: number | string;
  taskId?: number | string;
  meetingId?: number | string;
  type?: ImportTaskType;
}

export interface ExportTaskDto {
  taskId: string;
  meetingId: number | string;
  type: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "EXPIRED" | string;
  createdAt: string;
  filename?: string;
  requestedBy?: string;
  message?: string;
  format?: "xlsx" | string;
  startDate?: string;
  endDate?: string;
  progress?: number;
  completedAt?: string;
  expiresAt?: string;
  fileAvailable?: boolean;
  fileSize?: number;
  checksum?: string;
  generatedAt?: string;
}

interface ExportTaskWireDto {
  id?: number | string;
  taskId?: number | string;
  meetingId?: number | string;
  type: string;
  status: string;
  filename?: string;
  requestedBy?: string;
  completedAt?: string;
  message?: string;
  createdAt?: string;
  format?: string;
  startDate?: string;
  endDate?: string;
  progress?: number;
  expiresAt?: string;
  fileAvailable?: boolean;
  fileSize?: number;
  checksum?: string;
  generatedAt?: string;
}

export interface AdminUserDto {
  id: string | number;
  username: string;
  displayName?: string;
  name?: string;
  email?: string;
  status?: string;
  roles?: string[];
}

export interface CreateAdminUserPayload {
  username: string;
  displayName: string;
  password: string;
  status: string;
  roles: string[];
}

export interface UpdateAdminUserPayload {
  displayName: string;
  status: string;
  roles: string[];
}

export interface GrantMeetingPermissionPayload {
  meetingId: string | number;
  userId: string | number;
  role: string;
}

export interface SystemSettingsDto {
  baseUrl: string;
  tduckFrontUrl: string;
  tduckApiUrl: string;
  webhookToken?: string;
  webhookTokenConfigured?: boolean;
  webhookTokenMasked?: string;
  maskSensitive: boolean;
  brandName: string;
  browserTitle: string;
  metaDescription: string;
  logoUrl: string;
  faviconUrl: string;
}

export interface BrandingDto {
  brandName: string;
  browserTitle: string;
  metaDescription: string;
  logoUrl: string;
  faviconUrl: string;
}

export interface MeetingUserPermissionDto {
  id?: string | number;
  meetingId?: string | number;
  meetingCode?: string;
  meetingName?: string;
  userId?: string | number;
  username?: string;
  displayName?: string;
  role?: string;
  permission?: string;
  permissions?: string[];
  status?: string;
}

function idOf(value: string | number | null | undefined) {
  return value == null ? "" : String(value);
}

function backendId(id: string) {
  const parsed = Number(id);
  if (!Number.isFinite(parsed)) {
    throw new Error("当前是本地演示数据，无法调用后端接口");
  }
  return parsed;
}

function isBackendMeetingId(id: string) {
  return Number.isFinite(Number(id));
}

function requireMeetingId(id: string | null | undefined) {
  if (!id) {
    throw new Error("请先选择会议");
  }
  return id;
}

function splitTags(tags?: string) {
  return (tags || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function nowIso() {
  return new Date().toISOString();
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function filenameFromDisposition(disposition: string | undefined, fallback: string) {
  if (!disposition) return fallback;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }
  const asciiMatch = disposition.match(/filename="?([^";]+)"?/i);
  return asciiMatch?.[1] || fallback;
}

async function downloadErrorMessage(error: unknown, fallback: string) {
  const responseData = (error as { response?: { data?: unknown } } | null)?.response?.data;
  if (responseData instanceof Blob) {
    try {
      const text = await responseData.text();
      const parsed = JSON.parse(text) as { message?: unknown };
      if (typeof parsed.message === "string" && parsed.message.trim()) return parsed.message;
    } catch {
      // 非 JSON 下载错误继续交给通用错误提取，避免掩盖网络层信息。
    }
  }
  return extractErrorMessage(error, fallback);
}

function blankToUndefined(value?: string) {
  const text = value?.trim();
  return text || undefined;
}

function localMeetingCode() {
  const date = new Date();
  const datePart = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0")
  ].join("");
  return `MEET${datePart}-${Date.now().toString(36).slice(-6).toUpperCase()}`;
}

function fallbackPublicQueryToken(seed?: string) {
  const normalized = (seed || "local")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "");
  return `pq_${normalized || "local"}`;
}

function meetingRequestFrom(meeting: Partial<Meeting>, fallback?: Meeting) {
  const venue = blankToUndefined(meeting.venue ?? fallback?.venue);
  return {
    code: blankToUndefined(meeting.code ?? fallback?.code),
    name: meeting.name || fallback?.name || "新会议",
    organizer: meeting.organizer || fallback?.organizer || "主办方",
    coOrganizer: blankToUndefined(meeting.coOrganizer ?? fallback?.coOrganizer),
    location: venue,
    venueName: venue,
    dinnerVenue: blankToUndefined(meeting.dinnerVenue ?? fallback?.dinnerVenue),
    dinnerTime: blankToUndefined(meeting.dinnerTime ?? fallback?.dinnerTime),
    startTime: blankToUndefined(meeting.startTime ?? fallback?.startTime),
    endTime: blankToUndefined(meeting.endTime ?? fallback?.endTime),
    registrationStartTime: blankToUndefined(meeting.registrationStartTime ?? fallback?.registrationStartTime),
    registrationEndTime: blankToUndefined(meeting.registrationEndTime ?? fallback?.registrationEndTime),
    capacity: meeting.capacity || fallback?.capacity || 300,
    description: blankToUndefined(meeting.description ?? fallback?.description),
    agenda: blankToUndefined(meeting.agenda ?? fallback?.agenda),
    coverImageUrl: blankToUndefined(meeting.coverImageUrl ?? fallback?.coverImageUrl),
    contactName: blankToUndefined(meeting.contactName ?? fallback?.contactName),
    contactPhone: blankToUndefined(meeting.contactPhone ?? fallback?.contactPhone),
    status: meeting.status || fallback?.status || DEFAULT_NEW_MEETING_STATUS,
    seatQueryEnabled: meeting.seatQueryEnabled ?? fallback?.seatQueryEnabled ?? false,
    roomQueryEnabled: meeting.roomQueryEnabled ?? fallback?.roomQueryEnabled ?? false,
    dinnerQueryEnabled: meeting.dinnerQueryEnabled ?? fallback?.dinnerQueryEnabled ?? false,
    checkinEnabled: meeting.checkinEnabled ?? fallback?.checkinEnabled ?? true,
    selfCheckinEnabled: meeting.selfCheckinEnabled ?? fallback?.selfCheckinEnabled ?? true,
    publicQueryFields: meeting.publicQueryFields || fallback?.publicQueryFields || (["PHONE", "NAME"] as PublicQueryField[]),
    publicQueryMatchMode: meeting.publicQueryMatchMode || fallback?.publicQueryMatchMode || "FUZZY"
  };
}

function savedMeetingId() {
  return localStorage.getItem(CURRENT_MEETING_KEY) || "";
}

function emptyPage<T>(page = 0, size = 20): PagePayload<T> {
  return {
    content: [],
    page,
    size,
    totalElements: 0,
    totalPages: 0
  };
}

async function loadAllPageContent<T>(loadPage: (page: number) => Promise<PagePayload<T>>) {
  const firstPage = await loadPage(0);
  const remainingPages = Array.from(
    { length: Math.max(0, firstPage.totalPages - 1) },
    (_, index) => index + 1
  );
  const extraPages = await mapWithConcurrency(remainingPages, PAGE_LOAD_CONCURRENCY, loadPage);
  return [firstPage, ...extraPages].flatMap((page) => page.content);
}

const defaultQueryFieldOptions: MeetingQuerySettings["fieldOptions"] = [
  { field: "PHONE", label: "手机号" },
  { field: "NAME", label: "姓名" },
  { field: "PHONE_LAST4", label: "手机号后四位" },
  { field: "ATTENDEE_CODE", label: "参会码" },
  { field: "ORGANIZATION", label: "单位" },
  { field: "TITLE", label: "职务" },
  { field: "EMAIL", label: "邮箱" }
];

const defaultResultFieldOptions: MeetingQuerySettings["resultFieldOptions"] = [
  { field: "ATTENDEE_NAME", label: "姓名" },
  { field: "ATTENDEE_PHONE", label: "手机号" },
  { field: "ORGANIZATION", label: "单位" },
  { field: "TITLE", label: "职务" },
  { field: "EMAIL", label: "邮箱" },
  { field: "MEETING_NAME", label: "会议名称" },
  { field: "SEAT_AREA", label: "座位区域" },
  { field: "SEAT_ROW", label: "排号" },
  { field: "SEAT_NO", label: "座号" },
  { field: "SEAT_CODE", label: "座位号" },
  { field: "HOTEL_NAME", label: "酒店" },
  { field: "ROOM_TYPE", label: "房型" },
  { field: "ROOM_NO", label: "房间号" },
  { field: "ROOM_REMARK", label: "房间备注" },
  { field: "CHECKIN_TIPS", label: "入住提示" },
  { field: "DINNER_TABLE_NO", label: "晚宴桌号" },
  { field: "DINNER_VENUE", label: "晚宴地点" },
  { field: "DINNER_TIME", label: "晚宴时间" },
  { field: "DINNER_REMARK", label: "晚宴备注" }
];

const defaultVisibleResultFields: PublicQueryResultField[] = [
  "ATTENDEE_NAME",
  "SEAT_AREA",
  "SEAT_ROW",
  "SEAT_NO",
  "SEAT_CODE",
  "HOTEL_NAME",
  "ROOM_TYPE",
  "ROOM_NO",
  "ROOM_REMARK",
  "CHECKIN_TIPS",
  "DINNER_TABLE_NO",
  "DINNER_VENUE",
  "DINNER_TIME",
  "DINNER_REMARK"
];

const defaultSeatVisibleResultFields: PublicQueryResultField[] = ["ATTENDEE_NAME", "SEAT_AREA", "SEAT_ROW", "SEAT_NO", "SEAT_CODE"];
const defaultRoomVisibleResultFields: PublicQueryResultField[] = [
  "ATTENDEE_NAME",
  "HOTEL_NAME",
  "ROOM_TYPE",
  "ROOM_NO",
  "ROOM_REMARK",
  "CHECKIN_TIPS"
];
const defaultDinnerVisibleResultFields: PublicQueryResultField[] = ["ATTENDEE_NAME", "DINNER_TABLE_NO", "DINNER_VENUE", "DINNER_TIME", "DINNER_REMARK"];
const defaultCombinedQueryTypes: CombinedQueryType[] = ["SEAT", "ROOM", "DINNER"];
const publicPageKeys: PublicPageKey[] = ["entry", "combined", "seat", "room", "dinner", "checkin"];
const defaultSeatNotice = "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。";
const defaultRoomNotice = "凭会议证件及身份证办理入住，接待台位于酒店大堂。";
const defaultDinnerNotice = "请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。";

const requestRuntime = new RequestRuntime();
let bootstrapPromise: Promise<void> | null = null;
const workspaceLoadPromises = requestRuntime.createPromiseCache<string, Promise<void>>();
const attendeesLoadPromises = requestRuntime.createPromiseCache<string, Promise<void>>();
const seatsLoadPromises = requestRuntime.createPromiseCache<string, Promise<void>>();
const roomsLoadPromises = requestRuntime.createPromiseCache<string, Promise<void>>();
const dinnerLoadPromises = requestRuntime.createPromiseCache<string, Promise<void>>();
const dinnerTablesLoadPromises = requestRuntime.createPromiseCache<string, Promise<void>>();
const latestStatisticsRequests = new Map<string, LatestRequestToken>();
const ADMIN_PAGE_SIZE = 200;
const PAGE_LOAD_CONCURRENCY = 4;
const MAX_CACHED_ATTENDEE_MEETINGS = 2;
const attendeeMeetingAccessOrder: string[] = [];

function rememberAttendeeMeeting(meetingId: string) {
  const existingIndex = attendeeMeetingAccessOrder.indexOf(meetingId);
  if (existingIndex >= 0) attendeeMeetingAccessOrder.splice(existingIndex, 1);
  attendeeMeetingAccessOrder.push(meetingId);
}

function retainedAttendeeMeetingIds(currentMeetingId = "") {
  const retained = attendeeMeetingAccessOrder.slice(-MAX_CACHED_ATTENDEE_MEETINGS);
  if (currentMeetingId && !retained.includes(currentMeetingId)) {
    if (retained.length >= MAX_CACHED_ATTENDEE_MEETINGS) retained.shift();
    retained.push(currentMeetingId);
  }
  return new Set(retained);
}

function singleFlight<T>(promises: Map<string, Promise<T>>, key: string, loader: () => Promise<T>) {
  const existing = promises.get(key);
  if (existing) return existing;

  /*
   * 快速切换侧边栏时，旧页面的 onMounted 请求和新页面的按需请求可能同时命中同一资源。
   * 这里把同会议同资源的并发加载合并成一次，避免重复解析大列表并多次触发响应式重绘。
   */
  let promise: Promise<T>;
  promise = loader().finally(() => {
    // reset 后新账号可能已经为同一个 key 建立了新 Promise，旧 finally 不能误删它。
    if (promises.get(key) === promise) promises.delete(key);
  });
  promises.set(key, promise);
  return promise;
}

function statisticsRequestKey(meetingId: string, filters: { startDate?: string; endDate?: string } = {}) {
  return [meetingId, filters.startDate || "", filters.endDate || ""].join(":");
}

function publicQueryHistoryRequestKey(meetingId: string, filters: PublicQueryHistoryFilters) {
  return [
    meetingId,
    filters.page ?? 0,
    filters.size ?? 20,
    filters.type || "",
    filters.status || "",
    filters.keyword?.trim() || ""
  ].join(":");
}

export function clearMeetingRuntimeCaches() {
  bootstrapPromise = null;
  latestStatisticsRequests.clear();
  attendeeMeetingAccessOrder.splice(0);
  requestRuntime.reset();
}

function groupByMeetingId<T extends { meetingId: string }>(items: T[]): Map<string, T[]> {
  const index = new Map<string, T[]>();
  for (const item of items) {
    const existing = index.get(item.meetingId);
    if (existing) existing.push(item);
    else index.set(item.meetingId, [item]);
  }
  return index;
}

export const useMeetingStore = defineStore("meeting", {
  state: () => ({
    meetings: [] as Meeting[],
    attendees: [] as Attendee[],
    seats: [] as Seat[],
    rooms: [] as Room[],
    dinnerAssignments: [] as DinnerAssignment[],
    dinnerTables: [] as DinnerTable[],
    syncLogs: [] as SyncLog[],
    tduckSubmissionConflictsByMeetingId: {} as Record<string, PagePayload<TduckSubmissionConflictDto>>,
    checkinRecords: [] as CheckinRecord[],
    loadedAttendeeMeetingIds: [] as string[],
    statisticsByRequestKey: {} as Record<string, StatisticsOverviewDto>,
    activeStatisticsRequestKeyByMeetingId: {} as Record<string, string>,
    publicQueryHistoryByRequestKey: {} as Record<string, PagePayload<PublicQueryHistory>>,
    activePublicQueryHistoryKeyByMeetingId: {} as Record<string, string>,
    publicQuerySummaryByMeetingId: {} as Record<string, PublicQuerySummary[]>,
    apiAvailable: false,
    loading: false,
    currentMeetingId: savedMeetingId(),
    branding: defaultBranding()
  }),
  getters: {
    meetingById(state): Map<string, Meeting> {
      return new Map(state.meetings.map((meeting) => [meeting.id, meeting]));
    },
    attendeesByMeetingId(state): Map<string, Attendee[]> {
      return groupByMeetingId(state.attendees);
    },
    seatsByMeetingId(state): Map<string, Seat[]> {
      return groupByMeetingId(state.seats);
    },
    roomsByMeetingId(state): Map<string, Room[]> {
      return groupByMeetingId(state.rooms);
    },
    dinnerAssignmentsByMeetingId(state): Map<string, DinnerAssignment[]> {
      return groupByMeetingId(state.dinnerAssignments);
    },
    dinnerTablesByMeetingId(state): Map<string, DinnerTable[]> {
      return groupByMeetingId(state.dinnerTables);
    },
    checkinRecordsByMeetingId(state): Map<string, CheckinRecord[]> {
      return groupByMeetingId(state.checkinRecords);
    },
    currentMeeting(): Meeting | null {
      return this.meetingById.get(this.currentMeetingId) || null;
    },
    currentAttendees(): Attendee[] {
      return this.attendeesByMeetingId.get(this.currentMeetingId) || [];
    },
    currentSeats(): Seat[] {
      return this.seatsByMeetingId.get(this.currentMeetingId) || [];
    },
    currentRooms(): Room[] {
      return this.roomsByMeetingId.get(this.currentMeetingId) || [];
    },
    currentDinnerAssignments(): DinnerAssignment[] {
      return this.dinnerAssignmentsByMeetingId.get(this.currentMeetingId) || [];
    },
    currentDinnerTables(): DinnerTable[] {
      return this.dinnerTablesByMeetingId.get(this.currentMeetingId) || [];
    },
    currentCheckinRecords(): CheckinRecord[] {
      return this.checkinRecordsByMeetingId.get(this.currentMeetingId) || [];
    },
    currentTduckSubmissionConflicts(state): PagePayload<TduckSubmissionConflictDto> {
      return state.tduckSubmissionConflictsByMeetingId[this.currentMeetingId] || emptyPage<TduckSubmissionConflictDto>();
    },
    overview(state): StatisticsOverviewDto | null {
      const requestKey = state.activeStatisticsRequestKeyByMeetingId[state.currentMeetingId];
      return requestKey ? state.statisticsByRequestKey[requestKey] || null : null;
    },
    currentPublicQueryHistory(state): PagePayload<PublicQueryHistory> | null {
      const requestKey = state.activePublicQueryHistoryKeyByMeetingId[state.currentMeetingId];
      return requestKey ? state.publicQueryHistoryByRequestKey[requestKey] || null : null;
    },
    currentPublicQuerySummary(state): PublicQuerySummary[] {
      return state.publicQuerySummaryByMeetingId[state.currentMeetingId] || [];
    },
    currentAttendeeById(): Map<string, Attendee> {
      return new Map(this.currentAttendees.map((attendee) => [attendee.id, attendee]));
    },
    currentSeatByAttendeeId(): Map<string, Seat> {
      return new Map(this.currentSeats.filter((seat) => seat.attendeeId).map((seat) => [seat.attendeeId as string, seat]));
    },
    currentRoomByNo(): Map<string, Room> {
      return new Map(this.currentRooms.map((room) => [room.roomNo, room]));
    },
    currentAttendeesByRoomNo(): Map<string, Attendee[]> {
      const index = new Map<string, Attendee[]>();
      for (const attendee of this.currentAttendees) {
        if (!attendee.roomNo) continue;
        const existing = index.get(attendee.roomNo);
        if (existing) existing.push(attendee);
        else index.set(attendee.roomNo, [attendee]);
      }
      return index;
    },
    currentAttendeesByRoomId(): Map<string, Attendee[]> {
      const index = new Map<string, Attendee[]>();
      for (const attendee of this.currentAttendees) {
        if (!attendee.roomId) continue;
        const existing = index.get(attendee.roomId);
        if (existing) existing.push(attendee);
        else index.set(attendee.roomId, [attendee]);
      }
      return index;
    },
    currentDinnerAssignmentByAttendeeId(): Map<string, DinnerAssignment> {
      return new Map(this.currentDinnerAssignments.map((assignment) => [assignment.attendeeId, assignment]));
    },
    currentDinnerAssignmentsByTableNo(): Map<string, DinnerAssignment[]> {
      const index = new Map<string, DinnerAssignment[]>();
      for (const assignment of this.currentDinnerAssignments) {
        const existing = index.get(assignment.tableNo);
        if (existing) existing.push(assignment);
        else index.set(assignment.tableNo, [assignment]);
      }
      return index;
    }
  },
  actions: {
    resetForAuthChange() {
      /*
       * Pinia 的 $reset 只重建响应式 state，不会触及模块级 Promise Map。
       * 账号退出或主体变化时两者必须一起清理，避免新账号复用旧账号尚未完成的请求。
       */
      clearMeetingRuntimeCaches();
      localStorage.removeItem(CURRENT_MEETING_KEY);
      this.$reset();
    },
    async bootstrap() {
      // 多个页面可能同时触发初始化，复用同一个请求，避免座位页先渲染 mock 数据再被真实数据替换。
      if (!bootstrapPromise) {
        let promise: Promise<void>;
        promise = (async () => {
          await this.loadBranding();
          await this.loadMeetings();
        })().finally(() => {
          if (bootstrapPromise === promise) bootstrapPromise = null;
        });
        bootstrapPromise = promise;
      }
      await bootstrapPromise;
    },
    async loadBranding() {
      const request = requestRuntime.requests.begin("branding");
      try {
        const branding = normalizeBranding(unwrapApi(await http.get<BrandingDto>("/public/branding", { signal: request.signal })));
        if (!requestRuntime.requests.isCurrent(request)) return this.branding;
        this.branding = branding;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) return this.branding;
        // 品牌配置属于装饰信息，后端不可用时使用内置默认值，不影响登录和业务流程。
        this.branding = defaultBranding();
      } finally {
        requestRuntime.requests.finish(request);
      }
      applyDocumentBranding(this.branding);
      return this.branding;
    },
    async loadMeetings() {
      const request = requestRuntime.requests.begin("meeting-list");
      this.loading = true;
      try {
        const meetings = await loadAllPageContent<MeetingSummaryDto>(async (page) =>
          unwrapApi(
            await http.get<PagePayload<MeetingSummaryDto>>("/admin/meetings", {
              params: { page, size: 200 },
              signal: request.signal
            })
          )
        );
        if (!requestRuntime.requests.isCurrent(request)) return;
        this.meetings = meetings.map(mapMeetingSummary);
        if (!this.meetings.some((meeting) => meeting.id === this.currentMeetingId)) {
          this.setCurrentMeetingId(this.meetings[0]?.id || "");
        }
        this.apiAvailable = true;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) return;
        // 认证失败时 HTTP 拦截器会跳回登录页，这里不要再刷新 mock 计数，
        // 避免后台页面在跳转前短暂展示本地演示数据。
        if (isAuthFailure(error)) {
          this.apiAvailable = false;
          return;
        }
        if (canUseDemoFallback(error)) {
          this.apiAvailable = false;
          if (!this.meetings.length) {
            this.loadDemoData();
          }
          this.refreshMeetingCounters();
          return;
        }
        throw error;
      } finally {
        if (requestRuntime.requests.isCurrent(request)) this.loading = false;
        requestRuntime.requests.finish(request);
      }
    },
    setCurrentMeetingId(meetingId: string) {
      this.currentMeetingId = meetingId;
      if (meetingId) {
        if (this.attendees.some((attendee) => attendee.meetingId === meetingId)) rememberAttendeeMeeting(meetingId);
        localStorage.setItem(CURRENT_MEETING_KEY, meetingId);
      } else {
        localStorage.removeItem(CURRENT_MEETING_KEY);
      }
    },
    loadDemoData() {
      const preferredMeetingId = this.currentMeetingId || savedMeetingId();
      this.meetings = [...mockMeetings];
      this.attendees = [...mockAttendees];
      this.seats = [...mockSeats];
      this.rooms = [...mockRooms];
      this.dinnerAssignments = [...mockDinnerAssignments];
      this.syncLogs = [...mockSyncLogs];
      this.loadedAttendeeMeetingIds = this.meetings.map((meeting) => meeting.id);
      const fallbackMeetingId = this.meetings.some((meeting) => meeting.id === preferredMeetingId)
        ? preferredMeetingId
        : this.meetings[0]?.id || "";
      this.setCurrentMeetingId(fallbackMeetingId);
    },
    async selectMeeting(meetingId: string, options: { loadWorkspace?: boolean } = {}) {
      const selection = requestRuntime.requests.begin("meeting-selection");
      const targetMeetingId = idOf(meetingId);
      try {
        if (!targetMeetingId) {
          if (requestRuntime.requests.isCurrent(selection)) this.setCurrentMeetingId("");
          return null;
        }
        const loadWorkspace = options.loadWorkspace !== false;

        /*
         * Store 初始化时会先放一份 mock 会议，直接刷新 /meetings/:id/* 时不能把它当成真实列表。
         * 数字 ID 来自后端路由，优先读取详情；失败后再回退本地演示数据，避免页面显示“未选择会议”。
         */
        if (isBackendMeetingId(targetMeetingId)) {
          const remote = await this.loadMeetingDetail(targetMeetingId, { loadWorkspace });
          if (!requestRuntime.requests.isCurrent(selection)) return null;
          if (remote) return remote;
        }

        if (!this.meetings.length) {
          await this.bootstrap();
        }
        if (!requestRuntime.requests.isCurrent(selection)) return null;

        const local = this.meetings.find((meeting) => meeting.id === targetMeetingId);
        if (!local) return null;
        this.setCurrentMeetingId(local.id);
        if (loadWorkspace) {
          await this.loadMeetingWorkspace(local.id);
        }
        return requestRuntime.requests.isCurrent(selection) ? local : null;
      } finally {
        requestRuntime.requests.finish(selection);
      }
    },
    async loadMeetingDetail(meetingId: string, options: { loadWorkspace?: boolean } = {}) {
      const request = requestRuntime.requests.begin("meeting-detail");
      try {
        const detail = unwrapApi(
          await http.get<MeetingDetailDto>(`/admin/meetings/${backendId(meetingId)}`, { signal: request.signal })
        );
        if (!requestRuntime.requests.isCurrent(request)) return null;
        const mapped = mapMeetingDetail(detail);
        this.upsertMeeting(mapped);
        this.setCurrentMeetingId(mapped.id);
        if (options.loadWorkspace !== false) {
          await this.loadMeetingWorkspace(mapped.id);
        }
        return mapped;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) return null;
        const local = this.meetings.find((meeting) => meeting.id === meetingId);
        if (local) {
          this.setCurrentMeetingId(local.id);
          return local;
        }
        return null;
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async loadMeetingQuerySettings(meetingId?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const request = requestRuntime.requests.begin("meeting-query-settings");
      try {
        const settings = mapQuerySettings(
          unwrapApi(
            await http.get<MeetingQuerySettingsDto>(`/admin/meetings/${backendId(targetMeetingId)}/query-settings`, {
              signal: request.signal
            })
          )
        );
        if (!requestRuntime.requests.isCurrent(request)) {
          return meetingToQuerySettings(this.meetings.find((item) => item.id === targetMeetingId));
        }
        this.applyMeetingQuerySettings(settings);
        return settings;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) {
          return meetingToQuerySettings(this.meetings.find((item) => item.id === targetMeetingId));
        }
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询设置加载失败"));
        const meeting = this.meetings.find((item) => item.id === targetMeetingId);
        return meetingToQuerySettings(meeting);
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async saveMeetingQuerySettings(
      meetingId: string,
      settings: Pick<
        MeetingQuerySettings,
        | "seatQueryEnabled"
        | "roomQueryEnabled"
        | "dinnerQueryEnabled"
        | "checkinEnabled"
        | "selfCheckinEnabled"
        | "queryPaused"
        | "fields"
        | "requiredFields"
        | "matchMode"
        | "allowMultipleResults"
        | "combinedQueryTypes"
        | "pageTitle"
        | "pageDescription"
        | "announcement"
        | "publisher"
        | "startTime"
        | "endTime"
        | "buttonText"
        | "emptyMessage"
        | "seatNotice"
        | "roomNotice"
        | "dinnerNotice"
        | "pageContents"
        | "themeColor"
        | "logoUrl"
        | "backgroundUrl"
        | "resultLayout"
        | "visibleResultFields"
        | "maskedResultFields"
        | "highlightResultFields"
        | "seatVisibleResultFields"
        | "seatMaskedResultFields"
        | "seatHighlightResultFields"
        | "roomVisibleResultFields"
        | "roomMaskedResultFields"
        | "roomHighlightResultFields"
        | "dinnerVisibleResultFields"
        | "dinnerMaskedResultFields"
        | "dinnerHighlightResultFields"
      >
    ) {
      const targetMeetingId = requireMeetingId(meetingId);
      try {
        const saved = mapQuerySettings(
          unwrapApi(
            await http.put<MeetingQuerySettingsDto>(`/admin/meetings/${backendId(targetMeetingId)}/query-settings`, {
              seatQueryEnabled: settings.seatQueryEnabled,
              roomQueryEnabled: settings.roomQueryEnabled,
              dinnerQueryEnabled: settings.dinnerQueryEnabled,
              checkinEnabled: settings.checkinEnabled,
              selfCheckinEnabled: settings.selfCheckinEnabled,
              queryPaused: settings.queryPaused,
              fields: settings.fields,
              requiredFields: settings.requiredFields,
              matchMode: settings.matchMode,
              allowMultipleResults: settings.allowMultipleResults,
              combinedQueryTypes: settings.combinedQueryTypes,
              pageTitle: settings.pageTitle,
              pageDescription: settings.pageDescription,
              announcement: settings.announcement,
              publisher: settings.publisher,
              startTime: settings.startTime,
              endTime: settings.endTime,
              buttonText: settings.buttonText,
              emptyMessage: settings.emptyMessage,
              seatNotice: settings.seatNotice,
              roomNotice: settings.roomNotice,
              dinnerNotice: settings.dinnerNotice,
              pageContents: settings.pageContents,
              themeColor: settings.themeColor,
              logoUrl: settings.logoUrl,
              backgroundUrl: settings.backgroundUrl,
              resultLayout: settings.resultLayout,
              visibleResultFields: settings.visibleResultFields,
              maskedResultFields: settings.maskedResultFields,
              highlightResultFields: settings.highlightResultFields,
              seatVisibleResultFields: settings.seatVisibleResultFields,
              seatMaskedResultFields: settings.seatMaskedResultFields,
              seatHighlightResultFields: settings.seatHighlightResultFields,
              roomVisibleResultFields: settings.roomVisibleResultFields,
              roomMaskedResultFields: settings.roomMaskedResultFields,
              roomHighlightResultFields: settings.roomHighlightResultFields,
              dinnerVisibleResultFields: settings.dinnerVisibleResultFields,
              dinnerMaskedResultFields: settings.dinnerMaskedResultFields,
              dinnerHighlightResultFields: settings.dinnerHighlightResultFields
            })
          )
        );
        this.applyMeetingQuerySettings(saved);
        return saved;
      } catch (error) {
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询设置保存失败"));
        const local = meetingToQuerySettings(this.meetings.find((item) => item.id === targetMeetingId));
        const saved: MeetingQuerySettings = {
          ...local,
          seatQueryEnabled: settings.seatQueryEnabled,
          roomQueryEnabled: settings.roomQueryEnabled,
          dinnerQueryEnabled: settings.dinnerQueryEnabled,
          checkinEnabled: settings.checkinEnabled,
          selfCheckinEnabled: settings.selfCheckinEnabled,
          queryPaused: settings.queryPaused,
          fields: [...settings.fields],
          requiredFields: [...settings.requiredFields],
          matchMode: settings.matchMode,
          allowMultipleResults: settings.allowMultipleResults,
          combinedQueryTypes: [...settings.combinedQueryTypes],
          pageTitle: settings.pageTitle,
          pageDescription: settings.pageDescription,
          announcement: settings.announcement,
          publisher: settings.publisher,
          startTime: settings.startTime,
          endTime: settings.endTime,
          buttonText: settings.buttonText,
          emptyMessage: settings.emptyMessage,
          seatNotice: settings.seatNotice,
          roomNotice: settings.roomNotice,
          dinnerNotice: settings.dinnerNotice,
          pageContents: clonePageContents(settings.pageContents),
          themeColor: settings.themeColor,
          logoUrl: settings.logoUrl,
          backgroundUrl: settings.backgroundUrl,
          resultLayout: settings.resultLayout,
          visibleResultFields: [...settings.visibleResultFields],
          maskedResultFields: [...settings.maskedResultFields],
          highlightResultFields: [...settings.highlightResultFields],
          seatVisibleResultFields: [...settings.seatVisibleResultFields],
          seatMaskedResultFields: [...settings.seatMaskedResultFields],
          seatHighlightResultFields: [...settings.seatHighlightResultFields],
          roomVisibleResultFields: [...settings.roomVisibleResultFields],
          roomMaskedResultFields: [...settings.roomMaskedResultFields],
          roomHighlightResultFields: [...settings.roomHighlightResultFields],
          dinnerVisibleResultFields: [...settings.dinnerVisibleResultFields],
          dinnerMaskedResultFields: [...settings.dinnerMaskedResultFields],
          dinnerHighlightResultFields: [...settings.dinnerHighlightResultFields]
        };
        this.applyMeetingQuerySettings(saved);
        return saved;
      }
    },
    async loadPublicQuerySettings(params: { publicQueryToken?: string | null }) {
      try {
        const settings = mapQuerySettings(
          unwrapApi(
            await http.get<MeetingQuerySettingsDto>("/public/query-settings", {
              params: {
                token: params.publicQueryToken || undefined
              }
            })
          )
        );
        this.applyMeetingQuerySettings(settings);
        return settings;
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询设置加载失败"));
        if (!this.meetings.length) {
          this.loadDemoData();
        }
        if (!params.publicQueryToken) {
          throw new Error("公开查询链接无效");
        }
        const meeting = this.meetings.find((item) => item.publicQueryToken === params.publicQueryToken);
        if (!meeting) {
          throw new Error("公开查询链接无效");
        }
        return meetingToQuerySettings(meeting);
      }
    },
    async loadPublicCaptcha() {
      return unwrapApi(await http.get<PublicCaptchaDto>("/public/captcha"));
    },
    async publicSeatQuery(payload: {
      publicQueryToken?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }) {
      try {
        return unwrapApi(await http.post<PublicQueryResponse<SeatQueryResult>>("/public/seat-query", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "座位查询失败"));
        return localSeatQuery(this, payload);
      }
    },
    async publicRoomQuery(payload: {
      publicQueryToken?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }) {
      try {
        return unwrapApi(await http.post<PublicQueryResponse<RoomQueryResult>>("/public/room-query", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "住宿查询失败"));
        return localRoomQuery(this, payload);
      }
    },
    async publicDinnerQuery(payload: {
      publicQueryToken?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }) {
      try {
        return unwrapApi(await http.post<PublicQueryResponse<DinnerQueryResult>>("/public/dinner-query", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "晚宴查询失败"));
        return localDinnerQuery(this, payload);
      }
    },
    async publicCombinedQuery(payload: {
      publicQueryToken?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }, options: { queryTypes?: readonly CombinedQueryType[] } = {}): Promise<PublicCombinedQueryDto> {
      try {
        return unwrapApi(await http.post<PublicCombinedQueryDto>("/public/combined-query", payload));
      } catch (error) {
        if (isHttpStatus(error, [404, 405])) {
          /*
           * 前后端滚动发布期间，旧后端还没有综合接口。只在“路由不存在”时回退旧接口；
           * 其他错误继续上抛，避免把鉴权、限流或服务端异常误判为版本兼容问题。
           */
          const enabledTypes = new Set(options.queryTypes || defaultCombinedQueryTypes);
          const result: PublicCombinedQueryDto = {};
          const requests: Promise<void>[] = [];
          if (enabledTypes.has("SEAT")) {
            requests.push(this.publicSeatQuery(payload)
              .then((value) => { result.seat = value; })
              .catch((seatError) => { result.seatError = extractErrorMessage(seatError, "座位查询失败"); }));
          }
          if (enabledTypes.has("ROOM")) {
            requests.push(this.publicRoomQuery(payload)
              .then((value) => { result.room = value; })
              .catch((roomError) => { result.roomError = extractErrorMessage(roomError, "住宿查询失败"); }));
          }
          if (enabledTypes.has("DINNER")) {
            requests.push(this.publicDinnerQuery(payload)
              .then((value) => { result.dinner = value; })
              .catch((dinnerError) => { result.dinnerError = extractErrorMessage(dinnerError, "晚宴查询失败"); }));
          }
          await Promise.all(requests);
          return result;
        }
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "综合查询失败"));
        const result: PublicCombinedQueryDto = {};
        try {
          result.seat = localSeatQuery(this, payload);
        } catch (seatError) {
          result.seatError = seatError instanceof Error ? seatError.message : "座位查询失败";
        }
        try {
          result.room = localRoomQuery(this, payload);
        } catch (roomError) {
          result.roomError = roomError instanceof Error ? roomError.message : "住宿查询失败";
        }
        try {
          result.dinner = localDinnerQuery(this, payload);
        } catch (dinnerError) {
          result.dinnerError = dinnerError instanceof Error ? dinnerError.message : "晚宴查询失败";
        }
        return result;
      }
    },
    async publicSelfCheckin(payload: {
      publicQueryToken?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
      checkinSource?: PublicCheckinSource;
      checkinPage?: PublicPageKey;
    }) {
      try {
        return unwrapApi(await http.post<PublicCheckinResult>("/public/checkin", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "签到失败"));
        return localPublicSelfCheckin(this, payload);
      }
    },
    async publicSelfCheckinPreview(payload: {
      publicQueryToken?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
      checkinSource?: PublicCheckinSource;
      checkinPage?: PublicPageKey;
    }) {
      try {
        return unwrapApi(await http.post<PublicCheckinPreview>("/public/checkin-preview", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "签到信息核验失败"));
        return localPublicSelfCheckinPreview(this, payload);
      }
    },
    async adminQueryCandidates(meetingId: string, type: "seat" | "room", values: Record<PublicQueryField, string>) {
      const targetMeetingId = requireMeetingId(meetingId);
      try {
        return unwrapApi(
          await http.post<AdminQueryCandidate[]>(`/admin/meetings/${backendId(targetMeetingId)}/queries/${type}`, { values })
        );
      } catch (error) {
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询失败"));
        return localAdminCandidates(this, targetMeetingId, values);
      }
    },
    async loadPublicQueryHistory(meetingId: string, filters: PublicQueryHistoryFilters = {}) {
      const targetMeetingId = requireMeetingId(meetingId);
      const cacheKey = publicQueryHistoryRequestKey(targetMeetingId, filters);
      const request = requestRuntime.requests.begin(`public-query-history:${targetMeetingId}`);
      try {
        const keyword = filters.keyword?.trim();
        const page = unwrapApi(
          await http.get<PagePayload<PublicQueryHistoryDto>>(`/admin/meetings/${backendId(targetMeetingId)}/query-history`, {
            params: {
              page: filters.page ?? 0,
              size: filters.size ?? 20,
              type: filters.type || undefined,
              status: filters.status || undefined,
              keyword: keyword || undefined
            },
            signal: request.signal
          })
        );
        const result = {
          ...page,
          content: page.content.map(mapPublicQueryHistory)
        };
        if (requestRuntime.requests.isCurrent(request)) {
          this.publicQueryHistoryByRequestKey[cacheKey] = result;
          this.activePublicQueryHistoryKeyByMeetingId[targetMeetingId] = cacheKey;
        }
        return result;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) {
          return this.publicQueryHistoryByRequestKey[cacheKey] || emptyPage<PublicQueryHistory>(filters.page ?? 0, filters.size ?? 20);
        }
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询历史加载失败"));
        return emptyPage<PublicQueryHistory>(filters.page ?? 0, filters.size ?? 20);
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async loadPublicQuerySummary(meetingId: string) {
      const targetMeetingId = requireMeetingId(meetingId);
      const request = requestRuntime.requests.begin(`public-query-summary:${targetMeetingId}`);
      try {
        const summary = unwrapApi(
          await http.get<PublicQuerySummaryDto[]>(`/admin/meetings/${backendId(targetMeetingId)}/query-history/summary`, {
            signal: request.signal
          })
        ).map(mapPublicQuerySummary);
        if (requestRuntime.requests.isCurrent(request)) {
          this.publicQuerySummaryByMeetingId[targetMeetingId] = summary;
        }
        return summary;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) {
          return this.publicQuerySummaryByMeetingId[targetMeetingId] || [];
        }
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询汇总加载失败"));
        return [] as PublicQuerySummary[];
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    applyMeetingQuerySettings(settings: MeetingQuerySettings) {
      const meeting = this.meetings.find((item) => item.id === settings.meetingId || item.code === settings.meetingCode || item.publicQueryToken === settings.publicQueryToken);
      if (!meeting) return;
      meeting.publicQueryToken = settings.publicQueryToken;
      meeting.seatQueryEnabled = settings.seatQueryEnabled;
      meeting.roomQueryEnabled = settings.roomQueryEnabled;
      meeting.dinnerQueryEnabled = settings.dinnerQueryEnabled;
      meeting.checkinEnabled = settings.checkinEnabled;
      meeting.selfCheckinEnabled = settings.selfCheckinEnabled;
      meeting.publicQueryAllowMultipleResults = settings.allowMultipleResults;
      meeting.publicQueryFields = [...settings.fields];
      meeting.publicQueryRequiredFields = [...settings.requiredFields];
      meeting.publicQueryMatchMode = settings.matchMode;
    },
    async loadMeetingWorkspace(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const existing = workspaceLoadPromises.get(meetingId);
      if (existing) {
        await existing;
        return;
      }

      /*
       * 进入工作区、路由同步和侧边栏切换可能在同一时间请求完整工作区。
       * 复用同会议的加载 Promise，避免多轮参会人/座位/住宿/签到/统计请求堆叠拖死浏览器主线程。
       */
      const request = requestRuntime.requests.begin(`workspace:${meetingId}`);
      let promise: Promise<void>;
      promise = (async () => {
        await Promise.allSettled([
          this.loadAttendees(meetingId),
          this.loadSeats(meetingId),
          this.loadRooms(meetingId),
          this.loadDinnerTables(meetingId),
          this.loadDinnerAssignments(meetingId),
          this.loadDinnerSettings(meetingId),
          this.loadSyncLogs(meetingId),
          this.loadCheckinRecords(meetingId),
          this.loadStatistics(meetingId)
        ]);
        if (requestRuntime.requests.isCurrent(request)) {
          // 工作区数据可以跨会议预取，但只允许当前路由会议执行依赖 current* getter 的归并。
          if (this.currentMeetingId === meetingId) this.reconcileAssignments();
          this.refreshMeetingCounters();
        }
      })().finally(() => {
        requestRuntime.requests.finish(request);
        if (workspaceLoadPromises.get(meetingId) === promise) workspaceLoadPromises.delete(meetingId);
      });
      workspaceLoadPromises.set(meetingId, promise);
      await promise;
    },
    async loadAttendees(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      // LRU 以用户发起加载的时刻为准，旧请求晚返回时不能反过来淘汰当前会议。
      rememberAttendeeMeeting(meetingId);
      return singleFlight(attendeesLoadPromises, meetingId, async () => {
        const request = requestRuntime.requests.begin(`attendees:${meetingId}`);
        try {
          const firstPage = unwrapApi(
            await http.get<PagePayload<AttendeeDto>>("/admin/attendees", {
              params: { meetingId: backendId(meetingId), page: 0, size: ADMIN_PAGE_SIZE },
              signal: request.signal
            })
          );
          const remainingPages = Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) => index + 1);
          const extraPages = await mapWithConcurrency(
            remainingPages,
            PAGE_LOAD_CONCURRENCY,
            async (page) =>
              unwrapApi(
                await http.get<PagePayload<AttendeeDto>>("/admin/attendees", {
                  params: { meetingId: backendId(meetingId), page, size: ADMIN_PAGE_SIZE },
                  signal: request.signal
                })
              )
          );
          if (!requestRuntime.requests.isCurrent(request)) return;
          const content = [firstPage, ...extraPages].flatMap((page) => page.content);
          const retainedMeetingIds = retainedAttendeeMeetingIds(this.currentMeetingId);
          const mappedContent = retainedMeetingIds.has(meetingId)
            ? content.map((item) => mapAttendee(item, meetingId))
            : [];
          this.attendees = [
            ...this.attendees.filter((attendee) => attendee.meetingId !== meetingId && retainedMeetingIds.has(attendee.meetingId)),
            ...mappedContent
          ];
          const loadedMeetingIds = retainedMeetingIds.has(meetingId)
            ? [...this.loadedAttendeeMeetingIds, meetingId]
            : this.loadedAttendeeMeetingIds;
          this.loadedAttendeeMeetingIds = [...new Set(loadedMeetingIds)]
            .filter((loadedMeetingId) => retainedMeetingIds.has(loadedMeetingId));
        } finally {
          requestRuntime.requests.finish(request);
        }
      });
    },
    cancelAttendeeSearch(meetingId?: string) {
      const targetMeetingId = meetingId || this.currentMeetingId;
      if (targetMeetingId) requestRuntime.requests.cancel(`attendee-search:${targetMeetingId}`);
    },
    async searchAttendees(
      filters: { keyword?: string; auditStatus?: string | null; hasSeat?: boolean | null; tags?: string[] },
      options: { meetingId?: string } = {}
    ) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      const request = requestRuntime.requests.begin(`attendee-search:${meetingId}`);
      const keyword = filters.keyword?.trim();
      const tags = filters.tags?.filter(Boolean) || [];
      try {
        const page = unwrapApi(
          await http.get<PagePayload<AttendeeDto>>("/admin/attendees", {
            params: {
              meetingId: backendId(meetingId),
              keyword: keyword || undefined,
              reviewStatus: filters.auditStatus || undefined,
              hasSeat: filters.hasSeat == null ? undefined : filters.hasSeat,
              tags: tags.length ? tags.join(",") : undefined,
              page: 0,
              size: ADMIN_PAGE_SIZE
            },
            signal: request.signal
          })
        );
        if (!requestRuntime.requests.isCurrent(request)) return [] as Attendee[];
        const mapped = page.content.map((item) => mapAttendee(item, meetingId));
        for (const attendee of mapped) {
          const index = this.attendees.findIndex((item) => item.id === attendee.id);
          if (index >= 0) this.attendees[index] = { ...this.attendees[index], ...attendee };
          else this.attendees.push(attendee);
        }
        if (this.currentMeetingId === meetingId) this.reconcileAssignments();
        const resultIds = new Set(mapped.map((item) => item.id));
        return this.attendees.filter((item) => item.meetingId === meetingId && resultIds.has(item.id));
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) throw error;
        // 后端筛选接口不可用时不打断页面操作，改走与后端参数语义一致的本地筛选。
        return this.filterAttendeesLocally(filters, meetingId);
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async loadSeats(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(seatsLoadPromises, meetingId, async () => {
        const request = requestRuntime.requests.begin(`seats:${meetingId}`);
        try {
          const seats = unwrapApi(
            await http.get<SeatViewDto[]>(`/admin/seating/map/${backendId(meetingId)}`, { signal: request.signal })
          );
          if (!requestRuntime.requests.isCurrent(request)) return;
          this.seats = [...this.seats.filter((seat) => seat.meetingId !== meetingId), ...seats.map((item) => mapSeat(item, meetingId))];
        } finally {
          requestRuntime.requests.finish(request);
        }
      });
    },
    async saveSeatLayout(payload: SeatLayoutPayload) {
      const meeting = this.currentMeeting;
      const meetingId = requireMeetingId(this.currentMeetingId);
      const areaName = payload.areaName.trim() || "A区";
      const originalAreaName = payload.originalAreaName?.trim() || undefined;
      const rowCount = Math.max(1, Math.floor(payload.rowCount));
      const seatsPerRow = Math.max(1, Math.floor(payload.seatsPerRow));
      try {
        await http.post("/admin/seating/layouts", {
          meetingId: backendId(meetingId),
          venueName: payload.venueName?.trim() || meeting?.venue || "主会场",
          originalAreaName,
          areaName,
          rowCount,
          seatsPerRow,
          numberingRule: "row-seat",
          aisleSettings: "",
          stageDirection: "北",
          sortOrder: payload.sortOrder,
          defaultSeatType: payload.defaultSeatType || "NORMAL"
        });
        await Promise.allSettled([this.loadSeats(meetingId), this.loadStatistics(meetingId)]);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.saveSeatLayoutLocally({ originalAreaName, areaName, rowCount, seatsPerRow, sortOrder: payload.sortOrder, venueName: payload.venueName, defaultSeatType: payload.defaultSeatType });
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async deleteSeatArea(areaName: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const normalizedAreaName = areaName.trim();
      if (!normalizedAreaName) throw new Error("区域名称不能为空");
      try {
        await http.delete("/admin/seating/layouts", {
          params: {
            meetingId: backendId(meetingId),
            areaName: normalizedAreaName
          }
        });
        await Promise.allSettled([this.loadSeats(meetingId), this.loadStatistics(meetingId)]);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.deleteSeatAreaLocally(normalizedAreaName);
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async loadRooms(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(roomsLoadPromises, meetingId, async () => {
        const request = requestRuntime.requests.begin(`rooms:${meetingId}`);
        try {
          const rooms = unwrapApi(
            await http.get<RoomDto[]>("/admin/rooms", {
              params: { meetingId: backendId(meetingId) },
              signal: request.signal
            })
          );
          if (!requestRuntime.requests.isCurrent(request)) return;
          this.rooms = [...this.rooms.filter((room) => room.meetingId !== meetingId), ...rooms.map((item) => mapRoom(item, meetingId))];
        } finally {
          requestRuntime.requests.finish(request);
        }
      });
    },
    async loadDinnerAssignments(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(dinnerLoadPromises, meetingId, async () => {
        const request = requestRuntime.requests.begin(`dinner-assignments:${meetingId}`);
        try {
          const assignments = unwrapApi(
            await http.get<DinnerAssignmentDto[]>("/admin/dinner", {
              params: { meetingId: backendId(meetingId) },
              signal: request.signal
            })
          );
          if (!requestRuntime.requests.isCurrent(request)) return;
          this.dinnerAssignments = [
            ...this.dinnerAssignments.filter((assignment) => assignment.meetingId !== meetingId),
            ...assignments.map(mapDinnerAssignment)
          ];
        } finally {
          requestRuntime.requests.finish(request);
        }
      });
    },
    async loadDinnerTables(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(dinnerTablesLoadPromises, meetingId, async () => {
        const request = requestRuntime.requests.begin(`dinner-tables:${meetingId}`);
        try {
          const tables = unwrapApi(
            await http.get<DinnerTableDto[]>("/admin/dinner/tables", {
              params: { meetingId: backendId(meetingId) },
              signal: request.signal
            })
          );
          if (!requestRuntime.requests.isCurrent(request)) return;
          this.dinnerTables = [
            ...this.dinnerTables.filter((table) => table.meetingId !== meetingId),
            ...tables.map(mapDinnerTable)
          ];
        } finally {
          requestRuntime.requests.finish(request);
        }
      });
    },
    async loadDinnerSettings(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      if (!isBackendMeetingId(meetingId)) {
        const meeting = this.meetings.find((item) => item.id === meetingId);
        return {
          meetingId: 0,
          venue: meeting?.dinnerVenue,
          dinnerTime: meeting?.dinnerTime
        } as DinnerSettingsDto;
      }
      try {
        const settings = unwrapApi(await http.get<DinnerSettingsDto>("/admin/dinner/settings", { params: { meetingId: backendId(meetingId) } }));
        this.patchMeetingDinnerSettings(idOf(settings.meetingId), settings.venue, settings.dinnerTime);
        return settings;
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        const meeting = this.meetings.find((item) => item.id === meetingId);
        return {
          meetingId: Number(meetingId),
          venue: meeting?.dinnerVenue,
          dinnerTime: meeting?.dinnerTime
        } as DinnerSettingsDto;
      }
    },
    async saveDinnerSettings(payload: DinnerSettingsPayload) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      if (!isBackendMeetingId(meetingId)) {
        this.patchMeetingDinnerSettings(meetingId, payload.venue?.trim() || "", payload.dinnerTime?.trim() || "");
        return {
          meetingId: 0,
          venue: payload.venue?.trim() || "",
          dinnerTime: payload.dinnerTime?.trim() || ""
        } as DinnerSettingsDto;
      }
      const request = {
        meetingId: backendId(meetingId),
        venue: blankToUndefined(payload.venue),
        dinnerTime: blankToUndefined(payload.dinnerTime)
      };
      try {
        const settings = unwrapApi(await http.put<DinnerSettingsDto>("/admin/dinner/settings", request));
        this.patchMeetingDinnerSettings(idOf(settings.meetingId), settings.venue, settings.dinnerTime);
        return settings;
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        this.patchMeetingDinnerSettings(meetingId, payload.venue?.trim() || "", payload.dinnerTime?.trim() || "");
        return {
          meetingId: Number(meetingId),
          venue: payload.venue?.trim() || "",
          dinnerTime: payload.dinnerTime?.trim() || ""
        } as DinnerSettingsDto;
      }
    },
    async saveDinnerTable(payload: DinnerTablePayload) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const tableNo = payload.tableNo.trim();
      if (!tableNo) throw new Error("桌号不能为空");
      const request = {
        meetingId: backendId(meetingId),
        tableNo,
        capacity: Math.max(1, Math.floor(payload.capacity || 1)),
        remark: blankToUndefined(payload.remark)
      };
      const saved = payload.id
        ? unwrapApi(await http.put<DinnerTableDto>(`/admin/dinner/tables/${backendId(payload.id)}`, request))
        : unwrapApi(await http.post<DinnerTableDto>("/admin/dinner/tables", request));
      const table = mapDinnerTable(saved);
      this.dinnerTables = [
        ...this.dinnerTables.filter((item) => item.id !== table.id),
        table
      ];
      await this.loadDinnerAssignments(meetingId);
      return table;
    },
    async deleteDinnerTable(tableId: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      await http.delete(`/admin/dinner/tables/${backendId(tableId)}`, { params: { meetingId: backendId(meetingId) } });
      this.dinnerTables = this.dinnerTables.filter((table) => table.id !== tableId);
    },
    async createRoom(payload: CreateRoomPayload) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const roomNo = payload.roomNo.trim();
      if (!roomNo) throw new Error("房间号不能为空");
      const normalizedPayload: CreateRoomPayload = {
        roomNo,
        roomType: blankToUndefined(payload.roomType),
        hotelName: blankToUndefined(payload.hotelName),
        remark: blankToUndefined(payload.remark),
        capacity: normalizeRoomCapacity(payload.capacity),
        status: payload.status || "AVAILABLE"
      };
      if (!isBackendMeetingId(meetingId)) {
        return this.createRoomLocally(normalizedPayload);
      }

      try {
        const request = {
          meetingId: backendId(meetingId),
          ...normalizedPayload
        };
        const created = unwrapApi(await http.post<RoomDto>("/admin/rooms", request));
        const room = mapRoom(created, meetingId);
        this.upsertRoom(room);
        return room;
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        const room = this.createRoomLocally(normalizedPayload);
        return room;
      }
    },
    async loadSyncLogs(
      meetingId?: string,
      options: { page?: number; size?: number; status?: Exclude<SyncLog["status"], "PENDING"> } = {}
    ) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const pageNumber = Math.max(0, options.page || 0);
      const pageSize = Math.max(1, options.size || 20);
      const request = requestRuntime.requests.begin(`sync-logs:${meetingId}`);
      try {
        const page = unwrapApi(
          await http.get<PagePayload<SyncLogDto>>("/admin/tduck/sync-logs", {
            params: {
              meetingId: backendId(meetingId),
              page: pageNumber,
              size: pageSize,
              ...(options.status ? { status: options.status } : {})
            },
            signal: request.signal
          })
        );
        const mapped = {
          ...page,
          content: page.content.map((item) => mapSyncLog(item, meetingId))
        } satisfies PagePayload<SyncLog>;
        if (!requestRuntime.requests.isCurrent(request)) return mapped;
        this.syncLogs = [...this.syncLogs.filter((log) => log.meetingId !== meetingId), ...mapped.content];
        return mapped;
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async loadTduckSyncLogDetail(syncLogId: string, meetingId?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const detail = unwrapApi(
        await http.get<{
          id: number;
          meetingId?: number;
          formKey?: string;
          eventType?: string;
          status: SyncLog["status"];
          message?: string;
          tduckDataId?: string;
          createdAt?: string;
          formValues?: Record<string, unknown>;
        }>(`/admin/tduck/sync-logs/${encodeURIComponent(backendId(syncLogId))}`, {
          params: { meetingId: backendId(targetMeetingId) }
        })
      );
      return {
        id: idOf(detail.id),
        meetingId: idOf(detail.meetingId ?? targetMeetingId),
        formKey: detail.formKey || "",
        eventType: detail.eventType || "",
        status: detail.status,
        message: detail.message,
        dataId: detail.tduckDataId || "",
        createdAt: detail.createdAt || "",
        formValues: detail.formValues && typeof detail.formValues === "object" && !Array.isArray(detail.formValues)
          ? { ...detail.formValues }
          : {}
      } satisfies TduckSyncLogDetailDto;
    },
    async loadTduckSubmissionConflicts(meetingId?: string, options: { page?: number; size?: number } = {}) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const pageNumber = Math.max(0, options.page || 0);
      const pageSize = Math.max(1, options.size || 50);
      const request = requestRuntime.requests.begin(`tduck-submission-conflicts:${targetMeetingId}`);
      try {
        const page = unwrapApi(
          await http.get<PagePayload<TduckSubmissionConflictWireDto>>("/admin/tduck/sync-conflicts", {
            params: { meetingId: backendId(targetMeetingId), page: pageNumber, size: pageSize },
            signal: request.signal
          })
        );
        const mapped = {
          ...page,
          content: page.content.map(mapTduckSubmissionConflict)
        } satisfies PagePayload<TduckSubmissionConflictDto>;
        if (requestRuntime.requests.isCurrent(request)) {
          this.tduckSubmissionConflictsByMeetingId[targetMeetingId] = mapped;
        }
        return mapped;
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async loadTduckSubmissionConflict(submissionId: string | number, meetingId?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const normalizedSubmissionId = idOf(submissionId);
      if (!normalizedSubmissionId) throw new Error("TDuck 重复报名记录不存在");
      const response = unwrapApi(
        await http.get<TduckSubmissionConflictWireDto>(`/admin/tduck/sync-conflicts/${backendId(normalizedSubmissionId)}`, {
          params: { meetingId: backendId(targetMeetingId) }
        })
      );
      const conflict = mapTduckSubmissionConflict(response);
      const cachedPage = this.tduckSubmissionConflictsByMeetingId[targetMeetingId];
      if (cachedPage?.content.some((item) => item.id === conflict.id)) {
        this.tduckSubmissionConflictsByMeetingId[targetMeetingId] = {
          ...cachedPage,
          content: cachedPage.content.map((item) => item.id === conflict.id ? conflict : item)
        };
      }
      return conflict;
    },
    async resolveTduckSubmissionConflict(
      submissionId: string | number,
      meetingId: string,
      resolution: TduckSubmissionResolution,
      note?: string
    ) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const normalizedSubmissionId = idOf(submissionId);
      if (!normalizedSubmissionId) throw new Error("TDuck 重复报名记录不存在");
      const normalizedNote = note?.trim() || undefined;
      const response = unwrapApi(
        await http.post<TduckSubmissionConflictWireDto>(`/admin/tduck/sync-conflicts/${backendId(normalizedSubmissionId)}/resolve`, {
          meetingId: backendId(targetMeetingId),
          resolution,
          note: normalizedNote
        })
      );
      const resolved = mapTduckSubmissionConflict(response);
      const cachedPage = this.tduckSubmissionConflictsByMeetingId[targetMeetingId];
      if (cachedPage) {
        const removed = cachedPage.content.some((item) => item.id === resolved.id);
        const totalElements = Math.max(0, cachedPage.totalElements - (removed ? 1 : 0));
        this.tduckSubmissionConflictsByMeetingId[targetMeetingId] = {
          ...cachedPage,
          content: cachedPage.content.filter((item) => item.id !== resolved.id),
          totalElements,
          totalPages: totalElements ? Math.ceil(totalElements / cachedPage.size) : 0
        };
      }
      return resolved;
    },
    async loadCheckinRecords(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const request = requestRuntime.requests.begin(`checkin-records:${meetingId}`);
      try {
        const page = unwrapApi(
          await http.get<PagePayload<CheckinRecordDto>>("/admin/checkin/records", {
            params: { meetingId: backendId(meetingId), page: 0, size: 50 },
            signal: request.signal
          })
        );
        if (!requestRuntime.requests.isCurrent(request)) return;
        this.checkinRecords = [
          ...this.checkinRecords.filter((record) => record.meetingId !== meetingId),
          ...page.content.map((item) => mapCheckinRecord(item, meetingId, this.attendees))
        ];
      } finally {
        requestRuntime.requests.finish(request);
      }
    },
    async loadStatistics(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return this.loadStatisticsWithRange(meetingId, {});
    },
    async loadStatisticsWithRange(meetingId: string | undefined, filters: { startDate?: string; endDate?: string }) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const requestKey = statisticsRequestKey(meetingId, filters);
      /*
       * 完整 key 同时包含会议和日期范围；latestStatisticsRequests 再记录该会议当前真正期待的 token。
       * 因此 A 范围 -> B 范围 -> A 范围时，第一个 A 即使最后返回，也不能覆盖第三个 A。
       */
      const request = requestRuntime.requests.begin(`statistics:${requestKey}`);
      latestStatisticsRequests.set(meetingId, request);
      // 先切换当前范围指针；若该范围尚无缓存，getter 返回 null，避免新日期标签短暂搭配旧范围数据。
      this.activeStatisticsRequestKeyByMeetingId[meetingId] = requestKey;
      try {
        const overview = unwrapApi(
          await http.get<StatisticsOverviewDto>("/admin/statistics/overview", {
            params: {
              meetingId: backendId(meetingId),
              startDate: filters.startDate || undefined,
              endDate: filters.endDate || undefined
            },
            signal: request.signal
          })
        );
        if (requestRuntime.requests.isCurrent(request) && latestStatisticsRequests.get(meetingId) === request) {
          this.statisticsByRequestKey[requestKey] = overview;
          this.activeStatisticsRequestKeyByMeetingId[meetingId] = requestKey;
        }
        return overview;
      } catch (error) {
        if (isRequestCanceled(error) || !requestRuntime.requests.isCurrent(request)) {
          return this.statisticsByRequestKey[requestKey] || null;
        }
        throw error;
      } finally {
        if (latestStatisticsRequests.get(meetingId) === request) latestStatisticsRequests.delete(meetingId);
        requestRuntime.requests.finish(request);
      }
    },
    async createMeeting(payload: Partial<Meeting>) {
      const request = meetingRequestFrom(payload);
      try {
        const created = unwrapApi(await http.post<MeetingDetailDto["meeting"]>("/admin/meetings", request));
        const meeting = mapMeetingEntity(created);
        this.upsertMeeting(meeting);
        this.setCurrentMeetingId(meeting.id);
        return meeting;
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "会议创建失败"));
        const meeting: Meeting = {
          id: `m-${Date.now()}`,
          code: request.code || localMeetingCode(),
          publicQueryToken: fallbackPublicQueryToken(request.code || payload.name),
          name: request.name,
          organizer: request.organizer,
          venue: request.venueName || "",
          dinnerVenue: request.dinnerVenue || "",
          dinnerTime: request.dinnerTime || "",
          startTime: request.startTime || "",
          endTime: request.endTime || "",
          status: request.status as Meeting["status"],
          capacity: request.capacity,
          registeredCount: 0,
          approvedCount: 0,
          seatedCount: 0,
          roomedCount: 0,
          checkedInCount: 0,
          tduckFormKey: payload.tduckFormKey || "",
          tduckFormName: payload.tduckFormName || "",
          registrationUrl: payload.registrationUrl || "",
          seatQueryEnabled: payload.seatQueryEnabled || false,
          roomQueryEnabled: payload.roomQueryEnabled || false,
          dinnerQueryEnabled: payload.dinnerQueryEnabled || false,
          checkinEnabled: payload.checkinEnabled ?? true,
          selfCheckinEnabled: payload.selfCheckinEnabled ?? true,
          publicQueryFields: payload.publicQueryFields || ["PHONE", "NAME"],
          publicQueryRequiredFields: payload.publicQueryRequiredFields || payload.publicQueryFields || ["PHONE", "NAME"],
          publicQueryMatchMode: payload.publicQueryMatchMode || "FUZZY"
        };
        this.meetings.unshift(meeting);
        this.setCurrentMeetingId(meeting.id);
        return meeting;
      }
    },
    async updateMeeting(meetingId: string, payload: Partial<Meeting>) {
      const current = this.meetings.find((item) => item.id === meetingId);
      if (!current) throw new Error("会议不存在或已被删除");

      const request = meetingRequestFrom(payload, current);

      const saveLocalMeeting = () => {
        // 非数字 ID 来自前端 mock 数据，后端无法识别。这里仅更新当前会话中的本地状态，
        // 与项目已有 demo fallback 行为保持一致，避免开发环境编辑弹窗保存后直接报错。
        const meeting: Meeting = {
          ...current,
          code: current.code || request.code || localMeetingCode(),
          publicQueryToken: current.publicQueryToken || fallbackPublicQueryToken(current.code || request.code),
          name: request.name,
          organizer: request.organizer,
          coOrganizer: request.coOrganizer,
          venue: request.venueName || "",
          dinnerVenue: request.dinnerVenue || "",
          dinnerTime: request.dinnerTime || "",
          startTime: request.startTime || "",
          endTime: request.endTime || "",
          registrationStartTime: request.registrationStartTime || "",
          registrationEndTime: request.registrationEndTime || "",
          capacity: request.capacity,
          description: request.description,
          agenda: request.agenda,
          coverImageUrl: request.coverImageUrl,
          contactName: request.contactName,
          contactPhone: request.contactPhone,
          status: request.status as Meeting["status"],
          seatQueryEnabled: request.seatQueryEnabled,
          roomQueryEnabled: request.roomQueryEnabled,
          dinnerQueryEnabled: request.dinnerQueryEnabled,
          checkinEnabled: request.checkinEnabled,
          selfCheckinEnabled: request.selfCheckinEnabled,
          publicQueryFields: request.publicQueryFields,
          publicQueryRequiredFields: [...request.publicQueryFields],
          publicQueryMatchMode: request.publicQueryMatchMode
        };
        this.upsertMeeting(meeting);
        return meeting;
      };

      if (!isBackendMeetingId(meetingId)) return saveLocalMeeting();

      try {
        const updated = unwrapApi(await http.put<MeetingDetailDto["meeting"]>(`/admin/meetings/${backendId(meetingId)}`, request));
        const meeting = mapMeetingEntity(updated);
        this.upsertMeeting({ ...current, ...meeting });
        if (this.currentMeetingId === meetingId) this.setCurrentMeetingId(meeting.id);
        return { ...current, ...meeting };
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "会议保存失败"));

        // 开发环境后端未启动或当前为 mock 会议时，保持和创建/状态流转一致的演示体验：
        // 只更新前端状态，不伪造后端已持久化，刷新页面后仍会回到 mock 初始数据。
        return saveLocalMeeting();
      }
    },
    async transitionMeeting(meetingId: string, status: Meeting["status"]) {
      try {
        const updated = unwrapApi(await http.post<MeetingDetailDto["meeting"]>(`/admin/meetings/${backendId(meetingId)}/status`, { status }));
        const meeting = mapMeetingEntity(updated);
        this.upsertMeeting({ ...this.meetings.find((item) => item.id === meeting.id), ...meeting } as Meeting);
        await this.loadMeetingDetail(meeting.id);
        return meeting;
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "会议状态更新失败"));
        const meeting = this.meetings.find((item) => item.id === meetingId);
        if (!meeting) throw new Error("会议不存在");
        meeting.status = status;
        return meeting;
      }
    },
    async restartMeeting(meetingId: string) {
      try {
        const updated = unwrapApi(await http.post<MeetingDetailDto["meeting"]>(`/admin/meetings/${backendId(meetingId)}/restart`));
        const meeting = mapMeetingEntity(updated);
        this.upsertMeeting({ ...this.meetings.find((item) => item.id === meeting.id), ...meeting } as Meeting);
        await this.loadMeetingDetail(meeting.id);
        return meeting;
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "会议重新开启失败"));
        const meeting = this.meetings.find((item) => item.id === meetingId);
        if (!meeting) throw new Error("会议不存在");
        meeting.status = DEFAULT_NEW_MEETING_STATUS;
        return meeting;
      }
    },
    async loadTduckBinding(meetingId?: string) {
      const targetMeetingId = meetingId || this.currentMeetingId;
      const meeting = this.meetings.find((item) => item.id === targetMeetingId);
      if (!meeting) return null;
      try {
        const binding = unwrapApi(await http.get<TduckBindingDto>(`/admin/tduck/bindings/${backendId(meeting.id)}`));
        meeting.tduckFormKey = binding.formKey || "";
        meeting.tduckFormName = binding.formName || "";
        meeting.registrationUrl = binding.registrationUrl || "";
        return binding;
      } catch (error) {
        // 本地演示会议使用 m-xxx ID，或后端尚未绑定 TDuck 表单时，不覆盖已有前端演示数据。
        if (isAuthFailure(error)) throw error;
        return null;
      }
    },
    async loadTduckForms(keyword = "", page = 0, size = 20) {
      const forms = unwrapApi(
        await http.get<TduckFormOptionDto[]>("/admin/tduck/forms", {
          params: { keyword, page, size }
        })
      );
      return forms;
    },
    async createTduckForm(meetingId?: string, formName?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return unwrapApi(
        await http.post<TduckFormOptionDto>("/admin/tduck/forms", {
          meetingId: backendId(targetMeetingId),
          formName
        })
      );
    },
    async bindTduckForm(
      formKey: string,
      options: { replaceExisting?: boolean; confirmMeetingCode?: string; changeReason?: string } = {}
    ) {
      const meeting = this.currentMeeting;
      if (!meeting) return;
      try {
        const binding = unwrapApi(
          await http.post<TduckBindingDto>("/admin/tduck/bindings", {
            meetingId: backendId(meeting.id),
            formKey,
            bindingStatus: "BOUND",
            replaceExisting: options.replaceExisting,
            confirmMeetingCode: options.confirmMeetingCode,
            changeReason: options.changeReason
          })
        );
        meeting.tduckFormKey = binding.formKey;
        meeting.tduckFormName = binding.formName;
        meeting.registrationUrl = binding.registrationUrl;
        await this.loadSyncLogs(meeting.id);
        return binding;
      } catch (error) {
        if (canUseDemoFallback(error)) {
          const normalizedFormName = meeting.tduckFormName || `${meeting.name}报名表`;
          meeting.tduckFormKey = formKey;
          meeting.tduckFormName = normalizedFormName;
          meeting.registrationUrl = formKey ? `/tduck/s/${formKey}` : "";
          return {
            formKey,
            formName: normalizedFormName,
            registrationUrl: meeting.registrationUrl,
            validationStatus: "UNKNOWN",
            validationMessage: "当前为本地演示模式，未连接后端校验 TDuck 表单"
          } satisfies TduckBindingDto;
        }
        throw error;
      }
    },
    async updateTduckRegistrationEnabled(meetingId: string, enabled: boolean) {
      const targetMeetingId = requireMeetingId(meetingId);
      const binding = unwrapApi(
        await http.put<TduckBindingDto>(`/admin/tduck/bindings/${backendId(targetMeetingId)}/registration-enabled`, { enabled })
      );
      const meeting = this.meetings.find((item) => item.id === targetMeetingId);
      if (meeting) {
        meeting.tduckFormKey = binding.formKey || "";
        meeting.tduckFormName = binding.formName || "";
        meeting.registrationUrl = binding.registrationUrl || "";
      }
      return binding;
    },
    async loadTduckBrowserSession(meetingId?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return unwrapApi(
        await http.get<TduckBrowserSessionDto>("/admin/tduck/browser-session", {
          params: { meetingId: backendId(targetMeetingId) }
        })
      );
    },
    async syncTduckFields(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return unwrapApi(await http.post<TduckFieldSyncResponseDto>(`/admin/tduck/mappings/${backendId(meetingId)}/sync`));
    },
    async loadTduckMappings(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return unwrapApi(await http.get<TduckFieldMappingDto[]>(`/admin/tduck/mappings/${backendId(meetingId)}`));
    },
    async saveTduckMappings(meetingId: string, mappings: Array<Pick<TduckFieldMappingDto, "attendeeField" | "tduckFieldKey" | "tduckFieldLabel" | "requiredMapping">>) {
      const targetMeetingId = requireMeetingId(meetingId);
      return unwrapApi(
        await http.put<TduckFieldMappingDto[]>(`/admin/tduck/mappings/${backendId(targetMeetingId)}`, {
          mappings
        })
      );
    },
    async pullTduckSync(meetingId: string, dataId: string) {
      const normalizedMeetingId = requireMeetingId(meetingId);
      const normalizedDataId = dataId.trim();
      if (!normalizedDataId) throw new Error("请输入 TDuck 数据 ID");
      const result = unwrapApi(
        await http.post<TduckSyncResultDto>("/admin/tduck/sync/pull", {
          meetingId: backendId(normalizedMeetingId),
          dataId: normalizedDataId
        })
      );
      await Promise.allSettled([
        this.loadAttendees(normalizedMeetingId),
        this.loadSyncLogs(normalizedMeetingId),
        this.loadStatistics(normalizedMeetingId)
      ]);
      return result;
    },
    async pullAllTduckSync(
      meetingId: string,
      options: { signal?: AbortSignal; onUpdate?: (task: TduckBatchSyncResultDto) => void } = {}
    ) {
      const normalizedMeetingId = requireMeetingId(meetingId);
      let result = unwrapApi(
        await http.post<TduckBatchSyncResultDto>("/admin/tduck/sync/pull/all", {
          meetingId: backendId(normalizedMeetingId)
        }, { signal: options.signal })
      );
      if (result.taskId != null) {
        const taskId = String(result.taskId);
        result = await pollFileTask(
          result,
          async () => unwrapApi(
            await http.get<TduckBatchSyncResultDto>(`/admin/tduck/sync/pull/tasks/${encodeURIComponent(taskId)}`, {
              params: { meetingId: backendId(normalizedMeetingId) },
              signal: options.signal
            })
          ),
          new Set(["COMPLETED", "FAILED"]),
          {
            signal: options.signal,
            intervalMs: 1_000,
            maxIntervalMs: 3_000,
            backoffFactor: 1.5,
            timeoutMs: 2 * 60 * 60_000,
            resetBackoffWhen: (previous, current) =>
              (current.scanned ?? 0) > (previous.scanned ?? 0) || (current.progress ?? 0) > (previous.progress ?? 0),
            onUpdate: options.onUpdate
          }
        );
      }
      await Promise.allSettled([
        this.loadAttendees(normalizedMeetingId),
        this.loadSyncLogs(normalizedMeetingId),
        this.loadStatistics(normalizedMeetingId)
      ]);
      if (result.status === "FAILED") throw new Error(result.message || "一键同步失败");
      return result;
    },
    async auditAttendees(ids: string[], status: Attendee["auditStatus"], reason = "") {
      const auditedAt = nowIso();
      try {
        await http.put("/admin/attendees/batch-review", {
          reviewStatus: status,
          reason,
          attendeeIds: ids.map(backendId)
        });
        await this.loadAttendees(this.currentMeetingId);
      } catch (error) {
        if (!canUseDemoFallback(error)) {
          throw error;
        }
        this.attendees = this.attendees.map((attendee) =>
          ids.includes(attendee.id)
            ? {
                ...attendee,
                auditStatus: status,
                auditReason: reason,
                auditedAt
              }
            : attendee
        );
      }
      this.refreshMeetingCounters();
    },
    async updateAttendeeTags(attendeeId: string, tags: string[]) {
      const normalizedTags = normalizeTags(tags);
      try {
        const updated = unwrapApi(
          await http.put<AttendeeDto>(`/admin/attendees/${backendId(attendeeId)}/tags`, {
            tags: normalizedTags.join(",")
          })
        );
        const attendee = this.attendees.find((item) => item.id === attendeeId);
        this.attendees = this.attendees.map((item) =>
          item.id === attendeeId
            ? {
                ...item,
                ...mapAttendee(updated, attendee?.meetingId || this.currentMeetingId),
                // 标签接口返回的实体可能暂时缺少排座/住宿展示字段，这里保留前端已合并出的展示信息。
                seatLabel: attendee?.seatLabel,
                roomId: attendee?.roomId,
                seatAssigned: attendee?.seatAssigned,
                roomAssigned: attendee?.roomAssigned,
                dinnerAssigned: attendee?.dinnerAssigned,
                seatQueried: attendee?.seatQueried,
                roomQueried: attendee?.roomQueried,
                dinnerQueried: attendee?.dinnerQueried
              }
            : item
        );
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "标签保存失败"));
        // 本地演示或后端接口尚未就绪时仍允许维护标签，保证管理入口可用。
        this.attendees = this.attendees.map((attendee) => (attendee.id === attendeeId ? { ...attendee, tags: normalizedTags } : attendee));
      }
    },
    async updateAttendeeRemark(attendeeId: string, remark: string) {
      const normalizedRemark = remark.trim();
      try {
        const updated = unwrapApi(
          await http.put<AttendeeDto>(`/admin/attendees/${backendId(attendeeId)}/remark`, {
            remark: normalizedRemark || null
          })
        );
        const attendee = this.attendees.find((item) => item.id === attendeeId);
        this.attendees = this.attendees.map((item) =>
          item.id === attendeeId
            ? {
                ...item,
                ...mapAttendee(updated, attendee?.meetingId || this.currentMeetingId),
                /*
                 * 专用备注接口只负责 Attendee 实体，不重新聚合排座、住宿、晚宴和查询记录。
                 * 保留列表已加载的聚合状态，避免保存备注后这些列瞬间变成“未分配/未查询”。
                 */
                seatLabel: attendee?.seatLabel,
                roomId: attendee?.roomId,
                seatAssigned: attendee?.seatAssigned,
                roomAssigned: attendee?.roomAssigned,
                dinnerAssigned: attendee?.dinnerAssigned,
                seatQueried: attendee?.seatQueried,
                roomQueried: attendee?.roomQueried,
                dinnerQueried: attendee?.dinnerQueried
              }
            : item
        );
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "备注保存失败"));
        // 本地演示模式同样支持备注录入和清空，行为与真实接口保持一致。
        this.attendees = this.attendees.map((attendee) =>
          attendee.id === attendeeId ? { ...attendee, remark: normalizedRemark || undefined } : attendee
        );
      }
    },
    async autoAssignSeats() {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.post(`/admin/seating/auto-assign/${backendId(meetingId)}`);
        await Promise.allSettled([this.loadSeats(meetingId), this.loadAttendees(meetingId)]);
        this.reconcileAssignments();
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.autoAssignSeatsLocally();
      }
      this.refreshMeetingCounters();
    },
    async assignSeat(attendeeId: string, seatId: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.post("/admin/seating/manual-assign", { attendeeId: backendId(attendeeId), seatId: backendId(seatId) });
        await Promise.allSettled([this.loadSeats(meetingId), this.loadAttendees(meetingId)]);
        this.reconcileAssignments();
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.assignSeatLocally(attendeeId, seatId);
      }
      this.refreshMeetingCounters();
    },
    async clearSeat(attendeeId: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.post(`/admin/seating/clear/${backendId(attendeeId)}`);
        await Promise.allSettled([this.loadSeats(meetingId), this.loadAttendees(meetingId)]);
        this.reconcileAssignments();
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.clearSeatLocally(attendeeId);
      }
      this.refreshMeetingCounters();
    },
    async swapSeats(firstAttendeeId: string, secondAttendeeId: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.post("/admin/seating/swap", {
          firstAttendeeId: backendId(firstAttendeeId),
          secondAttendeeId: backendId(secondAttendeeId)
        });
        await Promise.allSettled([this.loadSeats(meetingId), this.loadAttendees(meetingId)]);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.swapSeatsLocally(firstAttendeeId, secondAttendeeId);
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async setSeatLocked(seatId: string, locked: boolean) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.put(`/admin/seating/seats/${backendId(seatId)}/lock`, { locked });
        await this.loadSeats(meetingId);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.setSeatLockedLocally(seatId, locked);
      }
      this.reconcileAssignments();
    },
    async updateSeatType(seatId: string, seatType: Seat["seatType"]) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.put(`/admin/seating/seats/${backendId(seatId)}/type`, { seatType });
        await this.loadSeats(meetingId);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        this.updateSeatTypeLocally(seatId, seatType);
      }
      this.reconcileAssignments();
    },
    async setSeatDisabled(seatId: string, disabled: boolean) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        await http.put(`/admin/seating/seats/${backendId(seatId)}/disabled`, { disabled });
        await Promise.allSettled([this.loadSeats(meetingId), this.loadStatistics(meetingId)]);
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        this.setSeatDisabledLocally(seatId, disabled);
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async assignRoom(attendeeId: string, roomId: string, roomRemark?: string) {
      const room = this.rooms.find((item) => item.id === roomId);
      if (!room) return;
      const attendee = this.attendees.find((item) => item.id === attendeeId);
      if (!attendee) return;
      if (room.status === "DISABLED") {
        throw new Error("停用房间不能分配入住人员");
      }
      const occupants = this.attendees.filter((item) => item.roomId === room.id || item.roomNo === room.roomNo);
      if (!occupants.some((item) => item.id === attendeeId) && occupants.length >= normalizeRoomCapacity(room.capacity)) {
        throw new Error("该房间已达到可入住人数上限");
      }
      const normalizedRoomRemark = roomRemark?.trim() || room.checkinTips || room.remark || "";
      try {
        await http.post("/admin/rooms/assign", {
          attendeeId: backendId(attendeeId),
          roomNo: room.roomNo,
          roomRemark: normalizedRoomRemark
        });
        await Promise.allSettled([this.loadAttendees(this.currentMeetingId), this.loadRooms(this.currentMeetingId)]);
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        attendee.roomNo = room.roomNo;
        attendee.roomId = room.id;
        attendee.roomRemark = normalizedRoomRemark;
        attendee.roomAssigned = true;
        room.status = "ASSIGNED";
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async clearRoomAssignment(attendeeId: string) {
      const attendee = this.attendees.find((item) => item.id === attendeeId);
      if (!attendee) return;
      const room = this.rooms.find((item) => item.id === attendee.roomId || item.roomNo === attendee.roomNo);
      try {
        await http.post("/admin/rooms/clear", undefined, { params: { attendeeId: backendId(attendeeId) } });
        await Promise.allSettled([this.loadAttendees(this.currentMeetingId), this.loadRooms(this.currentMeetingId)]);
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        attendee.roomNo = undefined;
        attendee.roomId = undefined;
        attendee.roomRemark = undefined;
        attendee.roomAssigned = false;
        if (room && !this.attendees.some((item) => item.id !== attendeeId && (item.roomId === room.id || item.roomNo === room.roomNo))) {
          room.status = "AVAILABLE";
        }
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async assignDinner(payload: DinnerAssignPayload) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const attendee = this.attendees.find((item) => item.id === payload.attendeeId);
      if (!attendee) throw new Error("参会人不存在");
      const tableNo = payload.tableNo.trim();
      if (!tableNo) throw new Error("请填写晚宴桌号");
      const request = {
        attendeeId: backendId(payload.attendeeId),
        tableNo,
        remark: blankToUndefined(payload.remark)
      };
      try {
        const saved = unwrapApi(await http.post<DinnerAssignmentDto>("/admin/dinner/assign", request));
        const assignment = mapDinnerAssignment(saved);
        this.dinnerAssignments = [
          ...this.dinnerAssignments.filter((item) => !(item.meetingId === assignment.meetingId && item.attendeeId === assignment.attendeeId)),
          assignment
        ];
        attendee.dinnerAssigned = true;
        await this.loadDinnerTables(meetingId);
        return assignment;
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        const assignment: DinnerAssignment = {
          id: `local-dinner-${Date.now()}`,
          meetingId,
          attendeeId: attendee.id,
          attendeeName: attendee.name,
          attendeePhone: attendee.phone,
          organization: attendee.company,
          title: attendee.title,
          attendeeType: attendee.attendeeType,
          tableNo,
          remark: payload.remark?.trim() || "",
          assignedBy: "local"
        };
        this.dinnerAssignments = [
          ...this.dinnerAssignments.filter((item) => !(item.meetingId === meetingId && item.attendeeId === attendee.id)),
          assignment
        ];
        attendee.dinnerAssigned = true;
        return assignment;
      }
    },
    async clearDinnerAssignment(attendeeId: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const attendee = this.attendees.find((item) => item.id === attendeeId);
      try {
        await http.post("/admin/dinner/clear", undefined, { params: { attendeeId: backendId(attendeeId) } });
        this.dinnerAssignments = this.dinnerAssignments.filter((item) => !(item.meetingId === meetingId && item.attendeeId === attendeeId));
        if (attendee) attendee.dinnerAssigned = false;
        await this.loadDinnerTables(meetingId);
      } catch (error) {
        if (this.apiAvailable || !canUseDemoFallback(error)) throw error;
        this.dinnerAssignments = this.dinnerAssignments.filter((item) => !(item.meetingId === meetingId && item.attendeeId === attendeeId));
        if (attendee) attendee.dinnerAssigned = false;
      }
    },
    async preflightImportTask(
      type: ImportTaskType,
      file: File,
      idempotencyKey: string,
      options: { meetingId?: string; signal?: AbortSignal; onProgress?: (percent: number) => void } = {}
    ) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      const form = new FormData();
      form.append("file", file);
      try {
        const task = unwrapApi(
          await http.post<ImportTaskWireDto>("/admin/imports", form, {
            params: {
              meetingId: backendId(meetingId),
              type,
              preflight: true,
              idempotencyKey
            },
            signal: options.signal,
            timeout: 120_000,
            onUploadProgress: (event) => {
              const total = event.total || file.size;
              if (total > 0) options.onProgress?.(Math.min(100, Math.round((event.loaded / total) * 100)));
            }
          })
        );
        return mapImportTask(task, meetingId, type, file.name);
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "导入文件格式检查失败"));
      }
    },
    async loadImportTasks(options: { meetingId?: string; page?: number; size?: number; signal?: AbortSignal } = {}) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const page = unwrapApi(
          await http.get<PagePayload<ImportTaskWireDto>>("/admin/imports", {
            params: {
              meetingId: backendId(meetingId),
              page: options.page ?? 0,
              size: options.size ?? 20
            },
            signal: options.signal
          })
        );
        return {
          ...page,
          content: page.content.map((item) => mapImportTask(item, meetingId))
        } satisfies PagePayload<ImportTaskDto>;
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "导入任务加载失败"));
      }
    },
    async loadImportTask(taskId: string, options: { meetingId?: string; signal?: AbortSignal } = {}) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const task = unwrapApi(
          await http.get<ImportTaskWireDto>(`/admin/imports/${encodeURIComponent(taskId)}`, {
            params: { meetingId: backendId(meetingId) },
            signal: options.signal
          })
        );
        return mapImportTask(task, meetingId);
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "导入任务状态加载失败"));
      }
    },
    async commitImportTask(taskId: string, options: { meetingId?: string; signal?: AbortSignal } = {}) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const task = unwrapApi(
          await http.post<ImportTaskWireDto>(`/admin/imports/${encodeURIComponent(taskId)}/commit`, null, {
            params: { meetingId: backendId(meetingId) },
            signal: options.signal
          })
        );
        return mapImportTask(task, meetingId);
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "正式导入启动失败"));
      }
    },
    async downloadImportFailures(taskId: string, options: { meetingId?: string; signal?: AbortSignal } = {}) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const response = await http.get<Blob>(`/admin/imports/${encodeURIComponent(taskId)}/failures`, {
          params: { meetingId: backendId(meetingId) },
          responseType: "blob",
          timeout: 120_000,
          signal: options.signal
        });
        downloadBlob(
          response.data,
          filenameFromDisposition(response.headers["content-disposition"], `meetingos-import-${taskId}-failures.xlsx`)
        );
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(await downloadErrorMessage(error, "失败明细下载失败"));
      }
    },
    async createExportTask(
      type: string,
      options: { meetingId?: string; startDate?: string; endDate?: string; signal?: AbortSignal } = {}
    ) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const task = unwrapApi(
          await http.post<ExportTaskWireDto>("/admin/exports", null, {
            params: {
              meetingId: backendId(meetingId),
              type,
              format: "xlsx",
              startDate: options.startDate || undefined,
              endDate: options.endDate || undefined
            },
            signal: options.signal
          })
        );
        return mapExportTask(task);
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "导出任务创建失败"));
      }
    },
    async loadExportTask(taskId: string, options: { meetingId?: string; signal?: AbortSignal } = {}) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const task = unwrapApi(
          await http.get<ExportTaskWireDto>(`/admin/exports/${encodeURIComponent(taskId)}`, {
            params: { meetingId: backendId(meetingId) },
            signal: options.signal
          })
        );
        return mapExportTask(task);
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "导出任务状态加载失败"));
      }
    },
    async loadExportTasks(options: { meetingId?: string; page?: number; size?: number; signal?: AbortSignal } = {}) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const page = unwrapApi(
          await http.get<PagePayload<ExportTaskWireDto>>("/admin/exports", {
            params: {
              meetingId: backendId(meetingId),
              page: options.page || 0,
              size: options.size || 20
            },
            signal: options.signal
          })
        );
        return { ...page, content: page.content.map(mapExportTask) } satisfies PagePayload<ExportTaskDto>;
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(extractErrorMessage(error, "导出任务加载失败"));
      }
    },
    async downloadExportTask(
      taskId: string,
      options: { meetingId?: string; filename?: string; signal?: AbortSignal } = {}
    ) {
      const meetingId = requireMeetingId(options.meetingId || this.currentMeetingId);
      try {
        const response = await http.get<Blob>(`/admin/exports/${encodeURIComponent(taskId)}/download`, {
          params: { meetingId: backendId(meetingId) },
          responseType: "blob",
          timeout: 120_000,
          signal: options.signal
        });
        downloadBlob(
          response.data,
          filenameFromDisposition(response.headers["content-disposition"], options.filename || `meetingos-export-${taskId}.xlsx`)
        );
      } catch (error) {
        if (isRequestCanceled(error)) throw error;
        throw new Error(await downloadErrorMessage(error, "导出文件下载失败"));
      }
    },
    async uploadImageAsset(file: File) {
      const form = new FormData();
      form.append("file", file);
      try {
        return unwrapApi(await http.post<AssetUploadDto>("/admin/assets/images", form));
      } catch (error) {
        if (canUseDemoFallback(error)) {
          throw new Error("当前后端未启动，无法上传图片。请启动后端后重试，或先手动填写图片 URL。");
        }
        throw new Error(extractErrorMessage(error, "图片上传失败"));
      }
    },
    async loadMeetingPrintSettings(meetingId?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      try {
        const response = unwrapApi(
          await http.get<{
            meetingId: number;
            nameplateTemplateUrl?: string | null;
            nameplateFields?: MeetingPrintFieldSettingsDto[] | null;
          }>(`/admin/meetings/${backendId(targetMeetingId)}/print-settings`)
        );
        return {
          meetingId: idOf(response.meetingId),
          nameplateTemplateUrl: response.nameplateTemplateUrl || "",
          nameplateFields: response.nameplateFields || []
        } satisfies MeetingPrintSettingsDto;
      } catch (error) {
        if (canUseDemoFallback(error)) {
          return { meetingId: targetMeetingId, nameplateTemplateUrl: "", nameplateFields: [] } satisfies MeetingPrintSettingsDto;
        }
        throw new Error(extractErrorMessage(error, "打印设置加载失败"));
      }
    },
    async saveMeetingPrintSettings(meetingId: string, settings: Pick<MeetingPrintSettingsDto, "nameplateTemplateUrl" | "nameplateFields">) {
      const targetMeetingId = requireMeetingId(meetingId);
      try {
        const response = unwrapApi(
          await http.put<{
            meetingId: number;
            nameplateTemplateUrl?: string | null;
            nameplateFields?: MeetingPrintFieldSettingsDto[] | null;
          }>(`/admin/meetings/${backendId(targetMeetingId)}/print-settings`, {
            nameplateTemplateUrl: settings.nameplateTemplateUrl || null,
            nameplateFields: settings.nameplateFields || []
          })
        );
        return {
          meetingId: idOf(response.meetingId),
          nameplateTemplateUrl: response.nameplateTemplateUrl || "",
          nameplateFields: response.nameplateFields || []
        } satisfies MeetingPrintSettingsDto;
      } catch (error) {
        if (canUseDemoFallback(error)) {
          return {
            meetingId: targetMeetingId,
            nameplateTemplateUrl: settings.nameplateTemplateUrl || "",
            nameplateFields: settings.nameplateFields || []
          } satisfies MeetingPrintSettingsDto;
        }
        throw new Error(extractErrorMessage(error, "打印设置保存失败"));
      }
    },
    async checkinByCode(input: string, entrance = "A入口", options: { checkinType?: "SCAN" | "SEARCH" | "MANUAL" | "SUPPLEMENT"; remark?: string } = {}) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        const isPhone = /^\+?\d{6,20}$/.test(input);
        const isQrLike = input.startsWith("MEETINGOS") || input.startsWith("http://") || input.startsWith("https://") || input.startsWith("/");
        const checkinType = options.checkinType || (isPhone ? "MANUAL" : isQrLike ? "SCAN" : "SEARCH");
        const response = unwrapApi(
          await http.post<{
            status: string;
            attendeeId?: number;
            attendeeName?: string;
            phone?: string;
            checkedInAt?: string;
            entrance?: string;
            operator?: string;
            message?: string;
          }>("/admin/checkin", {
            meetingId: backendId(meetingId),
            qrCode: isQrLike ? input : null,
            phone: isPhone ? input : null,
            name: !isPhone && !isQrLike ? input : null,
            entrance,
            checkinType,
            remark: options.remark || null
          })
        );
        if (response.status !== "SUCCESS") {
          throw new Error(response.message || "签到失败");
        }
        const attendee =
          this.attendees.find((item) => item.meetingId === meetingId && item.id === idOf(response.attendeeId)) ||
          (response.phone
            ? this.attendees.find((item) => item.meetingId === meetingId && item.phone === response.phone)
            : undefined);
        if (attendee) {
          attendee.checkinStatus = "CHECKED_IN";
          attendee.checkinTime = response.checkedInAt || nowIso();
          this.checkinRecords.unshift({
            id: `c-${Date.now()}`,
            meetingId: attendee.meetingId,
            attendeeId: attendee.id,
            attendeeName: attendee.name,
            method: checkinType === "SUPPLEMENT" ? "SUPPLEMENT" : isPhone ? "MANUAL" : isQrLike ? "SCAN_CODE" : "SEARCH",
            entrance: response.entrance || entrance,
            operator: response.operator || "当前用户",
            checkinTime: attendee.checkinTime
          });
          this.refreshMeetingCounters();
          return attendee;
        }
        await this.loadMeetingWorkspace(meetingId);
        const loadedAttendee =
          this.attendees.find((item) => item.meetingId === meetingId && item.id === idOf(response.attendeeId)) ||
          (response.phone
            ? this.attendees.find((item) => item.meetingId === meetingId && item.phone === response.phone)
            : undefined);
        if (!loadedAttendee) throw new Error("签到成功，但参会人数据刷新失败，请重新加载页面");
        return loadedAttendee;
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "签到失败"));
        return this.checkinByCodeLocally(input, entrance, error, options);
      }
    },
    async loadAdminUsers() {
      try {
        // 系统设置页需要完整判断“最后一个启用的超级管理员”，必须读取全部分页。
        return await loadAllPageContent(async (page) => {
          const users = unwrapApi(await http.get<AdminUserDto[] | PagePayload<AdminUserDto>>("/admin/users", {
            params: { page, size: 200 }
          }));
          if (Array.isArray(users)) {
            return {
              content: users,
              page: 0,
              size: users.length || 1,
              totalElements: users.length,
              totalPages: 1
            };
          }
          return users;
        });
      } catch (error) {
        // 仅开发演示模式允许占位数据；生产环境必须暴露加载失败，避免管理员对假数据执行授权。
        if (canUseDemoFallback(error)) return getLocalAdminUsers();
        throw error;
      }
    },
    async loadAuthorizableMeetings() {
      try {
        const meetings = await loadAllPageContent(async (page) =>
          unwrapApi(
            await http.get<PagePayload<MeetingSummaryDto>>("/admin/meetings", {
              params: { page, size: 200 }
            })
          )
        );
        return meetings.map(mapMeetingSummary);
      } catch (error) {
        if (canUseDemoFallback(error)) return [...this.meetings];
        throw error;
      }
    },
    async loadMeetingUserPermissions(filters: { meetingId?: string | number; userId?: string | number } = {}) {
      const meetingId = idOf(filters.meetingId);
      const userId = idOf(filters.userId);
      try {
        const permissions = unwrapApi(
          await http.get<MeetingUserPermissionDto[] | PagePayload<MeetingUserPermissionDto>>("/admin/permissions/meeting-users", {
            params: {
              meetingId: meetingId ? backendId(meetingId) : undefined,
              userId: userId ? backendId(userId) : undefined
            }
          })
        );
        return Array.isArray(permissions) ? permissions : permissions.content;
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        const fallbackMeetingId = meetingId || this.currentMeetingId;
        if (!fallbackMeetingId) return [];
        const permissions = getLocalMeetingUserPermissions(fallbackMeetingId);
        return userId ? permissions.filter((permission) => String(permission.userId) === userId) : permissions;
      }
    },
    async createAdminUser(payload: CreateAdminUserPayload) {
      const user = unwrapApi(await http.post<AdminUserDto>("/admin/users", payload));
      return user;
    },
    async updateAdminUser(userId: string | number, payload: UpdateAdminUserPayload) {
      const user = unwrapApi(await http.put<AdminUserDto>(`/admin/users/${backendId(idOf(userId))}`, payload));
      return user;
    },
    async resetAdminUserPassword(userId: string | number, password: string) {
      return unwrapApi(await http.post<AdminUserDto>(`/admin/users/${backendId(idOf(userId))}/reset-password`, { password }));
    },
    async grantMeetingUserPermission(payload: GrantMeetingPermissionPayload) {
      return unwrapApi(
        await http.post<MeetingUserPermissionDto>("/admin/permissions/meeting-users", {
          meetingId: backendId(idOf(payload.meetingId)),
          userId: backendId(idOf(payload.userId)),
          role: payload.role
        })
      );
    },
    async deleteMeetingUserPermission(permissionId: string | number) {
      await http.delete(`/admin/permissions/meeting-users/${backendId(idOf(permissionId))}`);
    },
    async loadSystemSettings() {
      const settings = normalizeSystemSettings(unwrapApi(await http.get<SystemSettingsDto>("/admin/settings")));
      this.branding = normalizeBranding(settings);
      applyDocumentBranding(this.branding);
      return settings;
    },
    async saveSystemSettings(payload: SystemSettingsDto) {
      const settings = normalizeSystemSettings(unwrapApi(await http.put<SystemSettingsDto>("/admin/settings", payload)));
      this.branding = normalizeBranding(settings);
      applyDocumentBranding(this.branding);
      return settings;
    },
    filterAttendeesLocally(
      filters: { keyword?: string; auditStatus?: string | null; hasSeat?: boolean | null; tags?: string[] },
      meetingId?: string
    ) {
      const targetMeetingId = meetingId || this.currentMeetingId;
      const keyword = filters.keyword?.trim();
      const tags = filters.tags?.filter(Boolean) || [];
      return this.attendees.filter((item) => {
        if (item.meetingId !== targetMeetingId) return false;
        const text = `${item.name}${item.phone}${item.company}`;
        const tagMatched = !tags.length || tags.every((tag) => item.tags.includes(tag));
        const seatMatched = filters.hasSeat == null || Boolean(item.seatLabel) === filters.hasSeat;
        return (
          (!keyword || text.includes(keyword)) &&
          (!filters.auditStatus || item.auditStatus === filters.auditStatus) &&
          seatMatched &&
          tagMatched
        );
      });
    },
    reconcileAssignments() {
      const currentMeetingId = this.currentMeetingId;
      const currentSeats = this.currentSeats;
      const currentRooms = this.currentRooms;
      const currentDinnerAssignments = this.currentDinnerAssignments;
      const seatLabelByAttendee = new Map<string, string>();
      const hasLoadedSeatsForMeeting = currentSeats.length > 0;
      for (const seat of currentSeats) {
        if (seat.attendeeId) {
          seatLabelByAttendee.set(seat.attendeeId, `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座`);
        }
      }
      const roomByNo = new Map(currentRooms.map((room) => [room.roomNo, room]));
      const dinnerAssignedAttendeeIds = new Set(currentDinnerAssignments.map((assignment) => assignment.attendeeId));
      this.attendees = this.attendees.map((attendee) => {
        if (attendee.meetingId !== currentMeetingId) return attendee;
        const room = attendee.roomNo ? roomByNo.get(attendee.roomNo) : undefined;
        const seatLabel = hasLoadedSeatsForMeeting ? seatLabelByAttendee.get(attendee.id) : attendee.seatLabel;
        return {
          ...attendee,
          // 参会人接口会直接返回 seatLabel；只有当前会议座位图已加载时，才用座位图结果覆盖它。
          // 否则首次进入参会人管理页时会因为 currentSeats 为空，把真实座位号误清成空值。
          seatLabel,
          seatAssigned: hasLoadedSeatsForMeeting ? seatLabelByAttendee.has(attendee.id) : attendee.seatAssigned || Boolean(seatLabel),
          roomAssigned: Boolean(attendee.roomNo),
          dinnerAssigned: dinnerAssignedAttendeeIds.has(attendee.id) || Boolean(attendee.dinnerAssigned),
          roomId: room?.id || attendee.roomId
        };
      });
    },
    refreshMeetingCounters() {
      const attendeesByMeetingId = this.attendeesByMeetingId;
      const loadedMeetingIds = new Set(this.loadedAttendeeMeetingIds);
      for (const meeting of this.meetings) {
        const attendees = attendeesByMeetingId.get(meeting.id) || [];
        updateMeetingCounters(meeting, attendees, loadedMeetingIds.has(meeting.id));
      }
    },
    upsertMeeting(meeting: Meeting) {
      const index = this.meetings.findIndex((item) => item.id === meeting.id);
      if (index >= 0) this.meetings[index] = { ...this.meetings[index], ...meeting };
      else this.meetings.unshift(meeting);
    },
    patchMeetingDinnerSettings(meetingId: string, dinnerVenue?: string, dinnerTime?: string) {
      const meeting = this.meetings.find((item) => item.id === meetingId);
      if (!meeting) return;
      meeting.dinnerVenue = dinnerVenue || "";
      meeting.dinnerTime = dinnerTime || "";
    },
    upsertRoom(room: Room) {
      const index = this.rooms.findIndex((item) => item.id === room.id);
      if (index >= 0) this.rooms[index] = { ...this.rooms[index], ...room };
      else this.rooms.unshift(room);
    },
    createRoomLocally(payload: CreateRoomPayload) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const roomNo = payload.roomNo.trim();
      if (this.rooms.some((room) => room.meetingId === meetingId && room.roomNo === roomNo)) {
        throw new Error(`房间号已存在: ${roomNo}`);
      }
      const room = mapRoom(
        {
          id: Date.now(),
          roomNo,
          roomType: payload.roomType,
          hotelName: payload.hotelName,
          remark: payload.remark,
          capacity: payload.capacity,
          status: payload.status || "AVAILABLE"
        },
        meetingId
      );
      room.id = `local-room-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      this.upsertRoom(room);
      return room;
    },
    autoAssignSeatsLocally() {
      const typePriority = ["VIP", "GUEST", "MEDIA", "STAFF", "NORMAL"];
      const availableSeats = this.seats
        .filter((seat) => seat.meetingId === this.currentMeetingId && seat.status === "EMPTY" && !seat.locked)
        .sort((a, b) => a.rowNo - b.rowNo || a.seatNo - b.seatNo);

      const waiting = this.currentAttendees
        .filter((attendee) => attendee.auditStatus === "APPROVED" && !attendee.seatLabel)
        .sort((a, b) => {
          const typeDiff = typePriority.indexOf(a.attendeeType) - typePriority.indexOf(b.attendeeType);
          if (typeDiff !== 0) return typeDiff;
          return new Date(a.auditedAt || a.registeredAt).getTime() - new Date(b.auditedAt || b.registeredAt).getTime();
        });

      // 本地回退仅用于无后端演示。真实排座由后端事务和唯一约束兜底，避免座位/参会人重复占用。
      for (const attendee of waiting) {
        const seatIndex = availableSeats.findIndex((seat) => {
          if (attendee.attendeeType === "GUEST") return seat.seatType === "GUEST" || seat.seatType === "VIP";
          return seat.seatType === attendee.attendeeType || seat.seatType === "NORMAL";
        });
        const fallbackIndex = seatIndex >= 0 ? seatIndex : 0;
        const seat = availableSeats.splice(fallbackIndex, 1)[0];
        if (!seat) break;
        seat.status = "ASSIGNED";
        seat.attendeeId = attendee.id;
        seat.attendeeName = attendee.name;
        seat.attendeeTags = attendee.tags.join(",");
        attendee.seatLabel = `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座`;
        attendee.seatAssigned = true;
      }
    },
    assignSeatLocally(attendeeId: string, seatId: string) {
      const attendee = this.attendees.find((item) => item.id === attendeeId);
      const seat = this.seats.find((item) => item.id === seatId);
      if (!attendee || !seat || seat.locked || seat.status === "DISABLED") return;

      // 手动调座必须先释放旧座位，避免一个参会人占多个座位。
      for (const oldSeat of this.seats.filter((item) => item.attendeeId === attendeeId)) {
        oldSeat.status = "EMPTY";
        oldSeat.attendeeId = undefined;
      }
      seat.status = "ASSIGNED";
      seat.attendeeId = attendeeId;
      seat.attendeeName = attendee.name;
      seat.attendeeTags = attendee.tags.join(",");
      attendee.seatLabel = `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座`;
      attendee.seatAssigned = true;
    },
    clearSeatLocally(attendeeId: string) {
      const attendee = this.attendees.find((item) => item.id === attendeeId);
      for (const seat of this.seats.filter((item) => item.attendeeId === attendeeId)) {
        seat.status = "EMPTY";
        seat.attendeeId = undefined;
        seat.attendeeName = undefined;
        seat.attendeeTags = undefined;
      }
      if (attendee) {
        attendee.seatLabel = undefined;
        attendee.seatAssigned = false;
      }
    },
    swapSeatsLocally(firstAttendeeId: string, secondAttendeeId: string) {
      if (firstAttendeeId === secondAttendeeId) return;
      const firstSeat = this.seats.find((item) => item.attendeeId === firstAttendeeId);
      const secondSeat = this.seats.find((item) => item.attendeeId === secondAttendeeId);
      const firstAttendee = this.attendees.find((item) => item.id === firstAttendeeId);
      const secondAttendee = this.attendees.find((item) => item.id === secondAttendeeId);
      if (!firstSeat || !secondSeat || !firstAttendee || !secondAttendee) {
        throw new Error("交换座位需要两个已分配座位的参会人");
      }
      if (firstSeat.locked || secondSeat.locked) {
        throw new Error("锁定座位不能参与交换");
      }

      firstSeat.attendeeId = secondAttendee.id;
      firstSeat.attendeeName = secondAttendee.name;
      firstSeat.attendeeTags = secondAttendee.tags.join(",");
      secondSeat.attendeeId = firstAttendee.id;
      secondSeat.attendeeName = firstAttendee.name;
      secondSeat.attendeeTags = firstAttendee.tags.join(",");
      firstAttendee.seatLabel = `${secondSeat.areaName} ${secondSeat.rowNo}排${secondSeat.seatNo}座`;
      secondAttendee.seatLabel = `${firstSeat.areaName} ${firstSeat.rowNo}排${firstSeat.seatNo}座`;
      firstAttendee.seatAssigned = true;
      secondAttendee.seatAssigned = true;
    },
    setSeatLockedLocally(seatId: string, locked: boolean) {
      const seat = this.seats.find((item) => item.id === seatId);
      if (!seat) return;
      if (seat.status === "DISABLED") {
        throw new Error("停用座位不能锁定");
      }
      seat.locked = locked;
    },
    updateSeatTypeLocally(seatId: string, seatType: Seat["seatType"]) {
      const seat = this.seats.find((item) => item.id === seatId);
      if (!seat) return;
      if (seat.status === "DISABLED") {
        throw new Error("停用座位不能修改类型");
      }
      seat.seatType = seatType;
    },
    setSeatDisabledLocally(seatId: string, disabled: boolean) {
      const seat = this.seats.find((item) => item.id === seatId);
      if (!seat) return;
      if (disabled && (seat.attendeeId || seat.status === "ASSIGNED" || seat.status === "CHECKED_IN")) {
        throw new Error("该座位已有人员入座，请先解除座位分配后再停用");
      }
      seat.status = disabled ? "DISABLED" : "EMPTY";
      if (!disabled && seat.seatType === "DISABLED") {
        seat.seatType = "NORMAL";
      }
      seat.locked = disabled ? false : seat.locked;
      seat.attendeeId = undefined;
      seat.attendeeName = undefined;
      seat.attendeeTags = undefined;
    },
    saveSeatLayoutLocally(payload: SeatLayoutPayload) {
      const meetingId = this.currentMeetingId;
      const nextAreaName = payload.areaName.trim() || "A区";
      const lookupAreaName = payload.originalAreaName?.trim() || nextAreaName;
      const renamed = lookupAreaName !== nextAreaName;
      const rowCount = Math.max(1, Math.floor(payload.rowCount));
      const seatsPerRow = Math.max(1, Math.floor(payload.seatsPerRow));
      const nextSeatType = payload.defaultSeatType || "NORMAL";
      const areaSeats = this.seats.filter((seat) => seat.meetingId === meetingId && seat.areaName === lookupAreaName);
      if (renamed && this.seats.some((seat) => seat.meetingId === meetingId && seat.areaName === nextAreaName)) {
        throw new Error("区域名称已存在，请换一个名称");
      }
      const removable = areaSeats.filter((seat) => seat.rowNo > rowCount || seat.seatNo > seatsPerRow);
      if (removable.some((seat) => seat.locked || seat.attendeeId || seat.status === "ASSIGNED" || seat.status === "CHECKED_IN")) {
        throw new Error("缩小座位数量会删除已分配或锁定座位，请先清空这些座位后再调整");
      }

      // 本地演示模式也按“原区域名 -> 新区域名”处理，保证改名不会误建第二个区域。
      this.seats = this.seats
        .filter((seat) => !(seat.meetingId === meetingId && seat.areaName === lookupAreaName && (seat.rowNo > rowCount || seat.seatNo > seatsPerRow)))
        .map((seat) =>
          seat.meetingId === meetingId && seat.areaName === lookupAreaName
            ? {
                ...seat,
                areaName: nextAreaName,
                areaSortOrder: payload.sortOrder,
                seatCode: `${nextAreaName}-${seat.rowNo}-${seat.seatNo}`,
                // 布局弹窗的默认座位类型是区域级批量设置，保存后旧座位也要同步更新。
                seatType: nextSeatType
              }
            : seat
        );
      const existingKeys = new Set(this.seats.filter((seat) => seat.meetingId === meetingId && seat.areaName === nextAreaName).map((seat) => `${seat.rowNo}-${seat.seatNo}`));
      for (let rowNo = 1; rowNo <= rowCount; rowNo++) {
        for (let seatNo = 1; seatNo <= seatsPerRow; seatNo++) {
          const key = `${rowNo}-${seatNo}`;
          if (existingKeys.has(key)) continue;
          this.seats.push({
            id: `local-seat-${Date.now()}-${rowNo}-${seatNo}`,
            meetingId,
            areaName: nextAreaName,
            areaSortOrder: payload.sortOrder,
            rowNo,
            seatNo,
            seatCode: `${nextAreaName}-${rowNo}-${seatNo}`,
            seatType: nextSeatType,
            status: "EMPTY",
            locked: false
          });
        }
      }
    },
    deleteSeatAreaLocally(areaName: string) {
      const meetingId = this.currentMeetingId;
      const areaSeats = this.seats.filter((seat) => seat.meetingId === meetingId && seat.areaName === areaName);
      if (!areaSeats.length) return;
      if (areaSeats.some((seat) => seat.locked || seat.attendeeId || seat.status === "ASSIGNED" || seat.status === "CHECKED_IN")) {
        throw new Error("删除区域会删除已分配或锁定座位，请先清空这些座位后再删除");
      }
      this.seats = this.seats.filter((seat) => !(seat.meetingId === meetingId && seat.areaName === areaName));
    },
    checkinByCodeLocally(input: string, entrance: string, originalError?: unknown, options: { checkinType?: "SCAN" | "SEARCH" | "MANUAL" | "SUPPLEMENT"; remark?: string } = {}) {
      if (options.checkinType === "SUPPLEMENT" && !options.remark?.trim()) {
        throw new Error("补签必须填写原因");
      }
      const targetMeetingId = requireMeetingId(this.currentMeetingId);
      const parsed = parseCheckinCode(input);
      const attendee =
        this.attendees.find((item) => item.attendeeCode === parsed.attendeeCode && item.meetingId === targetMeetingId) ||
        this.attendees.find((item) => item.id === parsed.attendeeCode && item.meetingId === targetMeetingId) ||
        this.attendees.find((item) => item.meetingId === targetMeetingId && (item.phone === input || item.name.includes(input)));
      if (!attendee) {
        if (originalError instanceof Error && this.apiAvailable) throw originalError;
        throw new Error("未找到参会人");
      }
      if (attendee.auditStatus !== "APPROVED") throw new Error("参会人未审核通过，不能签到");
      if (attendee.checkinStatus === "CHECKED_IN") {
        throw new Error(`已签到：${formatDateTimeLabel(attendee.checkinTime, "时间未知")}`);
      }

      const now = nowIso();
      attendee.checkinStatus = "CHECKED_IN";
      attendee.checkinTime = now;
      this.checkinRecords.unshift({
        id: `c-${Date.now()}`,
        meetingId: attendee.meetingId,
        attendeeId: attendee.id,
        attendeeName: attendee.name,
        method: options.checkinType === "SUPPLEMENT" ? "SUPPLEMENT" : input.startsWith("MEETINGOS-CHECKIN") ? "SCAN_CODE" : "SEARCH",
        entrance,
        operator: "超级管理员",
        checkinTime: now
      });
      this.refreshMeetingCounters();
      return attendee;
    }
  }
});

function mapMeetingSummary(item: MeetingSummaryDto): Meeting {
  return {
    id: idOf(item.id),
    code: item.code,
    publicQueryToken: item.publicQueryToken || fallbackPublicQueryToken(item.code),
    name: item.name,
    organizer: "主办方",
    venue: item.location || "",
    dinnerVenue: item.dinnerVenue || "",
    dinnerTime: item.dinnerTime || "",
    startTime: item.startTime || "",
    endTime: item.endTime || "",
    status: item.status,
    capacity: 0,
    registeredCount: item.registrationCount || 0,
    approvedCount: item.approvedCount || 0,
    seatedCount: item.seatedCount || 0,
    roomedCount: item.roomAssignedCount || 0,
    dinneredCount: item.dinnerAssignedCount || 0,
    checkedInCount: item.checkedInCount || 0,
    tduckFormKey: "",
    tduckFormName: "",
    registrationUrl: "",
    seatQueryEnabled: Boolean(item.seatQueryEnabled),
    roomQueryEnabled: Boolean(item.roomQueryEnabled),
    dinnerQueryEnabled: Boolean(item.dinnerQueryEnabled),
    checkinEnabled: item.checkinEnabled ?? true,
    selfCheckinEnabled: item.selfCheckinEnabled ?? true,
    publicQueryFields: parseQueryFields(item.publicQueryFields),
    publicQueryRequiredFields: normalizeRequiredQueryFields(item.publicQueryRequiredFields, parseQueryFields(item.publicQueryFields)),
    publicQueryMatchMode: item.publicQueryMatchMode || "FUZZY"
  };
}

function mapMeetingDetail(item: MeetingDetailDto): Meeting {
  return {
    ...mapMeetingEntity(item.meeting),
    registeredCount: item.registrationCount || 0,
    approvedCount: item.approvedCount || 0,
    seatedCount: item.seatedCount || 0,
    roomedCount: item.roomAssignedCount || 0,
    dinneredCount: item.dinnerAssignedCount || 0,
    checkedInCount: item.checkedInCount || 0
  };
}

function mapMeetingEntity(item: MeetingDetailDto["meeting"]): Meeting {
  return {
    id: idOf(item.id),
    code: item.code,
    publicQueryToken: item.publicQueryToken || fallbackPublicQueryToken(item.code),
    name: item.name,
    organizer: item.organizer || "主办方",
    coOrganizer: item.coOrganizer || "",
    venue: item.venueName || item.location || "",
    dinnerVenue: item.dinnerVenue || "",
    dinnerTime: item.dinnerTime || "",
    startTime: item.startTime || "",
    endTime: item.endTime || "",
    registrationStartTime: item.registrationStartTime || "",
    registrationEndTime: item.registrationEndTime || "",
    status: item.status,
    capacity: item.capacity || 0,
    description: item.description || "",
    agenda: item.agenda || "",
    coverImageUrl: item.coverImageUrl || "",
    contactName: item.contactName || "",
    contactPhone: item.contactPhone || "",
    registeredCount: 0,
    approvedCount: 0,
    seatedCount: 0,
    roomedCount: 0,
    dinneredCount: 0,
    checkedInCount: 0,
    tduckFormKey: "",
    tduckFormName: "",
    registrationUrl: "",
    seatQueryEnabled: Boolean(item.seatQueryEnabled),
    roomQueryEnabled: Boolean(item.roomQueryEnabled),
    dinnerQueryEnabled: Boolean(item.dinnerQueryEnabled),
    checkinEnabled: item.checkinEnabled ?? true,
    selfCheckinEnabled: item.selfCheckinEnabled ?? true,
    publicQueryFields: parseQueryFields(item.publicQueryFields),
    publicQueryRequiredFields: normalizeRequiredQueryFields(item.publicQueryRequiredFields, parseQueryFields(item.publicQueryFields)),
    publicQueryMatchMode: item.publicQueryMatchMode || "FUZZY"
  };
}

function mapAttendee(item: AttendeeDto, meetingId: string): Attendee {
  return {
    id: idOf(item.id),
    meetingId,
    tduckDataId: item.tduckDataId,
    attendeeCode: item.attendeeCode,
    name: item.name,
    phone: item.phone,
    company: item.organization || "",
    title: item.title || "",
    identityNo: item.identityNo,
    email: item.email,
    attendeeType: item.attendeeType || "NORMAL",
    accommodationRequired: item.accommodationRequired,
    remark: item.remark || undefined,
    tags: splitTags(item.tags),
    auditStatus: item.reviewStatus || "PENDING",
    auditReason: item.reviewReason,
    auditedAt: item.reviewedAt,
    registeredAt: item.registeredAt || "",
    roomNo: item.roomNo,
    roomRemark: item.roomRemark,
    seatLabel: item.seatLabel,
    seatAssigned: Boolean(item.seatAssigned || item.seatLabel),
    roomAssigned: Boolean(item.roomAssigned || item.roomNo),
    dinnerAssigned: Boolean(item.dinnerAssigned),
    seatQueried: Boolean(item.seatQueried),
    roomQueried: Boolean(item.roomQueried),
    dinnerQueried: Boolean(item.dinnerQueried),
    checkinStatus: item.checkinStatus || "NOT_CHECKED_IN",
    checkinTime: item.checkedInAt,
    formValues: item.formValues || {}
  };
}

function mapSeat(item: SeatViewDto, meetingId: string): Seat {
  return {
    id: idOf(item.seatId),
    meetingId,
    areaName: item.areaName,
    areaSortOrder: item.areaSortOrder,
    rowNo: item.rowNo,
    seatNo: item.seatNo,
    seatCode: item.seatCode,
    seatType: item.seatType,
    status: item.status,
    locked: item.locked,
    attendeeId: item.attendeeId == null ? undefined : idOf(item.attendeeId),
    attendeeName: item.attendeeName,
    attendeeTags: item.attendeeTags
  };
}

function mapRoom(item: RoomDto, meetingId: string): Room {
  const floorMatch = item.roomNo.match(/^(\d{1,2})/);
  return {
    id: idOf(item.id),
    meetingId,
    roomNo: item.roomNo,
    hotelName: item.hotelName || "会议酒店",
    building: "",
    floorNo: floorMatch?.[1] || "",
    roomType: item.roomType || "标准间",
    capacity: normalizeRoomCapacity(item.capacity),
    checkinTips: item.remark || "",
    remark: item.remark,
    status: item.status || "AVAILABLE"
  };
}

function mapDinnerAssignment(item: DinnerAssignmentDto): DinnerAssignment {
  return {
    id: idOf(item.id),
    meetingId: idOf(item.meetingId),
    attendeeId: idOf(item.attendeeId),
    attendeeName: item.attendeeName,
    attendeePhone: item.attendeePhone,
    organization: item.organization,
    title: item.title,
    attendeeType: item.attendeeType,
    tableNo: item.tableNo,
    remark: item.remark,
    assignedBy: item.assignedBy
  };
}

function mapDinnerTable(item: DinnerTableDto): DinnerTable {
  return {
    id: idOf(item.id),
    meetingId: idOf(item.meetingId),
    tableNo: item.tableNo,
    capacity: Math.max(1, Math.floor(item.capacity || 1)),
    occupiedCount: Math.max(0, Math.floor(item.occupiedCount || 0)),
    remainingCount: Math.max(0, Math.floor(item.remainingCount || 0)),
    overCapacityCount: Math.max(0, Math.floor(item.overCapacityCount || 0)),
    remark: item.remark,
    createdBy: item.createdBy
  };
}

function parseQueryFields(value?: string | PublicQueryField[]) {
  if (Array.isArray(value)) return value.length ? value : (["PHONE", "NAME"] as PublicQueryField[]);
  const fields = (value || "PHONE,NAME")
    .split(",")
    .map((item) => item.trim())
    .filter((item): item is PublicQueryField => defaultQueryFieldOptions.some((option) => option.field === item));
  return fields.length ? fields : (["PHONE", "NAME"] as PublicQueryField[]);
}

function normalizeRequiredQueryFields(value: string | PublicQueryField[] | undefined, fields: PublicQueryField[]) {
  const candidateFields = Array.isArray(value)
    ? value
    : (value || "")
      .split(",")
      .map((item) => item.trim())
      .filter((item): item is PublicQueryField => defaultQueryFieldOptions.some((option) => option.field === item));
  const requiredFields = Array.from(new Set(candidateFields.filter((field) => fields.includes(field))));
  // 缺少新字段的历史接口响应，继续采用原来的“全部查询字段必填”规则。
  return requiredFields.length ? requiredFields : [...fields];
}

function parseResultFields(value?: string | PublicQueryResultField[], fallback: PublicQueryResultField[] = defaultVisibleResultFields) {
  if (Array.isArray(value)) return value.length || fallback.length === 0 ? value : [...fallback];
  const fields = (value || "")
    .split(",")
    .map((item) => item.trim())
    .filter((item): item is PublicQueryResultField => defaultResultFieldOptions.some((option) => option.field === item));
  return fields.length || fallback.length === 0 ? fields : [...fallback];
}

function normalizeResultLayout(value?: string): MeetingQuerySettings["resultLayout"] {
  return value === "LIST" || value === "COMPACT" ? value : "CARD";
}

function seatResultFields(fields: PublicQueryResultField[]) {
  const allowed = new Set<PublicQueryResultField>([
    "ATTENDEE_NAME",
    "ATTENDEE_PHONE",
    "ORGANIZATION",
    "TITLE",
    "EMAIL",
    "MEETING_NAME",
    "SEAT_AREA",
    "SEAT_ROW",
    "SEAT_NO",
    "SEAT_CODE"
  ]);
  return fields.filter((field) => allowed.has(field));
}

function roomResultFields(fields: PublicQueryResultField[]) {
  const allowed = new Set<PublicQueryResultField>([
    "ATTENDEE_NAME",
    "ATTENDEE_PHONE",
    "ORGANIZATION",
    "TITLE",
    "EMAIL",
    "MEETING_NAME",
    "HOTEL_NAME",
    "ROOM_TYPE",
    "ROOM_NO",
    "ROOM_REMARK",
    "CHECKIN_TIPS"
  ]);
  return fields.filter((field) => allowed.has(field));
}

function dinnerResultFields(fields: PublicQueryResultField[]) {
  const allowed = new Set<PublicQueryResultField>([
    "ATTENDEE_NAME",
    "ATTENDEE_PHONE",
    "ORGANIZATION",
    "TITLE",
    "EMAIL",
    "MEETING_NAME",
    "DINNER_TABLE_NO",
    "DINNER_VENUE",
    "DINNER_TIME",
    "DINNER_REMARK"
  ]);
  return fields.filter((field) => allowed.has(field));
}

function mergeResultFields(...groups: PublicQueryResultField[][]) {
  return Array.from(new Set(groups.flat()));
}

function clonePageContents(contents: Record<PublicPageKey, PublicPageContent>): Record<PublicPageKey, PublicPageContent> {
  return publicPageKeys.reduce(
    (result, key) => {
      result[key] = { ...contents[key] };
      return result;
    },
    {} as Record<PublicPageKey, PublicPageContent>
  );
}

function buildDefaultPageContents(base: {
  meetingName: string;
  pageTitle?: string;
  pageDescription?: string;
  announcement?: string;
  publisher?: string;
  buttonText?: string;
  emptyMessage?: string;
  seatNotice?: string;
  roomNotice?: string;
  dinnerNotice?: string;
  resultCheckinEnabled?: boolean;
}): Record<PublicPageKey, PublicPageContent> {
  const title = base.pageTitle || `${base.meetingName}查询`;
  const description = base.pageDescription || "";
  const announcement = base.announcement || "";
  const publisher = base.publisher || "";
  const buttonText = base.buttonText || "立即查询";
  const emptyMessage = base.emptyMessage || "未查询到结果，请核对信息后重试";
  const resultCheckinEnabled = base.resultCheckinEnabled ?? true;
  return {
    entry: { title, description, announcement, publisher },
    combined: { title, description, announcement, publisher, buttonText, emptyMessage, checkinEnabled: resultCheckinEnabled },
    seat: {
      title,
      description,
      announcement,
      publisher,
      buttonText,
      emptyMessage,
      notice: base.seatNotice || defaultSeatNotice,
      checkinEnabled: resultCheckinEnabled
    },
    room: {
      title,
      description,
      announcement,
      publisher,
      buttonText,
      emptyMessage,
      notice: base.roomNotice || defaultRoomNotice,
      checkinEnabled: resultCheckinEnabled
    },
    dinner: {
      title,
      description,
      announcement,
      publisher,
      buttonText,
      emptyMessage,
      notice: base.dinnerNotice || defaultDinnerNotice,
      checkinEnabled: resultCheckinEnabled
    },
    checkin: { title, description, announcement, publisher, buttonText: "自助签到", autoCheckinEnabled: true }
  };
}

function normalizePageContents(
  raw: Partial<Record<PublicPageKey, PublicPageContent>> | undefined,
  fallback: Record<PublicPageKey, PublicPageContent>
): Record<PublicPageKey, PublicPageContent> {
  return publicPageKeys.reduce(
    (result, key) => {
      result[key] = { ...fallback[key], ...(raw?.[key] || {}) };
      return result;
    },
    {} as Record<PublicPageKey, PublicPageContent>
  );
}

function normalizeCombinedQueryTypes(types: CombinedQueryType[] | undefined): CombinedQueryType[] {
  if (types === undefined) return [...defaultCombinedQueryTypes];
  const allowed = new Set<CombinedQueryType>(defaultCombinedQueryTypes);
  const normalized = types.filter((type): type is CombinedQueryType => allowed.has(type as CombinedQueryType));
  return Array.from(new Set(normalized));
}

function mapQuerySettings(item: MeetingQuerySettingsDto): MeetingQuerySettings {
  const fields = parseQueryFields(item.fields);
  const requiredFields = normalizeRequiredQueryFields(item.requiredFields, fields);
  const visibleResultFields = parseResultFields(item.visibleResultFields, defaultVisibleResultFields);
  const maskedResultFields = parseResultFields(item.maskedResultFields, []);
  const highlightResultFields = parseResultFields(item.highlightResultFields, ["SEAT_CODE", "ROOM_NO", "DINNER_TABLE_NO"]);
  const seatVisibleResultFields = parseResultFields(item.seatVisibleResultFields, seatResultFields(visibleResultFields).length ? seatResultFields(visibleResultFields) : defaultSeatVisibleResultFields);
  const seatMaskedResultFields = parseResultFields(item.seatMaskedResultFields, seatResultFields(maskedResultFields));
  const seatHighlightResultFields = parseResultFields(item.seatHighlightResultFields, seatResultFields(highlightResultFields).length ? seatResultFields(highlightResultFields) : ["SEAT_CODE"]);
  const roomVisibleResultFields = parseResultFields(item.roomVisibleResultFields, roomResultFields(visibleResultFields).length ? roomResultFields(visibleResultFields) : defaultRoomVisibleResultFields);
  const roomMaskedResultFields = parseResultFields(item.roomMaskedResultFields, roomResultFields(maskedResultFields));
  const roomHighlightResultFields = parseResultFields(item.roomHighlightResultFields, roomResultFields(highlightResultFields).length ? roomResultFields(highlightResultFields) : ["ROOM_NO"]);
  const dinnerVisibleResultFields = parseResultFields(
    item.dinnerVisibleResultFields,
    dinnerResultFields(visibleResultFields).length ? dinnerResultFields(visibleResultFields) : defaultDinnerVisibleResultFields
  );
  const dinnerMaskedResultFields = parseResultFields(item.dinnerMaskedResultFields, dinnerResultFields(maskedResultFields));
  const dinnerHighlightResultFields = parseResultFields(
    item.dinnerHighlightResultFields,
    dinnerResultFields(highlightResultFields).length ? dinnerResultFields(highlightResultFields) : ["DINNER_TABLE_NO"]
  );
  const pageTitle = item.pageTitle || `${item.meetingName}查询`;
  const pageDescription = item.pageDescription || "";
  const announcement = item.announcement || "";
  const publisher = item.publisher || "";
  const buttonText = item.buttonText || "立即查询";
  const emptyMessage = item.emptyMessage || "未查询到结果，请核对信息后重试";
  const seatNotice = item.seatNotice || defaultSeatNotice;
  const roomNotice = item.roomNotice || defaultRoomNotice;
  const dinnerNotice = item.dinnerNotice || defaultDinnerNotice;
  const pageContents = normalizePageContents(
    item.pageContents,
    buildDefaultPageContents({
      meetingName: item.meetingName,
      pageTitle,
      pageDescription,
      announcement,
      publisher,
      buttonText,
      emptyMessage,
      seatNotice,
      roomNotice,
      dinnerNotice,
      resultCheckinEnabled: item.checkinEnabled ?? true
    })
  );
  return {
    meetingId: idOf(item.meetingId),
    meetingCode: item.meetingCode,
    publicQueryToken: item.publicQueryToken || fallbackPublicQueryToken(item.meetingCode),
    meetingName: item.meetingName,
    seatQueryEnabled: item.seatQueryEnabled,
    roomQueryEnabled: item.roomQueryEnabled,
    dinnerQueryEnabled: item.dinnerQueryEnabled ?? false,
    checkinEnabled: item.checkinEnabled ?? true,
    selfCheckinEnabled: item.selfCheckinEnabled ?? true,
    queryPaused: Boolean(item.queryPaused),
    fields,
    requiredFields,
    matchMode: item.matchMode || "FUZZY",
    allowMultipleResults: Boolean(item.allowMultipleResults),
    combinedQueryTypes: normalizeCombinedQueryTypes(item.combinedQueryTypes),
    pageTitle,
    pageDescription,
    announcement,
    publisher,
    startTime: item.startTime,
    endTime: item.endTime,
    buttonText,
    emptyMessage,
    seatNotice,
    roomNotice,
    dinnerNotice,
    pageContents,
    themeColor: item.themeColor || "#2563eb",
    logoUrl: item.logoUrl,
    backgroundUrl: item.backgroundUrl,
    resultLayout: normalizeResultLayout(item.resultLayout),
    visibleResultFields: mergeResultFields(seatVisibleResultFields, roomVisibleResultFields, dinnerVisibleResultFields),
    maskedResultFields: mergeResultFields(seatMaskedResultFields, roomMaskedResultFields, dinnerMaskedResultFields),
    highlightResultFields: mergeResultFields(seatHighlightResultFields, roomHighlightResultFields, dinnerHighlightResultFields),
    seatVisibleResultFields,
    seatMaskedResultFields,
    seatHighlightResultFields,
    roomVisibleResultFields,
    roomMaskedResultFields,
    roomHighlightResultFields,
    dinnerVisibleResultFields,
    dinnerMaskedResultFields,
    dinnerHighlightResultFields,
    captchaEnabled: Boolean(item.captchaEnabled),
    fieldOptions: item.fieldOptions?.length ? item.fieldOptions : defaultQueryFieldOptions,
    resultFieldOptions: item.resultFieldOptions?.length ? item.resultFieldOptions : defaultResultFieldOptions
  };
}

function meetingToQuerySettings(meeting?: Meeting | null): MeetingQuerySettings {
  const meetingName = meeting?.name || "当前会议";
  const fields = meeting?.publicQueryFields?.length ? meeting.publicQueryFields : (["PHONE", "NAME"] as PublicQueryField[]);
  const requiredFields = normalizeRequiredQueryFields(meeting?.publicQueryRequiredFields, fields);
  const pageContents = buildDefaultPageContents({
    meetingName,
    pageTitle: `${meetingName}查询`,
    pageDescription: "",
    announcement: "",
    publisher: "",
    buttonText: "立即查询",
    emptyMessage: "未查询到结果，请核对信息后重试",
    seatNotice: defaultSeatNotice,
    roomNotice: defaultRoomNotice,
    dinnerNotice: defaultDinnerNotice,
    resultCheckinEnabled: meeting?.checkinEnabled ?? true
  });
  return {
    meetingId: meeting?.id || "",
    meetingCode: meeting?.code || "",
    publicQueryToken: meeting?.publicQueryToken || "",
    meetingName,
    seatQueryEnabled: meeting?.seatQueryEnabled ?? true,
    roomQueryEnabled: meeting?.roomQueryEnabled ?? true,
    dinnerQueryEnabled: meeting?.dinnerQueryEnabled ?? false,
    checkinEnabled: meeting?.checkinEnabled ?? true,
    selfCheckinEnabled: meeting?.selfCheckinEnabled ?? true,
    queryPaused: false,
    fields,
    requiredFields,
    matchMode: meeting?.publicQueryMatchMode || "FUZZY",
    allowMultipleResults: Boolean(meeting?.publicQueryAllowMultipleResults),
    combinedQueryTypes: [...defaultCombinedQueryTypes],
    pageTitle: `${meetingName}查询`,
    pageDescription: "",
    announcement: "",
    publisher: "",
    buttonText: "立即查询",
    emptyMessage: "未查询到结果，请核对信息后重试",
    seatNotice: defaultSeatNotice,
    roomNotice: defaultRoomNotice,
    dinnerNotice: defaultDinnerNotice,
    pageContents,
    themeColor: "#2563eb",
    resultLayout: "CARD",
    visibleResultFields: [...defaultVisibleResultFields],
    maskedResultFields: [],
    highlightResultFields: ["SEAT_CODE", "ROOM_NO", "DINNER_TABLE_NO"],
    seatVisibleResultFields: [...defaultSeatVisibleResultFields],
    seatMaskedResultFields: [],
    seatHighlightResultFields: ["SEAT_CODE"],
    roomVisibleResultFields: [...defaultRoomVisibleResultFields],
    roomMaskedResultFields: [],
    roomHighlightResultFields: ["ROOM_NO"],
    dinnerVisibleResultFields: [...defaultDinnerVisibleResultFields],
    dinnerMaskedResultFields: [],
    dinnerHighlightResultFields: ["DINNER_TABLE_NO"],
    captchaEnabled: false,
    fieldOptions: defaultQueryFieldOptions,
    resultFieldOptions: defaultResultFieldOptions
  };
}

function mapPublicQueryHistory(item: PublicQueryHistoryDto): PublicQueryHistory {
  return {
    id: idOf(item.id),
    queryType: item.queryType,
    queryStatus: item.queryStatus,
    queryValues: item.queryValues || {},
    errorMessage: item.errorMessage,
    attendeeId: item.attendeeId == null ? undefined : idOf(item.attendeeId),
    attendeeName: item.attendeeName,
    attendeePhone: item.attendeePhone,
    resultSummary: item.resultSummary,
    clientIp: item.clientIp,
    userAgent: item.userAgent,
    createdAt: item.createdAt
  };
}

function mapPublicQuerySummary(item: PublicQuerySummaryDto): PublicQuerySummary {
  return {
    groupKey: item.groupKey,
    groupType: item.groupType,
    attendeeId: item.attendeeId == null ? undefined : idOf(item.attendeeId),
    attendeeName: item.attendeeName,
    attendeePhone: item.attendeePhone,
    failureReason: item.failureReason,
    totalCount: item.totalCount,
    successCount: item.successCount,
    failedCount: item.failedCount,
    latestQueryAt: item.latestQueryAt,
    latestResult: item.latestResult
  };
}

function localSeatQuery(
  store: ReturnType<typeof useMeetingStore>,
  payload: { publicQueryToken?: string | null; values: Record<PublicQueryField, string> }
): SeatQueryResult {
  const meeting = resolveLocalMeeting(store, payload);
  if (!meeting.seatQueryEnabled) throw new Error("座位查询尚未开放");
  const attendee = resolveSingleLocalAttendee(store, meeting, payload.values);
  if (attendee.auditStatus !== "APPROVED") throw new Error("报名尚未审核通过，暂不能查询");
  const seat = store.seats.find((item) => item.meetingId === meeting.id && item.attendeeId === attendee.id);
  if (!seat) throw new Error("尚未排座");
  const areaSeats = store.seats.filter((item) => item.meetingId === meeting.id && item.areaName === seat.areaName);
  const rowCount = Math.max(...areaSeats.map((item) => item.rowNo), 0);
  const seatsPerRow = Math.max(...areaSeats.map((item) => item.seatNo), 0);
  return {
    attendeeId: Number(attendee.id),
    attendeeName: attendee.name,
    attendeePhone: attendee.phone,
    organization: attendee.company,
    title: attendee.title,
    email: attendee.email,
    meetingName: meeting.name,
    areaName: seat.areaName,
    rowNo: seat.rowNo,
    seatNo: seat.seatNo,
    seatCode: `${seat.areaName}-${seat.rowNo}-${seat.seatNo}`,
    seatMap: rowCount > 0 && seatsPerRow > 0
      ? {
          areaName: seat.areaName,
          rowCount,
          seatsPerRow,
          currentRowNo: seat.rowNo,
          currentSeatNo: seat.seatNo
        }
      : null
  };
}

function localRoomQuery(
  store: ReturnType<typeof useMeetingStore>,
  payload: { publicQueryToken?: string | null; values: Record<PublicQueryField, string> }
): RoomQueryResult {
  const meeting = resolveLocalMeeting(store, payload);
  if (!meeting.roomQueryEnabled) throw new Error("住宿查询尚未开放");
  const attendee = resolveSingleLocalAttendee(store, meeting, payload.values);
  if (attendee.auditStatus !== "APPROVED") throw new Error("报名尚未审核通过，暂不能查询");
  if (!attendee.roomNo) throw new Error("尚未安排房间");
  const room = store.rooms.find((item) => item.meetingId === meeting.id && item.roomNo === attendee.roomNo);
  return {
    attendeeId: Number(attendee.id),
    attendeeName: attendee.name,
    meetingName: meeting.name,
    roomNo: attendee.roomNo,
    roomRemark: attendee.roomRemark,
    hotelName: room?.hotelName,
    roomType: room?.roomType,
    checkinTips: room?.checkinTips
  };
}

function localDinnerQuery(
  store: ReturnType<typeof useMeetingStore>,
  payload: { publicQueryToken?: string | null; values: Record<PublicQueryField, string> }
): DinnerQueryResult {
  const meeting = resolveLocalMeeting(store, payload);
  if (!meeting.dinnerQueryEnabled) throw new Error("晚宴查询尚未开放");
  const attendee = resolveSingleLocalAttendee(store, meeting, payload.values);
  if (attendee.auditStatus !== "APPROVED") throw new Error("报名尚未审核通过，暂不能查询");
  const assignment = store.dinnerAssignments.find((item) => item.meetingId === meeting.id && item.attendeeId === attendee.id);
  if (!assignment) throw new Error("尚未安排晚宴桌号");
  return {
    attendeeId: Number(attendee.id),
    attendeeName: attendee.name,
    meetingName: meeting.name,
    tableNo: assignment.tableNo,
    venue: meeting.dinnerVenue,
    dinnerTime: meeting.dinnerTime,
    remark: assignment.remark
  };
}

function localPublicSelfCheckin(
  store: ReturnType<typeof useMeetingStore>,
  payload: { publicQueryToken?: string | null; values: Record<PublicQueryField, string>; checkinSource?: PublicCheckinSource; checkinPage?: PublicPageKey }
): PublicCheckinResult {
  const meeting = resolveLocalMeeting(store, payload);
  const source = payload.checkinSource || "SELF";
  if (source === "RESULT") {
    const settings = meetingToQuerySettings(meeting);
    const pageKey = normalizeLocalResultCheckinPage(payload.checkinPage);
    const pageEnabled = settings.pageContents[pageKey].checkinEnabled ?? settings.checkinEnabled;
    if (!pageEnabled) throw new Error("当前页面查询后签到按钮尚未开放");
  } else if (!meeting.selfCheckinEnabled) {
    throw new Error("签到独立页尚未开放");
  }
  const attendee = resolveSingleLocalAttendee(store, meeting, payload.values);
  if (attendee.auditStatus !== "APPROVED") {
    return {
      status: "NOT_APPROVED",
      attendeeId: Number(attendee.id),
      attendeeName: attendee.name,
      phone: maskPhone(attendee.phone),
      message: "报名尚未审核通过"
    };
  }
  if (attendee.checkinStatus === "CHECKED_IN") {
    return {
      status: "DUPLICATE",
      attendeeId: Number(attendee.id),
      attendeeName: attendee.name,
      phone: maskPhone(attendee.phone),
      checkedInAt: attendee.checkinTime,
      entrance: "H5自助签到",
      operator: "system",
      message: `重复签到，首次签到时间: ${attendee.checkinTime || "时间未知"}`
    };
  }

  const now = nowIso();
  attendee.checkinStatus = "CHECKED_IN";
  attendee.checkinTime = now;
  store.checkinRecords.unshift({
    id: `c-${Date.now()}`,
    meetingId: meeting.id,
    attendeeId: attendee.id,
    attendeeName: attendee.name,
    method: "SEARCH",
    entrance: "H5自助签到",
    operator: "system",
    checkinTime: now
  });
  store.refreshMeetingCounters();
  return {
    status: "SUCCESS",
    attendeeId: Number(attendee.id),
    attendeeName: attendee.name,
    phone: maskPhone(attendee.phone),
    checkedInAt: now,
    entrance: "H5自助签到",
    operator: "system",
    message: "签到成功"
  };
}

function localPublicSelfCheckinPreview(
  store: ReturnType<typeof useMeetingStore>,
  payload: { publicQueryToken?: string | null; values: Record<PublicQueryField, string>; checkinSource?: PublicCheckinSource; checkinPage?: PublicPageKey }
): PublicCheckinPreview {
  const meeting = resolveLocalMeeting(store, payload);
  if (!meeting.selfCheckinEnabled) {
    throw new Error("签到独立页尚未开放");
  }
  const attendee = resolveSingleLocalAttendee(store, meeting, payload.values);
  if (attendee.auditStatus !== "APPROVED") {
    throw new Error("报名尚未审核通过");
  }
  if (attendee.checkinStatus === "CHECKED_IN") {
    return {
      ready: false,
      attendeeId: Number(attendee.id),
      attendeeName: attendee.name,
      phone: maskPhone(attendee.phone),
      checkedInAt: attendee.checkinTime,
      message: "您已完成签到，请勿重复提交。"
    };
  }
  return {
    ready: true,
    attendeeId: Number(attendee.id),
    attendeeName: attendee.name,
    phone: maskPhone(attendee.phone),
    message: "身份核验通过，请确认签到。"
  };
}

function normalizeLocalResultCheckinPage(pageKey?: PublicPageKey): Extract<PublicPageKey, "combined" | "seat" | "room" | "dinner"> {
  return pageKey === "seat" || pageKey === "room" || pageKey === "dinner" ? pageKey : "combined";
}

function localAdminCandidates(store: ReturnType<typeof useMeetingStore>, meetingId: string, values: Record<PublicQueryField, string>): AdminQueryCandidate[] {
  const meeting = store.meetings.find((item) => item.id === meetingId);
  if (!meeting) throw new Error("会议不存在");
  return localMatchingAttendees(store, meeting, values)
    .slice(0, 20)
    .map((attendee) => ({
      attendeeId: Number(attendee.id),
      attendeeName: attendee.name,
      phone: attendee.phone,
      organization: attendee.company,
      title: attendee.title,
      reviewStatus: attendee.auditStatus,
      seatLabel: attendee.seatLabel,
      roomNo: attendee.roomNo,
      roomRemark: attendee.roomRemark
    }));
}

function resolveLocalMeeting(store: ReturnType<typeof useMeetingStore>, payload: { publicQueryToken?: string | null }) {
  const token = payload.publicQueryToken?.trim();
  if (!token) throw new Error("公开查询链接无效");
  const meeting = store.meetings.find((item) => item.publicQueryToken === token);
  if (!meeting) throw new Error("公开查询链接无效");
  return meeting;
}

function resolveSingleLocalAttendee(store: ReturnType<typeof useMeetingStore>, meeting: Meeting, values: Record<PublicQueryField, string>) {
  const matches = localMatchingAttendees(store, meeting, values);
  if (!matches.length) throw new Error("未找到匹配报名记录");
  if (matches.length > 1) throw new Error("匹配到多条报名记录，请补充更精确的查询信息");
  return matches[0];
}

function localMatchingAttendees(store: ReturnType<typeof useMeetingStore>, meeting: Meeting, values: Record<PublicQueryField, string>) {
  const fields = meeting.publicQueryFields?.length ? meeting.publicQueryFields : (["PHONE", "NAME"] as PublicQueryField[]);
  const requiredFields = normalizeRequiredQueryFields(meeting.publicQueryRequiredFields, fields);
  for (const field of requiredFields) {
    if (!values[field]?.trim()) throw new Error(`请填写${queryFieldLabel(field)}`);
  }
  const matchFields = fields.filter((field) => values[field]?.trim());
  return store.attendees
    .filter((attendee) => attendee.meetingId === meeting.id)
    .filter((attendee) => matchFields.every((field) => localFieldMatches(attendee, field, values[field], meeting.publicQueryMatchMode)));
}

function localFieldMatches(attendee: Attendee, field: PublicQueryField, rawValue: string, mode: PublicQueryMatchMode) {
  const haystack = normalizeQueryValue(localFieldValue(attendee, field), field);
  const needle = normalizeQueryValue(rawValue, field);
  if (!haystack || !needle) return false;
  if (mode === "EXACT" && field === "PHONE_LAST4") return haystack.endsWith(needle);
  return mode === "EXACT" ? haystack === needle : haystack.includes(needle);
}

function localFieldValue(attendee: Attendee, field: PublicQueryField) {
  switch (field) {
    case "NAME":
      return attendee.name;
    case "PHONE":
    case "PHONE_LAST4":
      return attendee.phone;
    case "ATTENDEE_CODE":
      return attendee.attendeeCode || "";
    case "ORGANIZATION":
      return attendee.company;
    case "TITLE":
      return attendee.title;
    case "EMAIL":
      return attendee.email || "";
  }
}

function normalizeQueryValue(value: string, field: PublicQueryField) {
  const normalized = (value || "").trim().toLocaleLowerCase("zh-CN");
  return field === "PHONE" || field === "PHONE_LAST4" ? normalized.replace(/[\s-]/g, "") : normalized;
}

function maskPhone(phone?: string) {
  const normalized = (phone || "").replace(/[\s-]/g, "");
  if (normalized.length <= 4) return normalized ? "*".repeat(normalized.length) : "";
  return `${normalized.slice(0, 3)}****${normalized.slice(-4)}`;
}

function queryFieldLabel(field: PublicQueryField) {
  return defaultQueryFieldOptions.find((item) => item.field === field)?.label || field;
}

function mapSyncLog(item: SyncLogDto, meetingId: string): SyncLog {
  return {
    id: idOf(item.id),
    meetingId,
    formKey: item.formKey || "",
    dataId: item.tduckDataId || item.eventId || "",
    eventType: item.eventType || "",
    status: item.status,
    errorMessage: item.status === "FAILED" || item.status === "DUPLICATE_IGNORED" ? item.message : undefined,
    createdAt: item.createdAt || ""
  };
}

function mapTduckSubmissionConflict(item: TduckSubmissionConflictWireDto): TduckSubmissionConflictDto {
  return {
    id: idOf(item.id),
    meetingId: idOf(item.meetingId),
    attendeeId: idOf(item.attendeeId),
    currentTduckDataId: item.currentTduckDataId || undefined,
    incomingTduckDataId: item.incomingTduckDataId || undefined,
    status: item.status,
    submittedAt: item.submittedAt || undefined,
    createdAt: item.createdAt || undefined,
    existing: mapTduckSubmissionParticipant(item.existing),
    incoming: mapTduckSubmissionParticipant(item.incoming)
  };
}

function mapTduckSubmissionParticipant(item?: TduckSubmissionParticipantDto): TduckSubmissionParticipantDto {
  return {
    name: item?.name || "",
    phone: item?.phone || "",
    organization: item?.organization || "",
    title: item?.title || "",
    identityNo: item?.identityNo || "",
    email: item?.email || "",
    attendeeType: item?.attendeeType || "",
    accommodationRequired: Boolean(item?.accommodationRequired),
    remark: item?.remark || "",
    formValues: item?.formValues && typeof item.formValues === "object" && !Array.isArray(item.formValues)
      ? { ...item.formValues }
      : {}
  };
}

function mapCheckinRecord(item: CheckinRecordDto, meetingId: string, attendees: Attendee[]): CheckinRecord {
  const snapshot = item.requestSnapshot || {};
  const phone = typeof snapshot.phone === "string" ? snapshot.phone : "";
  const attendeeCode = typeof snapshot.attendeeCode === "string" ? snapshot.attendeeCode : "";
  const attendee = attendees.find((candidate) => candidate.id === idOf(item.attendeeId) || candidate.phone === phone || candidate.attendeeCode === attendeeCode);
  return {
    id: idOf(item.id),
    meetingId,
    attendeeId: attendee?.id || idOf(item.attendeeId),
    attendeeName: attendee?.name || item.attendeeName || item.resultStatus || "未知参会人",
    method: item.checkinType === "SUPPLEMENT" ? "SUPPLEMENT" : item.checkinType === "MANUAL" ? "MANUAL" : item.checkinType === "SEARCH" ? "SEARCH" : "SCAN_CODE",
    entrance: item.entrance || "",
    operator: item.operator || "",
    checkinTime: item.checkedInAt || item.createdAt || ""
  };
}

function normalizeRoomCapacity(capacity: number | null | undefined) {
  return Math.max(1, Math.floor(Number(capacity) || 1));
}

function normalizeTags(tags: string[]) {
  return Array.from(new Set(tags.map((tag) => tag.trim()).filter(Boolean)));
}

function mapExportTask(item: ExportTaskWireDto): ExportTaskDto {
  return {
    taskId: idOf(item.taskId ?? item.id),
    meetingId: item.meetingId || "",
    type: item.type,
    status: item.status,
    createdAt: item.createdAt || item.completedAt || "",
    filename: item.filename,
    requestedBy: item.requestedBy,
    message: item.message,
    format: item.format,
    startDate: item.startDate,
    endDate: item.endDate,
    progress: normalizeTaskProgress(item.progress),
    completedAt: item.completedAt,
    expiresAt: item.expiresAt,
    fileAvailable: item.fileAvailable,
    fileSize: item.fileSize,
    checksum: item.checksum,
    generatedAt: item.generatedAt
  };
}

function mapImportTask(
  item: ImportTaskWireDto,
  fallbackMeetingId: string,
  fallbackType: ImportTaskType = "attendees",
  fallbackFilename = ""
): ImportTaskDto {
  const totalRows = Math.max(0, Number(item.totalRows) || 0);
  const processedRows = Math.max(0, Number(item.processedRows) || 0);
  const importedRows = Math.max(0, Number(item.importedRows) || 0);
  const failedRows = Math.max(0, Number(item.failedRows) || 0);
  return {
    id: idOf(item.taskId ?? item.id),
    meetingId: item.meetingId ?? fallbackMeetingId,
    type: item.type || fallbackType,
    status: item.status || "PENDING",
    originalFilename: item.originalFilename || fallbackFilename,
    totalRows,
    processedRows,
    importedRows,
    failedRows,
    progress: normalizeTaskProgress(item.progress, totalRows > 0 ? (processedRows / totalRows) * 100 : 0),
    message: item.message,
    failureFileAvailable: Boolean(item.failureFileAvailable),
    startedAt: item.startedAt,
    createdAt: item.createdAt,
    completedAt: item.completedAt,
    expiresAt: item.expiresAt
  };
}

function normalizeTaskProgress(value: number | undefined, fallback = 0) {
  const progress = Number.isFinite(Number(value)) ? Number(value) : fallback;
  return Math.min(100, Math.max(0, Math.round(progress)));
}

function getLocalAdminUsers(): AdminUserDto[] {
  return [
    { id: "local-super-admin", username: "admin", displayName: "超级管理员", status: "ACTIVE", roles: ["SUPER_ADMIN"] },
    { id: "local-meeting-admin", username: "meeting-admin", displayName: "会议管理员", status: "ACTIVE", roles: ["MEETING_ADMIN"] },
    { id: "local-checkin", username: "checkin-staff", displayName: "签到工作人员", status: "ACTIVE", roles: ["CHECKIN_STAFF"] }
  ];
}

function getLocalMeetingUserPermissions(meetingId: string): MeetingUserPermissionDto[] {
  return [
    { id: "local-p-1", meetingId, userId: "local-super-admin", username: "admin", displayName: "超级管理员", role: "SUPER_ADMIN", permissions: ["全部会议管理"] },
    { id: "local-p-2", meetingId, userId: "local-meeting-admin", username: "meeting-admin", displayName: "会议管理员", role: "MEETING_ADMIN", permissions: ["参会人", "排座", "导出"] },
    { id: "local-p-3", meetingId, userId: "local-checkin", username: "checkin-staff", displayName: "签到工作人员", role: "CHECKIN_STAFF", permissions: ["签到"] }
  ];
}

function parseCheckinCode(input: string) {
  const compact = input.match(/MEETINGOS-CHECKIN:([^:]+):([^#?]+)/);
  if (compact) return { meetingId: compact[1], attendeeCode: compact[2] };

  try {
    const url = new URL(input, window.location.origin);
    return {
      meetingId: url.searchParams.get("meetingId") || "",
      attendeeCode: url.searchParams.get("attendeeCode") || url.searchParams.get("attendeeId") || url.searchParams.get("code") || ""
    };
  } catch {
    return { meetingId: "", attendeeCode: "" };
  }
}

function defaultBranding(): BrandingDto {
  return {
    brandName: "会议管理系统",
    browserTitle: "会议管理系统",
    metaDescription: "会议管理系统",
    logoUrl: "/favicon.svg",
    faviconUrl: "/favicon.svg"
  };
}

function normalizeBranding(value?: Partial<BrandingDto> | null): BrandingDto {
  const fallback = defaultBranding();
  const logoUrl = sanitizeImageUrl(value?.logoUrl) || fallback.logoUrl;
  const faviconUrl = sanitizeImageUrl(value?.faviconUrl) || logoUrl || fallback.faviconUrl;
  return {
    brandName: nonEmpty(value?.brandName, fallback.brandName),
    browserTitle: nonEmpty(value?.browserTitle, nonEmpty(value?.brandName, fallback.browserTitle)),
    metaDescription: nonEmpty(value?.metaDescription, fallback.metaDescription),
    logoUrl,
    faviconUrl
  };
}

function normalizeSystemSettings(value: SystemSettingsDto): SystemSettingsDto {
  const branding = normalizeBranding(value);
  return {
    ...value,
    ...branding,
    maskSensitive: value.maskSensitive ?? true
  };
}

function nonEmpty(value: unknown, fallback: string) {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

function applyDocumentBranding(branding: BrandingDto) {
  if (typeof document === "undefined") return;
  document.title = branding.browserTitle || branding.brandName;

  const description = ensureMetaElement("description");
  description.setAttribute("content", branding.metaDescription || branding.browserTitle || branding.brandName);

  ensureIconLink("icon", branding.faviconUrl || branding.logoUrl);
  ensureIconLink("shortcut icon", branding.faviconUrl || branding.logoUrl);
}

function ensureMetaElement(name: string) {
  const selector = `meta[name="${name}"]`;
  let element = document.head.querySelector<HTMLMetaElement>(selector);
  if (!element) {
    element = document.createElement("meta");
    element.setAttribute("name", name);
    document.head.appendChild(element);
  }
  return element;
}

function ensureIconLink(rel: string, href: string) {
  const safeHref = sanitizeImageUrl(href);
  if (!safeHref) return;
  let element = document.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`);
  if (!element) {
    element = document.createElement("link");
    element.setAttribute("rel", rel);
    document.head.appendChild(element);
  }
  element.setAttribute("href", safeHref);
}
