<template>
  <div class="fabric-viewport" :style="viewportStyle">
    <div ref="stage" class="fabric-stage" :style="stageStyle">
      <canvas ref="canvasElement" />
    </div>
    <div v-if="loading" class="fabric-loading">正在加载设计器…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from "vue";
import type { Canvas, FabricObject, Textbox } from "fabric";
import type { InvitationAsset, InvitationCanvasV1, InvitationElementV1, InvitationMeetingSnapshot } from "@/types/invitation";
import { DEFAULT_MEETING_IMAGE, resolveAssetUrl, resolveMeetingField } from "@/utils/invitationDocument";
import { invitationShapePoints, invitationShapeRadius, isInvitationPolygonShape, normalizeInvitationShape } from "@/utils/invitationShape";
import {
  invitationQrLabel,
  invitationQueryButtonDefaultText,
  isInvitationQueryTarget,
  resolveInvitationQrTarget
} from "@/utils/invitationActions";

type FabricModule = typeof import("fabric");
type IdentifiedFabricObject = FabricObject & {
  invitationElementId?: string;
  invitationBaseScaleX?: number;
  invitationBaseScaleY?: number;
  invitationBaseWidth?: number;
  invitationBaseElementWidth?: number;
  invitationBaseElementHeight?: number;
};
type InvitationTextbox = Textbox & IdentifiedFabricObject;
type ExternalRefreshVersion = {
  assets: number;
  meeting: number;
  qr: number;
};

const MULTI_SELECTION_ID = "__multiple__";

const props = withDefaults(defineProps<{
  canvas: InvitationCanvasV1;
  meeting: Partial<InvitationMeetingSnapshot>;
  assets?: InvitationAsset[];
  zoom?: number;
  selectedId?: string;
  shareUrl?: string;
  shareToken?: string;
  publicQueryToken?: string | null;
}>(), {
  assets: () => [],
  zoom: 1,
  selectedId: "",
  shareUrl: "",
  shareToken: "",
  publicQueryToken: ""
});

const emit = defineEmits<{
  select: [id: string];
  change: [element: InvitationElementV1];
  ready: [];
}>();

const canvasElement = ref<HTMLCanvasElement>();
const stage = ref<HTMLElement>();
const loading = ref(true);
let fabricModule: FabricModule | undefined;
let fabricCanvas: Canvas | undefined;
let suppressSelectionEvents = false;
let canvasIsInitializing = false;
let disposed = false;
let syncRevision = 0;
let syncPending = false;
let syncRunning = false;
let assetRefreshRevision = 0;
let meetingRefreshRevision = 0;
let qrRefreshRevision = 0;
let processedAssetRefreshRevision = 0;
let processedMeetingRefreshRevision = 0;
let processedQrRefreshRevision = 0;
let renderedCanvasSnapshot: InvitationCanvasV1 | undefined;
const localFabricChanges = new Map<string, InvitationElementV1>();

const viewportStyle = computed<CSSProperties>(() => ({
  width: `${props.canvas.width * props.zoom}px`,
  height: `${props.canvas.height * props.zoom}px`
}));
const stageStyle = computed<CSSProperties>(() => ({
  width: `${props.canvas.width}px`,
  height: `${props.canvas.height}px`,
  transform: `scale(${props.zoom})`
}));
const assetUrls = computed(() => new Map(props.assets.map((asset) => [asset.id, asset.url])));

watch(
  () => props.canvas,
  () => {
    const next = snapshotCanvas(props.canvas);
    // Fabric 内部拖拽或文字编辑已经把受限后的结果直接写回对象；仅在没有混入其他变更时跳过重复应用。
    if (canSkipLocalFabricSync(next)) renderedCanvasSnapshot = next;
    scheduleCanvasSync();
  },
  { deep: true }
);

watch(() => props.assets, () => {
  assetRefreshRevision += 1;
  scheduleCanvasSync();
}, { deep: true });
watch(() => props.meeting, () => {
  meetingRefreshRevision += 1;
  scheduleCanvasSync();
}, { deep: true });
watch(() => props.shareUrl, () => {
  qrRefreshRevision += 1;
  scheduleCanvasSync();
});
watch(() => props.shareToken, () => {
  qrRefreshRevision += 1;
  scheduleCanvasSync();
});
watch(() => props.publicQueryToken, () => {
  qrRefreshRevision += 1;
  scheduleCanvasSync();
});

watch(() => props.selectedId, (id) => {
  if (!fabricCanvas) return;
  const object = fabricCanvas.getObjects().find((item) => (item as IdentifiedFabricObject).invitationElementId === id);
  withSelectionEventsSuppressed(() => {
    if (object) {
      fabricCanvas?.setActiveObject(object);
    } else if (!id) {
      fabricCanvas?.discardActiveObject();
    }
  });
  if (object || !id) {
    fabricCanvas.requestRenderAll();
  }
});

onMounted(async () => {
  fabricModule = await import("fabric");
  await nextTick();
  if (!canvasElement.value) return;
  fabricCanvas = new fabricModule.Canvas(canvasElement.value, {
    width: props.canvas.width,
    height: props.canvas.height,
    backgroundColor: props.canvas.backgroundColor,
    preserveObjectStacking: true,
    selectionColor: "rgba(8, 120, 79, 0.10)",
    selectionBorderColor: "#08784f"
  });
  bindCanvasEvents();
  canvasIsInitializing = true;
  const initialized = await rebuildCanvas(syncRevision);
  canvasIsInitializing = false;
  if (!initialized) syncPending = true;
  if (syncPending) await flushCanvasSyncQueue();
  loading.value = false;
});

onBeforeUnmount(() => {
  disposed = true;
  syncRevision += 1;
  fabricCanvas?.dispose();
  fabricCanvas = undefined;
});

defineExpose({
  exportPng,
  selectAll,
  discardSelection
});

/**
 * 所有来源（属性面板、素材、会议字段、二维码）的变化都进入同一队列。
 * Fabric 图像和二维码创建是异步的，分开并发执行会让一个刷新取消另一个刷新，留下半张画布。
 */
