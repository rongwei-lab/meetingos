// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, onMounted, type App } from "vue";
import InvitationDesignerView from "../src/views/invitation/InvitationDesignerView.vue";
import type { InvitationDesignDocumentV1, InvitationDetail, InvitationShareLink } from "../src/types/invitation";

const state = vi.hoisted(() => ({
  route: undefined as undefined | { params: Record<string, string>; query: Record<string, string> },
  routeUpdateGuard: undefined as undefined | ((to: RouteLike, from: RouteLike) => Promise<boolean> | boolean),
  routeLeaveGuard: undefined as undefined | (() => Promise<boolean> | boolean),
  push: vi.fn(),
  syncMeetingFromRoute: vi.fn(),
  messages: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  api: {
    getInvitation: vi.fn(),
    listInvitationAssets: vi.fn(),
    listInvitationShareLinks: vi.fn(),
    saveInvitationDraft: vi.fn(),
    changeInvitationStatus: vi.fn(),
    uploadInvitationAsset: vi.fn()
  }
}));

interface RouteLike {
  params: Record<string, string>;
}

vi.mock("vue-router", async () => {
  const { reactive } = await import("vue");
  state.route = reactive({ params: { meetingId: "1", invitationId: "1" }, query: {} });
  return {
    useRoute: () => state.route,
    useRouter: () => ({ push: state.push }),
    onBeforeRouteUpdate: (guard: typeof state.routeUpdateGuard) => { state.routeUpdateGuard = guard; },
    onBeforeRouteLeave: (guard: typeof state.routeLeaveGuard) => { state.routeLeaveGuard = guard; }
  };
});

vi.mock("@/composables/useRouteMeeting", async () => {
  const { computed } = await import("vue");
  return {
    useRouteMeeting: () => ({
      routeMeetingId: computed(() => String(state.route?.params.meetingId || "")),
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
      publicQueryToken: "public-query-token",
      name: "测试会议",
      organizer: "测试组委会",
      startTime: "2026-07-20T09:00:00",
      endTime: "2026-07-20T17:00:00",
      venue: "测试会场",
      seatQueryEnabled: true,
      roomQueryEnabled: true,
      dinnerQueryEnabled: true,
      checkinEnabled: true,
      selfCheckinEnabled: true
    }
  })
}));

vi.mock("@/api/invitations", () => state.api);

vi.mock("naive-ui", async (importOriginal) => {
  const actual = await importOriginal<typeof import("naive-ui")>();
  const { defineComponent, h: render } = await import("vue");
  const dropdown = defineComponent({
    props: { options: { type: Array, default: () => [] } },
    emits: ["select"],
    setup: (props, { emit, slots }) => () => render("div", [
      slots.default?.(),
      ...(props.options as Array<{ key?: string | number; label?: string }>).filter((item) => item.label && item.key != null).map((item) => render("button", {
        type: "button",
        onClick: () => emit("select", item.key)
      }, item.label))
    ])
  });
  const colorPicker = defineComponent({
    props: { value: String },
    emits: ["update:value"],
    setup: (props, { emit }) => () => render("button", {
      type: "button",
      "data-color-value": props.value,
      onClick: () => emit("update:value", "#123456")
    }, props.value)
  });
  const select = defineComponent({
    inheritAttrs: false,
    props: ["value", "options", "multiple", "placeholder"],
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => () => {
      const options = (props.options || []) as Array<{ label?: string; value?: string | number }>;
      return render("select", {
        ...attrs,
        multiple: Boolean(props.multiple),
        value: props.multiple ? undefined : String(props.value ?? ""),
        "data-select-value": String(props.value ?? ""),
        onChange: (event: Event) => {
          const target = event.target as HTMLSelectElement;
          if (props.multiple) {
            const values = Array.from(target.selectedOptions).map((item) => options.find((option) => String(option.value) === item.value)?.value ?? item.value);
            emit("update:value", values);
            return;
          }
          emit("update:value", options.find((option) => String(option.value) === target.value)?.value ?? target.value);
        }
      }, [
        props.placeholder ? render("option", { value: "", disabled: true }, String(props.placeholder)) : null,
        ...options.map((option) => render("option", { value: String(option.value) }, option.label || String(option.value)))
      ]);
    }
  });
  return {
    ...actual,
    NColorPicker: colorPicker,
    NDropdown: dropdown,
    NSelect: select,
    useMessage: () => state.messages
  };
});

