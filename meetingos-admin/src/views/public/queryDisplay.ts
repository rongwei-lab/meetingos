import type { CSSProperties } from "vue";
import type { DinnerQueryResult, MeetingQuerySettings, PublicQueryResultField, RoomQueryResult, SeatQueryResult } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";

export type QueryResultKind = "seat" | "room" | "dinner";

export interface QueryDisplayRow {
  field: PublicQueryResultField;
  label: string;
  value: string;
  highlighted: boolean;
}

export interface QueryWindowState {
  available: boolean;
  message: string;
}

const sharedFields = new Set<PublicQueryResultField>(["ATTENDEE_NAME", "ATTENDEE_PHONE", "ORGANIZATION", "TITLE", "EMAIL", "MEETING_NAME"]);
const seatFields = new Set<PublicQueryResultField>(["SEAT_AREA", "SEAT_ROW", "SEAT_NO", "SEAT_CODE"]);
const roomFields = new Set<PublicQueryResultField>(["HOTEL_NAME", "ROOM_TYPE", "ROOM_NO", "ROOM_REMARK", "CHECKIN_TIPS"]);
const dinnerFields = new Set<PublicQueryResultField>(["DINNER_TABLE_NO", "DINNER_VENUE", "DINNER_TIME", "DINNER_REMARK"]);

export function pageTitle(settings: MeetingQuerySettings | null, fallback: string) {
  return settings?.pageTitle?.trim() || fallback;
}

export function fieldLabel(settings: MeetingQuerySettings | null, field: PublicQueryResultField) {
  return settings?.resultFieldOptions.find((item) => item.field === field)?.label || field;
}