function scheduleCanvasSync() {
  if (disposed) return;
  syncRevision += 1;
  syncPending = true;
  if (!canvasIsInitializing) void flushCanvasSyncQueue();
}

async function flushCanvasSyncQueue() {
  if (syncRunning || canvasIsInitializing || !fabricCanvas || !fabricModule || disposed) return;
  syncRunning = true;
  try {
    while (syncPending && fabricCanvas && fabricModule && !disposed) {
      syncPending = false;
      const revision = syncRevision;
      const externalRefresh = captureExternalRefreshVersion();
      const synchronized = await syncCanvas(revision, externalRefresh);
      if (!isSyncCurrent(revision)) {
        // 当前异步任务使用的是旧快照，下一轮只处理最新状态。
        syncPending = true;
        continue;
      }
      if (synchronized) markExternalRefreshProcessed(externalRefresh);
    }
  } finally {
    syncRunning = false;
    if (syncPending && !canvasIsInitializing && !disposed) void flushCanvasSyncQueue();
  }
}

function captureExternalRefreshVersion(): ExternalRefreshVersion {
  return {
    assets: assetRefreshRevision,
    meeting: meetingRefreshRevision,
    qr: qrRefreshRevision
  };
}

function hasAssetRefresh(refresh: ExternalRefreshVersion) {
  return refresh.assets > processedAssetRefreshRevision;
}

function hasMeetingRefresh(refresh: ExternalRefreshVersion) {
  return refresh.meeting > processedMeetingRefreshRevision;
}

function hasQrRefresh(refresh: ExternalRefreshVersion) {
  return refresh.qr > processedQrRefreshRevision;
}

function markExternalRefreshProcessed(refresh: ExternalRefreshVersion) {
  processedAssetRefreshRevision = Math.max(processedAssetRefreshRevision, refresh.assets);
  processedMeetingRefreshRevision = Math.max(processedMeetingRefreshRevision, refresh.meeting);
  processedQrRefreshRevision = Math.max(processedQrRefreshRevision, refresh.qr);
}

function isSyncCurrent(revision: number) {
  return !disposed && revision === syncRevision;
}

function withSelectionEventsSuppressed(callback: () => void) {
  suppressSelectionEvents = true;
  try {
    callback();
  } finally {
    suppressSelectionEvents = false;
  }
}

function canSkipLocalFabricSync(next: InvitationCanvasV1) {
  if (!renderedCanvasSnapshot || !localFabricChanges.size) return false;
  const previous = renderedCanvasSnapshot;
  const sameCanvas = previous.width === next.width
    && previous.height === next.height
    && previous.backgroundColor === next.backgroundColor
    && previous.backgroundImageAssetId === next.backgroundImageAssetId
    && previous.elements.length === next.elements.length;
  if (!sameCanvas) {
    localFabricChanges.clear();
    return false;
  }
  const nextById = new Map(next.elements.map((element) => [element.id, element]));
  const changedIds: string[] = [];
  for (const element of previous.elements) {
    const changed = nextById.get(element.id);
    if (!changed) {
      localFabricChanges.clear();
      return false;
    }
    if (!sameElement(element, changed)) changedIds.push(element.id);
  }
  if (!changedIds.length) {
    localFabricChanges.clear();
    return false;
  }
  const isOnlyFabricChange = changedIds.every((id) => {
    const expected = localFabricChanges.get(id);
    const actual = nextById.get(id);
    return Boolean(expected && actual && sameElement(expected, actual));
  });
  if (!isOnlyFabricChange) {
    localFabricChanges.clear();
    return false;
  }
  changedIds.forEach((id) => localFabricChanges.delete(id));
  return true;
}

async function rebuildCanvas(revision: number): Promise<boolean> {
  if (!fabricCanvas || !fabricModule || !isSyncCurrent(revision)) return false;
  const document = snapshotCanvas(props.canvas);
  // 仅用于首屏初始化。后续属性面板修改走 syncCanvas，不能先 clear()，否则会造成整张预览闪烁。
  withSelectionEventsSuppressed(() => fabricCanvas?.clear());
  fabricCanvas.setDimensions({ width: document.width, height: document.height });
  fabricCanvas.backgroundColor = document.backgroundColor;
  fabricCanvas.backgroundImage = undefined;
  const backgroundImage = await createBackgroundImage(document);
  const objects = await Promise.all([...document.elements].sort((a, b) => a.zIndex - b.zIndex).map(createFabricObject));
  if (!isSyncCurrent(revision) || !fabricCanvas) return false;
  fabricCanvas.backgroundImage = backgroundImage;
  objects.filter((item): item is FabricObject => Boolean(item)).forEach((object) => fabricCanvas?.add(object));
  const selected = objects.find((item) => item && (item as IdentifiedFabricObject).invitationElementId === props.selectedId);
  if (selected) withSelectionEventsSuppressed(() => fabricCanvas?.setActiveObject(selected));
  renderedCanvasSnapshot = document;
  fabricCanvas.requestRenderAll();
  emit("ready");
  return true;
}

/**
 * 属性面板会连续写入领域文档。这里按元素增量同步，避免任意一个数字或颜色变化都清空并异步重建整张 Fabric 画布。
 */
