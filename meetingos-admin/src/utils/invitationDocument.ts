import type {
  InvitationCanvasV1,
  InvitationDesignDocumentV1,
  InvitationElementType,
  InvitationElementV1,
  InvitationMeetingField,
  InvitationMeetingSnapshot
} from "@/types/invitation";
import { isSupportedInvitationShape } from "@/utils/invitationShape";
import { isInvitationQrPurpose, isInvitationQueryTarget } from "@/utils/invitationActions";

export const H5_CANVAS_WIDTH = 375;
export const H5_MIN_HEIGHT = 667;
export const H5_MAX_HEIGHT = 6000;
export const POSTER_CANVAS_WIDTH = 1080;
export const POSTER_CANVAS_HEIGHT = 1920;
export const MAX_INVITATION_ELEMENTS = 200;
export const MAX_INVITATION_DOCUMENT_BYTES = 1024 * 1024;

const POSTER_TYPES = new Set<InvitationElementType>([
  "TEXT",
  "IMAGE",
  "SHAPE",
  "DIVIDER",
  "MEETING_FIELD",
  "REGISTRATION_BUTTON",
  "CHANNEL_QR_CODE",
  "COUNTDOWN"
]);

export const DEFAULT_MEETING_IMAGE =
  "https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1200&q=88";

export function createElementId(prefix = "element") {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID().replace(/-/g, "")
    : Math.random().toString(36).slice(2, 14);
  return `${prefix}_${random.slice(0, 20)}`;
}

export function cloneInvitationDocument(document: InvitationDesignDocumentV1): InvitationDesignDocumentV1 {
  return JSON.parse(JSON.stringify(document)) as InvitationDesignDocumentV1;
}

export function createDefaultInvitationDocument(meeting?: Partial<InvitationMeetingSnapshot> | null): InvitationDesignDocumentV1 {
  const meetingName = meeting?.name || "2026 数字创新峰会";
  const start = formatMeetingDate(meeting?.startTime) || "2026年9月18日";
  const venue = meeting?.venueName || meeting?.location || "上海国际会议中心";
  const h5Elements: InvitationElementV1[] = [
    element("TEXT", 24, 54, 327, 74, 1, { text: meetingName, fontSize: 34, fontWeight: 700, color: "#13211a", lineHeight: 1.15 }),
    element("MEETING_FIELD", 24, 142, 327, 28, 2, { field: "START_TIME", labelPrefix: "时间  ", fontSize: 16, fontWeight: 500, color: "#174d37", lineHeight: 1.4 }),
    element("MEETING_FIELD", 24, 178, 327, 28, 3, { field: "VENUE_NAME", labelPrefix: "地点  ", fontSize: 16, fontWeight: 500, color: "#174d37", lineHeight: 1.4 }),
    element("MEETING_FIELD", 0, 226, 375, 230, 4, { field: "COVER_IMAGE", labelPrefix: "" }),
    element("COUNTDOWN", 20, 476, 335, 72, 5, { target: meeting?.startTime || "2026-09-18T09:00:00+08:00", format: "DD天 HH时 mm分 ss秒", fontSize: 22, fontWeight: 700, color: "#08784f" }),
    element("TEXT", 24, 584, 327, 34, 6, { text: "大会议程", fontSize: 24, fontWeight: 700, color: "#102b21", lineHeight: 1.2 }),
    element("MEETING_FIELD", 24, 632, 327, 180, 7, { field: "AGENDA", labelPrefix: "", fontSize: 15, fontWeight: 400, color: "#334a40", lineHeight: 1.75 }),
    element("DIVIDER", 24, 844, 327, 1, 8, { color: "#dce9e3", thickness: 1, style: "solid" }),
    element("TEXT", 24, 880, 327, 56, 9, { text: `主办方  ${meeting?.organizer || "MeetingOS 大会组委会"}`, fontSize: 15, fontWeight: 500, color: "#445a51", lineHeight: 1.4 }),
    element("REGISTRATION_BUTTON", 20, 980, 335, 54, 10, { text: "立即报名", fill: "#08784f", color: "#ffffff", borderRadius: 6, fontSize: 20, fontWeight: 700 })
  ];

  const posterElements: InvitationElementV1[] = [
    element("MEETING_FIELD", 0, 0, 1080, 820, 1, { field: "COVER_IMAGE", labelPrefix: "" }),
    element("TEXT", 78, 900, 924, 210, 2, { text: meetingName, fontSize: 78, fontWeight: 700, color: "#102b21", textAlign: "center", lineHeight: 1.15 }),
    element("TEXT", 120, 1160, 840, 80, 3, { text: `${start}  ·  ${venue}`, fontSize: 34, fontWeight: 500, color: "#356252", textAlign: "center", lineHeight: 1.2 }),
    element("REGISTRATION_BUTTON", 120, 1340, 840, 142, 4, { text: "扫码立即报名", fill: "#08784f", color: "#ffffff", borderRadius: 18, fontSize: 48, fontWeight: 700 }),
    element("CHANNEL_QR_CODE", 390, 1530, 300, 300, 5, { purpose: "REGISTRATION", foregroundColor: "#0b3425", backgroundColor: "#ffffff", label: "扫码报名" })
  ];

  return {
    version: 1,
    h5: { width: H5_CANVAS_WIDTH, height: 1100, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: h5Elements },
    poster: { width: POSTER_CANVAS_WIDTH, height: POSTER_CANVAS_HEIGHT, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: posterElements }
  };
}

