import type {
  InvitationElementV1,
  InvitationDesignDocumentV1,
  InvitationNavigationTarget,
  InvitationQrPurpose,
  InvitationQueryTarget
} from "@/types/invitation";

export const INVITATION_QUERY_TARGET_OPTIONS: Array<{ label: string; value: InvitationQueryTarget }> = [
  { label: "查询导航页", value: "QUERY_ENTRY" },
  { label: "综合查询", value: "COMBINED_QUERY" },
  { label: "座位查询", value: "SEAT_QUERY" },
  { label: "住宿查询", value: "ROOM_QUERY" },
  { label: "晚宴查询", value: "DINNER_QUERY" }
];

export const INVITATION_QR_PURPOSE_OPTIONS: Array<{ label: string; value: InvitationQrPurpose }> = [
  { label: "报名", value: "REGISTRATION" },
  ...INVITATION_QUERY_TARGET_OPTIONS,
  { label: "原渠道邀请函（兼容）", value: "CHANNEL_INVITATION" }
];

const QUERY_TARGET_PATHS: Record<InvitationQueryTarget, string> = {
  QUERY_ENTRY: "/public/entry",
  COMBINED_QUERY: "/public/query",
  SEAT_QUERY: "/public/seat-query",
  ROOM_QUERY: "/public/room-query",
  DINNER_QUERY: "/public/dinner-query"
};

const DEFAULT_LABELS: Record<InvitationQrPurpose, string> = {
  REGISTRATION: "扫码报名",
  QUERY_ENTRY: "扫码查询",
  COMBINED_QUERY: "扫码综合查询",
  SEAT_QUERY: "扫码查询座位",
  ROOM_QUERY: "扫码查询住宿",
  DINNER_QUERY: "扫码查询晚宴",
  CHANNEL_INVITATION: "扫码查看邀请函"
};

const DEFAULT_QUERY_BUTTON_TEXT: Record<InvitationQueryTarget, string> = {
  QUERY_ENTRY: "立即查询",
  COMBINED_QUERY: "立即查询",
  SEAT_QUERY: "查询座位",
  ROOM_QUERY: "查询住宿",
  DINNER_QUERY: "查询晚宴"
};

const QR_PURPOSES = new Set<InvitationQrPurpose>(INVITATION_QR_PURPOSE_OPTIONS.map((item) => item.value));
const QUERY_TARGETS = new Set<InvitationQueryTarget>(INVITATION_QUERY_TARGET_OPTIONS.map((item) => item.value));

export interface InvitationActionContext {
  origin?: string;
  meetingCode?: string | null;
  publicQueryToken?: string | null;
  shareToken?: string | null;
  shareUrl?: string | null;
}

export interface ResolvedInvitationTarget {
  url: string;
  unavailableReason: string;
}

export function isInvitationQueryTarget(value: unknown): value is InvitationQueryTarget {
  return typeof value === "string" && QUERY_TARGETS.has(value as InvitationQueryTarget);
}

export function isInvitationQrPurpose(value: unknown): value is InvitationQrPurpose {
  return typeof value === "string" && QR_PURPOSES.has(value as InvitationQrPurpose);
}

export function normalizeInvitationQueryTarget(value: unknown): InvitationQueryTarget {
  return isInvitationQueryTarget(value) ? value : "COMBINED_QUERY";
}

export function normalizeInvitationQrPurpose(value: unknown): InvitationQrPurpose {
  return isInvitationQrPurpose(value)
    ? value
    : "CHANNEL_INVITATION";
}

export function invitationQrDefaultLabel(purpose: InvitationQrPurpose) {
  return DEFAULT_LABELS[purpose];
}

export function invitationQrLabel(element: InvitationElementV1) {
  if (element.props.label !== undefined && element.props.label !== null) return String(element.props.label);
  // 历史二维码没有 purpose，当时默认文案就是“扫码报名”，不能因兼容解析而改变已发布版式。
  if (element.props.purpose === undefined || element.props.purpose === null) return "扫码报名";
  return invitationQrDefaultLabel(normalizeInvitationQrPurpose(element.props.purpose));
}

