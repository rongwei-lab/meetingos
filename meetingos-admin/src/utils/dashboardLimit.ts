export const DASHBOARD_DISPLAY_LIMIT_MIN = 1;
export const DASHBOARD_DISPLAY_LIMIT_MAX = 100;
export const DASHBOARD_DISPLAY_LIMIT_DEFAULT = 10;

export function normalizeDashboardDisplayLimit(value: number | null | undefined) {
  const candidate = value === null || value === undefined || !Number.isFinite(value)
    ? DASHBOARD_DISPLAY_LIMIT_DEFAULT
    : Math.round(value);
  return Math.min(DASHBOARD_DISPLAY_LIMIT_MAX, Math.max(DASHBOARD_DISPLAY_LIMIT_MIN, candidate));
}