vi.mock("@/components/invitation/InvitationFabricCanvas.vue", () => ({
  default: {
    name: "InvitationFabricCanvasStub",
    emits: ["ready"],
    setup: (_props: unknown, { emit, expose }: { emit: (event: string) => void; expose: (value: object) => void }) => {
      expose({ exportPng: () => "data:image/png;base64,test", selectAll: () => undefined, discardSelection: () => undefined });
      onMounted(() => emit("ready"));
      return () => h("div", { class: "fabric-stub" });
    }
  }
}));

vi.mock("@/components/invitation/InvitationDomRenderer.vue", () => ({
  default: { name: "InvitationDomRendererStub", setup: () => () => h("div", { class: "dom-renderer-stub" }) }
}));

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  if (!state.route) throw new Error("路由 mock 未初始化");
  state.route.params.meetingId = "1";
  state.route.params.invitationId = "1";
  state.route.query = {};
  state.routeUpdateGuard = undefined;
  state.routeLeaveGuard = undefined;
  state.push.mockReset();
  state.syncMeetingFromRoute.mockReset().mockResolvedValue({ id: 1 });
  Object.values(state.messages).forEach((mock) => mock.mockReset());
  Object.values(state.api).forEach((mock) => mock.mockReset());
  state.api.getInvitation.mockImplementation((_meetingId: string, invitationId: string) => Promise.resolve(detail(Number(invitationId), `方案 ${invitationId}`)));
  state.api.listInvitationAssets.mockResolvedValue([]);
  state.api.listInvitationShareLinks.mockResolvedValue([]);
  state.api.saveInvitationDraft.mockImplementation((_meetingId: string, invitationId: number, payload: { document: InvitationDesignDocumentV1 }) => Promise.resolve({
    ...detail(invitationId, `方案 ${invitationId}`),
    draftRevision: 2,
    draftDocument: payload.document
  }));
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("邀请函设计器路由与异步状态", () => {
  it("切换方案前保存草稿，保存失败时阻止路由更新", async () => {
    await mountView();
    clickButton("文本");
    await nextTick();

    const guard = requireRouteUpdateGuard();
    await expect(guard(routeTo("2"), routeTo("1"))).resolves.toBe(true);
    expect(state.api.saveInvitationDraft).toHaveBeenCalledTimes(1);

    clickButton("形状");
    await nextTick();
    state.api.saveInvitationDraft.mockRejectedValueOnce(new Error("network unavailable"));

    await expect(guard(routeTo("2"), routeTo("1"))).resolves.toBe(false);
    expect(state.api.changeInvitationStatus).not.toHaveBeenCalled();
    expect(state.messages.error).toHaveBeenCalledWith("草稿尚未保存，请稍后重试");
  });

  it("忽略旧方案迟到的加载响应", async () => {
    await mountView();
    const second = deferred<InvitationDetail>();
    state.api.getInvitation.mockImplementation((_meetingId: string, invitationId: string) => {
      if (invitationId === "2") return second.promise;
      return Promise.resolve(detail(Number(invitationId), `方案 ${invitationId}`));
    });

    state.route!.params.invitationId = "2";
    await vi.waitFor(() => expect(state.api.getInvitation).toHaveBeenCalledWith("1", "2"));
    state.route!.params.invitationId = "3";
    await vi.waitFor(() => expect(root?.textContent).toContain("方案 3"));

    second.resolve(detail(2, "迟到的方案 2"));
    await nextTick();
    await nextTick();

    expect(root?.textContent).toContain("方案 3");
    expect(root?.textContent).not.toContain("迟到的方案 2");
  });

  it("归档方案只允许预览和复制链接", async () => {
    state.api.getInvitation.mockResolvedValue(detail(1, "归档方案", "ARCHIVED"));
    await mountView();

    expect(root?.querySelector(".mobile-preview-area")).not.toBeNull();
    expect(root?.textContent).toContain("复制链接");
    expect(root?.textContent).not.toContain("保存草稿");
  });

  it("归档前先排空草稿保存队列", async () => {
    state.api.changeInvitationStatus.mockResolvedValue(detail(1, "方案 1", "ARCHIVED"));
    await mountView();
    clickButton("文本");
    await nextTick();

    clickButton("归档方案");
    await vi.waitFor(() => expect(state.api.changeInvitationStatus).toHaveBeenCalledWith("1", 1, "archive"));

    const saveOrder = state.api.saveInvitationDraft.mock.invocationCallOrder[0];
    const archiveOrder = state.api.changeInvitationStatus.mock.invocationCallOrder[0];
    expect(saveOrder).toBeLessThan(archiveOrder!);
  });

  it("草稿保存失败时不执行下线", async () => {
    state.api.getInvitation.mockResolvedValue(detail(1, "已发布方案", "PUBLISHED"));
    state.api.saveInvitationDraft.mockRejectedValueOnce(new Error("network unavailable"));
    await mountView();
    clickButton("形状");
    await nextTick();

    clickButton("下线公开页面");
    await vi.waitFor(() => expect(state.messages.error).toHaveBeenCalledWith("草稿尚未成功保存，无法更新方案状态"));

    expect(state.api.changeInvitationStatus).not.toHaveBeenCalled();
  });

  it("草稿海报只选择已启用且未过期的渠道", async () => {
    state.api.listInvitationShareLinks.mockResolvedValue([
      shareLink({ id: 10, channelName: "已过期渠道", expiresAt: "2020-01-01T00:00:00" }),
      shareLink({ id: 11, channelName: "草稿渠道", currentVersionNo: null, expiresAt: "2099-01-01T00:00:00" })
    ]);
    await mountView();

    clickButton("画布");
    await nextTick();
    clickButton("导出海报 PNG");

    await vi.waitFor(() => expect(findButton("导出 1080×1920 PNG", document.body).disabled).toBe(false));
    expect(document.body.textContent).toContain("草稿渠道 · 尚未发布");
    expect(document.body.textContent).not.toContain("已过期渠道");
  });

  it("文字对齐使用独立的单行分段控件", async () => {
    await mountView();
    clickButton("文本");
    await nextTick();

    const alignmentField = root?.querySelector(".inspector-field");
    const alignmentControl = alignmentField?.querySelector('[aria-label="文字对齐"]');

    expect(alignmentField?.tagName).toBe("DIV");
    expect(alignmentControl?.classList.contains("text-align-control")).toBe(true);
    expect(alignmentControl?.querySelectorAll("label")).toHaveLength(3);
  });

  it("形状、分隔线和报名按钮提供明确的颜色设置", async () => {
    await mountView();

    clickButton("形状");
    await nextTick();
    expect(root?.textContent).toContain("形状类型");
    expect(root?.textContent).toContain("圆角矩形");
    expect(root?.textContent).toContain("填充颜色");
    expect(root?.textContent).toContain("边框颜色");
    clickColorPicker("填充颜色");
    clickColorPicker("边框颜色");

    clickButton("分隔线");
    await nextTick();
    expect(root?.textContent).toContain("线条颜色");
    clickColorPicker("线条颜色");

    clickButton("报名按钮");
    await nextTick();
    expect(root?.textContent).toContain("按钮颜色");
    expect(root?.textContent).toContain("文字颜色");
    clickColorPicker("按钮颜色");
    clickColorPicker("文字颜色");

    clickButton("保存草稿");
    await vi.waitFor(() => expect(state.api.saveInvitationDraft).toHaveBeenCalled());

    const savedDocument = state.api.saveInvitationDraft.mock.calls.at(-1)?.[2].document as InvitationDesignDocumentV1;
    const shape = savedDocument.h5.elements.find((item) => item.type === "SHAPE");
    const divider = savedDocument.h5.elements.find((item) => item.type === "DIVIDER");
    const registrationButton = savedDocument.h5.elements.find((item) => item.type === "REGISTRATION_BUTTON");
    expect(shape?.props).toMatchObject({ fill: "#123456", stroke: "#123456" });
    expect(divider?.props).toMatchObject({ color: "#123456" });
    expect(registrationButton?.props).toMatchObject({ fill: "#123456", color: "#123456" });
  });

  it("H5 元素面板提供签到、查询和二维码组件", async () => {
    await mountView();

    const paletteLabels = Array.from(root?.querySelectorAll(".element-grid button span") || []).map((item) => item.textContent?.trim());
    expect(paletteLabels).toEqual(expect.arrayContaining(["签到按钮", "查询按钮", "二维码"]));
  });

  it("查询按钮默认使用综合查询，并可切换目标后保存", async () => {
    await mountView();

    clickButton("查询按钮");
    await nextTick();
    expect(findSelect("查询功能").value).toBe("COMBINED_QUERY");

    changeSelect("查询功能", "ROOM_QUERY");
    await nextTick();
    clickButton("保存草稿");
    await vi.waitFor(() => expect(state.api.saveInvitationDraft).toHaveBeenCalled());

    const savedDocument = state.api.saveInvitationDraft.mock.calls.at(-1)?.[2].document as InvitationDesignDocumentV1;
    const queryButton = savedDocument.h5.elements.find((item) => item.type === "QUERY_BUTTON");
    expect(queryButton?.props).toMatchObject({ queryTarget: "ROOM_QUERY", text: "查询住宿" });
  });

  it("二维码默认用于报名，并保存自定义说明", async () => {
    await mountView();

    clickButton("二维码");
    await nextTick();
    expect(findSelect("二维码用途").value).toBe("REGISTRATION");
    expect(findInput("二维码说明").value).toBe("扫码报名");

    changeInput("二维码说明", "扫码填写报名表");
    await nextTick();
    clickButton("保存草稿");
    await vi.waitFor(() => expect(state.api.saveInvitationDraft).toHaveBeenCalled());

    const savedDocument = state.api.saveInvitationDraft.mock.calls.at(-1)?.[2].document as InvitationDesignDocumentV1;
    const qrCode = savedDocument.h5.elements.find((item) => item.type === "CHANNEL_QR_CODE");
    expect(qrCode?.props).toMatchObject({ purpose: "REGISTRATION", label: "扫码填写报名表" });
  });

  it("海报模式不展示签到和查询按钮", async () => {
    await mountView();

    clickButton("海报");
    await nextTick();

    const paletteLabels = Array.from(root?.querySelectorAll(".element-grid button span") || []).map((item) => item.textContent?.trim());
    expect(paletteLabels).toContain("二维码");
    expect(paletteLabels).not.toContain("签到按钮");
    expect(paletteLabels).not.toContain("查询按钮");
  });
});