async function syncCanvas(revision: number, externalRefresh: ExternalRefreshVersion): Promise<boolean> {
  if (!fabricCanvas || !fabricModule || !isSyncCurrent(revision)) return false;
  if (!renderedCanvasSnapshot) {
    return rebuildCanvas(revision);
  }
  const previous = renderedCanvasSnapshot;
  const next = snapshotCanvas(props.canvas);
  const dimensionsChanged = previous.width !== next.width || previous.height !== next.height;
  if (dimensionsChanged) fabricCanvas.setDimensions({ width: next.width, height: next.height });
  if (previous.backgroundColor !== next.backgroundColor) fabricCanvas.backgroundColor = next.backgroundColor;
  if (dimensionsChanged || previous.backgroundImageAssetId !== next.backgroundImageAssetId) {
    if (!await updateBackgroundImage(revision, next)) return false;
  }
  if (!await reconcileFabricObjects(previous.elements, next.elements, revision)) return false;
  const refreshAssets = hasAssetRefresh(externalRefresh);
  const refreshMeeting = hasMeetingRefresh(externalRefresh);
  const refreshQr = hasQrRefresh(externalRefresh);
  if (refreshAssets || refreshMeeting || refreshQr) {
    const refreshSucceeded = await refreshExternalObjects(
      (element) => (refreshAssets && isAssetDependentElement(element))
        || (refreshMeeting && isMeetingDependentElement(element))
        || (refreshQr && element.type === "CHANNEL_QR_CODE"),
      refreshAssets,
      revision,
      next
    );
    if (!refreshSucceeded) return false;
  }
  if (!isSyncCurrent(revision) || !fabricCanvas) return false;
  reconcileObjectStack(next.elements);
  renderedCanvasSnapshot = next;
  restoreSelectedObject();
  fabricCanvas.requestRenderAll();
  emit("ready");
  return true;
}

async function refreshExternalObjects(
  predicate: (element: InvitationElementV1) => boolean,
  refreshBackground: boolean,
  revision: number,
  document: InvitationCanvasV1
): Promise<boolean> {
  if (!fabricCanvas || !fabricModule || !isSyncCurrent(revision)) return false;
  if (refreshBackground && !await updateBackgroundImage(revision, document)) return false;
  const activeIds = activeInvitationElementIds();
  for (const element of document.elements) {
    if (!predicate(element)) continue;
    if (!isSyncCurrent(revision) || !fabricCanvas) return false;
    const current = findFabricObject(element.id);
    if (current) {
      if (!await replaceFabricObject(current, element, revision, activeIds)) return false;
    } else if (!await addFabricObject(element, revision)) {
      return false;
    }
  }
  if (!isSyncCurrent(revision) || !fabricCanvas) return false;
  restoreActiveObjects(activeIds);
  return true;
}

async function reconcileFabricObjects(previousElements: InvitationElementV1[], nextElements: InvitationElementV1[], revision: number): Promise<boolean> {
  if (!fabricCanvas || !isSyncCurrent(revision)) return false;
  const previousById = new Map(previousElements.map((element) => [element.id, element]));
  const nextIds = new Set(nextElements.map((element) => element.id));
  const activeIds = activeInvitationElementIds();

  for (const element of previousElements) {
    if (!isSyncCurrent(revision) || !fabricCanvas) return false;
    if (!nextIds.has(element.id)) {
      const object = findFabricObject(element.id);
      if (object) fabricCanvas.remove(object);
    }
  }
  for (const element of nextElements) {
    if (!isSyncCurrent(revision) || !fabricCanvas) return false;
    const previous = previousById.get(element.id);
    const current = findFabricObject(element.id);
    if (!current) {
      if (!await addFabricObject(element, revision)) return false;
      continue;
    }
    if (!previous || !sameElement(previous, element)) {
      if (!updateFabricObject(current, previous, element)) {
        if (!await replaceFabricObject(current, element, revision, activeIds)) return false;
      }
    }
  }
  restoreActiveObjects(activeIds);
  return isSyncCurrent(revision);
}

function updateFabricObject(object: IdentifiedFabricObject, previous: InvitationElementV1 | undefined, element: InvitationElementV1) {
  if (!previous || previous.type !== element.type) return false;
  const rendersAsText = isTextElement(element);
  if (rendersAsText && object.type !== "textbox") return false;
  if (!rendersAsText && hasPresentationPropertyChange(previous, element)) return false;
  if (!rendersAsText && (previous.width !== element.width || previous.height !== element.height)) return false;

  applyObjectState(object, element, rendersAsText);
  if (rendersAsText) updateTextObject(object as InvitationTextbox, element);
  recordObjectBaseGeometry(object, element);
  object.setCoords();
  return true;
}

function applyObjectState(object: IdentifiedFabricObject, element: InvitationElementV1, resetTextScale: boolean) {
  object.set({
    left: element.x + element.width / 2,
    top: element.y + element.height / 2,
    angle: element.rotation,
    opacity: Number(element.props.opacity ?? 1),
    selectable: !element.locked,
    evented: !element.locked,
    lockMovementX: element.locked,
    lockMovementY: element.locked,
    lockRotation: element.locked,
    lockScalingX: element.locked,
    lockScalingY: element.locked
  });
  if (resetTextScale) {
    object.set({
      width: element.width,
      scaleX: object.invitationBaseScaleX || 1,
      scaleY: object.invitationBaseScaleY || 1
    });
    // Textbox 设置 width 会触发布局计算；height 必须在该计算后单独写入，避免被自然文本高度覆盖。
    object.set("height", element.height);
  }
}

function updateTextObject(object: InvitationTextbox, element: InvitationElementV1) {
  const propsValue = element.props;
  const fontSize = Number(propsValue.fontSize || 16);
  const content = element.type === "TEXT"
    ? String(propsValue.text || "文字")
    : `${String(propsValue.labelPrefix || "")}${resolveMeetingField(String(propsValue.field || ""), props.meeting)}`;
  object.set({
    text: content,
    fontSize,
    fontFamily: String(propsValue.fontFamily || "Inter, system-ui, sans-serif"),
    fontWeight: String(propsValue.fontWeight || 400),
    fontStyle: String(propsValue.fontStyle || "normal") as "normal" | "italic" | "oblique",
    fill: String(propsValue.color || "#17261f"),
    textAlign: String(propsValue.textAlign || "left") as "left" | "center" | "right" | "justify" | "justify-left" | "justify-center" | "justify-right",
    lineHeight: Number(propsValue.lineHeight || 1.4),
    charSpacing: Number(propsValue.letterSpacing || 0) / Math.max(1, fontSize) * 1000
  });
  object.initDimensions();
  // Textbox 会按内容重算自然高度；领域文档必须始终保留用户设定的固定盒子。
  object.set({ width: element.width });
  object.set("height", element.height);
  object.clipPath?.set({ width: element.width, height: element.height });
}

