<template>
  <div ref="viewport" class="invitation-render-viewport" :style="viewportStyle">
    <div class="invitation-render-canvas" :style="canvasStyle">
      <template v-for="element in orderedElements" :key="element.id">
        <div
          v-if="element.type === 'TEXT'"
          class="render-element render-text"
          :style="elementStyle(element)"
        >
          {{ element.props.text }}
        </div>

        <img
          v-else-if="element.type === 'IMAGE'"
          class="render-element render-image"
          :style="elementStyle(element)"
          :src="assetUrl(element)"
          :alt="String(element.props.alt || '')"
        />

        <svg
          v-else-if="element.type === 'SHAPE'"
          class="render-element render-shape"
          :style="elementStyle(element)"
          :viewBox="`0 0 ${element.width} ${element.height}`"
          preserveAspectRatio="none"
          aria-hidden="true"
          focusable="false"
        >
          <ellipse
            v-if="shapeKind(element) === 'ellipse'"
            :cx="element.width / 2"
            :cy="element.height / 2"
            :rx="Math.max(0, (element.width - shapeStrokeWidth(element)) / 2)"
            :ry="Math.max(0, (element.height - shapeStrokeWidth(element)) / 2)"
            :fill="shapeFill(element)"
            :stroke="shapeStroke(element)"
            :stroke-width="shapeStrokeWidth(element)"
          />
          <polygon
            v-else-if="isInvitationPolygonShape(shapeKind(element))"
            :points="shapePolygonPoints(element)"
            :fill="shapeFill(element)"
            :stroke="shapeStroke(element)"
            :stroke-width="shapeStrokeWidth(element)"
            stroke-linejoin="round"
          />
          <rect
            v-else
            :x="shapeStrokeWidth(element) / 2"
            :y="shapeStrokeWidth(element) / 2"
            :width="Math.max(0, element.width - shapeStrokeWidth(element))"
            :height="Math.max(0, element.height - shapeStrokeWidth(element))"
            :rx="shapeRectRadius(element)"
            :ry="shapeRectRadius(element)"
            :fill="shapeFill(element)"
            :stroke="shapeStroke(element)"
            :stroke-width="shapeStrokeWidth(element)"
          />
        </svg>

        <div
          v-else-if="element.type === 'DIVIDER'"
          class="render-element render-divider"
          :style="elementStyle(element)"
        />

        <img
          v-else-if="element.type === 'MEETING_FIELD' && element.props.field === 'COVER_IMAGE'"
          class="render-element render-image"
          :style="elementStyle(element)"
          :src="meeting.coverImageUrl || DEFAULT_MEETING_IMAGE"
          alt="会议主视觉"
        />

        <div
          v-else-if="element.type === 'MEETING_FIELD'"
          class="render-element render-meeting-field"
          :style="elementStyle(element)"
        >
          {{ element.props.labelPrefix }}{{ resolveMeetingField(String(element.props.field || ""), meeting) }}
        </div>

        <button
          v-else-if="isActionButton(element)"
          type="button"
          class="render-element render-action-button"
          :class="`render-${element.type.toLowerCase().replace(/_/g, '-')}`"
          :style="elementStyle(element)"
          :disabled="isActionDisabled(element)"
          @click="triggerAction(element)"
        >
          {{ actionButtonText(element) }}
        </button>

        <div
          v-else-if="element.type === 'CHANNEL_QR_CODE'"
          class="render-element render-qr"
          :style="elementStyle(element)"
        >
          <img v-if="qrDataUrls[element.id]" :src="qrDataUrls[element.id]" :alt="`${invitationQrLabel(element) || '会议'}二维码`" />
          <small v-else-if="qrUnavailableReasons[element.id]" class="qr-unavailable">{{ qrUnavailableReasons[element.id] }}</small>
          <span v-if="invitationQrLabel(element)">{{ invitationQrLabel(element) }}</span>
        </div>

        <div
          v-else-if="element.type === 'COUNTDOWN'"
          class="render-element render-countdown"
          :style="elementStyle(element)"
        >
          <small>距离大会开始</small>
          <strong>{{ countdownText(element) }}</strong>
        </div>

        <div
          v-else-if="element.type === 'BACKGROUND_MUSIC'"
          class="render-element render-audio"
          :style="elementStyle(element)"
        >
          <audio :ref="(node) => registerAudio(element.id, node)" :src="String(element.props.url || '')" loop preload="none" />
          <button type="button" :aria-label="playingAudioIds.has(element.id) ? '暂停背景音乐' : '播放背景音乐'" @click="toggleAudio(element.id)">
            <n-icon :component="playingAudioIds.has(element.id) ? PauseOutline : MusicalNotesOutline" />
          </button>
          <span>{{ element.props.title || "背景音乐" }}</span>
        </div>

        <video
          v-else-if="element.type === 'VIDEO'"
          class="render-element render-video"
          :style="elementStyle(element)"
          :src="String(element.props.url || '')"
          :poster="assetUrl(element, 'coverAssetId')"
          :controls="element.props.controls !== false"
          playsinline
          preload="metadata"
        />

        <div
          v-else-if="element.type === 'CAROUSEL'"
          class="render-element render-carousel"
          :style="elementStyle(element)"
          role="region"
          aria-roledescription="轮播图"
          aria-label="会议精彩瞬间"
          tabindex="0"
          @keydown="handleCarouselKeydown(element, $event)"
          @pointerdown="beginCarouselSwipe(element, $event)"
          @pointerup="endCarouselSwipe(element, $event)"
          @pointercancel="cancelCarouselSwipe(element.id)"
        >
          <img
            v-if="carouselUrls(element).length"
            :src="carouselUrls(element)[currentCarouselIndex(element)]"
            :alt="carouselImageAlt(element)"
            draggable="false"
          />
          <template v-if="carouselUrls(element).length > 1">
            <button
              type="button"
              class="carousel-arrow carousel-previous"
              aria-label="上一张"
              @click.stop="moveCarousel(element, -1)"
            >
              <n-icon :component="ChevronBackOutline" />
            </button>
            <button
              type="button"
              class="carousel-arrow carousel-next"
              aria-label="下一张"
              @click.stop="moveCarousel(element, 1)"
            >
              <n-icon :component="ChevronForwardOutline" />
            </button>
          </template>
          <div v-if="carouselUrls(element).length > 1" class="carousel-dots" role="group" aria-label="选择轮播图片">
            <button
              v-for="(_, index) in carouselUrls(element)"
              :key="index"
              type="button"
              class="carousel-dot"
              :class="{ active: index === currentCarouselIndex(element) }"
              :aria-label="`显示第 ${index + 1} 张，共 ${carouselUrls(element).length} 张`"
              :aria-current="index === currentCarouselIndex(element) ? 'true' : undefined"
              @click.stop="selectCarouselSlide(element, index)"
            />
          </div>
        </div>

        <InvitationMap
          v-else-if="element.type === 'AMAP'"
          class="render-element"
          :style="elementStyle(element)"
          :longitude="Number(element.props.longitude || 0)"
          :latitude="Number(element.props.latitude || 0)"
          :address="String(element.props.address || '')"
          :zoom="Number(element.props.zoom || 15)"
          :navigation-url="String(element.props.navigationUrl || '')"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type CSSProperties } from "vue";
