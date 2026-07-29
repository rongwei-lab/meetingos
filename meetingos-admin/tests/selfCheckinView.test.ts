// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, defineComponent, h, nextTick, reactive, type App, type Component } from "vue";
import SelfCheckinView from "../src/views/public/SelfCheckinView.vue";
import type { MeetingQuerySettings } from "../src/utils/types";

const state = vi.hoisted(() => ({
  loadPublicQuerySettings: vi.fn(),
  publicSelfCheckin: vi.fn(),
  publicSelfCheckinPreview: vi.fn(),
  loadPublicCaptcha: vi.fn()
}));

const routeState = reactive({ query: { token: "public-query-token" } });

vi.mock("vue-router", () => ({ useRoute: () => routeState }));
vi.mock("@/stores/meeting", () => ({ useMeetingStore: () => state }));
vi.mock("naive-ui", () => ({
  useMessage: () => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn() })
}));

const passthrough = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h("div", attrs, slots.default?.());
  }
});

const formItem = defineComponent({
  inheritAttrs: false,
  props: { label: String },
  setup(props, { attrs, slots }) {
    return () => h("label", attrs, [h("span", props.label), slots.default?.()]);
  }
});

const input = defineComponent({
  inheritAttrs: false,
  props: { value: { type: String, default: "" } },
  emits: ["update:value"],
  setup(props, { attrs, emit }) {
    return () => h("input", {
      ...attrs,
      value: props.value,
      onInput: (event: Event) => emit("update:value", (event.target as HTMLInputElement).value)
    });
  }
});

const button = defineComponent({
  inheritAttrs: false,
  props: {
    loading: Boolean,
    disabled: Boolean
  },
  setup(props, { attrs, slots }) {
    return () => h("button", {
      ...attrs,
      disabled: props.loading || props.disabled
    }, slots.default?.());
  }
});

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  state.loadPublicQuerySettings.mockReset().mockResolvedValue(settings());
  state.publicSelfCheckin.mockReset().mockResolvedValue({
    status: "DUPLICATE",
    attendeeId: 7,
    attendeeName: "张晓平",
    organization: "会议示例单位",
    phone: "****5888",
    checkedInAt: "2026-07-10T14:58:10",
    message: "您已完成签到，请勿重复提交。"
  });
  state.publicSelfCheckinPreview.mockReset();
  state.loadPublicCaptcha.mockReset();
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("公开自助签到结果", () => {
  it("重复签到时展示参会人的单位", async () => {
    mountView();
    await vi.waitFor(() => expect(root?.querySelector("input")).not.toBeNull());

    const phoneInput = root?.querySelector<HTMLInputElement>("input");
    if (!phoneInput) throw new Error("未找到手机号输入框");
    phoneInput.value = "13800005888";
    phoneInput.dispatchEvent(new Event("input", { bubbles: true }));
    await nextTick();

    findButton("自助签到").click();

    await vi.waitFor(() => expect(root?.textContent).toContain("已签到"));
    expect(root?.textContent).toContain("单位");
    expect(root?.textContent).toContain("会议示例单位");
    expect(root?.textContent).toContain("****5888");
    expect(state.publicSelfCheckin).toHaveBeenCalledOnce();
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(SelfCheckinView);
  register("NAlert", passthrough);
  register("NForm", passthrough);
  register("NFormItem", formItem);
  register("NInput", input);
  register("NButton", button);
  app.mount(root);
}

function register(name: string, component: Component) {
  app?.component(name, component);
}

function findButton(label: string) {
  const target = [...(root?.querySelectorAll<HTMLButtonElement>("button") || [])]
    .find((item) => item.textContent?.trim() === label);
  if (!target) throw new Error(`未找到按钮：${label}`);
  return target;
}

function settings(): MeetingQuerySettings {
  return {
    meetingId: "1",
    meetingCode: "MEET-001",
    publicQueryToken: "public-query-token",
    meetingName: "测试会议",
    seatQueryEnabled: true,
    roomQueryEnabled: true,
    dinnerQueryEnabled: false,
    checkinEnabled: true,
    selfCheckinEnabled: true,
    queryPaused: false,
    fields: ["PHONE"],
    requiredFields: ["PHONE"],
    matchMode: "EXACT",
    allowMultipleResults: false,
    combinedQueryTypes: ["SEAT"],
    pageTitle: "会议自助签到",
    buttonText: "自助签到",
    emptyMessage: "未查询到结果",
    seatNotice: "",
    roomNotice: "",
    dinnerNotice: "",
    pageContents: {
      entry: {},
      combined: {},
      seat: {},
      room: {},
      dinner: {},
      checkin: {}
    },
    themeColor: "#2563eb",
    resultLayout: "CARD",
    visibleResultFields: [],
    maskedResultFields: [],
    highlightResultFields: [],
    seatVisibleResultFields: [],
    seatMaskedResultFields: [],
    seatHighlightResultFields: [],
    roomVisibleResultFields: [],
    roomMaskedResultFields: [],
    roomHighlightResultFields: [],
    dinnerVisibleResultFields: [],
    dinnerMaskedResultFields: [],
    dinnerHighlightResultFields: [],
    captchaEnabled: false,
    fieldOptions: [{ field: "PHONE", label: "手机号" }],
    resultFieldOptions: []
  };
}
