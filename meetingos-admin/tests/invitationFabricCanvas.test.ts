// @vitest-environment jsdom

import { createCanvas, loadImage } from "canvas";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, reactive, ref, type App } from "vue";
import type { Canvas, FabricObject } from "fabric";
import InvitationFabricCanvas from "../src/components/invitation/InvitationFabricCanvas.vue";
import type { InvitationCanvasV1, InvitationElementV1 } from "../src/types/invitation";

const fabricState = vi.hoisted(() => ({ canvas: undefined as Canvas | undefined }));

vi.mock("fabric", async (importOriginal) => {
  const actual = await importOriginal<typeof import("fabric")>();
  class CapturingCanvas extends actual.Canvas {
    constructor(...args: ConstructorParameters<typeof actual.Canvas>) {
      super(...args);
      fabricState.canvas = this;
    }
  }
  return { ...actual, Canvas: CapturingCanvas };
});

interface FabricCanvasExpose {
  exportPng: () => string;
  selectAll: () => void;
  discardSelection: () => void;
}

interface MountedCanvas {
  app: App<Element>;
  root: HTMLDivElement;
  canvas: InvitationCanvasV1;
  api: ReturnType<typeof ref<FabricCanvasExpose>>;
  changes: InvitationElementV1[];
  selections: string[];
  nextReady: () => Promise<void>;
}

const mounted: MountedCanvas[] = [];

afterEach(() => {
  mounted.splice(0).forEach(({ app, root }) => {
    app.unmount();
    root.remove();
  });
  fabricState.canvas = undefined;
});

describe("邀请函 Fabric 画布", () => {
  it("使用对应的 Fabric 图元渲染七种形状和旧 circle", async () => {
    const shapes = ["rectangle", "rounded-rectangle", "ellipse", "triangle", "diamond", "hexagon", "star", "circle"];
    const fixture = await mountFabricCanvas(shapes.map((shape, index) => element(
      `shape_${index}`,
      "SHAPE",
      index * 55,
      5,
      50,
      50,
      0,
      { shape, fill: "#ff0000", stroke: "#222222", strokeWidth: 2, borderRadius: 10 }
    )), 440, 60);

    const objects = requireFabricCanvas().getObjects();
    const innerTypes = objects.map((object) => {
      const children = "getObjects" in object ? (object as FabricObject & { getObjects: () => FabricObject[] }).getObjects() : [];
      return children[1]?.type;
    });
    expect(innerTypes).toEqual(["rect", "rect", "ellipse", "polygon", "polygon", "polygon", "polygon", "ellipse"]);
    objects.forEach((object) => {
      expect(object.width).toBeCloseTo(50);
      expect(object.height).toBeCloseTo(50);
    });

    const image = await loadImage(fixture.api.value!.exportPng());
    expect(image.width).toBe(440);
    expect(image.height).toBe(60);
  });

  it("与公开 DOM 一样围绕未旋转盒子中心旋转", async () => {
    const fixture = await mountFabricCanvas([
      element("shape", "SHAPE", 60, 80, 80, 40, 90, { fill: "#ff0000", stroke: "transparent", strokeWidth: 0 })
    ], 200, 200);

    const object = requireFabricCanvas().getObjects()[0]!;
    expect(object.originX).toBe("center");
    expect(object.originY).toBe("center");
    expect(object.getCenterPoint()).toMatchObject({ x: 100, y: 100 });

    const image = await loadImage(fixture.api.value!.exportPng());
    const pixels = createCanvas(200, 200);
    const context = pixels.getContext("2d");
    context.drawImage(image, 0, 0);
    const redAt = (x: number, y: number) => {
      const [red, green, blue, alpha] = context.getImageData(x, y, 1, 1).data;
      return red! > 200 && green! < 60 && blue! < 60 && alpha! > 200;
    };

    expect(redAt(100, 65)).toBe(true);
    expect(redAt(100, 100)).toBe(true);
    expect(redAt(65, 100)).toBe(false);
  });

  it("回写 Textbox 内在宽度且不混入 canvas zoom", async () => {
    const fixture = await mountFabricCanvas([
      element("text", "TEXT", 20, 30, 100, 50, 0, { text: "中文输入", fontSize: 20, color: "#17261f" })
    ], 300, 200);
    const canvas = requireFabricCanvas();
    const text = canvas.getObjects()[0] as FabricObject;

    canvas.setZoom(2);
    text.set("width", 150);
    text.set("height", 50);
    text.set("left", Number(text.left) + 25);
    canvas.fire("object:modified", { target: text } as never);

    expect(fixture.changes).toHaveLength(1);
    expect(fixture.changes[0]).toMatchObject({ id: "text", x: 20, y: 30, width: 150, height: 50 });
    expect(fixture.changes[0]?.props.fontSize).toBe(20);
  });

  it("内联编辑改变自然高度时保持领域盒子位置", async () => {
    const fixture = await mountFabricCanvas([
      element("text", "TEXT", 20, 30, 100, 50, 0, { text: "编辑前", fontSize: 20, color: "#17261f" })
    ], 300, 200);
    const canvas = requireFabricCanvas();
    const text = canvas.getObjects()[0] as FabricObject & { text?: string };

    // 模拟 Fabric updateFromTextArea：保留左上角 30，但按 25px 自然高度重算中心。
    text.set({ top: 42.5, height: 25 });
    text.text = "中文内联编辑后";
    canvas.fire("text:changed", { target: text } as never);

    expect(text.getCenterPoint()).toMatchObject({ x: 70, y: 55 });
    expect(text.height).toBe(50);
    expect(fixture.changes[0]).toMatchObject({
      x: 20,
      y: 30,
      width: 100,
      height: 50,
      props: { text: "中文内联编辑后" }
    });

    canvas.fire("object:modified", { target: text } as never);
    expect(fixture.changes[1]).toMatchObject({ x: 20, y: 30, width: 100, height: 50 });
  });

  it("文本框缩放只更新外框尺寸且紧接移动不会改变字号", async () => {
    const fixture = await mountFabricCanvas([
      element("text", "TEXT", 20, 30, 100, 50, 0, { text: "保持尺寸", fontSize: 20, color: "#17261f" })
    ], 400, 300, { applyChanges: true });
    const canvas = requireFabricCanvas();
    const text = canvas.getObjects()[0]!;

    text.set({ scaleX: 2, scaleY: 2 });
    canvas.fire("object:modified", { target: text } as never);
    text.set({ left: Number(text.left) + 10, top: Number(text.top) + 10 });
    canvas.fire("object:modified", { target: text } as never);

    expect(fixture.changes.map((item) => [item.width, item.height, item.props.fontSize])).toEqual([
      [200, 100, 20],
      [200, 100, 20]
    ]);
    await nextTick();
  });

  it("用哨兵表达多选，重建期间不误发空选择", async () => {
    const fixture = await mountFabricCanvas([
      element("first", "SHAPE", 20, 20, 40, 40, 0, { fill: "#ff0000", strokeWidth: 0 }),
      element("second", "SHAPE", 100, 100, 40, 40, 0, { fill: "#00ff00", strokeWidth: 0 })
    ], 200, 200);

    fixture.api.value!.selectAll();
    await nextTick();
    expect(fixture.selections).toEqual(["__multiple__"]);

    const ready = fixture.nextReady();
    fixture.canvas.backgroundColor = "#f8faf9";
    await ready;
    expect(fixture.selections).toEqual(["__multiple__"]);
  });

  it("ActiveSelection 回写包含组缩放但不包含 zoom 或 DPR", async () => {
    const fixture = await mountFabricCanvas([
      element("first", "SHAPE", 20, 20, 40, 30, 0, { fill: "#ff0000", strokeWidth: 0 }),
      element("second", "SHAPE", 100, 100, 50, 20, 0, { fill: "#00ff00", strokeWidth: 0 })
    ], 300, 200);
    const canvas = requireFabricCanvas();

    fixture.api.value!.selectAll();
    const selection = canvas.getActiveObject();
    if (!selection) throw new Error("未创建 ActiveSelection");
    canvas.setZoom(2);
    selection.set({ scaleX: 1.5, scaleY: 1.5 });
    canvas.fire("object:modified", { target: selection } as never);

    expect(fixture.changes).toHaveLength(2);
    expect(fixture.changes.map((item) => [item.id, item.width, item.height])).toEqual([
      ["first", 60, 45],
      ["second", 75, 30]
    ]);
  });

  it("二维码稳定边界使用元素背景色", () => {
    const source = readFileSync(resolve("src/components/invitation/InvitationFabricCanvas.vue"), "utf8");

    expect(source).toContain('groupBounds(element, String(element.props.backgroundColor || "#ffffff"))');
  });
});

