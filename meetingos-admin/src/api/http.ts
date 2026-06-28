import axios from "axios";

const USER_KEY = "meetingos.user";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 10000,
  withCredentials: true
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = String(error.config?.url || "");
    if (isAuthFailure(error) && !url.includes("/auth/login") && !url.includes("/auth/me") && !url.includes("/auth/logout")) {
      // 认证失败说明当前 Cookie 会话已过期或无效，不能继续降级到本地 mock 数据，
      // 否则座位、参会人等后台操作会看起来成功但没有写入真实数据库。
      localStorage.removeItem(USER_KEY);
      if (!window.location.pathname.startsWith("/login")) {
        const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`;
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
      }
    }
    return Promise.reject(error);
  }
);

export function isAuthFailure(error: unknown) {
  if (!axios.isAxiosError(error)) return false;
  return error.response?.status === 401 || error.response?.status === 403;
}

export const demoFallbackEnabled =
  import.meta.env.DEV && import.meta.env.VITE_ENABLE_DEMO_FALLBACK !== "false";

export function canUseDemoFallback(error: unknown) {
  if (!demoFallbackEnabled) return false;
  if (!axios.isAxiosError(error)) return false;
  if (!error.response) return true;

  // Vite dev proxy 在后端未启动时可能返回空 5xx。此时仍按“本地演示模式”处理；
  // 带有明确错误体的真实后端 5xx 不吞掉，避免掩盖业务接口问题。
  const status = error.response.status;
  const data = error.response.data;
  const emptyProxyFailure = (data == null || data === "") && [500, 502, 503, 504].includes(status);
  return emptyProxyFailure;
}

export function extractErrorMessage(error: unknown, fallback = "请求失败") {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;
    if (data && typeof data === "object" && "message" in data) {
      const message = (data as { message?: unknown }).message;
      if (typeof message === "string" && message.trim()) return message;
    }
    if (error.message) return error.message;
  }
  return error instanceof Error ? error.message : fallback;
}

export async function tryApi<T>(request: () => Promise<{ data: T }>, fallback: () => T): Promise<T> {
  try {
    const response = await request();
    return response.data;
  } catch (error) {
    if (!canUseDemoFallback(error)) throw error;
    // 后端未启动时使用本地演示数据，保证前端完整流程可走通。
    return fallback();
  }
}

export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

export interface PagePayload<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  return Boolean(value && typeof value === "object" && "success" in value && "data" in value);
}

export function unwrapApi<T>(response: { data: T | ApiEnvelope<T> }): T {
  if (!isApiEnvelope<T>(response.data)) {
    return response.data;
  }
  if (!response.data.success) {
    throw new Error(response.data.message || response.data.code || "请求失败");
  }
  return response.data.data;
}
