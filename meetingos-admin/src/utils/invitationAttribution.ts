const ATTRIBUTION_FRAGMENT = /^#mos_at=([A-Za-z0-9._~-]{1,255})$/;

export function readMeetingAttributionToken(hash: string | null | undefined) {
  const match = String(hash || "").match(ATTRIBUTION_FRAGMENT);
  return match?.[1] || "";
}

export function appendMeetingAttributionFragment(registrationUrl: string, hash: string | null | undefined) {
  const token = readMeetingAttributionToken(hash);
  const url = new URL(registrationUrl);
  // TDuck 原地址里的 fragment 可能携带未知状态，统一清理后只附加经过白名单校验的归因令牌。
  url.hash = token ? `mos_at=${token}` : "";
  return url.toString();
}
