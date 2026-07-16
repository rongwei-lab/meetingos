// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, defineComponent, h, ref, type App, type Component } from "vue";
import RegistrationConfigView from "../src/views/registration/RegistrationConfigView.vue";

const state = vi.hoisted(() => ({
  meeting: {
    id: "3",
    code: "MEET-730",
    name: "730",
    tduckFormKey: "",
    tduckFormName: "",
    registrationUrl: ""
  },
  binding: null as null | {
    formKey: string;
    formName: string;
    registrationUrl: string;
    registrationEnabled: boolean;
    tduckPublished: boolean;
    validationStatus: string;
  },
  loadTduckBinding: vi.fn(),
  loadTduckForms: vi.fn(),
  loadTduckMappings: vi.fn(),
  loadSyncLogs: vi.fn(),
  syncMeetingFromRoute: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() })
}));

vi.mock("@/composables/useRouteMeeting", () => ({
  useRouteMeeting: () => ({
    routeMeetingId: ref("3"),
    syncMeetingFromRoute: state.syncMeetingFromRoute
  })
}));

vi.mock("@/stores/meeting", () => ({
  useMeetingStore: () => ({
    currentMeetingId: "3",
    currentMeeting: state.meeting,
    syncLogs: [],
    loadTduckBinding: state.loadTduckBinding,
    loadTduckForms: state.loadTduckForms,
    loadTduckMappings: state.loadTduckMappings,
    loadSyncLogs: state.loadSyncLogs
  })
}));

vi.mock("@/api/http", () => ({
  extractErrorMessage: (_error: unknown, fallback: string) => fallback
}));

vi.mock("qrcode", () => ({
  default: { toCanvas: vi.fn().mockResolvedValue(undefined) }
}));

vi.mock("@/components/PageHeader.vue", () => ({
  default: defineComponent({
    props: { title: String },
    setup: (props, { slots }) => () => h("header", [h("h1", props.title), slots.default?.()])
  })
}));

vi.mock("@/components/StatusTag.vue", () => ({
  default: defineComponent({
    props: { value: String },
    setup: (props) => () => h("span", props.value)
  })
}));

vi.mock("naive-ui", () => {
  const passthrough = defineComponent({
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, slots.default?.())
  });
  return {
    NDataTable: passthrough,
    NIcon: passthrough,
    NSelect: passthrough,
    NSwitch: passthrough,
    useMessage: () => ({
      error: vi.fn(),
      info: vi.fn(),
      success: vi.fn(),
      warning: vi.fn()
    })
  };
});

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  state.meeting.tduckFormKey = "";
  state.meeting.tduckFormName = "";
  state.meeting.registrationUrl = "";
  state.binding = null;
  state.loadTduckBinding.mockReset().mockImplementation(async () => state.binding);
  state.loadTduckForms.mockReset().mockResolvedValue([]);
  state.loadTduckMappings.mockReset().mockResolvedValue([]);
  state.loadSyncLogs.mockReset().mockResolvedValue([]);
  state.syncMeetingFromRoute.mockReset().mockResolvedValue(state.meeting);
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("TDuck 表单新建入口", () => {
  it("会议已有绑定表单时隐藏新建入口并保留编辑入口", async () => {
    state.meeting.tduckFormKey = "RbFOPlV5";
    state.meeting.tduckFormName = "730 报名表";
    state.binding = {
      formKey: "RbFOPlV5",
      formName: "730 报名表",
      registrationUrl: "/tduck/s/RbFOPlV5",
      registrationEnabled: true,
      tduckPublished: true,
      validationStatus: "VALID"
    };

    mountView();
    await vi.waitFor(() => expect(state.loadTduckBinding).toHaveBeenCalledWith("3"));
    await vi.waitFor(() => expect(root?.textContent).toContain("已绑定"));

    expect(root?.textContent).not.toContain("新建 TDuck 表单");
    expect(root?.textContent).toContain("编辑当前表单");
  });

  it("会议尚未绑定表单时保留新建入口", async () => {
    mountView();
    await vi.waitFor(() => expect(state.loadTduckBinding).toHaveBeenCalledWith("3"));

    expect(root?.textContent).toContain("新建 TDuck 表单");
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(RegistrationConfigView);

  const passthrough: Component = {
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, [slots.header?.(), slots.default?.()])
  };
  for (const name of ["NAlert", "NButton", "NCard", "NDropdown", "NEmpty", "NForm", "NFormItem", "NInput", "NSpace", "NTag"]) {
    app.component(name, passthrough);
  }
  app.mount(root);
}
