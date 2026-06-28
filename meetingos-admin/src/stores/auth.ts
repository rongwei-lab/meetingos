import { defineStore } from "pinia";
import { canUseDemoFallback, extractErrorMessage, http, isAuthFailure, unwrapApi } from "@/api/http";

const USER_KEY = "meetingos.user";
export const SYSTEM_ADMIN_USERNAME = "admin";

interface LoginResponse {
  userId: number;
  username: string;
  displayName: string;
  roles: string[];
}

interface CurrentUserResponse {
  userId: number;
  username: string;
  displayName: string;
  roles: string[];
}

interface AuthUser {
  username: string;
  name: string;
  email: string;
  role: string;
}

function createEmptyUser(): AuthUser {
  return {
    username: "",
    name: "未登录",
    email: "",
    role: ""
  };
}

function createAuthUser(username: string, displayName?: string, roles?: string[]): AuthUser {
  const normalizedUsername = username.trim();
  const normalizedDisplayName = displayName?.trim();
  return {
    username: normalizedUsername,
    name: normalizedDisplayName || normalizedUsername || "未登录",
    email: normalizedUsername,
    role: roles?.[0] || ""
  };
}

export function isSystemAdminUsername(username?: string) {
  return (username || "").trim().toLocaleLowerCase("en-US") === SYSTEM_ADMIN_USERNAME;
}

function readStoredUser(): AuthUser {
  const storedUser = localStorage.getItem(USER_KEY);
  if (storedUser) {
    try {
      return normalizeStoredUser(JSON.parse(storedUser));
    } catch {
      localStorage.removeItem(USER_KEY);
    }
  }
  localStorage.removeItem("meetingos.token");
  return createEmptyUser();
}

function normalizeStoredUser(value: unknown): AuthUser {
  if (!value || typeof value !== "object") return createEmptyUser();
  const user = value as Record<string, unknown>;
  const username = readString(user.username) || readString(user.email);
  return {
    username,
    name: readString(user.name) || readString(user.displayName) || username || "未登录",
    email: readString(user.email) || username,
    role: readString(user.role)
  };
}

function readString(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function persistUser(user: AuthUser) {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: readStoredUser(),
    sessionChecked: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.user.username),
    canAccessSystemSettings: (state) => isSystemAdminUsername(state.user.username)
  },
  actions: {
    async login(username: string, password: string) {
      if (!username || !password) {
        throw new Error("请输入用户名和密码");
      }
      try {
        const data = unwrapApi(await http.post<LoginResponse>("/auth/login", { username, password }));
        this.user = createAuthUser(data.username, data.displayName, data.roles);
        this.sessionChecked = true;
      } catch (error) {
        // 只在后端完全不可达时允许演示账号进入；如果后端返回 401/403，
        // 必须提示真实认证失败，避免进入后续页面操作 mock 数据。
        if (!canUseDemoFallback(error) || username !== "admin" || password !== "admin123") {
          if (isAuthFailure(error)) {
            throw new Error("账号或密码错误，或账号已停用");
          }
          throw new Error(extractErrorMessage(error, "登录失败，请稍后重试"));
        }
        /*
         * 仅在开发环境且后端完全不可达时保留演示入口。只要后端返回了明确认证失败，
         * 上面的分支就会中止，避免弱默认密码在真实服务里被误认为可用账号。
         */
        this.user = createAuthUser("admin", "超级管理员", ["SUPER_ADMIN"]);
        this.sessionChecked = true;
      }
      persistUser(this.user);
    },
    async ensureSession() {
      if (this.isAuthenticated && this.sessionChecked) {
        return true;
      }
      try {
        const data = unwrapApi(await http.get<CurrentUserResponse>("/auth/me"));
        this.user = createAuthUser(data.username, data.displayName, data.roles);
        this.sessionChecked = true;
        persistUser(this.user);
        return true;
      } catch (error) {
        this.user = createEmptyUser();
        this.sessionChecked = true;
        localStorage.removeItem(USER_KEY);
        return false;
      }
    },
    async logout() {
      try {
        await http.post("/auth/logout");
      } catch {
        // 即使后端会话已过期，前端也必须清理本地用户缓存。
      }
      this.user = createEmptyUser();
      this.sessionChecked = true;
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem("meetingos.token");
    }
  }
});