import { ChevronBackOutline, ChevronForwardOutline, MusicalNotesOutline, PauseOutline } from "@vicons/ionicons5";
import InvitationMap from "@/components/invitation/InvitationMap.vue";
import type { InvitationAsset, InvitationCanvasV1, InvitationElementV1, InvitationMeetingSnapshot, InvitationNavigationTarget } from "@/types/invitation";
import { DEFAULT_MEETING_IMAGE, resolveAssetUrl, resolveMeetingField } from "@/utils/invitationDocument";
import { invitationShapePoints, invitationShapeRadius, isInvitationPolygonShape, normalizeInvitationShape } from "@/utils/invitationShape";
import {
  invitationQrLabel,
  invitationQueryButtonDefaultText,
  isInvitationQueryTarget,
  resolveInvitationQrTarget
} from "@/utils/invitationActions";

const props = withDefaults(defineProps<{
  canvas: InvitationCanvasV1;
  meeting: Partial<InvitationMeetingSnapshot>;
  assets?: InvitationAsset[];
  shareUrl?: string;
  shareToken?: string;
  publicQueryToken?: string | null;
  fixedScale?: number;
  registrationDisabled?: boolean;
  actionsDisabled?: boolean;
}>(), {
  assets: () => [],
  shareUrl: "",
  shareToken: "",
  publicQueryToken: "",
  fixedScale: undefined,
  registrationDisabled: false,
  actionsDisabled: false
});

const emit = defineEmits<{ register: []; navigate: [target: InvitationNavigationTarget] }>();

