import { expect, test } from "@playwright/test";
import { collectPageErrors, expectNoFrameworkError } from "./invitation-test-helpers";

const shareToken = process.env.E2E_INVITATION_SHARE_TOKEN?.trim();
test.skip(!shareToken, "未设置 E2E_INVITATION_SHARE_TOKEN，跳过需要已发布渠道数据的公开页测试");

test("已发布邀请函在移动视口显示正文与报名 CTA", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  const response = await page.goto(`/i/${encodeURIComponent(shareToken!)}`);

  expect(response?.ok(), "邀请函 SPA 入口应成功返回").toBe(true);
  await expect(page).toHaveURL(new RegExp(`/i/${escapeRegExp(shareToken!)}(?:[?#]|$)`));
  await expect(page).toHaveTitle(/邀请函/);
  await expect(page.locator(".invitation-document")).toBeVisible();
  await expect(page.locator(".public-invitation-state.unavailable-state")).toHaveCount(0);

  const stickyCta = page.locator(".sticky-registration-bar");
  await expect(stickyCta).toBeVisible();
  await expect(stickyCta.getByRole("button")).toBeVisible();

  const renderedText = (await page.locator(".invitation-document").innerText()).trim();
  expect(renderedText.length, "邀请正文不能是空白画布").toBeGreaterThan(10);
  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth
  );
  expect(horizontalOverflow, "移动邀请函不应产生横向滚动").toBeLessThanOrEqual(1);
  await expectNoFrameworkError(page, pageErrors);
});

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