export function createBlankInvitationDocument(): InvitationDesignDocumentV1 {
  return {
    version: 1,
    h5: { width: H5_CANVAS_WIDTH, height: H5_MIN_HEIGHT, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: [] },
    poster: { width: POSTER_CANVAS_WIDTH, height: POSTER_CANVAS_HEIGHT, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: [] }
  };
}

export function validateInvitationDocument(document: InvitationDesignDocumentV1, amapConfigured: boolean): string[] {
  const errors: string[] = [];
  if (document.version !== 1) errors.push("设计文档版本必须为 1");
  validateCanvas(document.h5, H5_CANVAS_WIDTH, H5_MIN_HEIGHT, H5_MAX_HEIGHT, false, errors);
  validateCanvas(document.poster, POSTER_CANVAS_WIDTH, POSTER_CANVAS_HEIGHT, POSTER_CANVAS_HEIGHT, true, errors);
  if (!amapConfigured && document.h5.elements.some((item) => item.type === "AMAP")) {
    errors.push("当前环境未配置高德地图密钥，含地图元素的方案不能发布");
  }
  if (new TextEncoder().encode(JSON.stringify(document)).byteLength > MAX_INVITATION_DOCUMENT_BYTES) {
    errors.push("设计文档不能超过 1 MiB");
  }
  return errors;
}

export function resolveMeetingField(field: InvitationMeetingField | string, meeting: Partial<InvitationMeetingSnapshot>): string {
  const value: Record<string, string | null | undefined> = {
    NAME: meeting.name,
    ORGANIZER: meeting.organizer,
    CO_ORGANIZER: meeting.coOrganizer,
    START_TIME: formatMeetingDateTime(meeting.startTime),
    END_TIME: formatMeetingDateTime(meeting.endTime),
    LOCATION: meeting.location,
    VENUE_NAME: meeting.venueName || meeting.location,
    DESCRIPTION: meeting.description,
    AGENDA: meeting.agenda,
    COVER_IMAGE: meeting.coverImageUrl,
    CONTACT_NAME: meeting.contactName,
    CONTACT_PHONE: meeting.contactPhone
  };
  return value[field] || "未配置";
}

export function resolveAssetUrl(assetId: unknown, assetUrls: Map<number, string>, previewUrl?: unknown) {
  const numericId = Number(assetId);
  if (Number.isFinite(numericId) && assetUrls.has(numericId)) return assetUrls.get(numericId) || "";
  return typeof previewUrl === "string" ? previewUrl : "";
}

export function toPosterDocument(document: InvitationDesignDocumentV1): InvitationDesignDocumentV1 {
  const next = cloneInvitationDocument(document);
  next.poster.elements = next.poster.elements.filter((element) => POSTER_TYPES.has(element.type));
  return next;
}

export function formatMeetingDate(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
}

export function formatMeetingDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const minute = String(date.getMinutes()).padStart(2, "0");
  return `${formatMeetingDate(value)} ${String(date.getHours()).padStart(2, "0")}:${minute}`;
}

function element(type: InvitationElementType, x: number, y: number, width: number, height: number, zIndex: number, props: InvitationElementV1["props"]): InvitationElementV1 {
  return { id: createElementId(type.toLowerCase()), type, x, y, width, height, rotation: 0, zIndex, locked: false, props };
}