const viewport = ref<HTMLElement>();
const responsiveScale = ref(1);
const now = ref(Date.now());
const qrDataUrls = reactive<Record<string, string>>({});
const qrUnavailableReasons = reactive<Record<string, string>>({});
const carouselIndexes = reactive<Record<string, number>>({});
const playingAudioIds = reactive(new Set<string>());
const audioElements = new Map<string, HTMLAudioElement>();
const carouselTimers = new Map<string, number>();
const carouselPointerStarts = new Map<string, { pointerId: number; clientX: number; clientY: number }>();
let qrRenderGeneration = 0;
let resizeObserver: ResizeObserver | undefined;
let countdownTimer: number | undefined;

const scale = computed(() => props.fixedScale ?? responsiveScale.value);
const orderedElements = computed(() => [...props.canvas.elements].sort((left, right) => left.zIndex - right.zIndex));
const assetUrls = computed(() => new Map(props.assets.map((asset) => [asset.id, asset.url])));
const viewportStyle = computed<CSSProperties>(() => ({
  width: props.fixedScale ? `${props.canvas.width * scale.value}px` : "100%",
  height: `${props.canvas.height * scale.value}px`
}));
const canvasStyle = computed<CSSProperties>(() => ({
  width: `${props.canvas.width}px`,
  height: `${props.canvas.height}px`,
  backgroundColor: props.canvas.backgroundColor,
  backgroundImage: canvasBackgroundUrl.value ? `url(${JSON.stringify(canvasBackgroundUrl.value)})` : undefined,
  backgroundSize: "cover",
  backgroundPosition: "center",
  transform: `scale(${scale.value})`
}));
const canvasBackgroundUrl = computed(() => props.canvas.backgroundImageAssetId
  ? assetUrls.value.get(props.canvas.backgroundImageAssetId) || ""
  : "");

watch([
  () => props.shareUrl,
  () => props.shareToken,
  () => props.publicQueryToken,
  () => props.meeting.code,
  () => props.canvas.elements
], () => void renderQrs(), { deep: true, immediate: true });
watch([() => props.canvas.elements, () => props.assets], setupCarousels, { deep: true });

onMounted(() => {
  const updateScale = () => {
    if (props.fixedScale || !viewport.value) return;
    // 公开 H5 逻辑宽度固定为 375px，390/430px 设备也需要按容器宽度等比放大。
    responsiveScale.value = viewport.value.clientWidth / props.canvas.width;
  };
  resizeObserver = new ResizeObserver(updateScale);
  if (viewport.value) resizeObserver.observe(viewport.value);
  updateScale();
  countdownTimer = window.setInterval(() => { now.value = Date.now(); }, 1000);
  setupCarousels();
});

onBeforeUnmount(() => {
  qrRenderGeneration += 1;
  resizeObserver?.disconnect();
  if (countdownTimer) window.clearInterval(countdownTimer);
  carouselTimers.forEach((timer) => window.clearInterval(timer));
  carouselPointerStarts.clear();
  audioElements.forEach((audio) => audio.pause());
});

function elementStyle(element: InvitationElementV1): CSSProperties {
  const props = element.props;
  const style: CSSProperties = {
    left: `${element.x}px`,
    top: `${element.y}px`,
    width: `${element.width}px`,
    height: `${element.height}px`,
    zIndex: element.zIndex,
    opacity: Number(props.opacity ?? 1),
    transform: `rotate(${element.rotation}deg)`,
    color: String(props.color || "#17261f"),
    fontSize: `${Number(props.fontSize || 16)}px`,
    fontFamily: String(props.fontFamily || "Inter, system-ui, sans-serif"),
    fontWeight: String(props.fontWeight || 400),
    fontStyle: String(props.fontStyle || "normal"),
    lineHeight: Number(props.lineHeight || 1.4),
    letterSpacing: `${Number(props.letterSpacing || 0)}px`,
    textAlign: (props.textAlign || "left") as CSSProperties["textAlign"]
  };
  if (element.type === "IMAGE" || (element.type === "MEETING_FIELD" && props.field === "COVER_IMAGE")) {
    style.objectFit = (props.objectFit || "cover") as CSSProperties["objectFit"];
    style.borderRadius = `${Number(props.borderRadius || 0)}px`;
  }
  if (element.type === "SHAPE") {
    style.overflow = "visible";
  }
  if (element.type === "DIVIDER") {
    style.height = `${Number(props.thickness || 1)}px`;
    style.borderTop = `${Number(props.thickness || 1)}px ${String(props.style || "solid")} ${String(props.color || "#dce9e3")}`;
  }
  if (isActionButton(element)) {
    style.background = String(props.fill || "#08784f");
    style.color = String(props.color || "#ffffff");
    style.borderRadius = `${Number(props.borderRadius || 6)}px`;
  }
  if (element.type === "CHANNEL_QR_CODE") {
    style.background = String(props.backgroundColor || "#ffffff");
    style.color = String(props.foregroundColor || "#0b3425");
  }
  return style;
}

