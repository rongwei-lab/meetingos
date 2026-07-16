// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, ref, type App } from "vue";
import InvitationMapPicker from "../src/components/invitation/InvitationMapPicker.vue";

const amap = vi.hoisted(() => ({
  load: vi.fn(),
  mapHandlers: {} as Record<string, (event: { lnglat?: { lng?: number; lat?: number; getLng?: () => number; getLat?: () => number } }) => void>,
  markerHandlers: {} as Record<string, (event: { lnglat?: { lng?: number; lat?: number; getLng?: () => number; getLat?: () => number } }) => void>,
  markerSetPosition: vi.fn(),
  mapSetCenter: vi.fn(),
  search: vi.fn(),
  getAddress: vi.fn()
}));

vi.mock("@amap/amap-jsapi-loader", () => ({ default: { load: amap.load } }));

vi.mock("naive-ui", async () => {
  const { defineComponent, h: render } = await import("vue");
  const button = defineComponent({
    inheritAttrs: false,
    props: ["disabled", "type", "loading"],
    setup: (props, { attrs, slots }) => () => render("button", { ...attrs, type: "button", disabled: Boolean(props.disabled) }, slots.default?.())
  });
  const input = defineComponent({
    inheritAttrs: false,
    props: ["value", "placeholder", "disabled"],
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => () => render("input", {
      ...attrs,
      value: String(props.value ?? ""),
      placeholder: props.placeholder,
      disabled: Boolean(props.disabled),
      onInput: (event: Event) => emit("update:value", (event.target as HTMLInputElement).value)
    })
  });
  return {
    NAlert: defineComponent({ setup: (_props, { slots }) => () => render("div", { class: "alert-stub" }, slots.default?.()) }),
    NButton: button,
    NEmpty: defineComponent({ props: ["description"], setup: (props) => () => render("div", { class: "empty-stub" }, props.description) }),
    NIcon: defineComponent({ setup: () => () => render("span") }),
    NInput: input,
    NModal: defineComponent({
      props: ["show"],
      emits: ["update:show"],
      setup: (props, { slots }) => () => props.show ? render("div", { class: "modal-stub" }, [slots.default?.(), slots.footer?.()]) : null
    }),
    NSpace: defineComponent({ setup: (_props, { slots }) => () => render("div", slots.default?.()) }),
    NSpin: defineComponent({ setup: () => () => render("span", "loading") })
  };
});

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  Object.values(amap).forEach((value) => {
    if (typeof value === "function" && "mockReset" in value) value.mockReset();
  });
  amap.mapHandlers = {};
  amap.markerHandlers = {};
  vi.stubEnv("VITE_AMAP_KEY", "test-key");
  vi.stubEnv("VITE_AMAP_SECURITY_CODE", "test-security");
  amap.load.mockResolvedValue(createMockAmap());
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
  vi.unstubAllEnvs();
});

