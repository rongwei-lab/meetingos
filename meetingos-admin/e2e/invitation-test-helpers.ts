import { expect, type Page } from "@playwright/test";

const FRAMEWORK_OVERLAY_SELECTOR = [
  "vite-error-overlay",
  "#webpack-dev-server-client-overlay",
  "nextjs-portal",
  "[data-nextjs-dialog-overlay]"
].join(",");

export function collectPageErrors(page: Page) {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

export async function expectNoFrameworkError(page: Page, pageErrors: string[]) {
  await expect(page.locator(FRAMEWORK_OVERLAY_SELECTOR)).toHaveCount(0);
  expect(pageErrors, "页面不应出现未捕获的运行时错误").toEqual([]);
}

export function missingEnvironmentVariables(names: string[]) {
  return names.filter((name) => !process.env[name]?.trim());
}
