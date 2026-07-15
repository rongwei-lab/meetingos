import type { InvitationShareLink, InvitationStatus } from "@/types/invitation";

export interface InvitationPublicationState {
  id: number;
  status: InvitationStatus;
}

export interface InvitationShareLinkAvailability {
  available: boolean;
  reason: string | null;
}

const LOCAL_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/;

export function invitationShareLinkAvailability(
  link: InvitationShareLink,
  invitations: readonly InvitationPublicationState[],
  now = Date.now()
): InvitationShareLinkAvailability {
  const invitation = invitations.find((item) => item.id === link.invitationId);
  if (!invitation || invitation.status !== "PUBLISHED") {
    return { available: false, reason: invitation ? "方案未发布" : "方案状态未知" };
  }
  if (!link.enabled) return { available: false, reason: "渠道已停用" };
  if (!link.currentVersionNo) return { available: false, reason: "尚无发布版本" };
  if (link.expiresAt) {
    const expiresAt = parseServerLocalDateTime(link.expiresAt);
    if (expiresAt === null) return { available: false, reason: "失效时间无效" };
    if (expiresAt <= now) return { available: false, reason: "渠道已过期" };
  }
  return { available: true, reason: null };
}

export function isInvitationShareLinkAvailable(
  link: InvitationShareLink,
  invitations: readonly InvitationPublicationState[],
  now = Date.now()
) {
  return invitationShareLinkAvailability(link, invitations, now).available;
}

export function normalizeInvitationLocalDateTime(value: string | null | undefined) {
  if (!value) return null;
  if (parseServerLocalDateTime(value) === null || !LOCAL_DATE_TIME_PATTERN.test(value)) {
    throw new Error("失效时间格式无效，请重新选择");
  }
  // 后端字段是 LocalDateTime，必须保留用户选择的本地墙上时间，不能转成带 Z 的 UTC 时间。
  return value;
}

function parseServerLocalDateTime(value: string) {
  const match = value.match(LOCAL_DATE_TIME_PATTERN);
  if (!match) return null;
  const [, yearText, monthText, dayText, hourText, minuteText, secondText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  const local = new Date(year, month - 1, day, hour, minute, second, 0);
  if (
    local.getFullYear() !== year
    || local.getMonth() !== month - 1
    || local.getDate() !== day
    || local.getHours() !== hour
    || local.getMinutes() !== minute
    || local.getSeconds() !== second
  ) {
    return null;
  }
  return local.getTime();
}
