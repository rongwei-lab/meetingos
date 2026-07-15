// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, type App } from "vue";
import InvitationDomRenderer from "../src/components/invitation/InvitationDomRenderer.vue";
import type { InvitationCanvasV1, InvitationElementV1, InvitationNavigationTarget } from "../src/types/invitation";

const { toDataURL } = vi.hoisted(() => ({
  toDataURL: vi.fn(async (_value: string, options: { color?: { dark?: string; light?: string } }) =>
    `data:image/png;base64,${options.color?.dark}-${options.color?.light}`)
}));

vi.mock("qrcode", () => ({ default: { toDataURL } }));

class ResizeObserverMock {
  observe() {}
  disconnect() {}
}

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  toDataURL.mockClear();
  vi.stubGlobal("ResizeObserver", ResizeObserverMock);
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
  vi.unstubAllGlobals();
});

describe("邀请函 DOM 二维码", () => {
  it("每个二维码使用元素自己的前景色和背景色", async () => {
    const elements: InvitationElementV1[] = [
      qrElement("qr_green", 1, "#123456", "#fefefe"),
      qrElement("qr_black", 2, "#000000", "#ffffff")
    ];
    const canvas: InvitationCanvasV1 = {
      width: 375,
      height: 667,
      backgroundColor: "#ffffff",
      backgroundImageAssetId: null,
      elements
    };
    root = document.createElement("div");
    document.body.appendChild(root);
    app = createApp(InvitationDomRenderer, {
      canvas,
      meeting: {},
      shareUrl: "https://meeting.example/i/channel"
    });
    app.component("NIcon", { setup: () => () => h("span") });
    app.mount(root);

    await vi.waitFor(() => expect(toDataURL).toHaveBeenCalledTimes(2));

    expect(toDataURL.mock.calls.map(([, options]) => options.color)).toEqual([
      { dark: "#123456", light: "#fefefe" },
      { dark: "#000000", light: "#ffffff" }
    ]);
    expect(Array.from(root.querySelectorAll<HTMLImageElement>(".render-qr img")).map((image) => image.src)).toEqual([
      "data:image/png;base64,#123456-#fefefe",
      "data:image/png;base64,#000000-#ffffff"
    ]);
  });

  it("按二维码用途生成报名、查询和历史渠道地址，并支持自定义或隐藏说明", async () => {
    const shareToken = "share_token_1234567890";
    const queryToken = "query token + source";
    const shareUrl = "https://meeting.example/i/current_share_token";
    const elements: InvitationElementV1[] = [
      qrElement("qr_legacy", 1, "#123456", "#fefefe"),
      qrElement("qr_registration", 2, "#000000", "#ffffff", {
        purpose: "REGISTRATION",
        label: "现场扫码报名"
      }),
      qrElement("qr_seat", 3, "#0b3425", "#ffffff", {
        purpose: "SEAT_QUERY",
        label: ""
      })
    ];
    const canvas = invitationCanvas(elements, 720);
    mountRenderer(canvas, {
      meeting: { code: "MEETING001" },
      shareUrl,
      shareToken,
      publicQueryToken: queryToken
    });

    await vi.waitFor(() => expect(toDataURL).toHaveBeenCalledTimes(3));

    const targets = toDataURL.mock.calls.map(([value]) => value);
    expect(targets[0]).toBe(shareUrl);
    expect(targets[1]).toBe(`${window.location.origin}/r/MEETING001#mos_inv=${shareToken}`);
    const queryUrl = new URL(targets[2]!);
    expect(queryUrl.origin).toBe(window.location.origin);
    expect(queryUrl.pathname).toBe("/public/seat-query");
    expect(queryUrl.searchParams.get("token")).toBe(queryToken);

    const qrNodes = Array.from(root!.querySelectorAll<HTMLElement>(".render-qr"));
    expect(qrNodes[0]?.querySelector("span")?.textContent).toBe("扫码报名");
    expect(qrNodes[1]?.querySelector("span")?.textContent).toBe("现场扫码报名");
    expect(qrNodes[2]?.querySelector("span")).toBeNull();
    expect(qrNodes[2]?.querySelector("img")?.alt).toBe("会议二维码");
  });

  it("用途依赖的标识缺失时显示不可用原因且不生成伪二维码", async () => {
    const canvas = invitationCanvas([
      qrElement("qr_registration", 1, "#000000", "#ffffff", { purpose: "REGISTRATION" }),
      qrElement("qr_query", 2, "#000000", "#ffffff", { purpose: "ROOM_QUERY" })
    ], 420);
    mountRenderer(canvas, { meeting: { code: "MEETING001" } });

    await vi.waitFor(() => expect(root?.textContent).toContain("请先创建并启用邀请渠道"));
    expect(root?.textContent).toContain("会议未配置公开查询令牌");
    expect(toDataURL).not.toHaveBeenCalled();
    expect(root?.querySelectorAll(".render-qr img")).toHaveLength(0);
  });
});

