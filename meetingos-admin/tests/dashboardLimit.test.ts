import { describe, expect, it } from "vitest";
import {
  DASHBOARD_DISPLAY_LIMIT_DEFAULT,
  DASHBOARD_DISPLAY_LIMIT_MAX,
  DASHBOARD_DISPLAY_LIMIT_MIN,
  normalizeDashboardDisplayLimit
} from "../src/utils/dashboardLimit";

describe("统计看板显示数量", () => {
  it("保留 1 和大于 20 的有效输入", () => {
    expect(normalizeDashboardDisplayLimit(1)).toBe(DASHBOARD_DISPLAY_LIMIT_MIN);
    expect(normalizeDashboardDisplayLimit(22)).toBe(22);
    expect(normalizeDashboardDisplayLimit(100)).toBe(DASHBOARD_DISPLAY_LIMIT_MAX);
  });

  it("在编辑中的空值或越界值提交时归一化", () => {
    expect(normalizeDashboardDisplayLimit(null)).toBe(DASHBOARD_DISPLAY_LIMIT_DEFAULT);
    expect(normalizeDashboardDisplayLimit(0)).toBe(DASHBOARD_DISPLAY_LIMIT_MIN);
    expect(normalizeDashboardDisplayLimit(101)).toBe(DASHBOARD_DISPLAY_LIMIT_MAX);
  });
});
