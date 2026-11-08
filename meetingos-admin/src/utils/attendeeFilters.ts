export function isRegistrationTimeInRange(
  registeredAt: string | null | undefined,
  range: readonly [number, number] | null
) {
  if (!range) return true;
  if (!registeredAt?.trim()) return false;

  const timestamp = Date.parse(registeredAt.trim().replace(" ", "T"));
  const start = Math.floor(range[0] / 1000) * 1000;
  // 筛选精确到秒；包含结束秒内的全部记录，避免遗漏后端带微秒的报名时间。
  const endExclusive = Math.floor(range[1] / 1000) * 1000 + 1000;
  return Number.isFinite(timestamp) && timestamp >= start && timestamp < endExclusive;
}