function shapeKind(element: InvitationElementV1) {
  return normalizeInvitationShape(element.props.shape);
}

function shapeStrokeWidth(element: InvitationElementV1) {
  return Math.max(0, Number(element.props.strokeWidth || 0));
}

function shapeFill(element: InvitationElementV1) {
  return String(element.props.fill || "#e7f3ee");
}

function shapeStroke(element: InvitationElementV1) {
  return String(element.props.stroke || "transparent");
}

function shapeRectRadius(element: InvitationElementV1) {
  const strokeWidth = shapeStrokeWidth(element);
  return Math.max(0, invitationShapeRadius(
    shapeKind(element),
    Math.max(0, element.width - strokeWidth),
    Math.max(0, element.height - strokeWidth),
    element.props.borderRadius
  ) - strokeWidth / 2);
}

function shapePolygonPoints(element: InvitationElementV1) {
  const inset = shapeStrokeWidth(element) / 2;
  return invitationShapePoints(shapeKind(element), element.width, element.height, inset)
    .map((point) => `${point.x},${point.y}`)
    .join(" ");
}

function assetUrl(element: InvitationElementV1, key = "assetId") {
  return resolveAssetUrl(element.props[key], assetUrls.value);
}

function carouselUrls(element: InvitationElementV1) {
  const ids = Array.isArray(element.props.assetIds) ? element.props.assetIds : [];
  return ids.map((id) => assetUrls.value.get(Number(id)) || "").filter(Boolean);
}

function currentCarouselIndex(element: InvitationElementV1) {
  const count = carouselUrls(element).length;
  if (!count) return 0;
  const current = carouselIndexes[element.id] ?? 0;
  return current >= 0 && current < count ? current : 0;
}

function carouselImageAlt(element: InvitationElementV1) {
  const count = carouselUrls(element).length;
  return `会议精彩瞬间，第 ${currentCarouselIndex(element) + 1} 张，共 ${count} 张`;
}

function countdownText(element: InvitationElementV1) {
  const target = new Date(String(element.props.target || props.meeting.startTime || "")).getTime();
  if (!Number.isFinite(target)) return "时间待定";
  const remaining = Math.max(0, target - now.value);
  const days = Math.floor(remaining / 86400000);
  const hours = Math.floor(remaining / 3600000) % 24;
  const minutes = Math.floor(remaining / 60000) % 60;
  const seconds = Math.floor(remaining / 1000) % 60;
  return `${days} 天  ${String(hours).padStart(2, "0")} 时  ${String(minutes).padStart(2, "0")} 分  ${String(seconds).padStart(2, "0")} 秒`;
}

async function renderQrs() {
  const generation = ++qrRenderGeneration;
  const qrElements = props.canvas.elements.filter((element) => element.type === "CHANNEL_QR_CODE");
  if (!qrElements.length) {
    Object.keys(qrDataUrls).forEach((id) => delete qrDataUrls[id]);
    Object.keys(qrUnavailableReasons).forEach((id) => delete qrUnavailableReasons[id]);
    return;
  }
  try {
    const { default: QRCode } = await import("qrcode");
    const entries = await Promise.all(qrElements.map(async (element) => {
      const target = resolveInvitationQrTarget(element, {
        meetingCode: props.meeting.code,
        publicQueryToken: props.publicQueryToken,
        shareToken: props.shareToken,
        shareUrl: props.shareUrl
      });
      if (!target.url) return [element.id, "", target.unavailableReason] as const;
      return [element.id, await QRCode.toDataURL(target.url, {
        width: 640,
        margin: 2,
        errorCorrectionLevel: "H",
        color: {
          dark: String(element.props.foregroundColor || "#0b3425"),
          light: String(element.props.backgroundColor || "#ffffff")
        }
      }), ""] as const;
    }));
    if (generation !== qrRenderGeneration) return;
    Object.keys(qrDataUrls).forEach((id) => delete qrDataUrls[id]);
    Object.keys(qrUnavailableReasons).forEach((id) => delete qrUnavailableReasons[id]);
    entries.forEach(([id, dataUrl, reason]) => {
      if (dataUrl) qrDataUrls[id] = dataUrl;
      if (reason) qrUnavailableReasons[id] = reason;
    });
  } catch {
    if (generation !== qrRenderGeneration) return;
    Object.keys(qrDataUrls).forEach((id) => delete qrDataUrls[id]);
  }
}