async function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(InvitationDesignerView);
  app.component("NModal", {
    props: { show: Boolean },
    setup: (props, { slots }) => () => props.show ? h("div", { class: "modal-stub" }, [slots.default?.(), slots.footer?.()]) : null
  });
  app.component("NFormItem", { setup: (_props, { slots }) => () => h("div", slots.default?.()) });
  app.component("NSpace", { setup: (_props, { slots }) => () => h("div", slots.default?.()) });
  app.component("NAlert", { setup: (_props, { slots }) => () => h("div", slots.default?.()) });
  app.component("NInput", {
    inheritAttrs: false,
    props: ["value", "type", "readonly", "maxlength", "placeholder"],
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => () => h(props.type === "textarea" ? "textarea" : "input", {
      ...attrs,
      value: String(props.value ?? ""),
      readonly: Boolean(props.readonly),
      maxlength: props.maxlength,
      placeholder: props.placeholder,
      onInput: (event: Event) => emit("update:value", (event.target as HTMLInputElement | HTMLTextAreaElement).value)
    })
  });
  app.config.warnHandler = () => undefined;
  app.mount(root);
  await vi.waitFor(() => expect(root?.querySelector(".designer-shell")).not.toBeNull());
}

function clickButton(label: string) {
  findButton(label, root).click();
}

