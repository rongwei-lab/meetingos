// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, reactive, type App } from "vue";
import InvitationMap from "../src/components/invitation/InvitationMap.vue";

const { loadMap } = vi.hoisted(() => ({ loadMap: vi.fn() }));

vi.mock("@amap/amap-jsapi-loader", () => ({ default: { load: loadMap } }));

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  loadMap.mockReset();
  vi.stubEnv("VITE_AMAP_KEY", "");
  vi.stubEnv("VITE_AMAP_SECURITY_CODE", "");
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
  vi.unstubAllEnvs();
});

describe("邀请函地图导航降级", () => {
  it("缺少密钥且导航地址非法时使用当前经纬度和地址生成高德 HTTPS 链接", async () => {
    const state = mountMap({
      longitude: 121.49,
      latitude: 31.24,
      address: "上海会议中心",
      navigationUrl: "javascript:alert(1)"
    });

    await vi.waitFor(() => expect(root!.textContent).toContain("地图服务未配置"));
    expectNavigation({ position: "121.49,31.24", name: "上海会议中心" });

    state.longitude = 116.397;
    state.latitude = 39.908;
    state.address = "北京会议中心";
    await nextTick();

    expectNavigation({ position: "116.397,39.908", name: "北京会议中心" });
  });

  it("优先使用给定的合法 HTTPS 导航地址", async () => {
    const navigationUrl = "https://navigation.example/route?destination=113.2644%2C23.1291";
    mountMap({ longitude: 121.49, latitude: 31.24, address: "上海会议中心", navigationUrl });

    await vi.waitFor(() => expect(root!.querySelector(".navigation-link")).not.toBeNull());

    expect(root!.querySelector<HTMLAnchorElement>(".navigation-link")!.href).toBe(navigationUrl);
  });

  it("旧的高德 marker 链接不会覆盖当前坐标和地址", async () => {
    mountMap({
      longitude: 116.397,
      latitude: 39.908,
      address: "北京会议中心",
      navigationUrl: "https://uri.amap.com/marker?position=121.49,31.24&name=%E4%B8%8A%E6%B5%B7%E4%BC%9A%E8%AE%AE%E4%B8%AD%E5%BF%83"
    });

    await vi.waitFor(() => expect(root!.querySelector(".navigation-link")).not.toBeNull());

    expectNavigation({ position: "116.397,39.908", name: "北京会议中心" });
  });

  it("地图脚本加载失败时仍提供生成的导航链接", async () => {
    vi.stubEnv("VITE_AMAP_KEY", "test-key");
    loadMap.mockRejectedValueOnce(new Error("load failed"));
    mountMap({ longitude: 104.066, latitude: 30.572, address: "成都会议中心", navigationUrl: "" });

    await vi.waitFor(() => expect(root!.textContent).toContain("地图加载失败"));

    expectNavigation({ position: "104.066,30.572", name: "成都会议中心" });
  });
});

function mountMap(initial: {
  longitude: number;
  latitude: number;
  address: string;
  navigationUrl: string;
}) {
  const state = reactive({ ...initial, zoom: 15 });
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp({
    setup: () => () => h(InvitationMap, { ...state })
  });
  app.component("NIcon", { setup: () => () => h("span") });
  app.mount(root);
  return state;
}

function expectNavigation(expected: { position: string; name: string }) {
  const anchor = root!.querySelector<HTMLAnchorElement>(".navigation-link")!;
  const url = new URL(anchor.href);
  expect(url.protocol).toBe("https:");
  expect(url.host).toBe("uri.amap.com");
  expect(url.pathname).toBe("/marker");
  expect(url.searchParams.get("position")).toBe(expected.position);
  expect(url.searchParams.get("name")).toBe(expected.name);
  expect(url.searchParams.get("coordinate")).toBe("gaode");
  expect(url.searchParams.get("callnative")).toBe("0");
}
