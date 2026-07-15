import { defineConfig, devices } from "@playwright/test";

const baseURL = (process.env.E2E_BASE_URL || "http://127.0.0.1:5173").replace(/\/+$/, "");
const startWebServer = process.env.E2E_START_WEB_SERVER === "true";
const baseUrlObject = new URL(baseURL);
const devServerPort = Number(baseUrlObject.port || (baseUrlObject.protocol === "https:" ? 443 : 80));

if (startWebServer && !["127.0.0.1", "localhost"].includes(baseUrlObject.hostname)) {
  throw new Error("E2E_START_WEB_SERVER=true 时，E2E_BASE_URL 必须指向本机 localhost/127.0.0.1");
}

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: "list",
  outputDir: "./node_modules/.cache/meetingos-playwright",
  timeout: 30_000,
  expect: {
    timeout: 10_000
  },
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  },
  projects: [
    {
      name: "invitation-mobile-chromium",
      testMatch: /invitation-public\.spec\.ts/,
      use: {
        ...devices["iPhone 13"],
        browserName: "chromium"
      }
    },
    {
      name: "invitation-desktop-chromium",
      testMatch: /invitation-designer\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        browserName: "chromium"
      }
    },
    {
      name: "invitation-tduck-chromium",
      testMatch: /invitation-tduck-attribution\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        browserName: "chromium"
      }
    }
  ],
  webServer: startWebServer
    ? {
        command: `npm run dev -- --host 127.0.0.1 --port ${devServerPort}`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000
      }
    : undefined
});