function recordObjectBaseGeometry(object: IdentifiedFabricObject, element: InvitationElementV1) {
  object.invitationBaseScaleX = Math.abs(Number(object.scaleX || 1));
  object.invitationBaseScaleY = Math.abs(Number(object.scaleY || 1));
  object.invitationBaseWidth = Math.max(0.000001, Number(object.width || element.width));
  object.invitationBaseElementWidth = element.width;
  object.invitationBaseElementHeight = element.height;
}

function hasPresentationPropertyChange(previous: InvitationElementV1, element: InvitationElementV1) {
  return JSON.stringify(previous.props) !== JSON.stringify(element.props);
}

function isTextElement(element: InvitationElementV1) {
  return element.type === "TEXT" || (element.type === "MEETING_FIELD" && element.props.field !== "COVER_IMAGE");
}

function sameElement(previous: InvitationElementV1, element: InvitationElementV1) {
  return JSON.stringify(previous) === JSON.stringify(element);
}

function snapshotCanvas(canvas: InvitationCanvasV1) {
  return JSON.parse(JSON.stringify(canvas)) as InvitationCanvasV1;
}

function findFabricObject(elementId: string) {
  return fabricCanvas?.getObjects().find((item) => (item as IdentifiedFabricObject).invitationElementId === elementId) as IdentifiedFabricObject | undefined;
}

async function addFabricObject(element: InvitationElementV1, revision: number): Promise<boolean> {
  const object = await createFabricObject(element);
  if (!isSyncCurrent(revision) || !fabricCanvas) return false;
  if (object) fabricCanvas.add(object);
  return true;
}

async function replaceFabricObject(current: IdentifiedFabricObject, element: InvitationElementV1, revision: number, activeIds = activeInvitationElementIds()): Promise<boolean> {
  const next = await createFabricObject(element);
  if (!isSyncCurrent(revision) || !fabricCanvas) return false;
  if (!next || !fabricCanvas.getObjects().includes(current)) return true;
  const index = fabricCanvas.getObjects().indexOf(current);
  // 异步图片/二维码准备期间保持旧对象可见，新的对象准备好后再原位替换，避免出现空白闪烁。
  withSelectionEventsSuppressed(() => {
    fabricCanvas?.add(next);
    if (index >= 0) fabricCanvas?.moveObjectTo(next, index);
    fabricCanvas?.remove(current);
  });
  restoreActiveObjects(activeIds);
  return true;
}

function reconcileObjectStack(elements: InvitationElementV1[]) {
  if (!fabricCanvas) return;
  const canvas = fabricCanvas;
  [...elements].sort((a, b) => a.zIndex - b.zIndex).forEach((element, index) => {
    const object = findFabricObject(element.id);
    if (object) canvas.moveObjectTo(object, index);
  });
}

function activeInvitationElementIds() {
  return fabricCanvas?.getActiveObjects()
    .map((object) => (object as IdentifiedFabricObject).invitationElementId)
    .filter((id): id is string => Boolean(id)) || [];
}

function restoreActiveObjects(ids: string[]) {
  if (!fabricCanvas || !fabricModule || !ids.length) return;
  const objects = ids.map(findFabricObject).filter((item): item is IdentifiedFabricObject => Boolean(item));
  withSelectionEventsSuppressed(() => {
    if (objects.length === 1) fabricCanvas?.setActiveObject(objects[0]);
    else if (objects.length > 1 && fabricCanvas) fabricCanvas.setActiveObject(new fabricModule!.ActiveSelection(objects, { canvas: fabricCanvas }));
  });
}

function restoreSelectedObject() {
  if (!fabricCanvas || !props.selectedId || props.selectedId === MULTI_SELECTION_ID) return;
  const object = findFabricObject(props.selectedId);
  if (object) withSelectionEventsSuppressed(() => fabricCanvas?.setActiveObject(object));
}

function isAssetDependentElement(element: InvitationElementV1) {
  return element.type === "IMAGE"
    || (element.type === "MEETING_FIELD" && element.props.field === "COVER_IMAGE")
    || element.type === "VIDEO"
    || element.type === "CAROUSEL";
}

function isMeetingDependentElement(element: InvitationElementV1) {
  return element.type === "MEETING_FIELD" || element.type === "COUNTDOWN";
}

async function createBackgroundImage(document: InvitationCanvasV1) {
  if (!fabricModule || !document.backgroundImageAssetId) return undefined;
  const backgroundUrl = assetUrls.value.get(document.backgroundImageAssetId);
  if (!backgroundUrl) return undefined;
  try {
    const background = await fabricModule.FabricImage.fromURL(backgroundUrl, { crossOrigin: "anonymous" });
    const coverScale = Math.max(document.width / Math.max(1, background.width), document.height / Math.max(1, background.height));
    background.set({
      left: (document.width - background.width * coverScale) / 2,
      top: (document.height - background.height * coverScale) / 2,
      originX: "left",
      originY: "top",
      scaleX: coverScale,
      scaleY: coverScale,
      selectable: false,
      evented: false
    });
    return background;
  } catch {
    // 素材跨域或加载失败时仍保留背景色，避免设计器整体不可用。
    return undefined;
  }
}

async function updateBackgroundImage(revision: number, document: InvitationCanvasV1): Promise<boolean> {
  const background = await createBackgroundImage(document);
  if (!isSyncCurrent(revision) || !fabricCanvas) return false;
  // 图片尚未就绪时保留当前背景，只有显式移除或新图片成功加载后才替换。
  if (!document.backgroundImageAssetId || background) fabricCanvas.backgroundImage = background;
  return true;
}

