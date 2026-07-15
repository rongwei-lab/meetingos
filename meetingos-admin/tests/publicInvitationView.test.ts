// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, reactive, type App, type Component } from "vue";
import PublicInvitationView from "../src/views/public/PublicInvitationView.vue";
import type { InvitationElementV1, PublicInvitationPayload } from "../src/types/invitation";

const api = vi.hoisted(() => ({
  getPublicInvitation: vi.fn(),
  recordInvitationView: vi.fn(),
  createRegistrationIntent: vi.fn()
}));
const navigation = vi.hoisted(() => ({ resolve: vi.fn() }));

const routeState = reactive({ params: { shareToken: "public-token" } });

vi.mock("vue-router", () => ({ useRoute: () => routeState }));
vi.mock("@/api/invitations", () => api);
vi.mock("@/utils/invitationActions", async (importOriginal) => ({
  ...await importOriginal<typeof import("../src/utils/invitationActions")>(),
  resolveInvitationNavigationTarget: navigation.resolve
}));

class ResizeObserverMock {
  observe() {}
  disconnect() {}
}

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  routeState.params.shareToken = "public-token";
  api.getPublicInvitation.mockReset();
  api.recordInvitationView.mockReset().mockResolvedValue({ recorded: true });
  api.createRegistrationIntent.mockReset();
  navigation.resolve.mockReset().mockImplementation((target: string, context: { publicQueryToken?: string | null }) => ({
    url: context.publicQueryToken ? `${window.location.origin}${window.location.pathname}#invitation-${target}` : "",
    unavailableReason: context.publicQueryToken ? "" : "会议未配置公开查询令牌"
  }));
  window.history.replaceState(null, "", "/i/public-token");
  vi.stubGlobal("ResizeObserver", ResizeObserverMock);
  let frameId = 0;
  vi.stubGlobal("requestAnimationFrame", vi.fn((callback: FrameRequestCallback) => {
    const id = ++frameId;
    queueMicrotask(() => callback(0));
    return id;
  }));
  vi.stubGlobal("cancelAnimationFrame", vi.fn());
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
    x: 0,
    y: 0,
    top: 0,
    right: 375,
    bottom: 667,
    left: 0,
    width: 375,
    height: 667,
    toJSON: () => ({})
  });
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("公开邀请函报名 CTA", () => {
  it("报名在点击后关闭时保留邀请内容并禁用 CTA", async () => {
    api.getPublicInvitation.mockResolvedValue(payload(true));
    api.createRegistrationIntent.mockRejectedValue(axiosLikeError(400, "报名入口已关闭"));
    mountView();
    await waitForInvitation();

    registrationButton().click();

    await vi.waitFor(() => expect(root?.textContent).toContain("报名通道已关闭"));
    expect(root?.textContent).toContain("测试会议");
    expect(root?.textContent).toContain("报名入口已关闭");
    expect(root?.textContent).not.toContain("邀请函暂不可用");
    expect(registrationButton().disabled).toBe(true);
  });

  it("瞬时网络失败保留邀请内容并提供重试状态", async () => {
    api.getPublicInvitation.mockResolvedValue(payload(true));
    api.createRegistrationIntent.mockRejectedValue(Object.assign(new Error("Network Error"), { isAxiosError: true }));
    mountView();
    await waitForInvitation();

    registrationButton().click();

    await vi.waitFor(() => expect(root?.textContent).toContain("报名请求失败"));
    expect(root?.textContent).toContain("测试会议");
    expect(root?.textContent).toContain("网络连接异常，请稍后重试");
    expect(root?.textContent).toContain("重试报名");
    expect(root?.textContent).not.toContain("邀请函暂不可用");
    expect(registrationButton().disabled).toBe(false);
  });

  it("瞬时失败后的 CTA 重试复用同一个客户端事件 ID", async () => {
    api.getPublicInvitation.mockResolvedValue(payload(true));
    api.createRegistrationIntent
      .mockRejectedValueOnce(Object.assign(new Error("Network Error"), { isAxiosError: true }))
      .mockRejectedValueOnce(axiosLikeError(400, "报名入口已关闭"));
    mountView();
    await waitForInvitation();

    registrationButton().click();
    await vi.waitFor(() => expect(root?.textContent).toContain("重试报名"));
    registrationButton().click();
    await vi.waitFor(() => expect(api.createRegistrationIntent).toHaveBeenCalledTimes(2));
    await vi.waitFor(() => expect(root?.textContent).toContain("报名通道已关闭"));

    expect(api.createRegistrationIntent.mock.calls[0]?.[1]).toBe(api.createRegistrationIntent.mock.calls[1]?.[1]);
  });

  it("初始报名关闭时仍展示邀请正文和明确的禁用按钮", async () => {
    api.getPublicInvitation.mockResolvedValue(payload(false));
    mountView();
    await waitForInvitation();

    expect(root?.textContent).toContain("测试会议");
    expect(root?.textContent).toContain("报名时间已结束");
    expect(root?.textContent).toContain("报名未开放");
    expect(registrationButton().disabled).toBe(true);
  });

  it("PV 瞬时失败后使用同一事件 ID 做有限退避重试", async () => {
    api.getPublicInvitation.mockResolvedValue(payload(true));
    api.recordInvitationView
      .mockRejectedValueOnce(new Error("temporary failure"))
      .mockResolvedValueOnce({ recorded: true });
    mountView();
    await waitForInvitation();

    await vi.waitFor(() => expect(api.recordInvitationView).toHaveBeenCalledTimes(2), { timeout: 1500 });

    expect(api.recordInvitationView.mock.calls[0]?.[1]).toBe(api.recordInvitationView.mock.calls[1]?.[1]);
    await new Promise((resolve) => window.setTimeout(resolve, 550));
    expect(api.recordInvitationView).toHaveBeenCalledTimes(2);
  });

  it("切换 shareToken 时忽略旧请求并加载新邀请函", async () => {
    routeState.params.shareToken = "old-token";
    const oldRequest = deferred<PublicInvitationPayload>();
    const newRequest = deferred<PublicInvitationPayload>();
    api.getPublicInvitation.mockImplementation((token: string) => token === "old-token" ? oldRequest.promise : newRequest.promise);
    mountView();
    await vi.waitFor(() => expect(api.getPublicInvitation).toHaveBeenCalledWith("old-token"));

    routeState.params.shareToken = "new-token";
    await nextTick();
    await vi.waitFor(() => expect(api.getPublicInvitation).toHaveBeenCalledWith("new-token"));
    newRequest.resolve(payload(true, "新会议"));
    await vi.waitFor(() => expect(root?.textContent).toContain("新会议"));

    oldRequest.resolve(payload(true, "旧会议"));
    await Promise.resolve();
    await nextTick();
    expect(root?.textContent).toContain("新会议");
    expect(root?.textContent).not.toContain("旧会议");
  });

  it("签到和查询按钮使用公开查询令牌导航且不创建报名 intent", async () => {
    api.getPublicInvitation.mockResolvedValue(payloadWithActions());
    mountView();
    await waitForInvitation();

    const checkinButton = root!.querySelector<HTMLButtonElement>(".render-checkin-button")!;
    const queryButton = root!.querySelector<HTMLButtonElement>(".render-query-button")!;
    expect(checkinButton.disabled).toBe(false);
    expect(queryButton.disabled).toBe(false);

    checkinButton.click();
    expect(window.location.hash).toBe("#invitation-SELF_CHECKIN");
    queryButton.click();
    expect(window.location.hash).toBe("#invitation-ROOM_QUERY");

    expect(navigation.resolve.mock.calls).toEqual([
      ["SELF_CHECKIN", { publicQueryToken: "public-query-token" }],
      ["ROOM_QUERY", { publicQueryToken: "public-query-token" }]
    ]);
    expect(api.createRegistrationIntent).not.toHaveBeenCalled();
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(PublicInvitationView);
  const stub: Component = { setup: () => () => h("span") };
  app.component("NIcon", stub);
  app.component("NSpin", stub);
  app.mount(root);
}

async function waitForInvitation() {
  await vi.waitFor(() => expect(root?.textContent).toContain("测试会议"));
}

function registrationButton() {
  const button = root?.querySelector<HTMLButtonElement>(".sticky-registration-bar button");
  if (!button) throw new Error("未找到报名按钮");
  return button;
}

function axiosLikeError(status: number, message: string) {
  return Object.assign(new Error(message), {
    isAxiosError: true,
    response: { status, data: { message } }
  });
}

function payload(registrationOpen: boolean, meetingName = "测试会议"): PublicInvitationPayload {
  return {
    invitationId: 7,
    versionNo: 2,
    channelType: "WECHAT_FRIENDS",
    channelName: "社群",
    designDocument: {
      version: 1,
      h5: {
        width: 375,
        height: 667,
        backgroundColor: "#ffffff",
        backgroundImageAssetId: null,
        elements: [{
          id: "meeting_title",
          type: "TEXT",
          x: 20,
          y: 40,
          width: 335,
          height: 60,
          rotation: 0,
          zIndex: 1,
          locked: false,
          props: { text: meetingName, fontSize: 30, color: "#17261f" }
        }]
      },
      poster: { width: 1080, height: 1920, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: [] }
    },
    meeting: {
      id: 1,
      code: "TEST001",
      name: meetingName,
      organizer: "测试组委会",
      startTime: "2026-07-20T09:00:00",
      endTime: "2026-07-20T17:00:00"
    },
    assets: [],
    registrationOpen,
    registrationMessage: registrationOpen ? "报名开放中" : "报名时间已结束"
  };
}

function payloadWithActions(): PublicInvitationPayload {
  const result = payload(true);
  result.publicQueryToken = "public-query-token";
  result.designDocument.h5.elements.push(
    actionElement("checkin", "CHECKIN_BUTTON", 120, { text: "现场签到" }),
    actionElement("query", "QUERY_BUTTON", 190, { text: "查询住宿", queryTarget: "ROOM_QUERY" })
  );
  return result;
}

function actionElement(
  id: string,
  type: "CHECKIN_BUTTON" | "QUERY_BUTTON",
  y: number,
  props: InvitationElementV1["props"]
): InvitationElementV1 {
  return {
    id,
    type,
    x: 20,
    y,
    width: 335,
    height: 52,
    rotation: 0,
    zIndex: y,
    locked: false,
    props
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
