// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  closeTduckLaunchTab,
  navigateTduckLaunchTab,
  openTduckLaunchTab,
  persistTduckBrowserSession,
  TDUCK_LAUNCH_PATH
} from "../src/utils/tduckNavigation";

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe("TDuck 新标签页导航", () => {
  it("写入 TDuck 原生登录态，供首页和编辑器共用", () => {
    persistTduckBrowserSession({ token: "  tduck-token  ", userInfo: { id: 1, username: "admin" } });

    expect(localStorage.getItem("token")).toBe("tduck-token");
    expect(JSON.parse(localStorage.getItem("userInfo") || "{}")).toEqual({
      id: 1,
      username: "admin",
      token: "tduck-token"
    });
  });

  it("缺少登录 token 时不写入 TDuck 登录态", () => {
    localStorage.setItem("token", "existing-token");

    expect(() => persistTduckBrowserSession({ token: "   ", userInfo: {} })).toThrow("TDuck 未返回登录 token");
    expect(localStorage.getItem("token")).toBe("existing-token");
  });

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
