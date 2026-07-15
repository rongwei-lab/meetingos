// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, reactive, type App, type Component } from "vue";
import RegistrationRedirectView from "../src/views/public/RegistrationRedirectView.vue";

const api = vi.hoisted(() => ({ get: vi.fn(), createRegistrationIntent: vi.fn() }));
const routeState = reactive({ params: { meetingCode: "OLD" }, hash: "" });

vi.mock("vue-router", () => ({ useRoute: () => routeState }));
vi.mock("@/api/http", () => ({
  http: { get: api.get },
  unwrapApi: <T>(response: { data: T }) => response.data
}));
vi.mock("@/api/invitations", () => ({ createRegistrationIntent: api.createRegistrationIntent }));

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  routeState.params.meetingCode = "OLD";
  routeState.hash = "";
  api.get.mockReset();
  api.createRegistrationIntent.mockReset();
  window.history.replaceState(null, "", "/");
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("报名中转页路由切换", () => {
  it("切换 meetingCode 时忽略旧响应并保留新会议配置", async () => {
    const oldRequest = deferred<{ data: RegistrationInfo }>();
    const newRequest = deferred<{ data: RegistrationInfo }>();
    api.get.mockImplementation((url: string) => url.includes("OLD") ? oldRequest.promise : newRequest.promise);
    mountView();
    await vi.waitFor(() => expect(api.get).toHaveBeenCalledWith("/public/meetings/OLD/registration"));

    routeState.params.meetingCode = "NEW";
    await nextTick();
    await vi.waitFor(() => expect(api.get).toHaveBeenCalledWith("/public/meetings/NEW/registration"));
    newRequest.resolve({ data: registration("NEW", "新会议") });
    await vi.waitFor(() => expect(root?.textContent).toContain("新会议"));

    oldRequest.resolve({ data: registration("OLD", "旧会议") });
    await Promise.resolve();
    await nextTick();
    expect(root?.textContent).toContain("新会议");
    expect(root?.textContent).not.toContain("旧会议");
  });
});

describe("报名二维码渠道归因", () => {
  it("将 mos_inv 换成 mos_at 后进入 TDuck", async () => {
    routeState.hash = "#mos_inv=channel-token-1234";
    api.get.mockResolvedValue({ data: openRegistration("OLD") });
    api.createRegistrationIntent.mockResolvedValue({
      attributionToken: "signed.attribution_token~1",
      redirectUrl: "http://localhost/r/OLD#mos_at=signed.attribution_token~1",
      expiresAt: "2026-07-16T12:00:00"
    });
    mountView();
    await waitForOpenRegistration();

    actionButton().click();

    await vi.waitFor(() => expect(api.createRegistrationIntent).toHaveBeenCalledTimes(1));
    await vi.waitFor(() => expect(window.location.hash).toBe("#mos_at=signed.attribution_token~1"));
    expect(api.createRegistrationIntent).toHaveBeenCalledWith("channel-token-1234", expect.stringMatching(/^[0-9a-f-]{36}$/i));
  });

  it("归因意图创建失败时降级为不带归因的普通报名", async () => {
    routeState.hash = "#mos_inv=channel-token-1234";
    api.get.mockResolvedValue({ data: openRegistration("OLD") });
    api.createRegistrationIntent.mockRejectedValue(new Error("temporary failure"));
    mountView();
    await waitForOpenRegistration();

    actionButton().click();

    await vi.waitFor(() => expect(api.createRegistrationIntent).toHaveBeenCalledTimes(1));
    await vi.waitFor(() => expect(window.location.hash).toBe(""));
  });

  it("已有 mos_at 时直接保留且不重复创建归因意图", async () => {
    routeState.hash = "#mos_at=existing.attribution_token~1";
    api.get.mockResolvedValue({ data: openRegistration("OLD") });
    mountView();
    await waitForOpenRegistration();

    actionButton().click();

    await vi.waitFor(() => expect(window.location.hash).toBe("#mos_at=existing.attribution_token~1"));
    expect(api.createRegistrationIntent).not.toHaveBeenCalled();
  });

  it("同次归因失败后的重试复用客户端事件 ID", async () => {
    routeState.hash = "#mos_inv=channel-token-1234";
    api.get.mockResolvedValue({ data: openRegistration("OLD") });
    api.createRegistrationIntent.mockRejectedValue(new Error("temporary failure"));
    mountView();
    await waitForOpenRegistration();

    actionButton().click();
    await vi.waitFor(() => expect(api.createRegistrationIntent).toHaveBeenCalledTimes(1));
    actionButton().click();
    await vi.waitFor(() => expect(api.createRegistrationIntent).toHaveBeenCalledTimes(2));

    expect(api.createRegistrationIntent.mock.calls[0]?.[1]).toBe(api.createRegistrationIntent.mock.calls[1]?.[1]);
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(RegistrationRedirectView);
  const passthrough: Component = { setup: (_props, { slots }) => () => h("div", slots.default?.()) };
  app.component("NAlert", passthrough);
  app.component("NButton", passthrough);
  app.mount(root);
}

interface RegistrationInfo {
  meetingId: number;
  meetingCode: string;
  meetingName: string;
  meetingStatus: string;
  open: boolean;
  message: string;
  registrationUrl?: string;
}

function openRegistration(code: string): RegistrationInfo {
  return {
    ...registration(code, "开放会议"),
    open: true,
    message: "报名开放中",
    registrationUrl: `${window.location.origin}${window.location.pathname}`
  };
}

function actionButton() {
  const button = root?.querySelector<HTMLElement>(".action");
  if (!button) throw new Error("未找到报名按钮");
  return button;
}

async function waitForOpenRegistration() {
  await vi.waitFor(() => expect(root?.textContent).toContain("开放会议"));
}

function registration(code: string, name: string): RegistrationInfo {
  return {
    meetingId: code === "NEW" ? 2 : 1,
    meetingCode: code,
    meetingName: name,
    meetingStatus: "PUBLISHED",
    open: false,
    message: "报名暂未开放"
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}
