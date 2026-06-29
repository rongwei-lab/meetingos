import { defineStore } from "pinia";
import { canUseDemoFallback, extractErrorMessage, http, type PagePayload, isAuthFailure, unwrapApi } from "@/api/http";
import { mockAttendees, mockMeetings, mockRooms, mockSeats, mockSyncLogs } from "@/utils/mockData";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import type {
  AdminQueryCandidate,
  Attendee,
  CheckinRecord,
  Meeting,
  MeetingQuerySettings,
  PublicCheckinResult,
  PublicQueryField,
  PublicQueryHistory,
  PublicQueryMatchMode,
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

const CURRENT_MEETING_KEY = "meetingos.currentMeetingId";

interface MeetingSummaryDto {
  id: number;
  code: string;
  name: string;
  startTime?: string;
  endTime?: string;
  location?: string;
  registrationCount?: number;
  approvedCount?: number;
  seatedCount?: number;
  roomAssignedCount?: number;
  checkedInCount?: number;
  status: Meeting["status"];
  seatQueryEnabled?: boolean;
  roomQueryEnabled?: boolean;
  checkinEnabled?: boolean;
  publicQueryFields?: string;
  publicQueryMatchMode?: PublicQueryMatchMode;
}

interface MeetingDetailDto {
  meeting: {
    id: number;
    code: string;
    name: string;
    organizer?: string;
    coOrganizer?: string;
    startTime?: string;
    endTime?: string;
    registrationStartTime?: string;
    registrationEndTime?: string;
    location?: string;
    venueName?: string;
    capacity?: number;
    description?: string;
    agenda?: string;
    coverImageUrl?: string;
    contactName?: string;
    contactPhone?: string;
    status: Meeting["status"];
    seatQueryEnabled?: boolean;
    roomQueryEnabled?: boolean;
    checkinEnabled?: boolean;
    publicQueryFields?: string;
    publicQueryMatchMode?: PublicQueryMatchMode;
  };
  registrationCount?: number;
  approvedCount?: number;
  seatedCount?: number;
  roomAssignedCount?: number;
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
  remark?: string;
  seatQueried?: boolean;
  roomQueried?: boolean;
  checkinStatus?: Attendee["checkinStatus"];
  checkedInAt?: string;
  attendeeCode?: string;
  tduckDataId?: string;
  formValues?: Record<string, unknown>;
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
  meetingName: string;
  seatQueryEnabled: boolean;
  roomQueryEnabled: boolean;
  checkinEnabled?: boolean;
  queryPaused?: boolean;
  fields: PublicQueryField[];
  matchMode: PublicQueryMatchMode;
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

export interface PublicCaptchaDto {
  enabled: boolean;
  captchaId?: string | null;
  question?: string | null;
  expiresInSeconds?: number;
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

export interface ImportResultDto {
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  message: string;
  failedRows?: Array<{ rowNumber: number; reason: string }>;
}

export interface ExportTaskDto {
  taskId: string;
  meetingId: number | string;
  type: string;
  status: "PENDING" | "RUNNING" | "APPROVED" | "REJECTED" | "COMPLETED" | "FAILED" | "LOCAL" | string;
  createdAt: string;
  filename?: string;
  requestedBy?: string;
  message?: string;
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
  exportApproval: boolean;
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

function filenameFromDisposition(disposition: string | undefined, fallback: string) {
  if (!disposition) return fallback;
  const utf8Match = disposition.match(/filename\\*=UTF-8''([^;]+)/i);
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

function meetingRequestFrom(meeting: Partial<Meeting>, fallback?: Meeting) {
  const venue = blankToUndefined(meeting.venue ?? fallback?.venue);
  return {
    code: blankToUndefined(meeting.code ?? fallback?.code),
    name: meeting.name || fallback?.name || "新会议",
    organizer: meeting.organizer || fallback?.organizer || "主办方",
    coOrganizer: blankToUndefined(meeting.coOrganizer ?? fallback?.coOrganizer),
    location: venue,
    venueName: venue,
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
    status: meeting.status || fallback?.status || "DRAFT",
    seatQueryEnabled: meeting.seatQueryEnabled ?? fallback?.seatQueryEnabled ?? false,
    roomQueryEnabled: meeting.roomQueryEnabled ?? fallback?.roomQueryEnabled ?? false,
    checkinEnabled: meeting.checkinEnabled ?? fallback?.checkinEnabled ?? true,
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
  { field: "CHECKIN_TIPS", label: "入住提示" }
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
  "CHECKIN_TIPS"
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

let bootstrapPromise: Promise<void> | null = null;
const workspaceLoadPromises = new Map<string, Promise<void>>();
const attendeesLoadPromises = new Map<string, Promise<void>>();
const seatsLoadPromises = new Map<string, Promise<void>>();
const roomsLoadPromises = new Map<string, Promise<void>>();
const statisticsLoadPromises = new Map<string, Promise<void>>();
const ADMIN_PAGE_SIZE = 200;

function singleFlight<T>(promises: Map<string, Promise<T>>, key: string, loader: () => Promise<T>) {
  const existing = promises.get(key);
  if (existing) return existing;

  /*
   * 快速切换侧边栏时，旧页面的 onMounted 请求和新页面的按需请求可能同时命中同一资源。
   * 这里把同会议同资源的并发加载合并成一次，避免重复解析大列表并多次触发响应式重绘。
   */
  const promise = loader().finally(() => {
    promises.delete(key);
  });
  promises.set(key, promise);
  return promise;
}

export const useMeetingStore = defineStore("meeting", {
  state: () => ({
    meetings: [] as Meeting[],
    attendees: [] as Attendee[],
    seats: [] as Seat[],
    rooms: [] as Room[],
    syncLogs: [] as SyncLog[],
    checkinRecords: [] as CheckinRecord[],
    overview: null as StatisticsOverviewDto | null,
    apiAvailable: false,
    loading: false,
    currentMeetingId: savedMeetingId(),
    branding: defaultBranding()
  }),
  getters: {
    currentMeeting(state) {
      return state.meetings.find((meeting) => meeting.id === state.currentMeetingId) || null;
    },
    currentAttendees(state) {
      return state.attendees.filter((attendee) => attendee.meetingId === state.currentMeetingId);
    },
    currentSeats(state) {
      return state.seats.filter((seat) => seat.meetingId === state.currentMeetingId);
    },
    currentRooms(state) {
      return state.rooms.filter((room) => room.meetingId === state.currentMeetingId);
    }
  },
  actions: {
    async bootstrap() {
      // 多个页面可能同时触发初始化，复用同一个请求，避免座位页先渲染 mock 数据再被真实数据替换。
      if (!bootstrapPromise) {
        bootstrapPromise = (async () => {
          await this.loadBranding();
          await this.loadMeetings();
        })().finally(() => {
            bootstrapPromise = null;
          });
      }
      await bootstrapPromise;
    },
    async loadBranding() {
      try {
        this.branding = normalizeBranding(unwrapApi(await http.get<BrandingDto>("/public/branding")));
      } catch {
        // 品牌配置属于装饰信息，后端不可用时使用内置默认值，不影响登录和业务流程。
        this.branding = defaultBranding();
      }
      applyDocumentBranding(this.branding);
      return this.branding;
    },
    async loadMeetings() {
      this.loading = true;
      try {
        const page = unwrapApi(await http.get<PagePayload<MeetingSummaryDto>>("/admin/meetings", { params: { page: 0, size: 100 } }));
        this.meetings = page.content.map(mapMeetingSummary);
        if (!this.meetings.some((meeting) => meeting.id === this.currentMeetingId)) {
          this.setCurrentMeetingId(this.meetings[0]?.id || "");
        }
        this.apiAvailable = true;
      } catch (error) {
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
        this.loading = false;
      }
    },
    setCurrentMeetingId(meetingId: string) {
      this.currentMeetingId = meetingId;
      if (meetingId) {
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
      this.syncLogs = [...mockSyncLogs];
      const fallbackMeetingId = this.meetings.some((meeting) => meeting.id === preferredMeetingId)
        ? preferredMeetingId
        : this.meetings[0]?.id || "";
      this.setCurrentMeetingId(fallbackMeetingId);
    },
    async selectMeeting(meetingId: string, options: { loadWorkspace?: boolean } = {}) {
      const targetMeetingId = idOf(meetingId);
      if (!targetMeetingId) {
        this.setCurrentMeetingId("");
        return null;
      }
      const loadWorkspace = options.loadWorkspace !== false;

      /*
       * Store 初始化时会先放一份 mock 会议，直接刷新 /meetings/:id/* 时不能把它当成真实列表。
       * 数字 ID 来自后端路由，优先读取详情；失败后再回退本地演示数据，避免页面显示“未选择会议”。
       */
      if (isBackendMeetingId(targetMeetingId)) {
        const remote = await this.loadMeetingDetail(targetMeetingId, { loadWorkspace });
        if (remote) return remote;
      }

      if (!this.meetings.length) {
        await this.bootstrap();
      }

      const local = this.meetings.find((meeting) => meeting.id === targetMeetingId);
      if (!local) return null;
      this.setCurrentMeetingId(local.id);
      if (loadWorkspace) {
        await this.loadMeetingWorkspace(local.id);
      }
      return local;
    },
    async loadMeetingDetail(meetingId: string, options: { loadWorkspace?: boolean } = {}) {
      try {
        const detail = unwrapApi(await http.get<MeetingDetailDto>(`/admin/meetings/${backendId(meetingId)}`));
        const mapped = mapMeetingDetail(detail);
        this.upsertMeeting(mapped);
        this.setCurrentMeetingId(mapped.id);
        if (options.loadWorkspace !== false) {
          await this.loadMeetingWorkspace(mapped.id);
        }
        return mapped;
      } catch {
        const local = this.meetings.find((meeting) => meeting.id === meetingId);
        if (local) {
          this.setCurrentMeetingId(local.id);
          return local;
        }
        return null;
      }
    },
    async loadMeetingQuerySettings(meetingId?: string) {
      const targetMeetingId = requireMeetingId(meetingId || this.currentMeetingId);
      try {
        const settings = mapQuerySettings(
          unwrapApi(await http.get<MeetingQuerySettingsDto>(`/admin/meetings/${backendId(targetMeetingId)}/query-settings`))
        );
        this.applyMeetingQuerySettings(settings);
        return settings;
      } catch (error) {
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询设置加载失败"));
        const meeting = this.meetings.find((item) => item.id === targetMeetingId);
        return meetingToQuerySettings(meeting);
      }
    },
    async saveMeetingQuerySettings(
      meetingId: string,
      settings: Pick<
        MeetingQuerySettings,
        | "seatQueryEnabled"
        | "roomQueryEnabled"
        | "checkinEnabled"
        | "queryPaused"
        | "fields"
        | "matchMode"
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
      >
    ) {
      const targetMeetingId = requireMeetingId(meetingId);
      try {
        const saved = mapQuerySettings(
          unwrapApi(
            await http.put<MeetingQuerySettingsDto>(`/admin/meetings/${backendId(targetMeetingId)}/query-settings`, {
              seatQueryEnabled: settings.seatQueryEnabled,
              roomQueryEnabled: settings.roomQueryEnabled,
              checkinEnabled: settings.checkinEnabled,
              queryPaused: settings.queryPaused,
              fields: settings.fields,
              matchMode: settings.matchMode,
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
              roomHighlightResultFields: settings.roomHighlightResultFields
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
          checkinEnabled: settings.checkinEnabled,
          queryPaused: settings.queryPaused,
          fields: [...settings.fields],
          matchMode: settings.matchMode,
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
          roomHighlightResultFields: [...settings.roomHighlightResultFields]
        };
        this.applyMeetingQuerySettings(saved);
        return saved;
      }
    },
    async loadPublicQuerySettings(params: { meetingId?: string | number | null; meetingCode?: string | null }) {
      try {
        const settings = mapQuerySettings(
          unwrapApi(
            await http.get<MeetingQuerySettingsDto>("/public/query-settings", {
              params: {
                meetingId: params.meetingId || undefined,
                meetingCode: params.meetingCode || undefined
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
        const meeting =
          this.meetings.find((item) => item.id === String(params.meetingId)) ||
          this.meetings.find((item) => item.code === params.meetingCode) ||
          this.currentMeeting ||
          this.meetings[0];
        return meetingToQuerySettings(meeting);
      }
    },
    async loadPublicCaptcha() {
      return unwrapApi(await http.get<PublicCaptchaDto>("/public/captcha"));
    },
    async publicSeatQuery(payload: {
      meetingId?: string | number | null;
      meetingCode?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }) {
      try {
        return unwrapApi(await http.post<SeatQueryResult>("/public/seat-query", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "座位查询失败"));
        return localSeatQuery(this, payload);
      }
    },
    async publicRoomQuery(payload: {
      meetingId?: string | number | null;
      meetingCode?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }) {
      try {
        return unwrapApi(await http.post<RoomQueryResult>("/public/room-query", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "住宿查询失败"));
        return localRoomQuery(this, payload);
      }
    },
    async publicSelfCheckin(payload: {
      meetingId?: string | number | null;
      meetingCode?: string | null;
      values: Record<PublicQueryField, string>;
      captchaId?: string | null;
      captchaAnswer?: string | null;
    }) {
      try {
        return unwrapApi(await http.post<PublicCheckinResult>("/public/checkin", payload));
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "签到失败"));
        return localPublicSelfCheckin(this, payload);
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
            }
          })
        );
        return {
          ...page,
          content: page.content.map(mapPublicQueryHistory)
        };
      } catch (error) {
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询历史加载失败"));
        return emptyPage<PublicQueryHistory>(filters.page ?? 0, filters.size ?? 20);
      }
    },
    async loadPublicQuerySummary(meetingId: string) {
      const targetMeetingId = requireMeetingId(meetingId);
      try {
        return unwrapApi(
          await http.get<PublicQuerySummaryDto[]>(`/admin/meetings/${backendId(targetMeetingId)}/query-history/summary`)
        ).map(mapPublicQuerySummary);
      } catch (error) {
        if (this.apiAvailable && !canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "查询汇总加载失败"));
        return [] as PublicQuerySummary[];
      }
    },
    applyMeetingQuerySettings(settings: MeetingQuerySettings) {
      const meeting = this.meetings.find((item) => item.id === settings.meetingId || item.code === settings.meetingCode);
      if (!meeting) return;
      meeting.seatQueryEnabled = settings.seatQueryEnabled;
      meeting.roomQueryEnabled = settings.roomQueryEnabled;
      meeting.checkinEnabled = settings.checkinEnabled;
      meeting.publicQueryFields = [...settings.fields];
      meeting.publicQueryMatchMode = settings.matchMode;
    },
    async loadMeetingWorkspace(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      this.setCurrentMeetingId(meetingId);
      const existing = workspaceLoadPromises.get(meetingId);
      if (existing) {
        await existing;
        return;
      }

      /*
       * 进入工作区、路由同步和侧边栏切换可能在同一时间请求完整工作区。
       * 复用同会议的加载 Promise，避免多轮参会人/座位/住宿/签到/统计请求堆叠拖死浏览器主线程。
       */
      const promise = (async () => {
        await Promise.allSettled([
          this.loadAttendees(meetingId),
          this.loadSeats(meetingId),
          this.loadRooms(meetingId),
          this.loadSyncLogs(meetingId),
          this.loadCheckinRecords(meetingId),
          this.loadStatistics(meetingId)
        ]);
        this.reconcileAssignments();
        this.refreshMeetingCounters();
      })().finally(() => {
        workspaceLoadPromises.delete(meetingId);
      });
      workspaceLoadPromises.set(meetingId, promise);
      await promise;
    },
    async loadAttendees(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(attendeesLoadPromises, meetingId, async () => {
        const firstPage = unwrapApi(
          await http.get<PagePayload<AttendeeDto>>("/admin/attendees", {
            params: { meetingId: backendId(meetingId), page: 0, size: ADMIN_PAGE_SIZE }
          })
        );
        const remainingPages = Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) => index + 1);
        const extraPages = await Promise.all(
          remainingPages.map(async (page) =>
            unwrapApi(
              await http.get<PagePayload<AttendeeDto>>("/admin/attendees", {
                params: { meetingId: backendId(meetingId), page, size: ADMIN_PAGE_SIZE }
              })
            )
          )
        );
        const content = [firstPage, ...extraPages].flatMap((page) => page.content);
        this.attendees = [
          ...this.attendees.filter((attendee) => attendee.meetingId !== meetingId),
          ...content.map((item) => mapAttendee(item, meetingId))
        ];
      });
    },
    async searchAttendees(filters: { keyword?: string; auditStatus?: string | null; attendeeType?: string | null; hasSeat?: boolean | null; tags?: string[] }) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const keyword = filters.keyword?.trim();
      const tags = filters.tags?.filter(Boolean) || [];
      try {
        const page = unwrapApi(
          await http.get<PagePayload<AttendeeDto>>("/admin/attendees", {
            params: {
              meetingId: backendId(meetingId),
              keyword: keyword || undefined,
              reviewStatus: filters.auditStatus || undefined,
              attendeeType: filters.attendeeType || undefined,
              hasSeat: filters.hasSeat == null ? undefined : filters.hasSeat,
              tags: tags.length ? tags.join(",") : undefined,
              page: 0,
              size: ADMIN_PAGE_SIZE
            }
          })
        );
        const mapped = page.content.map((item) => mapAttendee(item, meetingId));
        for (const attendee of mapped) {
          const index = this.attendees.findIndex((item) => item.id === attendee.id);
          if (index >= 0) this.attendees[index] = { ...this.attendees[index], ...attendee };
          else this.attendees.push(attendee);
        }
        this.reconcileAssignments();
        const resultIds = new Set(mapped.map((item) => item.id));
        return this.currentAttendees.filter((item) => resultIds.has(item.id));
      } catch {
        // 后端筛选接口不可用时不打断页面操作，改走与后端参数语义一致的本地筛选。
        return this.filterAttendeesLocally(filters);
      }
    },
    async loadSeats(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(seatsLoadPromises, meetingId, async () => {
        const seats = unwrapApi(await http.get<SeatViewDto[]>(`/admin/seating/map/${backendId(meetingId)}`));
        this.seats = [...this.seats.filter((seat) => seat.meetingId !== meetingId), ...seats.map((item) => mapSeat(item, meetingId))];
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
        const rooms = unwrapApi(await http.get<RoomDto[]>("/admin/rooms", { params: { meetingId: backendId(meetingId) } }));
        this.rooms = [...this.rooms.filter((room) => room.meetingId !== meetingId), ...rooms.map((item) => mapRoom(item, meetingId))];
      });
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
    async loadSyncLogs(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const page = unwrapApi(
        await http.get<PagePayload<SyncLogDto>>("/admin/tduck/sync-logs", {
          params: { meetingId: backendId(meetingId), page: 0, size: 50 }
        })
      );
      this.syncLogs = [...this.syncLogs.filter((log) => log.meetingId !== meetingId), ...page.content.map((item) => mapSyncLog(item, meetingId))];
    },
    async loadCheckinRecords(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const page = unwrapApi(
        await http.get<PagePayload<CheckinRecordDto>>("/admin/checkin/records", {
          params: { meetingId: backendId(meetingId), page: 0, size: 50 }
        })
      );
      this.checkinRecords = [
        ...this.checkinRecords.filter((record) => record.meetingId !== meetingId),
        ...page.content.map((item) => mapCheckinRecord(item, meetingId, this.attendees))
      ];
    },
    async loadStatistics(meetingId?: string) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      return singleFlight(statisticsLoadPromises, meetingId, async () => {
        this.overview = unwrapApi(await http.get<StatisticsOverviewDto>("/admin/statistics/overview", { params: { meetingId: backendId(meetingId) } }));
      });
    },
    async loadStatisticsWithRange(meetingId: string | undefined, filters: { startDate?: string; endDate?: string }) {
      meetingId = requireMeetingId(meetingId || this.currentMeetingId);
      const requestKey = [meetingId, filters.startDate || "", filters.endDate || ""].join(":");
      return singleFlight(statisticsLoadPromises, requestKey, async () => {
        this.overview = unwrapApi(
          await http.get<StatisticsOverviewDto>("/admin/statistics/overview", {
            params: {
              meetingId: backendId(meetingId),
              startDate: filters.startDate || undefined,
              endDate: filters.endDate || undefined
            }
          })
        );
      });
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
          name: request.name,
          organizer: request.organizer,
          venue: request.venueName || "",
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
          checkinEnabled: payload.checkinEnabled ?? true,
          publicQueryFields: payload.publicQueryFields || ["PHONE", "NAME"],
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
          name: request.name,
          organizer: request.organizer,
          coOrganizer: request.coOrganizer,
          venue: request.venueName || "",
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
          checkinEnabled: request.checkinEnabled,
          publicQueryFields: request.publicQueryFields,
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
                seatQueried: attendee?.seatQueried,
                roomQueried: attendee?.roomQueried
              }
            : item
        );
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "标签保存失败"));
        // 本地演示或后端接口尚未就绪时仍允许维护标签，保证管理入口可用。
        this.attendees = this.attendees.map((attendee) => (attendee.id === attendeeId ? { ...attendee, tags: normalizedTags } : attendee));
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
        if (room && !this.attendees.some((item) => item.id !== attendeeId && (item.roomId === room.id || item.roomNo === room.roomNo))) {
          room.status = "AVAILABLE";
        }
      }
      this.reconcileAssignments();
      this.refreshMeetingCounters();
    },
    async importAttendees(file: File) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const result = await this.uploadImport("/admin/attendees/import", file, "参会人");
      await this.loadAttendees(meetingId);
      this.refreshMeetingCounters();
      return result;
    },
    async importRooms(file: File) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const result = await this.uploadImport("/admin/rooms/import", file, "房间号");
      await this.loadRooms(meetingId);
      return result;
    },
    async importRoomAssignments(file: File) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const result = await this.uploadImport("/admin/rooms/assignments/import", file, "房间分配");
      await Promise.allSettled([this.loadRooms(meetingId), this.loadAttendees(meetingId)]);
      this.reconcileAssignments();
      this.refreshMeetingCounters();
      return result;
    },
    async createExportTask(type: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        const task = unwrapApi(
          await http.post<ExportTaskWireDto>("/admin/exports", null, {
            params: { meetingId: backendId(meetingId), type }
          })
        );
        return mapExportTask(task);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "导出任务创建失败"));
        return {
          taskId: `local-${Date.now()}`,
          meetingId: Number(meetingId) || 0,
          type,
          status: "LOCAL",
          createdAt: new Date().toISOString()
        };
      }
    },
    async loadExportTasks() {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        const tasks = unwrapApi(
          await http.get<ExportTaskWireDto[] | PagePayload<ExportTaskWireDto>>("/admin/exports", {
            params: { meetingId: backendId(meetingId) }
          })
        );
        const content = Array.isArray(tasks) ? tasks : tasks.content;
        return content.map(mapExportTask);
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "导出任务加载失败"));
        // 当前后端可能只实现创建/下载，列表接口不可用时由页面保留本地任务缓存。
        return null;
      }
    },
    async approveExportTask(taskId: string | number, approved: boolean, message?: string) {
      const task = unwrapApi(
        await http.post<ExportTaskWireDto>(`/admin/exports/${backendId(idOf(taskId))}/approval`, {
          approved,
          message
        })
      );
      return mapExportTask(task);
    },
    async downloadExport(type: string, options: { format?: "csv" | "xlsx"; startDate?: string; endDate?: string } = {}) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const response = await http.get<Blob>("/admin/exports/download", {
        params: {
          meetingId: backendId(meetingId),
          type,
          format: options.format || "csv",
          startDate: options.startDate || undefined,
          endDate: options.endDate || undefined
        },
        responseType: "blob"
      });
      const fallbackFilename = `meetingos-${type}.${options.format || "csv"}`;
      downloadBlob(response.data, filenameFromDisposition(response.headers["content-disposition"], fallbackFilename));
    },
    async uploadImport(url: string, file: File, subject: string) {
      const meetingId = requireMeetingId(this.currentMeetingId);
      const form = new FormData();
      form.append("file", file);
      try {
        return unwrapApi(
          await http.post<ImportResultDto>(url, form, {
            params: { meetingId: backendId(meetingId) }
          })
        );
      } catch (error) {
        if (!canUseDemoFallback(error)) throw error;
        return {
          totalRows: 0,
          importedRows: 0,
          skippedRows: 0,
          message: `当前处于本地演示模式，${subject}文件未提交到后端`,
          failedRows: []
        };
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
        const attendee = this.attendees.find((item) => item.id === idOf(response.attendeeId)) || this.attendees.find((item) => item.phone === response.phone);
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
        return this.attendees.find((item) => item.id === idOf(response.attendeeId)) || this.currentAttendees[0];
      } catch (error) {
        if (!canUseDemoFallback(error)) throw new Error(extractErrorMessage(error, "签到失败"));
        return this.checkinByCodeLocally(input, entrance, error, options);
      }
    },
    async loadAdminUsers() {
      try {
        const users = unwrapApi(await http.get<AdminUserDto[] | PagePayload<AdminUserDto>>("/admin/users"));
        return Array.isArray(users) ? users : users.content;
      } catch {
        // 用户/权限模块属于 MVP 后续增强；接口不可用时展示占位数据，不影响系统设置页进入。
        return getLocalAdminUsers();
      }
    },
    async loadMeetingUserPermissions() {
      const meetingId = requireMeetingId(this.currentMeetingId);
      try {
        const permissions = unwrapApi(
          await http.get<MeetingUserPermissionDto[] | PagePayload<MeetingUserPermissionDto>>("/admin/permissions/meeting-users", {
            params: { meetingId: backendId(meetingId) }
          })
        );
        return Array.isArray(permissions) ? permissions : permissions.content;
      } catch {
        return getLocalMeetingUserPermissions(meetingId);
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
    filterAttendeesLocally(filters: { keyword?: string; auditStatus?: string | null; attendeeType?: string | null; hasSeat?: boolean | null; tags?: string[] }) {
      const keyword = filters.keyword?.trim();
      const tags = filters.tags?.filter(Boolean) || [];
      return this.currentAttendees.filter((item) => {
        const text = `${item.name}${item.phone}${item.company}`;
        const tagMatched = !tags.length || tags.every((tag) => item.tags.includes(tag));
        const seatMatched = filters.hasSeat == null || Boolean(item.seatLabel) === filters.hasSeat;
        return (
          (!keyword || text.includes(keyword)) &&
          (!filters.auditStatus || item.auditStatus === filters.auditStatus) &&
          (!filters.attendeeType || item.attendeeType === filters.attendeeType) &&
          seatMatched &&
          tagMatched
        );
      });
    },
    reconcileAssignments() {
      const seatLabelByAttendee = new Map<string, string>();
      for (const seat of this.currentSeats) {
        if (seat.attendeeId) {
          seatLabelByAttendee.set(seat.attendeeId, `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座`);
        }
      }
      const roomByNo = new Map(this.currentRooms.map((room) => [room.roomNo, room]));
      this.attendees = this.attendees.map((attendee) => {
        if (attendee.meetingId !== this.currentMeetingId) return attendee;
        const room = attendee.roomNo ? roomByNo.get(attendee.roomNo) : undefined;
        return {
          ...attendee,
          seatLabel: seatLabelByAttendee.get(attendee.id),
          roomId: room?.id || attendee.roomId
        };
      });
    },
    refreshMeetingCounters() {
      for (const meeting of this.meetings) {
        const attendees = this.attendees.filter((item) => item.meetingId === meeting.id);
        meeting.registeredCount = attendees.length || meeting.registeredCount || 0;
        meeting.approvedCount = attendees.filter((item) => item.auditStatus === "APPROVED").length || meeting.approvedCount || 0;
        meeting.seatedCount = attendees.filter((item) => Boolean(item.seatLabel)).length || meeting.seatedCount || 0;
        meeting.roomedCount = attendees.filter((item) => Boolean(item.roomNo)).length || meeting.roomedCount || 0;
        meeting.checkedInCount = attendees.filter((item) => item.checkinStatus === "CHECKED_IN").length || meeting.checkedInCount || 0;
      }
    },
    upsertMeeting(meeting: Meeting) {
      const index = this.meetings.findIndex((item) => item.id === meeting.id);
      if (index >= 0) this.meetings[index] = { ...this.meetings[index], ...meeting };
      else this.meetings.unshift(meeting);
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
    },
    clearSeatLocally(attendeeId: string) {
      const attendee = this.attendees.find((item) => item.id === attendeeId);
      for (const seat of this.seats.filter((item) => item.attendeeId === attendeeId)) {
        seat.status = "EMPTY";
        seat.attendeeId = undefined;
        seat.attendeeName = undefined;
        seat.attendeeTags = undefined;
      }
      if (attendee) attendee.seatLabel = undefined;
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
      const parsed = parseCheckinCode(input);
      const attendee =
        this.attendees.find((item) => item.attendeeCode === parsed.attendeeCode && item.meetingId === parsed.meetingId) ||
        this.attendees.find((item) => item.id === parsed.attendeeCode && item.meetingId === parsed.meetingId) ||
        this.attendees.find((item) => item.phone === input || item.name.includes(input));
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
    name: item.name,
    organizer: "主办方",
    venue: item.location || "",
    startTime: item.startTime || "",
    endTime: item.endTime || "",
    status: item.status,
    capacity: 0,
    registeredCount: item.registrationCount || 0,
    approvedCount: item.approvedCount || 0,
    seatedCount: item.seatedCount || 0,
    roomedCount: item.roomAssignedCount || 0,
    checkedInCount: item.checkedInCount || 0,
    tduckFormKey: "",
    tduckFormName: "",
    registrationUrl: "",
    seatQueryEnabled: Boolean(item.seatQueryEnabled),
    roomQueryEnabled: Boolean(item.roomQueryEnabled),
    checkinEnabled: item.checkinEnabled ?? true,
    publicQueryFields: parseQueryFields(item.publicQueryFields),
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
    checkedInCount: item.checkedInCount || 0
  };
}

function mapMeetingEntity(item: MeetingDetailDto["meeting"]): Meeting {
  return {
    id: idOf(item.id),
    code: item.code,
    name: item.name,
    organizer: item.organizer || "主办方",
    coOrganizer: item.coOrganizer || "",
    venue: item.venueName || item.location || "",
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
    checkedInCount: 0,
    tduckFormKey: "",
    tduckFormName: "",
    registrationUrl: "",
    seatQueryEnabled: Boolean(item.seatQueryEnabled),
    roomQueryEnabled: Boolean(item.roomQueryEnabled),
    checkinEnabled: item.checkinEnabled ?? true,
    publicQueryFields: parseQueryFields(item.publicQueryFields),
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
    remark: item.remark,
    tags: splitTags(item.tags),
    auditStatus: item.reviewStatus || "PENDING",
    auditReason: item.reviewReason,
    auditedAt: item.reviewedAt,
    registeredAt: item.registeredAt || "",
    roomNo: item.roomNo,
    roomRemark: item.roomRemark,
    seatQueried: Boolean(item.seatQueried),
    roomQueried: Boolean(item.roomQueried),
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

function parseQueryFields(value?: string | PublicQueryField[]) {
  if (Array.isArray(value)) return value.length ? value : (["PHONE", "NAME"] as PublicQueryField[]);
  const fields = (value || "PHONE,NAME")
    .split(",")
    .map((item) => item.trim())
    .filter((item): item is PublicQueryField => defaultQueryFieldOptions.some((option) => option.field === item));
  return fields.length ? fields : (["PHONE", "NAME"] as PublicQueryField[]);
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

function mergeResultFields(primary: PublicQueryResultField[], secondary: PublicQueryResultField[]) {
  return Array.from(new Set([...primary, ...secondary]));
}

function mapQuerySettings(item: MeetingQuerySettingsDto): MeetingQuerySettings {
  const visibleResultFields = parseResultFields(item.visibleResultFields, defaultVisibleResultFields);
  const maskedResultFields = parseResultFields(item.maskedResultFields, []);
  const highlightResultFields = parseResultFields(item.highlightResultFields, ["SEAT_CODE", "ROOM_NO"]);
  const seatVisibleResultFields = parseResultFields(item.seatVisibleResultFields, seatResultFields(visibleResultFields).length ? seatResultFields(visibleResultFields) : defaultSeatVisibleResultFields);
  const seatMaskedResultFields = parseResultFields(item.seatMaskedResultFields, seatResultFields(maskedResultFields));
  const seatHighlightResultFields = parseResultFields(item.seatHighlightResultFields, seatResultFields(highlightResultFields).length ? seatResultFields(highlightResultFields) : ["SEAT_CODE"]);
  const roomVisibleResultFields = parseResultFields(item.roomVisibleResultFields, roomResultFields(visibleResultFields).length ? roomResultFields(visibleResultFields) : defaultRoomVisibleResultFields);
  const roomMaskedResultFields = parseResultFields(item.roomMaskedResultFields, roomResultFields(maskedResultFields));
  const roomHighlightResultFields = parseResultFields(item.roomHighlightResultFields, roomResultFields(highlightResultFields).length ? roomResultFields(highlightResultFields) : ["ROOM_NO"]);
  return {
    meetingId: idOf(item.meetingId),
    meetingCode: item.meetingCode,
    meetingName: item.meetingName,
    seatQueryEnabled: item.seatQueryEnabled,
    roomQueryEnabled: item.roomQueryEnabled,
    checkinEnabled: item.checkinEnabled ?? true,
    queryPaused: Boolean(item.queryPaused),
    fields: parseQueryFields(item.fields),
    matchMode: item.matchMode || "FUZZY",
    pageTitle: item.pageTitle || `${item.meetingName}查询`,
    pageDescription: item.pageDescription,
    announcement: item.announcement,
    publisher: item.publisher,
    startTime: item.startTime,
    endTime: item.endTime,
    buttonText: item.buttonText || "立即查询",
    emptyMessage: item.emptyMessage || "未查询到结果，请核对信息后重试",
    seatNotice: item.seatNotice || "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。",
    roomNotice: item.roomNotice || "凭会议证件及身份证办理入住，接待台位于酒店大堂。",
    themeColor: item.themeColor || "#2563eb",
    logoUrl: item.logoUrl,
    backgroundUrl: item.backgroundUrl,
    resultLayout: normalizeResultLayout(item.resultLayout),
    visibleResultFields: mergeResultFields(seatVisibleResultFields, roomVisibleResultFields),
    maskedResultFields: mergeResultFields(seatMaskedResultFields, roomMaskedResultFields),
    highlightResultFields: mergeResultFields(seatHighlightResultFields, roomHighlightResultFields),
    seatVisibleResultFields,
    seatMaskedResultFields,
    seatHighlightResultFields,
    roomVisibleResultFields,
    roomMaskedResultFields,
    roomHighlightResultFields,
    captchaEnabled: Boolean(item.captchaEnabled),
    fieldOptions: item.fieldOptions?.length ? item.fieldOptions : defaultQueryFieldOptions,
    resultFieldOptions: item.resultFieldOptions?.length ? item.resultFieldOptions : defaultResultFieldOptions
  };
}

function meetingToQuerySettings(meeting?: Meeting | null): MeetingQuerySettings {
  const meetingName = meeting?.name || "当前会议";
  return {
    meetingId: meeting?.id || "",
    meetingCode: meeting?.code || "",
    meetingName,
    seatQueryEnabled: meeting?.seatQueryEnabled ?? true,
    roomQueryEnabled: meeting?.roomQueryEnabled ?? true,
    checkinEnabled: meeting?.checkinEnabled ?? true,
    queryPaused: false,
    fields: meeting?.publicQueryFields?.length ? meeting.publicQueryFields : ["PHONE", "NAME"],
    matchMode: meeting?.publicQueryMatchMode || "FUZZY",
    pageTitle: `${meetingName}查询`,
    pageDescription: "",
    announcement: "",
    publisher: "",
    buttonText: "立即查询",
    emptyMessage: "未查询到结果，请核对信息后重试",
    seatNotice: "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。",
    roomNotice: "凭会议证件及身份证办理入住，接待台位于酒店大堂。",
    themeColor: "#2563eb",
    resultLayout: "CARD",
    visibleResultFields: [...defaultVisibleResultFields],
    maskedResultFields: [],
    highlightResultFields: ["SEAT_CODE", "ROOM_NO"],
    seatVisibleResultFields: [...defaultSeatVisibleResultFields],
    seatMaskedResultFields: [],
    seatHighlightResultFields: ["SEAT_CODE"],
    roomVisibleResultFields: [...defaultRoomVisibleResultFields],
    roomMaskedResultFields: [],
    roomHighlightResultFields: ["ROOM_NO"],
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
  payload: { meetingId?: string | number | null; meetingCode?: string | null; values: Record<PublicQueryField, string> }
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
  payload: { meetingId?: string | number | null; meetingCode?: string | null; values: Record<PublicQueryField, string> }
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

function localPublicSelfCheckin(
  store: ReturnType<typeof useMeetingStore>,
  payload: { meetingId?: string | number | null; meetingCode?: string | null; values: Record<PublicQueryField, string> }
): PublicCheckinResult {
  const meeting = resolveLocalMeeting(store, payload);
  if (!meeting.checkinEnabled) throw new Error("签到尚未开放");
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

function resolveLocalMeeting(store: ReturnType<typeof useMeetingStore>, payload: { meetingId?: string | number | null; meetingCode?: string | null }) {
  const meeting =
    store.meetings.find((item) => item.id === String(payload.meetingId)) ||
    store.meetings.find((item) => item.code === payload.meetingCode) ||
    store.currentMeeting;
  if (!meeting) throw new Error("会议不存在");
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
  for (const field of fields) {
    if (!values[field]?.trim()) throw new Error(`请填写${queryFieldLabel(field)}`);
  }
  return store.attendees
    .filter((attendee) => attendee.meetingId === meeting.id)
    .filter((attendee) => fields.every((field) => localFieldMatches(attendee, field, values[field], meeting.publicQueryMatchMode)));
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
    errorMessage: item.status === "FAILED" ? item.message : undefined,
    createdAt: item.createdAt || ""
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
    message: item.message
  };
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
    maskSensitive: value.maskSensitive ?? true,
    exportApproval: value.exportApproval ?? false
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
