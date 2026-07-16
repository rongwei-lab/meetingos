export const TDUCK_LAUNCH_PATH = "/tduck-launching.html";

export interface TduckBrowserSession {
  token: string;
  userInfo: Record<string, unknown>;
}

/**
 * TDuck 和 MeetingOS 通过同一域名部署，TDuck 原生路由守卫只识别这两个 localStorage key。
 * 统一在此处写入，确保从报名配置和顶部入口进入 TDuck 时使用完全一致的登录态格式。
 */
export function persistTduckBrowserSession(session: TduckBrowserSession) {
  const token = typeof session.token === "string" ? session.token.trim() : "";
  if (!token) throw new Error("TDuck 未返回登录 token");

  const userInfo = session.userInfo && typeof session.userInfo === "object" ? session.userInfo : {};
  localStorage.setItem("token", token);
  localStorage.setItem("userInfo", JSON.stringify({ ...userInfo, token }));
}

export function openTduckLaunchTab() {
  const targetWindow = window.open(TDUCK_LAUNCH_PATH, "_blank");
  if (targetWindow) targetWindow.opener = null;
  return targetWindow;
}

export function navigateTduckLaunchTab(targetWindow: Window, targetUrl: string) {
  if (targetWindow.closed) return false;
  targetWindow.location.replace(targetUrl);
  return true;
}

export function closeTduckLaunchTab(targetWindow: Window) {
  if (!targetWindow.closed) targetWindow.close();
}