function clickColorPicker(label: string) {
  const field = Array.from(root?.querySelectorAll("label") || []).find((item) => item.textContent?.includes(label));
  const button = field?.querySelector("button");
  if (!button) throw new Error(`未找到颜色设置：${label}`);
  button.click();
}

function findSelect(label: string) {
  const select = findField(label).querySelector("select");
  if (!(select instanceof HTMLSelectElement)) throw new Error(`未找到选择框：${label}`);
  return select;
}

function changeSelect(label: string, value: string) {
  const select = findSelect(label);
  select.value = value;
  select.dispatchEvent(new Event("change", { bubbles: true }));
}

function findInput(label: string) {
  const input = findField(label).querySelector("input, textarea");
  if (!(input instanceof HTMLInputElement) && !(input instanceof HTMLTextAreaElement)) throw new Error(`未找到输入框：${label}`);
  return input;
}

function changeInput(label: string, value: string) {
  const input = findInput(label);
  input.value = value;
  input.dispatchEvent(new Event("input", { bubbles: true }));
}

function findField(label: string) {
  const field = Array.from(root?.querySelectorAll("label") || []).find((item) => item.textContent?.includes(label));
  if (!field) throw new Error(`未找到设置项：${label}`);
  return field;
}

function findButton(label: string, container: ParentNode | null) {
  const button = Array.from(container?.querySelectorAll("button") || []).find((item) => item.textContent?.trim() === label);
  if (!button) throw new Error(`未找到按钮：${label}`);
  return button;
}