export function publicPageStyle(settings: MeetingQuerySettings | null): CSSProperties {
  const themeColor = settings?.themeColor || "#2563eb";
  const style: CSSProperties & Record<string, string> = {
    "--public-theme": themeColor,
    "--public-theme-soft": hexToRgba(themeColor, 0.12),
    "--public-theme-border": hexToRgba(themeColor, 0.28)
  };

  const backgroundUrl = sanitizeImageUrl(settings?.backgroundUrl);
  if (backgroundUrl) {
    // 背景图只作为视觉增强，叠加浅色遮罩确保表单和查询结果始终可读。
    const safeUrl = backgroundUrl.replace(/"/g, '\\"');
    style.backgroundImage = `linear-gradient(135deg, rgba(248, 250, 252, 0.9), rgba(239, 246, 255, 0.84)), url("${safeUrl}")`;
  }
  return style;
}

export function resultLayoutClass(settings: MeetingQuerySettings | null) {
  return `result-layout-${(settings?.resultLayout || "CARD").toLowerCase()}`;
}

export function queryWindowState(settings: MeetingQuerySettings | null): QueryWindowState {
  if (!settings) return { available: false, message: "查询设置加载中" };
  if (settings.queryPaused) return { available: false, message: "查询已暂停，请稍后再试" };

  const now = Date.now();
  const start = settings.startTime ? Date.parse(settings.startTime) : null;
  const end = settings.endTime ? Date.parse(settings.endTime) : null;
  if (start && Number.isFinite(start) && now < start) {
    return { available: false, message: "查询尚未开始" };
  }
  if (end && Number.isFinite(end) && now > end) {
    return { available: false, message: "查询已结束" };
  }
  return { available: true, message: "" };
}

export function normalizePublicQueryError(message: string, settings: MeetingQuerySettings | null) {
  if (message.includes("未找到匹配") || message.includes("未查询到")) {
    return settings?.emptyMessage || message;
  }
  return message;
}

export function buildSeatRows(settings: MeetingQuerySettings | null, result: SeatQueryResult | null) {
  return buildResultRows(settings, result, "seat");
}

export function buildRoomRows(settings: MeetingQuerySettings | null, result: RoomQueryResult | null) {
  return buildResultRows(settings, result, "room");
}

export function buildDinnerRows(settings: MeetingQuerySettings | null, result: DinnerQueryResult | null) {
  return buildResultRows(settings, result, "dinner");
}

function buildResultRows(
  settings: MeetingQuerySettings | null,
  result: SeatQueryResult | RoomQueryResult | DinnerQueryResult | null,
  kind: QueryResultKind
): QueryDisplayRow[] {
  if (!settings || !result) return [];
	  const fields = kind === "seat"
	    ? settings.seatVisibleResultFields
	    : kind === "room"
	      ? settings.roomVisibleResultFields
	      : settings.dinnerVisibleResultFields;
	  const maskedFields = kind === "seat" ? settings.seatMaskedResultFields : kind === "room" ? settings.roomMaskedResultFields : settings.dinnerMaskedResultFields;
	  const highlightFields = kind === "seat" ? settings.seatHighlightResultFields : kind === "room" ? settings.roomHighlightResultFields : settings.dinnerHighlightResultFields;
  return fields
    .filter((field) => isFieldAvailableForKind(field, kind))
    .map((field) => {
      const raw = resultValue(result, field);
      const masked = maskedFields.includes(field) ? maskValue(field, raw) : raw;
      return {
        field,
        label: fieldLabel(settings, field),
        value: masked,
        highlighted: highlightFields.includes(field)
      };
    })
    .filter((row) => row.value.trim() !== "");
}

function isFieldAvailableForKind(field: PublicQueryResultField, kind: QueryResultKind) {
  if (sharedFields.has(field)) return true;
  if (kind === "seat") return seatFields.has(field);
  if (kind === "room") return roomFields.has(field);
  return dinnerFields.has(field);
}

function resultValue(result: SeatQueryResult | RoomQueryResult | DinnerQueryResult, field: PublicQueryResultField) {
  switch (field) {
    case "ATTENDEE_NAME":
      return result.attendeeName || "";
    case "ATTENDEE_PHONE":
      return "attendeePhone" in result ? result.attendeePhone || "" : "";
    case "ORGANIZATION":
      return "organization" in result ? result.organization || "" : "";
    case "TITLE":
      return "title" in result ? result.title || "" : "";
    case "EMAIL":
      return "email" in result ? result.email || "" : "";
    case "MEETING_NAME":
      return result.meetingName || "";
    case "SEAT_AREA":
      return "areaName" in result ? result.areaName || "" : "";
    case "SEAT_ROW":
      return "rowNo" in result && result.rowNo ? `${result.rowNo}排` : "";
    case "SEAT_NO":
      return "seatNo" in result && result.seatNo ? `${result.seatNo}座` : "";
    case "SEAT_CODE":
      return "seatCode" in result ? result.seatCode || "" : "";
    case "HOTEL_NAME":
      return "hotelName" in result ? result.hotelName || "" : "";
    case "ROOM_TYPE":
      return "roomType" in result ? result.roomType || "" : "";
    case "ROOM_NO":
      return "roomNo" in result ? result.roomNo || "" : "";
    case "ROOM_REMARK":
      return "roomRemark" in result ? result.roomRemark || "" : "";
    case "CHECKIN_TIPS":
      return "checkinTips" in result ? result.checkinTips || "" : "";
    case "DINNER_TABLE_NO":
      return "tableNo" in result ? result.tableNo || "" : "";
    case "DINNER_VENUE":
      return "venue" in result ? result.venue || "" : "";
    case "DINNER_TIME":
      return "dinnerTime" in result ? result.dinnerTime || "" : "";
    case "DINNER_REMARK":
      return "remark" in result ? result.remark || "" : "";
  }
}

function maskValue(field: PublicQueryResultField, value: string) {
  const text = value.trim();
  if (!text) return "";
  if (field === "ATTENDEE_PHONE") {
    const normalized = text.replace(/\s|-/g, "");
    if (normalized.length <= 4) return "*".repeat(normalized.length);
    return `${normalized.slice(0, 3)}****${normalized.slice(-4)}`;
  }
  if (field === "EMAIL") {
    const [name, domain] = text.split("@");
    if (!domain) return genericMask(text);
    return `${name.slice(0, 1)}***@${domain}`;
  }
  if (field === "ATTENDEE_NAME") {
    return text.length <= 1 ? text : `${text.slice(0, 1)}${"*".repeat(Math.max(text.length - 1, 1))}`;
  }
  return genericMask(text);
}

function genericMask(value: string) {
  if (value.length <= 2) return `${value.slice(0, 1)}*`;
  return `${value.slice(0, 1)}${"*".repeat(Math.min(value.length - 2, 6))}${value.slice(-1)}`;
}

function hexToRgba(hex: string, alpha: number) {
  const normalized = /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : "#2563eb";
  const r = Number.parseInt(normalized.slice(1, 3), 16);
  const g = Number.parseInt(normalized.slice(3, 5), 16);
  const b = Number.parseInt(normalized.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
