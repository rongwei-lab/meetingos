export function createAmapNavigationUrl(longitude: number, latitude: number, address: string) {
  const url = new URL("https://uri.amap.com/marker");
  url.searchParams.set("position", `${longitude},${latitude}`);
  url.searchParams.set("name", address.trim() || "会议地点");
  url.searchParams.set("coordinate", "gaode");
  url.searchParams.set("callnative", "0");
  return url.toString();
}

export function parseSafeHttpsUrl(value?: string) {
  if (!value?.trim()) return undefined;
  try {
    const url = new URL(value.trim());
    return url.protocol === "https:" && !url.username && !url.password ? url : undefined;
  } catch {
    return undefined;
  }
}

export function isDefaultAmapMarkerUrl(url: URL) {
  const normalizedPath = url.pathname.replace(/\/+$/, "") || "/";
  return url.hostname.toLowerCase() === "uri.amap.com" && normalizedPath === "/marker";
}