function isActionButton(element: InvitationElementV1) {
  return element.type === "REGISTRATION_BUTTON"
    || element.type === "CHECKIN_BUTTON"
    || element.type === "QUERY_BUTTON";
}

function isActionDisabled(element: InvitationElementV1) {
  if (props.actionsDisabled) return true;
  if (element.type === "REGISTRATION_BUTTON") return props.registrationDisabled;
  if (!props.publicQueryToken) return true;
  return element.type === "QUERY_BUTTON" && !isInvitationQueryTarget(element.props.queryTarget);
}

function actionButtonText(element: InvitationElementV1) {
  if (element.props.text) return String(element.props.text);
  if (element.type === "CHECKIN_BUTTON") return "立即签到";
  if (element.type === "QUERY_BUTTON") return invitationQueryButtonDefaultText(
    isInvitationQueryTarget(element.props.queryTarget) ? element.props.queryTarget : "COMBINED_QUERY"
  );
  return "立即报名";
}

function triggerAction(element: InvitationElementV1) {
  if (isActionDisabled(element)) return;
  if (element.type === "REGISTRATION_BUTTON") {
    emit("register");
    return;
  }
  if (element.type === "CHECKIN_BUTTON") {
    emit("navigate", "SELF_CHECKIN");
    return;
  }
  if (element.type === "QUERY_BUTTON" && isInvitationQueryTarget(element.props.queryTarget)) {
    emit("navigate", element.props.queryTarget);
  }
}

function setupCarousels() {
  carouselTimers.forEach((timer) => window.clearInterval(timer));
  carouselTimers.clear();
  void nextTick(() => {
    const activeIds = new Set<string>();
    props.canvas.elements.filter((item) => item.type === "CAROUSEL").forEach((element) => {
      activeIds.add(element.id);
      const count = carouselUrls(element).length;
      carouselIndexes[element.id] = count ? Math.min(currentCarouselIndex(element), count - 1) : 0;
      startCarouselTimer(element);
    });
    Object.keys(carouselIndexes).forEach((id) => {
      if (!activeIds.has(id)) delete carouselIndexes[id];
    });
  });
}

function startCarouselTimer(element: InvitationElementV1) {
  const previousTimer = carouselTimers.get(element.id);
  if (previousTimer !== undefined) window.clearInterval(previousTimer);
  carouselTimers.delete(element.id);
  const count = carouselUrls(element).length;
  if (count <= 1 || element.props.autoplay === false) return;
  const configuredInterval = Number(element.props.intervalMs || 4000);
  const interval = Number.isFinite(configuredInterval) ? Math.max(2000, configuredInterval) : 4000;
  carouselTimers.set(element.id, window.setInterval(() => {
    carouselIndexes[element.id] = (currentCarouselIndex(element) + 1) % count;
  }, interval));
}

function selectCarouselSlide(element: InvitationElementV1, index: number) {
  const count = carouselUrls(element).length;
  if (!count) return;
  carouselIndexes[element.id] = ((index % count) + count) % count;
  startCarouselTimer(element);
}

function moveCarousel(element: InvitationElementV1, offset: number) {
  selectCarouselSlide(element, currentCarouselIndex(element) + offset);
}

function handleCarouselKeydown(element: InvitationElementV1, event: KeyboardEvent) {
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
  event.preventDefault();
  moveCarousel(element, event.key === "ArrowLeft" ? -1 : 1);
}

