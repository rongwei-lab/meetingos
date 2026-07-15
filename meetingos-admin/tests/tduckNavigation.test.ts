// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { closeTduckLaunchTab, navigateTduckLaunchTab, openTduckLaunchTab, TDUCK_LAUNCH_PATH } from "../src/utils/tduckNavigation";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("TDuck 新标签页导航", () => {
  it("同步打开过渡页并在登录态准备完成后替换为目标地址", () => {
    const replace = vi.fn();
    const close = vi.fn();
    const targetWindow = { closed: false, opener: {}, location: { replace }, close } as unknown as Window;
    const open = vi.spyOn(window, "open").mockReturnValue(targetWindow);

    expect(openTduckLaunchTab()).toBe(targetWindow);
    expect(open).toHaveBeenCalledWith(TDUCK_LAUNCH_PATH, "_blank");
    expect(targetWindow.opener).toBeNull();
    expect(navigateTduckLaunchTab(targetWindow, "/tduck/home")).toBe(true);
    expect(replace).toHaveBeenCalledWith("/tduck/home");

    closeTduckLaunchTab(targetWindow);
    expect(close).toHaveBeenCalledTimes(1);
  });

  it("目标标签已关闭时不再导航", () => {
    const replace = vi.fn();
    const targetWindow = { closed: true, opener: {}, location: { replace }, close: vi.fn() } as unknown as Window;

    expect(navigateTduckLaunchTab(targetWindow, "/tduck/home")).toBe(false);
    expect(replace).not.toHaveBeenCalled();
  });
});