describe("邀请函地图选点弹窗", () => {
  it("通过地点搜索选择位置后写回地址、经纬度和导航链接", async () => {
    amap.search.mockImplementation((_keyword: string, callback: (status: string, result: unknown) => void) => callback("complete", {
      poiList: {
        pois: [{
          id: "poi-1",
          name: "北京会议中心",
          address: "北四环中路8号",
          location: { getLng: () => 116.397128, getLat: () => 39.916527 }
        }]
      }
    }));
    const confirmed = await mountPicker();
    await vi.waitFor(() => expect(amap.mapHandlers.click).toBeTypeOf("function"));

    changeSearchInput("北京会议中心");
    clickButton("搜索");
    await vi.waitFor(() => expect(root?.textContent).toContain("北京会议中心"));
    await nextTick();
    clickButton("北京会议中心北四环中路8号");
    clickButton("确认位置");

    expect(confirmed.value).toMatchObject({
      address: "北京会议中心 · 北四环中路8号",
      longitude: 116.397128,
      latitude: 39.916527
    });
    const url = new URL(confirmed.value!.navigationUrl);
    expect(url.searchParams.get("position")).toBe("116.397128,39.916527");
    expect(url.searchParams.get("name")).toBe("北京会议中心 · 北四环中路8号");
  });

  it("点击地图选点后使用逆地理编码地址", async () => {
    amap.getAddress.mockImplementation((_position: [number, number], callback: (status: string, result: unknown) => void) => callback("complete", {
      regeocode: { formattedAddress: "四川省成都市高新区天府大道" }
    }));
    const confirmed = await mountPicker();
    await vi.waitFor(() => expect(amap.mapHandlers.click).toBeTypeOf("function"));

    amap.mapHandlers.click?.({ lnglat: { lng: 104.066541, lat: 30.572269 } });
    await nextTick();
    clickButton("确认位置");

    expect(amap.markerSetPosition).toHaveBeenCalledWith([104.066541, 30.572269]);
    expect(amap.mapSetCenter).toHaveBeenCalledWith([104.066541, 30.572269]);
    expect(confirmed.value).toMatchObject({
      address: "四川省成都市高新区天府大道",
      longitude: 104.066541,
      latitude: 30.572269
    });
  });

  it("缺少高德密钥时禁用选点确认", async () => {
    vi.stubEnv("VITE_AMAP_KEY", "");
    await mountPicker();

    expect(root?.textContent).toContain("当前构建未配置高德地图密钥");
    expect(findButton("确认位置").disabled).toBe(true);
    expect(amap.load).not.toHaveBeenCalled();
  });
});

async function mountPicker() {
  const visible = ref(true);
  const confirmed = ref<{ address: string; longitude: number; latitude: number; navigationUrl: string } | null>(null);
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp({
    setup: () => () => h(InvitationMapPicker, {
      show: visible.value,
      address: "主会场",
      longitude: 121.49,
      latitude: 31.24,
      "onUpdate:show": (value: boolean) => { visible.value = value; },
      onConfirm: (value: { address: string; longitude: number; latitude: number; navigationUrl: string }) => { confirmed.value = value; }
    })
  });
  app.mount(root);
  await vi.waitFor(() => expect(root?.querySelector(".modal-stub")).not.toBeNull());
  if (import.meta.env.VITE_AMAP_KEY) {
    await vi.waitFor(() => expect(amap.load).toHaveBeenCalled());
  }
  await nextTick();
  return confirmed;
}

function createMockAmap() {
  return {
    Map: class {
      add = vi.fn();
      setCenter = amap.mapSetCenter;
      destroy = vi.fn();
      on(event: string, handler: (event: { lnglat?: { lng?: number; lat?: number } }) => void) {
        amap.mapHandlers[event] = handler;
      }
    },
    Marker: class {
      setPosition = amap.markerSetPosition;
      on(event: string, handler: (event: { lnglat?: { lng?: number; lat?: number } }) => void) {
        amap.markerHandlers[event] = handler;
      }
    },
    ToolBar: class {},
    PlaceSearch: class {
      search(keyword: string, callback: (status: string, result: unknown) => void) {
        amap.search(keyword, callback);
      }
    },
    Geocoder: class {
      getAddress(position: [number, number], callback: (status: string, result: unknown) => void) {
        amap.getAddress(position, callback);
      }
    }
  };
}

function changeSearchInput(value: string) {
  const input = root?.querySelector<HTMLInputElement>('input[placeholder="搜索地点、酒店、会场或详细地址"]');
  if (!input) throw new Error("未找到搜索输入框");
  input.value = value;
  input.dispatchEvent(new Event("input", { bubbles: true }));
}

function clickButton(label: string) {
  findButton(label).click();
}

function findButton(label: string) {
  const normalized = (value: string | null | undefined) => value?.replace(/\s+/g, "").trim();
  const button = Array.from(root?.querySelectorAll("button") || []).find((item) => normalized(item.textContent) === normalized(label));
  if (!button) throw new Error(`未找到按钮：${label}`);
  return button as HTMLButtonElement;
}