function beginCarouselSwipe(element: InvitationElementV1, event: PointerEvent) {
  if (event.isPrimary === false) return;
  if ((event.target as HTMLElement | null)?.closest("button")) return;
  carouselPointerStarts.set(element.id, {
    pointerId: event.pointerId,
    clientX: event.clientX,
    clientY: event.clientY
  });
  (event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
}

function endCarouselSwipe(element: InvitationElementV1, event: PointerEvent) {
  const start = carouselPointerStarts.get(element.id);
  carouselPointerStarts.delete(element.id);
  if (!start || start.pointerId !== event.pointerId) return;
  const horizontalDistance = event.clientX - start.clientX;
  const verticalDistance = event.clientY - start.clientY;
  if (Math.abs(horizontalDistance) < 32 || Math.abs(horizontalDistance) <= Math.abs(verticalDistance)) return;
  event.preventDefault();
  moveCarousel(element, horizontalDistance < 0 ? 1 : -1);
}

function cancelCarouselSwipe(id: string) {
  carouselPointerStarts.delete(id);
}

function registerAudio(id: string, node: unknown) {
  if (node instanceof HTMLAudioElement) audioElements.set(id, node);
}

async function toggleAudio(id: string) {
  const audio = audioElements.get(id);
  if (!audio) return;
  if (playingAudioIds.has(id)) {
    audio.pause();
    playingAudioIds.delete(id);
    return;
  }
  audioElements.forEach((candidate, candidateId) => {
    if (candidateId !== id) {
      candidate.pause();
      playingAudioIds.delete(candidateId);
    }
  });
  try {
    await audio.play();
    playingAudioIds.add(id);
  } catch {
    playingAudioIds.delete(id);
  }
}
</script>

<style scoped>
.invitation-render-viewport {
  position: relative;
  overflow: hidden;
  margin: 0 auto;
}

.invitation-render-canvas {
  position: absolute;
  inset: 0 auto auto 0;
  overflow: hidden;
  transform-origin: left top;
  color: #17261f;
}

.render-element {
  position: absolute;
  box-sizing: border-box;
}

.render-text,
.render-meeting-field {
  white-space: pre-wrap;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.render-image,
.render-video,
.render-carousel img {
  display: block;
}

.render-action-button {
  border: 0;
  display: grid;
  place-items: center;
  letter-spacing: 0;
  cursor: pointer;
  box-shadow: 0 7px 18px rgba(8, 120, 79, 0.16);
}

.render-action-button:disabled {
  cursor: not-allowed;
  filter: grayscale(0.7);
  opacity: 0.56 !important;
}

.render-qr {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  justify-items: center;
  gap: 5px;
  padding: 8px;
}

.render-qr img {
  min-height: 0;
  max-width: 100%;
  max-height: 100%;
  aspect-ratio: 1;
}

.render-qr span {
  max-width: 100%;
  display: -webkit-box;
  overflow: hidden;
  overflow-wrap: anywhere;
  text-align: center;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 12px;
  font-weight: 650;
}

.qr-unavailable {
  align-self: center;
  padding: 8px;
  color: #7b4e31;
  font-size: 11px;
  text-align: center;
}

.render-countdown {
  display: grid;
  place-content: center;
  gap: 6px;
  padding: 10px;
  text-align: center;
  background: #f6faf8;
  border-top: 1px solid #e1ece7;
  border-bottom: 1px solid #e1ece7;
}

.render-countdown small {
  color: #61776d;
  font-size: 12px;
}

.render-countdown strong {
  font-size: inherit;
}

.render-audio {
  display: flex;
  align-items: center;
  gap: 8px;
}

.render-audio button {
  width: 38px;
  height: 38px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: rgba(8, 120, 79, 0.88);
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.render-audio span {
  color: #345247;
  font-size: 12px;
}

.render-video,
.render-carousel {
  overflow: hidden;
  background: #e8efec;
}

.render-carousel {
  touch-action: pan-y;
  outline: none;
}

.render-carousel:focus-visible {
  box-shadow: inset 0 0 0 3px rgba(8, 120, 79, 0.72);
}

.render-video {
  object-fit: cover;
}

.render-carousel img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.carousel-dots {
  position: absolute;
  left: 50%;
  bottom: 3px;
  display: flex;
  gap: 0;
  transform: translateX(-50%);
}

.carousel-dot {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  display: grid;
  place-items: center;
  background: transparent;
  cursor: pointer;
}

.carousel-dot::before {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  box-shadow: 0 0 0 1px rgba(23, 38, 31, 0.14);
  content: "";
}

.carousel-dot.active::before {
  background: #08784f;
}

.carousel-dot:focus-visible::before {
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.94), 0 0 0 5px #08784f;
}

.carousel-arrow {
  position: absolute;
  top: 50%;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: rgba(23, 38, 31, 0.46);
  box-shadow: 0 2px 8px rgba(23, 38, 31, 0.2);
  cursor: pointer;
  transform: translateY(-50%);
  backdrop-filter: blur(5px);
}

.carousel-arrow .n-icon {
  font-size: 20px;
}

.carousel-arrow:focus-visible {
  outline: 3px solid rgba(255, 255, 255, 0.94);
  outline-offset: 2px;
}

.carousel-previous {
  left: 9px;
}

.carousel-next {
  right: 9px;
}
</style>