function bindCanvasEvents() {
  if (!fabricCanvas) return;
  const emitSelection = () => {
    if (suppressSelectionEvents) return;
    const object = fabricCanvas?.getActiveObject() as IdentifiedFabricObject | undefined;
    if (object?.type === "activeselection") {
      emit("select", MULTI_SELECTION_ID);
      return;
    }
    emit("select", object?.invitationElementId || "");
  };
  fabricCanvas.on("selection:created", emitSelection);
  fabricCanvas.on("selection:updated", emitSelection);
  fabricCanvas.on("selection:cleared", () => {
    if (suppressSelectionEvents) return;
    emit("select", "");
  });
  fabricCanvas.on("object:moving", ({ target }) => snapObject(target as IdentifiedFabricObject));
  fabricCanvas.on("object:resizing", ({ target }) => keepTextBounds(target as IdentifiedFabricObject));
  fabricCanvas.on("object:modified", ({ target }) => syncModifiedObjects(target as IdentifiedFabricObject));
  fabricCanvas.on("text:changed", ({ target }) => syncTextContent(target as IdentifiedFabricObject));
}

async function createFabricObject(element: InvitationElementV1): Promise<FabricObject | null> {
  if (!fabricModule) return null;
  const { Ellipse, FabricImage, Group, Line, Polygon, Rect, Textbox } = fabricModule;
  const propsValue = element.props;
  let object: FabricObject;
  if (element.type === "IMAGE" || (element.type === "MEETING_FIELD" && propsValue.field === "COVER_IMAGE")) {
    const source = element.type === "IMAGE"
      ? resolveAssetUrl(propsValue.assetId, assetUrls.value)
      : props.meeting.coverImageUrl || DEFAULT_MEETING_IMAGE;
    try {
      const image = await FabricImage.fromURL(source, { crossOrigin: "anonymous" });
      const sourceWidth = Math.max(1, image.width);
      const sourceHeight = Math.max(1, image.height);
      const objectFit = String(propsValue.objectFit || "cover");
      if (objectFit === "contain") {
        const scale = Math.min(element.width / sourceWidth, element.height / sourceHeight);
        const bounds = new Rect({ left: 0, top: 0, originX: "left", originY: "top", width: element.width, height: element.height, fill: "rgba(0,0,0,0)", strokeWidth: 0 });
        image.set({
          left: (element.width - sourceWidth * scale) / 2,
          top: (element.height - sourceHeight * scale) / 2,
          originX: "left",
          originY: "top",
          strokeWidth: 0,
          scaleX: scale,
          scaleY: scale
        });
        object = new Group([bounds, image], { left: element.x, top: element.y });
        applyRoundedClip(object, element, Number(propsValue.borderRadius || 0));
      } else {
        const scale = Math.max(element.width / sourceWidth, element.height / sourceHeight);
        const visibleWidth = element.width / scale;
        const visibleHeight = element.height / scale;
        image.set({
          left: element.x,
          top: element.y,
          width: visibleWidth,
          height: visibleHeight,
          cropX: Math.max(0, (sourceWidth - visibleWidth) / 2),
          cropY: Math.max(0, (sourceHeight - visibleHeight) / 2),
          strokeWidth: 0,
          scaleX: scale,
          scaleY: scale
        });
        applyRoundedClip(image, { ...element, width: visibleWidth, height: visibleHeight }, Number(propsValue.borderRadius || 0) / scale);
        object = image;
      }
    } catch {
      object = placeholderRect(element, "图片加载失败");
    }
  } else if (element.type === "TEXT" || element.type === "MEETING_FIELD") {
    const content = element.type === "TEXT"
      ? String(propsValue.text || "文字")
      : `${String(propsValue.labelPrefix || "")}${resolveMeetingField(String(propsValue.field || ""), props.meeting)}`;
    const fontSize = Number(propsValue.fontSize || 16);
    const text = new Textbox(content, {
      left: element.x,
      top: element.y,
      width: element.width,
      height: element.height,
      fontSize,
      fontFamily: String(propsValue.fontFamily || "Inter, system-ui, sans-serif"),
      fontWeight: String(propsValue.fontWeight || 400),
      fontStyle: String(propsValue.fontStyle || "normal") as "normal" | "italic" | "oblique",
      fill: String(propsValue.color || "#17261f"),
      textAlign: String(propsValue.textAlign || "left") as "left" | "center" | "right" | "justify" | "justify-left" | "justify-center" | "justify-right",
      lineHeight: Number(propsValue.lineHeight || 1.4),
      charSpacing: Number(propsValue.letterSpacing || 0) / Math.max(1, fontSize) * 1000,
      splitByGrapheme: true,
      strokeWidth: 0,
      editable: element.type === "TEXT"
    });
    text.set("height", element.height);
    text.clipPath = new Rect({ width: element.width, height: element.height, originX: "center", originY: "center", strokeWidth: 0 });
    object = text;
  } else if (element.type === "SHAPE") {
    const strokeWidth = Math.max(0, Number(propsValue.strokeWidth || 0));
    const shapeWidth = Math.max(0, element.width - strokeWidth);
    const shapeHeight = Math.max(0, element.height - strokeWidth);
    const shapeKind = normalizeInvitationShape(propsValue.shape);
    const commonShapeOptions = {
      // Fabric 的 left/top 已以含描边的外包围盒定位；从 0 开始才能与公开 SVG 的半线宽内缩模型一致。
      left: 0,
      top: 0,
      originX: "left" as const,
      originY: "top" as const,
      fill: String(propsValue.fill || "#e7f3ee"),
      stroke: String(propsValue.stroke || "transparent"),
      strokeWidth,
      strokeLineJoin: "round" as const
    };
    const shape = shapeKind === "ellipse"
      ? new Ellipse({ ...commonShapeOptions, rx: shapeWidth / 2, ry: shapeHeight / 2 })
      : isInvitationPolygonShape(shapeKind)
        ? new Polygon(invitationShapePoints(shapeKind, shapeWidth, shapeHeight), commonShapeOptions)
        : new Rect({
            ...commonShapeOptions,
            width: shapeWidth,
            height: shapeHeight,
            rx: Math.max(0, invitationShapeRadius(shapeKind, shapeWidth, shapeHeight, propsValue.borderRadius) - strokeWidth / 2),
            ry: Math.max(0, invitationShapeRadius(shapeKind, shapeWidth, shapeHeight, propsValue.borderRadius) - strokeWidth / 2)
          });
    object = new Group([groupBounds(element), shape], { left: element.x, top: element.y });
  } else if (element.type === "DIVIDER") {
    const thickness = Math.min(element.height, Math.max(1, Number(propsValue.thickness || 1)));
    const line = new Line([thickness / 2, element.height / 2, Math.max(thickness / 2, element.width - thickness / 2), element.height / 2], {
      stroke: String(propsValue.color || "#dce9e3"),
      strokeWidth: thickness,
      strokeDashArray: propsValue.style === "dashed" ? [8, 6] : undefined
    });
    object = new Group([groupBounds(element), line], { left: element.x, top: element.y });
  } else if (isActionButtonType(element.type)) {
    const background = new Rect({ left: 0, top: 0, originX: "left", originY: "top", width: element.width, height: element.height, rx: Number(propsValue.borderRadius || 6), ry: Number(propsValue.borderRadius || 6), fill: String(propsValue.fill || "#08784f"), strokeWidth: 0 });
    const labelTop = Math.max(0, (element.height - Number(propsValue.fontSize || 20) * 1.35) / 2);
    const label = new Textbox(actionButtonText(element), { left: 0, originX: "left", originY: "top", width: element.width, top: labelTop, textAlign: "center", fontSize: Number(propsValue.fontSize || 20), fontWeight: String(propsValue.fontWeight || 700), fill: String(propsValue.color || "#ffffff"), splitByGrapheme: true, strokeWidth: 0 });
    label.set("height", Math.max(1, element.height - labelTop));
    object = new Group([groupBounds(element), background, label], { left: element.x, top: element.y });
  } else if (element.type === "CHANNEL_QR_CODE") {
    object = await createQrObject(element);
  } else if (element.type === "COUNTDOWN") {
    const background = new Rect({ left: 0.5, top: 0.5, originX: "left", originY: "top", width: Math.max(0, element.width - 1), height: Math.max(0, element.height - 1), fill: "#f6faf8", stroke: "#e1ece7", strokeWidth: 1 });
    const label = new Textbox(countdownPreviewText(String(propsValue.target || props.meeting.startTime || "")), { left: 0, originX: "left", originY: "top", width: element.width, top: 11, textAlign: "center", lineHeight: 1.45, fontSize: Number(propsValue.fontSize || 18), fontWeight: String(propsValue.fontWeight || 700), fill: String(propsValue.color || "#08784f"), splitByGrapheme: true, strokeWidth: 0 });
    label.set("height", Math.max(1, element.height - 11));
    object = new Group([groupBounds(element), background, label], { left: element.x, top: element.y });
  } else {
    object = placeholderRect(element, interactiveElementLabel(element.type));
  }
  const identified = object as IdentifiedFabricObject;
  identified.invitationElementId = element.id;
  object.set({
    // 领域 x/y 是未旋转盒子的左上角，公开 DOM 和服务端边界校验都围绕盒子中心旋转。
    left: element.x + element.width / 2,
    top: element.y + element.height / 2,
    originX: "center",
    originY: "center",
    angle: element.rotation,
    opacity: Number(propsValue.opacity ?? 1),
    selectable: !element.locked,
    evented: !element.locked,
    lockMovementX: element.locked,
    lockMovementY: element.locked,
    lockRotation: element.locked,
    lockScalingX: element.locked,
    lockScalingY: element.locked,
    borderColor: "#08784f",
    cornerColor: "#ffffff",
    cornerStrokeColor: "#08784f",
    cornerStyle: "circle",
    transparentCorners: false,
    cornerSize: 9,
    padding: 1
  });
  identified.invitationBaseScaleX = Math.abs(Number(object.scaleX || 1));
  identified.invitationBaseScaleY = Math.abs(Number(object.scaleY || 1));
  identified.invitationBaseWidth = Math.max(0.000001, Number(object.width || element.width));
  identified.invitationBaseElementWidth = element.width;
  identified.invitationBaseElementHeight = element.height;
  return object;
}