async function mountFabricCanvas(
  elements: InvitationElementV1[],
  width: number,
  height: number,
  options: { applyChanges?: boolean; shareUrl?: string } = {}
) {
  const canvas = reactive<InvitationCanvasV1>({
    width,
    height,
    backgroundColor: "#ffffff",
    backgroundImageAssetId: null,
    elements
  });
  const api = ref<FabricCanvasExpose>();
  const changes: InvitationElementV1[] = [];
  const selections: string[] = [];
  const selectedId = ref("");
  let readyResolver: (() => void) | undefined;
  const nextReady = () => new Promise<void>((resolve) => { readyResolver = resolve; });
  const firstReady = nextReady();
  const root = document.createElement("div");
  document.body.appendChild(root);
  const app = createApp({
    setup: () => () => h(InvitationFabricCanvas, {
      ref: api,
      canvas,
      meeting: {},
      selectedId: selectedId.value,
      shareUrl: options.shareUrl || "",
      onChange: (value: InvitationElementV1) => {
        changes.push(value);
        if (options.applyChanges) {
          const index = canvas.elements.findIndex((item) => item.id === value.id);
          if (index >= 0) canvas.elements[index] = value;
        }
      },
      onSelect: (value: string) => {
        selections.push(value);
        selectedId.value = value;
      },
      onReady: () => {
        readyResolver?.();
        readyResolver = undefined;
      }
    })
  });
  app.config.warnHandler = () => undefined;
  app.mount(root);
  const fixture = { app, root, canvas, api, changes, selections, nextReady };
  mounted.push(fixture);
  await firstReady;
  return fixture;
}

function requireFabricCanvas() {
  if (!fabricState.canvas) throw new Error("Fabric Canvas 尚未创建");
  return fabricState.canvas;
}

function element(
  id: string,
  type: InvitationElementV1["type"],
  x: number,
  y: number,
  width: number,
  height: number,
  rotation: number,
  props: InvitationElementV1["props"]
): InvitationElementV1 {
  return { id, type, x, y, width, height, rotation, zIndex: 1, locked: false, props };
}