describe("邀请函 DOM 动作按钮", () => {
  it("报名、签到和可配置查询按钮发出彼此独立的结构化动作", async () => {
    const register = vi.fn();
    const navigations: InvitationNavigationTarget[] = [];
    const canvas = invitationCanvas([
      actionElement("registration", "REGISTRATION_BUTTON", 10, { text: "马上报名" }),
      actionElement("checkin", "CHECKIN_BUTTON", 80, {}),
      actionElement("query", "QUERY_BUTTON", 150, { queryTarget: "ROOM_QUERY" })
    ], 230);
    mountRenderer(canvas, {
      meeting: {},
      publicQueryToken: "query-token",
      onRegister: register,
      onNavigate: (target: InvitationNavigationTarget) => navigations.push(target)
    });
    await nextTick();

    const registration = root!.querySelector<HTMLButtonElement>(".render-registration-button")!;
    const checkin = root!.querySelector<HTMLButtonElement>(".render-checkin-button")!;
    const query = root!.querySelector<HTMLButtonElement>(".render-query-button")!;
    expect(registration.textContent?.trim()).toBe("马上报名");
    expect(checkin.textContent?.trim()).toBe("立即签到");
    expect(query.textContent?.trim()).toBe("查询住宿");

    registration.click();
    checkin.click();
    query.click();

    expect(register).toHaveBeenCalledTimes(1);
    expect(navigations).toEqual(["SELF_CHECKIN", "ROOM_QUERY"]);
  });

  it("查询令牌缺失、查询目标非法或全局禁用时不会触发动作", async () => {
    const navigate = vi.fn();
    const canvas = invitationCanvas([
      actionElement("checkin", "CHECKIN_BUTTON", 10, {}),
      actionElement("query", "QUERY_BUTTON", 80, { queryTarget: "UNSUPPORTED" })
    ], 160);
    mountRenderer(canvas, { meeting: {}, onNavigate: navigate });
    await nextTick();

    const buttons = Array.from(root!.querySelectorAll<HTMLButtonElement>(".render-action-button"));
    expect(buttons.every((button) => button.disabled)).toBe(true);
    buttons.forEach((button) => button.click());
    expect(navigate).not.toHaveBeenCalled();
  });
});

describe("邀请函 DOM 形状", () => {
  it("使用 SVG 渲染矩形、椭圆和多边形并兼容旧 circle", async () => {
    const shapes = ["rectangle", "rounded-rectangle", "ellipse", "triangle", "diamond", "hexagon", "star", "circle"];
    const canvas: InvitationCanvasV1 = {
      width: 375,
      height: 667,
      backgroundColor: "#ffffff",
      backgroundImageAssetId: null,
      elements: shapes.map((shape, index) => ({
        id: `shape_${index}`,
        type: "SHAPE" as const,
        x: 10,
        y: index * 70,
        width: 100,
        height: 60,
        rotation: 0,
        zIndex: index,
        locked: false,
        props: { shape, fill: "#123456", stroke: "#abcdef", strokeWidth: 2, borderRadius: 12 }
      }))
    };
    root = document.createElement("div");
    document.body.appendChild(root);
    app = createApp(InvitationDomRenderer, { canvas, meeting: {} });
    app.component("NIcon", { setup: () => () => h("span") });
    app.mount(root);
    await nextTick();

    const nodes = Array.from(root.querySelectorAll<SVGElement>("svg.render-shape"));
    expect(nodes).toHaveLength(8);
    expect(nodes.map((node) => node.firstElementChild?.tagName.toLowerCase())).toEqual([
      "rect", "rect", "ellipse", "polygon", "polygon", "polygon", "polygon", "ellipse"
    ]);
    expect(nodes[6]?.querySelector("polygon")?.getAttribute("points")?.trim().split(/\s+/)).toHaveLength(10);
    expect(nodes[0]?.querySelector("rect")?.getAttribute("fill")).toBe("#123456");
    expect(nodes[0]?.querySelector("rect")?.getAttribute("stroke")).toBe("#abcdef");
  });
});