function placeholderRect(element: InvitationElementV1, label: string) {
  if (!fabricModule) throw new Error("Fabric 尚未加载");
  const { Group, Rect, Textbox } = fabricModule;
  const background = new Rect({ left: 0.5, top: 0.5, originX: "left", originY: "top", width: Math.max(0, element.width - 1), height: Math.max(0, element.height - 1), fill: "#edf5f1", stroke: "#b9d3c7", strokeWidth: 1, rx: 4, ry: 4 });
  const textTop = Math.min(element.height - 1, Math.max(4, element.height / 2 - 10));
  const text = new Textbox(label, { width: Math.max(1, element.width - 20), left: Math.min(10, element.width / 2), top: textTop, originX: "left", originY: "top", textAlign: "center", fontSize: Math.min(15, Math.max(10, element.height / 5)), fill: "#477062", splitByGrapheme: true, strokeWidth: 0 });
  text.set("height", Math.max(1, element.height - textTop));
  return new Group([groupBounds(element), background, text], { left: element.x, top: element.y });
}

async function createQrObject(element: InvitationElementV1) {
  if (!fabricModule) throw new Error("Fabric 尚未加载");
  const target = resolveInvitationQrTarget(element, {
    meetingCode: props.meeting.code,
    publicQueryToken: props.publicQueryToken,
    shareToken: props.shareToken,
    shareUrl: props.shareUrl
  });
  if (!target.url) return placeholderRect(element, target.unavailableReason || "二维码不可用");
  try {
    const [{ default: QRCode }, { FabricImage, Group, Textbox }] = await Promise.all([import("qrcode"), import("fabric")]);
    const url = await QRCode.toDataURL(target.url, { width: 720, margin: 2, errorCorrectionLevel: "H", color: { dark: String(element.props.foregroundColor || "#0b3425"), light: String(element.props.backgroundColor || "#ffffff") } });
    const image = await FabricImage.fromURL(url);
    const labelText = invitationQrLabel(element);
    const labelHeight = labelText ? Math.min(36, Math.max(22, element.height * 0.12)) : 0;
    const qrSize = Math.max(1, Math.min(element.width, element.height - labelHeight));
    const scale = qrSize / Math.max(1, image.width);
    image.set({ left: (element.width - qrSize) / 2, top: 0, originX: "left", originY: "top", strokeWidth: 0, scaleX: scale, scaleY: scale });
    const children: FabricObject[] = [groupBounds(element, String(element.props.backgroundColor || "#ffffff")), image];
    if (labelText) {
      const labelTop = qrSize + 4;
      const label = new Textbox(labelText, { left: 0, top: labelTop, originX: "left", originY: "top", width: element.width, textAlign: "center", fontSize: Math.max(12, labelHeight * 0.55), fontWeight: 600, fill: String(element.props.foregroundColor || "#0b3425"), splitByGrapheme: true, strokeWidth: 0 });
      label.set("height", Math.max(1, element.height - labelTop));
      children.push(label);
    }
    return new Group(children, { left: element.x, top: element.y });
  } catch {
    return placeholderRect(element, "二维码生成失败");
  }
}

