// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, defineComponent, h, type App, type Component } from "vue";
import InvitationWorkspaceView from "../src/views/invitation/InvitationWorkspaceView.vue";
import type { InvitationShareLink, InvitationSummary } from "../src/types/invitation";

const state = vi.hoisted(() => ({
  route: undefined as undefined | { params: Record<string, string>; query: Record<string, string> },
  replace: vi.fn(),
  push: vi.fn(),
  syncMeetingFromRoute: vi.fn(),
  messages: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  api: {
    changeInvitationStatus: vi.fn(),
    copyInvitation: vi.fn(),
    createInvitation: vi.fn(),
    createInvitationShareLink: vi.fn(),
    detectTduckAttributionCapability: vi.fn(),
    getInvitation: vi.fn(),
    getInvitationAnalytics: vi.fn(),
    listInvitationAssets: vi.fn(),
    listInvitations: vi.fn(),
    listInvitationShareLinks: vi.fn(),
    updateInvitationShareLink: vi.fn()
  }
}));

vi.mock("vue-router", async () => {
  const { reactive } = await import("vue");
  state.route = reactive({ params: { meetingId: "1" }, query: { tab: "channels" } });
  return {
    useRoute: () => state.route,
    useRouter: () => ({ replace: state.replace, push: state.push })
  };
});

vi.mock("@/composables/useRouteMeeting", async () => {
  const { computed } = await import("vue");
  return {
    useRouteMeeting: () => ({
      routeMeetingId: computed(() => state.route?.params.meetingId || ""),
      syncMeetingFromRoute: state.syncMeetingFromRoute
    })
  };
});

vi.mock("@/composables/useResponsive", async () => {
  const { ref } = await import("vue");
  return { useIsMobile: () => ref(false) };
});

vi.mock("@/stores/meeting", () => ({
  useMeetingStore: () => ({
    currentMeeting: {
      id: 1,
      code: "MEETING001",
      name: "测试会议",
      organizer: "测试组委会",
      startTime: "2026-07-20T09:00:00",
      endTime: "2026-07-20T17:00:00"
    }
  })
}));

vi.mock("@/api/invitations", () => state.api);

vi.mock("naive-ui", async (importOriginal) => {
  const actual = await importOriginal<typeof import("naive-ui")>();
  const passthrough = defineComponent({ setup: (_props, { slots }) => () => h("div", slots.default?.()) });
  const dataTable = defineComponent({
    props: { data: { type: Array, default: () => [] } },
    setup: (props) => () => h("div", { class: "data-table-stub" }, (props.data as InvitationShareLink[]).map((row) => row.channelName))
  });
  return {
    ...actual,
    NButton: passthrough,
    NDataTable: dataTable,
    NDatePicker: passthrough,
    NIcon: passthrough,
    NPopconfirm: passthrough,
    NSwitch: passthrough,
    NTag: passthrough,
    useMessage: () => state.messages
  };
});

vi.mock("@/components/invitation/InvitationAnalyticsChart.vue", () => ({
  default: { name: "InvitationAnalyticsChartStub", setup: () => () => h("div") }
}));

vi.mock("@/components/invitation/InvitationFabricCanvas.vue", () => ({
  default: { name: "InvitationFabricCanvasStub", setup: () => () => h("div") }
}));

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  if (!state.route) throw new Error("路由 mock 未初始化");
  state.route.params.meetingId = "1";
  state.route.query = { tab: "channels" };
  state.replace.mockReset();
  state.push.mockReset();
  state.syncMeetingFromRoute.mockReset().mockResolvedValue({ id: 1 });
  Object.values(state.messages).forEach((mock) => mock.mockReset());
  Object.values(state.api).forEach((mock) => mock.mockReset());
  state.api.detectTduckAttributionCapability.mockResolvedValue(false);
  state.api.listInvitationShareLinks.mockResolvedValue([shareLink()]);
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("邀请函渠道工作区初始化", () => {
  it("直达渠道页时在默认方案确定后加载对应渠道", async () => {
    const invitationRequest = deferred<InvitationSummary[]>();
    state.api.listInvitations.mockReturnValue(invitationRequest.promise);
    mountView();

    expect(state.api.listInvitationShareLinks).not.toHaveBeenCalled();
    invitationRequest.resolve([invitation()]);

    await vi.waitFor(() => expect(state.api.listInvitationShareLinks).toHaveBeenCalledWith("1", 42));
    expect(state.api.listInvitationShareLinks).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(root?.textContent).toContain("合作方社群"));
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(InvitationWorkspaceView);
  const passthrough: Component = { setup: (_props, { slots }) => () => h("div", slots.default?.()) };
  ["NAlert", "NSpin", "NInput", "NEmpty", "NSelect", "NModal", "NForm", "NFormItem", "NRadioGroup", "NRadioButton", "NSpace"]
    .forEach((name) => app?.component(name, passthrough));
  app.config.warnHandler = () => undefined;
  app.mount(root);
}

function invitation(): InvitationSummary {
  return {
    id: 42,
    name: "主视觉发布版",
    status: "PUBLISHED",
    draftRevision: 3,
    latestVersionNo: 1,
    shareLinkCount: 1,
    publishedAt: "2026-07-14T10:00:00",
    updatedAt: "2026-07-14T10:00:00"
  };
}

function shareLink(): InvitationShareLink {
  return {
    id: 7,
    invitationId: 42,
    channelType: "WECHAT_FRIENDS",
    channelName: "合作方社群",
    shareToken: "share-token",
    shareUrl: "https://meeting.example/i/share-token",
    enabled: true,
    expiresAt: null,
    currentVersionNo: 1,
    createdAt: "2026-07-14T10:00:00",
    updatedAt: "2026-07-14T10:00:00"
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}