export function invitationQueryButtonDefaultText(target: InvitationQueryTarget) {
  return DEFAULT_QUERY_BUTTON_TEXT[target];
}

export function resolveInvitationNavigationTarget(
  target: InvitationNavigationTarget,
  context: InvitationActionContext
): ResolvedInvitationTarget {
  const token = String(context.publicQueryToken || "").trim();
  if (!token) return { url: "", unavailableReason: "会议未配置公开查询令牌" };
  const path = target === "SELF_CHECKIN" ? "/public/checkin" : QUERY_TARGET_PATHS[target];
  return { url: absoluteUrl(path, context.origin, { token }), unavailableReason: "" };
}

export function resolveInvitationQrTarget(
  element: InvitationElementV1,
  context: InvitationActionContext
): ResolvedInvitationTarget {
  const rawPurpose = element.props.purpose;
  if (rawPurpose !== undefined && rawPurpose !== null
      && (typeof rawPurpose !== "string" || !QR_PURPOSES.has(rawPurpose as InvitationQrPurpose))) {
    return { url: "", unavailableReason: "二维码用途不受支持" };
  }
  const purpose = normalizeInvitationQrPurpose(rawPurpose);
  if (purpose === "CHANNEL_INVITATION") {
    const shareUrl = String(context.shareUrl || "").trim();
    if (shareUrl) return { url: shareUrl, unavailableReason: "" };
    const shareToken = String(context.shareToken || "").trim();
    return shareToken
      ? { url: absoluteUrl(`/i/${encodeURIComponent(shareToken)}`, context.origin), unavailableReason: "" }
      : { url: "", unavailableReason: "请先创建并启用邀请渠道" };
  }
  if (purpose === "REGISTRATION") {
    const meetingCode = String(context.meetingCode || "").trim();
    if (!meetingCode) return { url: "", unavailableReason: "会议缺少报名编号" };
    const shareToken = String(context.shareToken || extractShareToken(context.shareUrl, context.origin)).trim();
    if (!shareToken) return { url: "", unavailableReason: "请先创建并启用邀请渠道" };
    const url = new URL(`/r/${encodeURIComponent(meetingCode)}`, resolveOrigin(context.origin));
    url.hash = `mos_inv=${shareToken}`;
    return { url: url.toString(), unavailableReason: "" };
  }
  return resolveInvitationNavigationTarget(purpose, context);
}

export function extractInvitationShareTokenFragment(hash: string | null | undefined) {
  const match = String(hash || "").match(/^#mos_inv=([A-Za-z0-9_-]{16,96})$/);
  return match?.[1] || "";
}

export function invitationDocumentRequiresPublicQueryToken(document: InvitationDesignDocumentV1) {
  return [...document.h5.elements, ...document.poster.elements].some((element) => {
    if (element.type === "CHECKIN_BUTTON" || element.type === "QUERY_BUTTON") return true;
    if (element.type !== "CHANNEL_QR_CODE" || element.props.purpose == null) return false;
    return isInvitationQueryTarget(element.props.purpose);
  });
}

function extractShareToken(shareUrl: string | null | undefined, origin?: string) {
  if (!shareUrl) return "";
  try {
    const match = new URL(shareUrl, resolveOrigin(origin)).pathname.match(/\/i\/([^/]+)\/?$/);
    return match ? decodeURIComponent(match[1] || "") : "";
  } catch {
    return "";
  }
}

function absoluteUrl(path: string, origin?: string, query?: Record<string, string>) {
  const url = new URL(path, resolveOrigin(origin));
  Object.entries(query || {}).forEach(([key, value]) => url.searchParams.set(key, value));
  return url.toString();
}

function resolveOrigin(origin?: string) {
  if (origin) return origin;
  if (typeof window !== "undefined" && window.location?.origin) return window.location.origin;
  return "http://localhost";
}