describe("邀请函 DOM 轮播", () => {
  it("关闭自动播放后仍可通过按钮、圆点、键盘和滑动切换", async () => {
    const carousel: InvitationElementV1 = {
      id: "carousel_manual",
      type: "CAROUSEL",
      x: 20,
      y: 20,
      width: 335,
      height: 210,
      rotation: 0,
      zIndex: 1,
      locked: false,
      props: { assetIds: [11, 12, 13], autoplay: false }
    };
    const canvas: InvitationCanvasV1 = {
      width: 375,
      height: 667,
      backgroundColor: "#ffffff",
      backgroundImageAssetId: null,
      elements: [carousel]
    };
    root = document.createElement("div");
    document.body.appendChild(root);
    app = createApp(InvitationDomRenderer, {
      canvas,
      meeting: {},
      assets: [11, 12, 13].map((id) => ({
        id,
        originalName: `${id}.jpg`,
        url: `https://assets.example/${id}.jpg`,
        contentType: "image/jpeg",
        sizeBytes: 100,
        width: 800,
        height: 600,
        createdAt: "2026-07-14T00:00:00Z"
      }))
    });
    app.component("NIcon", { setup: () => () => h("span") });
    app.mount(root);
    await nextTick();

    const carouselNode = root.querySelector<HTMLElement>(".render-carousel")!;
    const image = () => root!.querySelector<HTMLImageElement>(".render-carousel img")!;
    expect(image().src).toBe("https://assets.example/11.jpg");

    root.querySelector<HTMLButtonElement>(".carousel-next")!.click();
    await nextTick();
    expect(image().src).toBe("https://assets.example/12.jpg");

    root.querySelectorAll<HTMLButtonElement>(".carousel-dot")[2].click();
    await nextTick();
    expect(image().src).toBe("https://assets.example/13.jpg");
    expect(root.querySelectorAll<HTMLButtonElement>(".carousel-dot")[2].getAttribute("aria-current")).toBe("true");

    carouselNode.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }));
    await nextTick();
    expect(image().src).toBe("https://assets.example/12.jpg");

    carouselNode.dispatchEvent(pointerEvent("pointerdown", 240, 100));
    carouselNode.dispatchEvent(pointerEvent("pointerup", 120, 105));
    await nextTick();
    expect(image().src).toBe("https://assets.example/13.jpg");
  });
});

function qrElement(
  id: string,
  zIndex: number,
  foregroundColor: string,
  backgroundColor: string,
  props: InvitationElementV1["props"] = {}
): InvitationElementV1 {
  return {
    id,
    type: "CHANNEL_QR_CODE",
    x: 20,
    y: 20 + zIndex * 180,
    width: 160,
    height: 160,
    rotation: 0,
    zIndex,
    locked: false,
    props: { foregroundColor, backgroundColor, ...props }
  };
}

function actionElement(
  id: string,
  type: "REGISTRATION_BUTTON" | "CHECKIN_BUTTON" | "QUERY_BUTTON",
  y: number,
  props: InvitationElementV1["props"]
): InvitationElementV1 {
  return {
    id,
    type,
    x: 20,
    y,
    width: 240,
    height: 52,
    rotation: 0,
    zIndex: y,
    locked: false,
    props
  };
}

function invitationCanvas(elements: InvitationElementV1[], height = 667): InvitationCanvasV1 {
  return {
    width: 375,
    height,
    backgroundColor: "#ffffff",
    backgroundImageAssetId: null,
    elements
  };
}

function mountRenderer(canvas: InvitationCanvasV1, rendererProps: Record<string, unknown>) {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(InvitationDomRenderer, { canvas, ...rendererProps });
  app.component("NIcon", { setup: () => () => h("span") });
  app.mount(root);
}

function pointerEvent(type: string, clientX: number, clientY: number) {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperties(event, {
    pointerId: { value: 1 },
    isPrimary: { value: true },
    clientX: { value: clientX },
    clientY: { value: clientY }
  });
  return event;
}
