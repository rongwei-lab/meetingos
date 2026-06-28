const LOCALHOSTS = new Set(["localhost", "127.0.0.1", "::1"]);

export function sanitizeExternalRedirectUrl(value?: string | null) {
  const url = parseUrl(value);
  if (!url) return "";
  if (url.protocol === "https:") return url.toString();
  if (import.meta.env.DEV && url.protocol === "http:" && LOCALHOSTS.has(url.hostname)) {
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