function requireRouteUpdateGuard() {
  const guard = state.routeUpdateGuard;
  if (!guard) throw new Error("未注册路由更新守卫");
  return async (to: RouteLike, from: RouteLike) => await guard(to, from);
}

function routeTo(invitationId: string): RouteLike {
  return { params: { meetingId: "1", invitationId } };
}

function detail(id: number, name: string, status: InvitationDetail["status"] = "DRAFT"): InvitationDetail {
  return {
    id,
    name,
    status,
    draftRevision: 1,
    publishedAt: null,
    updatedAt: "2026-07-14T10:00:00",
    draftDocument: documentFixture(),
    latestPublishedVersion: null
  };
}

function documentFixture(): InvitationDesignDocumentV1 {
  return {
    version: 1,
    h5: { width: 375, height: 667, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: [] },
    poster: { width: 1080, height: 1920, backgroundColor: "#ffffff", backgroundImageAssetId: null, elements: [] }
  };
}

function shareLink(overrides: Partial<InvitationShareLink> = {}): InvitationShareLink {
  return {
    id: 11,
    invitationId: 1,
    channelType: "OFFLINE_POSTER",
    channelName: "线下海报",
    shareToken: "share-token",
    shareUrl: "https://meeting.example/i/share-token",
    enabled: true,
    expiresAt: null,
    currentVersionNo: 1,
    createdAt: "2026-07-14T10:00:00",
    updatedAt: "2026-07-14T10:00:00",
    ...overrides
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}