function validateCanvas(canvas: InvitationCanvasV1, expectedWidth: number, minHeight: number, maxHeight: number, poster: boolean, errors: string[]) {
  if (!canvas || canvas.width !== expectedWidth || canvas.height < minHeight || canvas.height > maxHeight) {
    errors.push(`${poster ? "海报" : "H5"}画布尺寸不合法`);
    return;
  }
  if (!/^#[0-9a-f]{3,8}$/i.test(canvas.backgroundColor)) errors.push(`${poster ? "海报" : "H5"}背景颜色不合法`);
  if (canvas.elements.length > MAX_INVITATION_ELEMENTS) errors.push(`${poster ? "海报" : "H5"}画布最多支持 200 个元素`);
  const ids = new Set<string>();
  canvas.elements.forEach((item) => {
    if (!/^[A-Za-z0-9_-]{1,80}$/.test(item.id) || ids.has(item.id)) errors.push(`元素 ID 重复或不合法：${item.id}`);
    ids.add(item.id);
    if (poster && !POSTER_TYPES.has(item.type)) errors.push(`海报不支持互动元素：${item.type}`);
    if (item.x < 0 || item.y < 0 || item.width <= 0 || item.height <= 0 || item.x + item.width > canvas.width || item.y + item.height > canvas.height) {
      errors.push(`元素超出画布：${item.id}`);
    }
    if (item.props.opacity != null && !isFiniteNumberInRange(item.props.opacity, 0, 1)) {
      errors.push(`元素透明度不合法：${item.id}`);
    }
    if (item.type === "CAROUSEL" && Array.isArray(item.props.assetIds) && item.props.assetIds.length > 10) errors.push("轮播图最多 10 张");
    if (item.type === "QUERY_BUTTON" && !isInvitationQueryTarget(item.props.queryTarget)) {
      errors.push(`查询按钮目标不合法：${item.id}`);
    }
    if ((item.type === "QUERY_BUTTON" || item.type === "CHECKIN_BUTTON")
        && (!String(item.props.text || "").trim() || String(item.props.text).length > 40)) {
      errors.push(`${item.type === "QUERY_BUTTON" ? "查询" : "签到"}按钮文字必须为 1 至 40 个字符`);
    }
    if (item.type === "SHAPE") {
      if (!isSupportedInvitationShape(item.props.shape)) errors.push(`不支持的形状类型：${String(item.props.shape)}`);
      const maximumMetric = Math.min(item.width, item.height) / 2;
      if (item.props.strokeWidth != null && !isFiniteNumberInRange(item.props.strokeWidth, 0, maximumMetric)) {
        errors.push(`形状描边宽度不合法：${item.id}`);
      }
      if (item.props.borderRadius != null && !isFiniteNumberInRange(item.props.borderRadius, 0, maximumMetric)) {
        errors.push(`形状圆角半径不合法：${item.id}`);
      }
    }
    if (item.type === "CHANNEL_QR_CODE") {
      if (item.props.purpose != null && !isInvitationQrPurpose(item.props.purpose)) {
        errors.push(`二维码用途不合法：${item.id}`);
      }
      if (item.props.label != null && String(item.props.label).length > 60) {
        errors.push(`二维码说明最多 60 个字符：${item.id}`);
      }
      const foreground = String(item.props.foregroundColor || "");
      const background = String(item.props.backgroundColor || "");
      if (!/^#[0-9a-f]{6}$/i.test(foreground) || !/^#[0-9a-f]{6}$/i.test(background)) {
        errors.push("渠道二维码前景色和背景色必须是不透明 6 位十六进制颜色");
      } else if (colorContrast(foreground, background) < 4.5) {
        errors.push("渠道二维码前景色与背景色对比度必须至少为 4.5:1");
      }
    }
    if (["BACKGROUND_MUSIC", "VIDEO"].includes(item.type)) {
      const url = String(item.props.url || "");
      if (!url.startsWith("https://")) errors.push(`${item.type === "VIDEO" ? "视频" : "音乐"}仅支持 HTTPS 直链`);
    }
  });
}

function isFiniteNumberInRange(value: unknown, minimum: number, maximum: number) {
  return typeof value === "number" && Number.isFinite(value) && value >= minimum && value <= maximum;
}

function colorContrast(left: string, right: string) {
  const luminance = (color: string) => {
    const channels = [1, 3, 5].map((offset) => Number.parseInt(color.slice(offset, offset + 2), 16) / 255)
      .map((value) => value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
    return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722;
  };
  const first = luminance(left);
  const second = luminance(right);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}
