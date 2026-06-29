const LOCALHOSTS = new Set(["localhost", "127.0.0.1", "::1"]);

export function sanitizeExternalRedirectUrl(value?: string | null) {
  const url = parseUrl(value);
  if (!url) return "";
  if (url.protocol === "https:") return url.toString();
  /*
   * Docker 本地部署会把 TDuck 暴露在同一个 8080 origin 下，例如
   * http://127.0.0.1:8080/tduck/s/{formKey}。生产构建里也要允许这种同源中转，
   * 但仍然拒绝普通外部 http，避免公开报名页变成开放重定向入口。
   */
  if (url.protocol === "http:" && url.origin === window.location.origin) {
    return url.toString();
  }
  if (isLocalLoopbackRedirect(url)) {
    return url.toString();
  }
  return "";
}

export function sanitizeImageUrl(value?: string | null) {
  if (!value) return "";
  const trimmed = value.trim();
  if (trimmed === "/favicon.svg" || trimmed.startsWith("/uploads/")) {
    return trimmed;
  }
  const url = parseUrl(trimmed);
  if (!url) return "";
  if (url.protocol === "https:") return url.toString();
  if (import.meta.env.DEV && url.protocol === "http:" && url.origin === window.location.origin) {
    return url.toString();
  }
  return "";
}

export function assertSafeImageUrl(value: string, label: string) {
  if (value && !sanitizeImageUrl(value)) {
    throw new Error(`${label}仅允许 /uploads/ 下的同源图片、/favicon.svg 或 https 图片地址`);
  }
}

export function isAllowedUploadImage(file: File) {
  const allowedTypes = new Set(["image/jpeg", "image/png", "image/webp", "image/gif"]);
  const allowedExts = [".jpg", ".jpeg", ".png", ".webp", ".gif"];
  const name = file.name.toLowerCase();
  return allowedTypes.has(file.type) && allowedExts.some((ext) => name.endsWith(ext));
}

function parseUrl(value?: string | null) {
  if (!value || !value.trim()) return null;
  try {
    return new URL(value.trim(), window.location.origin);
  } catch {
    return null;
  }
}

function isLocalLoopbackRedirect(url: URL) {
  if (url.protocol !== "http:" || !LOCALHOSTS.has(url.hostname)) {
    return false;
  }
  /*
   * 本机访问时，后端配置可能返回 127.0.0.1，而用户浏览器打开的是 localhost
   * 或反过来。两者都只指向当前机器，允许它们互相跳转可以兼容本地 Docker 联调。
   */
  return window.location.protocol === "http:" && LOCALHOSTS.has(window.location.hostname);
}
