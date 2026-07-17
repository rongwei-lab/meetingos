export interface DocumentBranding {
  brandName?: string;
  browserTitle?: string;
  metaDescription?: string;
  faviconUrl?: string;
  logoUrl?: string;
}

export function applyDocumentBranding(branding: DocumentBranding) {
  const title = firstText(branding.browserTitle, branding.brandName, "会议管理系统");
  document.title = title;

  let description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
  if (!description) {
    description = document.createElement("meta");
    description.name = "description";
    document.head.appendChild(description);
  }
  description.content = firstText(branding.metaDescription, title);

  const iconUrl = firstText(branding.faviconUrl, branding.logoUrl);
  if (iconUrl) {
    ensureIconLink("icon", iconUrl);
    ensureIconLink("shortcut icon", iconUrl);
  }
}

function ensureIconLink(rel: string, href: string) {
  let link = document.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`);
  if (!link) {
    link = document.createElement("link");
    link.rel = rel;
    document.head.appendChild(link);
  }
  link.href = href;
}

function firstText(...values: Array<string | undefined>) {
  return values.find((value) => value?.trim())?.trim() || "";
}
