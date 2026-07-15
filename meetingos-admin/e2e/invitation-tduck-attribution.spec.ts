import { expect, test, type Request } from "@playwright/test";
import { missingEnvironmentVariables } from "./invitation-test-helpers";

type FormAction = {
  action: "fill" | "check" | "click" | "selectOption";
  selector: string;
  value?: string;
};

const required = [
  "E2E_INVITATION_SHARE_TOKEN",
  "E2E_TDUCK_BASE_URL",
  "E2E_TDUCK_FORM_KEY",
  "E2E_TDUCK_FORM_ACTIONS_JSON",
  "E2E_TDUCK_SUBMIT_SELECTOR"
];
const missing = missingEnvironmentVariables(required);
test.skip(
  process.env.E2E_TDUCK_ATTRIBUTION !== "true",
  "真实 TDuck 测试默认关闭；显式设置 E2E_TDUCK_ATTRIBUTION=true 后才会产生一条真实报名"
);
test.skip(missing.length > 0, `缺少 ${missing.join(", ")}，无法执行真实 TDuck 提交`);

test("真实 TDuck 提交把 mos_at 写入 extValue 并在成功后清理会话", async ({ page }) => {
  test.setTimeout(90_000);
  const shareToken = process.env.E2E_INVITATION_SHARE_TOKEN!.trim();
  const tduckBaseUrl = ensureTrailingSlash(process.env.E2E_TDUCK_BASE_URL!.trim());
  const formKey = process.env.E2E_TDUCK_FORM_KEY!.trim();
  const formActions = parseFormActions(process.env.E2E_TDUCK_FORM_ACTIONS_JSON!);
  const submitSelector = process.env.E2E_TDUCK_SUBMIT_SELECTOR!.trim();

  const capabilityResponse = await page.request.get(
    new URL("meetingos-capabilities.json", tduckBaseUrl).toString()
  );
  expect(capabilityResponse.ok(), "TDuck 必须暴露 MeetingOS 能力文件").toBe(true);
  const capabilities = await capabilityResponse.json() as {
    schemaVersion?: number;
    capabilities?: { meetingosAttributionExtValue?: string };
  };
  expect(capabilities.schemaVersion).toBe(1);
  expect(capabilities.capabilities?.meetingosAttributionExtValue).toBe("v1");

  await page.goto(`/i/${encodeURIComponent(shareToken)}`);
  const registerButton = page.locator(".sticky-registration-bar").getByRole("button");
  await expect(registerButton).toBeEnabled();

  const [intentResponse] = await Promise.all([
    page.waitForResponse((response) => {
      const url = new URL(response.url());
      return response.request().method() === "POST"
        && url.pathname.endsWith(`/public/invitations/${shareToken}/registration-intents`);
    }),
    registerButton.click()
  ]);
  expect(intentResponse.ok(), "报名点击应成功创建归因意图").toBe(true);
  const intentEnvelope = await intentResponse.json() as {
    data?: { attributionToken?: string; redirectUrl?: string };
    attributionToken?: string;
    redirectUrl?: string;
  };
  const intent = intentEnvelope.data || intentEnvelope;
  const attributionToken = intent.attributionToken;
  expect(attributionToken).toMatch(/^[A-Za-z0-9._~-]{1,255}$/);

  await page.waitForURL(
    (url) => url.pathname.startsWith("/r/") && url.hash === `#mos_at=${attributionToken}`,
    { timeout: 15_000 }
  );
  await page.waitForURL(
    (url) => isWithinBase(url, tduckBaseUrl) && !url.hash.includes("mos_at="),
    { timeout: 30_000 }
  );

  const storageKey = `meetingos:attribution:${formKey}`;
  await expect.poll(() => page.evaluate((key) => sessionStorage.getItem(key), storageKey)).toBe(attributionToken);
  expect(new URL(page.url()).hash).not.toContain("mos_at=");

  for (const action of formActions) {
    const locator = page.locator(action.selector).first();
    await expect(locator, `找不到 TDuck 表单控件：${action.selector}`).toBeVisible();
    if (action.action === "fill") await locator.fill(action.value || "");
    if (action.action === "check") await locator.check();
    if (action.action === "click") await locator.click();
    if (action.action === "selectOption") await locator.selectOption(action.value || "");
  }

  const extValueRequestPromise = page.waitForRequest(
    (request) => request.method() === "POST" && readExtValue(request) === attributionToken,
    { timeout: 30_000 }
  );
  await page.locator(submitSelector).first().click();
  const extValueRequest = await extValueRequestPromise;
  expect(readExtValue(extValueRequest)).toBe(attributionToken);

  // TDuck 只有在真实提交成功后才调用 clearMeetingOsAttribution；保留 token 表示提交未完成。
  await expect.poll(
    () => page.evaluate((key) => sessionStorage.getItem(key), storageKey),
    { timeout: 30_000 }
  ).toBeNull();
});

function parseFormActions(raw: string): FormAction[] {
  const parsed: unknown = JSON.parse(raw);
  if (!Array.isArray(parsed)) throw new Error("E2E_TDUCK_FORM_ACTIONS_JSON 必须是 JSON 数组");
  for (const item of parsed) {
    if (!item || typeof item !== "object") throw new Error("每个 TDuck 表单动作必须是对象");
    const action = item as Partial<FormAction>;
    if (!["fill", "check", "click", "selectOption"].includes(String(action.action))) {
      throw new Error(`不支持的 TDuck 表单动作：${String(action.action)}`);
    }
    if (typeof action.selector !== "string" || !action.selector.trim()) {
      throw new Error("TDuck 表单动作必须提供 selector");
    }
  }
  return parsed as FormAction[];
}

function ensureTrailingSlash(value: string) {
  return value.endsWith("/") ? value : `${value}/`;
}

function isWithinBase(url: URL, baseUrl: string) {
  const base = new URL(baseUrl);
  return url.origin === base.origin && url.pathname.startsWith(base.pathname);
}

function readExtValue(request: Request): string | undefined {
  let jsonValue: string | undefined;
  try {
    jsonValue = findExtValue(request.postDataJSON());
  } catch {
    jsonValue = undefined;
  }
  if (jsonValue) return jsonValue;
  const raw = request.postData() || "";
  const decoded = decodeRequestBody(raw);
  const match = decoded.match(/["']?extValue["']?\s*[:=]\s*["']?([A-Za-z0-9._~-]{1,255})/);
  return match?.[1];
}

function findExtValue(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findExtValue(item);
      if (found) return found;
    }
    return undefined;
  }
  if (!value || typeof value !== "object") return undefined;
  for (const [key, item] of Object.entries(value)) {
    if (key === "extValue" && typeof item === "string") return item;
    const found = findExtValue(item);
    if (found) return found;
  }
  return undefined;
}

function decodeRequestBody(raw: string) {
  let decoded = raw;
  for (let index = 0; index < 2; index += 1) {
    try {
      decoded = decodeURIComponent(decoded).replace(/\\"/g, '"');
    } catch {
      break;
    }
  }
  return decoded;
}
