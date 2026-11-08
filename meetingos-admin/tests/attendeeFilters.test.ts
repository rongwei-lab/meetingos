import { describe, expect, it } from "vitest";
import { isRegistrationTimeInRange } from "../src/utils/attendeeFilters";

const range: [number, number] = [
  new Date(2026, 8, 15, 23, 10, 2).getTime(),
  new Date(2026, 8, 15, 23, 10, 17).getTime()
];

describe("报名时间筛选", () => {
  it("包含开始和结束秒，并保留结束秒内带微秒的记录", () => {
    expect(isRegistrationTimeInRange("2026-09-15T23:10:01.999999", range)).toBe(false);
    expect(isRegistrationTimeInRange("2026-09-15T23:10:02", range)).toBe(true);
    expect(isRegistrationTimeInRange("2026-09-15T23:10:17.999999", range)).toBe(true);
    expect(isRegistrationTimeInRange("2026-09-15T23:10:18", range)).toBe(false);
  });

  it("兼容空格分隔时间，忽略选择器内部残留的毫秒", () => {
    const rangeWithMilliseconds: [number, number] = [range[0] + 500, range[1] + 500];
    expect(isRegistrationTimeInRange("2026-09-15 23:10:02", rangeWithMilliseconds)).toBe(true);
    expect(isRegistrationTimeInRange("2026-09-15 23:10:17.999999", rangeWithMilliseconds)).toBe(true);
    expect(isRegistrationTimeInRange("2026-09-15 23:10:18", rangeWithMilliseconds)).toBe(false);
  });

  it("启用范围时排除无报名时间的记录，清空后恢复全部记录", () => {
    for (const value of [null, undefined, "", " ", "invalid", "2026-09-14T23:10:02"]) {
      expect(isRegistrationTimeInRange(value, range)).toBe(false);
      expect(isRegistrationTimeInRange(value, null)).toBe(true);
    }
  });
});
