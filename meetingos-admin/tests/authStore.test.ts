import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { hasSystemAdminRole, useAuthStore } from "../src/stores/auth";

function installLocalStorage(initialUser?: unknown) {
  const values = new Map<string, string>();
  if (initialUser) values.set("meetingos.user", JSON.stringify(initialUser));
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear()
  });
}

describe("auth store 系统权限", () => {
  beforeEach(() => {
    installLocalStorage();
    setActivePinia(createPinia());
  });

  it("自定义用户名拥有 SUPER_ADMIN 角色时可以访问系统设置", () => {
    installLocalStorage({
      username: "platform-root",
      name: "平台管理员",
      roles: ["MEETING_ADMIN", "SUPER_ADMIN"]
    });
    setActivePinia(createPinia());

    expect(useAuthStore().canAccessSystemSettings).toBe(true);
  });

  it("固定 admin 用户名没有 SUPER_ADMIN 角色时不能访问系统设置", () => {
    installLocalStorage({ username: "admin", name: "会议管理员", roles: ["MEETING_ADMIN"] });
    setActivePinia(createPinia());

    expect(useAuthStore().canAccessSystemSettings).toBe(false);
  });

  it("角色判断兼容大小写并拒绝相似角色名", () => {
    expect(hasSystemAdminRole(["super_admin"])).toBe(true);
    expect(hasSystemAdminRole(["MEETING_ADMIN"])).toBe(false);
  });
});