function groupBounds(element: Pick<InvitationElementV1, "width" | "height">, fill = "rgba(0,0,0,0)") {
  if (!fabricModule) throw new Error("Fabric 尚未加载");
  return new fabricModule.Rect({ left: 0, top: 0, originX: "left", originY: "top", width: element.width, height: element.height, fill, strokeWidth: 0, selectable: false, evented: false });
}

function applyRoundedClip(object: FabricObject, element: Pick<InvitationElementV1, "width" | "height">, radius: number) {
  if (!fabricModule || radius <= 0) return;
  object.clipPath = new fabricModule.Rect({ width: element.width, height: element.height, rx: radius, ry: radius, originX: "center", originY: "center", strokeWidth: 0 });
}

function countdownPreviewText(target: string) {
  const targetTime = Date.parse(target);
  if (!Number.isFinite(targetTime)) return "距离大会开始\n时间待定";
  let remaining = Math.max(0, targetTime - Date.now());
  const days = Math.floor(remaining / 86_400_000);
  remaining %= 86_400_000;
  const hours = Math.floor(remaining / 3_600_000);
  remaining %= 3_600_000;
  const minutes = Math.floor(remaining / 60_000);
  return `距离大会开始\n${days} 天  ${hours} 时  ${minutes} 分`;
}

function interactiveElementLabel(type: InvitationElementV1["type"]) {
  return ({ BACKGROUND_MUSIC: "背景音乐 · 点击播放", VIDEO: "视频 · 封面预览", CAROUSEL: "轮播图 · 互动预览", AMAP: "高德地图 · 地址导航" } as Partial<Record<InvitationElementV1["type"], string>>)[type] || type;
}

function isActionButtonType(type: InvitationElementV1["type"]) {
  return type === "REGISTRATION_BUTTON" || type === "CHECKIN_BUTTON" || type === "QUERY_BUTTON";
}

function actionButtonText(element: InvitationElementV1) {
  if (element.props.text) return String(element.props.text);
  if (element.type === "CHECKIN_BUTTON") return "立即签到";
  if (element.type === "QUERY_BUTTON") return invitationQueryButtonDefaultText(
    isInvitationQueryTarget(element.props.queryTarget) ? element.props.queryTarget : "COMBINED_QUERY"
  );
  return "立即报名";
}

function snapObject(target: IdentifiedFabricObject) {
  const threshold = 5;
  const bounds = target.getBoundingRect();
  const deltaX = nearestSnapDelta([bounds.left, bounds.left + bounds.width / 2, bounds.left + bounds.width], [0, props.canvas.width / 2, props.canvas.width], threshold);
  const deltaY = nearestSnapDelta([bounds.top, bounds.top + bounds.height / 2, bounds.top + bounds.height], [0, props.canvas.height / 2, props.canvas.height], threshold);
  if (deltaX !== 0) target.set("left", Number(target.left || 0) + deltaX);
  if (deltaY !== 0) target.set("top", Number(target.top || 0) + deltaY);
}

function nearestSnapDelta(points: number[], guides: number[], threshold: number) {
  let nearest = 0;
  let distance = threshold + 1;
  for (const point of points) {
    for (const guide of guides) {
      const candidate = guide - point;
      if (Math.abs(candidate) < distance) {
        nearest = candidate;
        distance = Math.abs(candidate);
      }
    }
  }
  return distance <= threshold ? nearest : 0;
}

function syncObject(target: IdentifiedFabricObject) {
  if (!target.invitationElementId) return;
  const source = props.canvas.elements.find((item) => item.id === target.invitationElementId);
  if (!source) return;
  // getObjectScaling 包含 ActiveSelection 的变换，但不包含 canvas zoom 和 Retina/DPR。
  const objectScaling = target.getObjectScaling();
  const rendersAsText = isTextElement(source);
  const intrinsicWidthScale = rendersAsText
    ? Math.abs(Number(target.width || 1)) / Math.max(0.000001, target.invitationBaseWidth || 1)
    : 1;
  const visualScaleX = objectScaling.x * intrinsicWidthScale;
  const visualScaleY = objectScaling.y;
  const userScaleX = visualScaleX / Math.max(0.000001, target.invitationBaseScaleX || 1);
  const userScaleY = visualScaleY / Math.max(0.000001, target.invitationBaseScaleY || 1);
  const baseElementWidth = target.invitationBaseElementWidth || source.width;
  const baseElementHeight = target.invitationBaseElementHeight || source.height;
  const width = Math.min(props.canvas.width, Math.max(1, baseElementWidth * userScaleX));
  const height = Math.min(props.canvas.height, Math.max(1, baseElementHeight * userScaleY));
  const center = target.getCenterPoint();
  const rotation = Math.round(Number(target.getTotalAngle()) * 100) / 100;
  const radians = rotation * Math.PI / 180;
  const extentX = Math.abs(Math.cos(radians)) * width / 2 + Math.abs(Math.sin(radians)) * height / 2;
  const extentY = Math.abs(Math.sin(radians)) * width / 2 + Math.abs(Math.cos(radians)) * height / 2;
  const centerX = clampCenter(center.x, width, extentX, props.canvas.width);
  const centerY = clampCenter(center.y, height, extentY, props.canvas.height);
  const x = centerX - width / 2;
  const y = centerY - height / 2;
  const next: InvitationElementV1 = {
    ...source,
    x: Math.round(x * 100) / 100,
    y: Math.round(y * 100) / 100,
    width: Math.round(width * 100) / 100,
    height: Math.round(height * 100) / 100,
    rotation,
    props: { ...source.props }
  };
  // 领域边界校验会夹紧位置和尺寸，必须立即把受限结果回写 Fabric；否则下一次属性更新才会突然跳回。
  applyCommittedObjectGeometry(target, source, next);
  commitFabricChange(next);
}

