export const TDUCK_LAUNCH_PATH = "/tduck-launching.html";

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
