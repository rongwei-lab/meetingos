import { expect, test } from "@playwright/test";
import {
  collectPageErrors,
  expectNoFrameworkError,
  missingEnvironmentVariables
} from "./invitation-test-helpers";

const required = [
  "E2E_ADMIN_USERNAME",
  "E2E_ADMIN_PASSWORD",
  "E2E_MEETING_ID",
  "E2E_INVITATION_ID"
];
const missing = missingEnvironmentVariables(required);
test.skip(missing.length > 0, `缺少 ${missing.join(", ")}，跳过需要后台数据的设计器测试`);

test("管理员登录后可加载桌面邀请函设计器", async ({ page }) => {
  const meetingId = encodeURIComponent(process.env.E2E_MEETING_ID!.trim());
  const invitationId = encodeURIComponent(process.env.E2E_INVITATION_ID!.trim());
  const designerPath = `/meetings/${meetingId}/invitations/${invitationId}/design`;
  const pageErrors = collectPageErrors(page);

  await page.goto(designerPath);
  await page.waitForURL((url) => url.pathname === "/login" || url.pathname === designerPath);

  if (new URL(page.url()).pathname === "/login") {
    await page.getByPlaceholder("请输入手机号 / 邮箱").fill(process.env.E2E_ADMIN_USERNAME!);
    await page.getByPlaceholder("请输入密码").fill(process.env.E2E_ADMIN_PASSWORD!);
    await page.getByRole("button", { name: /登\s*录/ }).click();
  }

  await page.waitForURL((url) => url.pathname === designerPath);
  await expect(page.locator(".designer-shell")).toBeVisible();
  await expect(page.locator(".designer-error")).toHaveCount(0);
  await expect(page.locator(".designer-title strong")).not.toHaveText("");
  await expect(page.getByRole("button", { name: "预览" })).toBeVisible();
  await expectNoFrameworkError(page, pageErrors);
});