function applyCommittedObjectGeometry(target: IdentifiedFabricObject, source: InvitationElementV1, next: InvitationElementV1) {
  target.set({
    left: next.x + next.width / 2,
    top: next.y + next.height / 2,
    angle: next.rotation
  });
  if (isTextElement(source)) {
    // 文本框缩放只改变排版边界；字号由属性面板显式控制，不能随外框拖拽产生小数漂移。
    target.set({
      width: next.width,
      scaleX: target.invitationBaseScaleX || 1,
      scaleY: target.invitationBaseScaleY || 1
    });
    target.set("height", next.height);
    target.clipPath?.set({ width: next.width, height: next.height });
  } else {
    const baseWidth = Math.max(0.000001, target.invitationBaseElementWidth || source.width);
    const baseHeight = Math.max(0.000001, target.invitationBaseElementHeight || source.height);
    target.set({
      scaleX: (target.invitationBaseScaleX || 1) * next.width / baseWidth,
      scaleY: (target.invitationBaseScaleY || 1) * next.height / baseHeight
    });
  }
  recordObjectBaseGeometry(target, next);
  target.setCoords();
}

function commitFabricChange(next: InvitationElementV1) {
  localFabricChanges.set(next.id, JSON.parse(JSON.stringify(next)) as InvitationElementV1);
  emit("change", next);
}

function clampCenter(value: number, size: number, rotatedExtent: number, canvasSize: number) {
  const minimum = Math.max(size / 2, rotatedExtent);
  const maximum = Math.min(canvasSize - size / 2, canvasSize - rotatedExtent);
  if (minimum > maximum) return canvasSize / 2;
  return Math.min(maximum, Math.max(minimum, value));
}

function keepTextBounds(target: IdentifiedFabricObject) {
  if (!target.invitationElementId || target.type !== "textbox") return;
  const source = props.canvas.elements.find((item) => item.id === target.invitationElementId);
  if (!source || (source.type !== "TEXT" && source.type !== "MEETING_FIELD")) return;
  target.set("height", source.height);
  target.clipPath?.set({ width: Math.max(1, Number(target.width || source.width)), height: source.height });
}

function syncTextContent(target: IdentifiedFabricObject) {
  if (!target.invitationElementId || !("text" in target)) return;
  const source = props.canvas.elements.find((item) => item.id === target.invitationElementId);
  if (!source || source.type !== "TEXT") return;
  /*
   * Fabric 输入时会按 Textbox 的自然高度锚定原左上角，再把 origin=center 的 top
   * 改成自然高度中心。领域文档使用固定盒子，必须在每次输入后恢复盒子中心，
   * 否则仅编辑文字也会把 x/y 回写成新的自然高度位置。
   */
  target.set({
    left: source.x + source.width / 2,
    top: source.y + source.height / 2,
    width: source.width,
    height: source.height
  });
  keepTextBounds(target);
  recordObjectBaseGeometry(target, source);
  target.setCoords();
  commitFabricChange({ ...source, props: { ...source.props, text: String(target.text || "") } });
}

function syncModifiedObjects(target: IdentifiedFabricObject) {
  if (target.type === "activeselection" && fabricCanvas && fabricModule) {
    /*
     * ActiveSelection 内的子对象使用组内坐标，直接把领域坐标写给 left/top 会再叠加一次组变换。
     * 先解除选择，让 Fabric 将本次组合变换落到各对象的画布坐标，再做边界夹紧并恢复多选状态。
     */
    const objects = fabricCanvas.getActiveObjects() as IdentifiedFabricObject[];
    withSelectionEventsSuppressed(() => fabricCanvas?.discardActiveObject());
    objects.forEach(syncObject);
    withSelectionEventsSuppressed(() => {
      if (objects.length) fabricCanvas?.setActiveObject(new fabricModule!.ActiveSelection(objects, { canvas: fabricCanvas }));
    });
    fabricCanvas.requestRenderAll();
    return;
  }
  syncObject(target);
}

function exportPng() {
  if (!fabricCanvas) throw new Error("画布尚未加载");
  fabricCanvas.discardActiveObject();
  fabricCanvas.requestRenderAll();
  return fabricCanvas.toDataURL({ format: "png", multiplier: 1 });
}

function selectAll() {
  if (!fabricCanvas || !fabricModule) return;
  const objects = fabricCanvas.getObjects().filter((item) => item.selectable);
  if (!objects.length) return;
  fabricCanvas.setActiveObject(new fabricModule.ActiveSelection(objects, { canvas: fabricCanvas }));
  fabricCanvas.requestRenderAll();
}

function discardSelection() {
  fabricCanvas?.discardActiveObject();
  fabricCanvas?.requestRenderAll();
}
</script>

<style scoped>
.fabric-viewport {
  position: relative;
  flex: 0 0 auto;
}

.fabric-stage {
  position: absolute;
  inset: 0 auto auto 0;
  transform-origin: left top;
  box-shadow: 0 8px 30px rgba(15, 45, 32, 0.14);
}

.fabric-loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #6f8179;
  background: rgba(255, 255, 255, 0.82);
  font-size: 13px;
}
</style>
