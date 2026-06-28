<template>
  <div class="theme-visual" :class="`theme-visual--${variant}`" aria-hidden="true">
    <div class="visual-halo visual-halo-a"></div>
    <div class="visual-halo visual-halo-b"></div>

    <div v-if="variant === 'checkin'" class="visual-scene checkin-scene">
      <div class="kiosk">
        <div class="kiosk-screen">
          <n-icon :component="CheckmarkCircleOutline" />
        </div>
        <div class="kiosk-slot"></div>
        <div class="kiosk-stand"></div>
        <div class="kiosk-base"></div>
      </div>
      <div class="calendar-card soft-panel">
        <div class="panel-tab"></div>
        <div class="people-grid">
          <span v-for="index in 24" :key="index" :class="personClass(index)"></span>
        </div>
      </div>
      <div class="stack-panel soft-panel">
        <div v-for="item in checkinCards" :key="item.tone" class="mini-row">
          <span class="mini-icon" :class="item.tone">
            <n-icon :component="item.icon" />
          </span>
          <span></span>
          <span></span>
        </div>
      </div>
      <div class="badge-card soft-panel">
        <div class="lanyard"></div>
        <n-icon :component="IdCardOutline" />
      </div>
      <div class="small-calendar soft-panel">
        <div></div>
        <span v-for="index in 9" :key="index"></span>
      </div>
    </div>

    <div v-else-if="variant === 'dashboard'" class="visual-scene dashboard-scene">
      <div class="browser-board soft-panel">
        <div class="browser-top">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <div class="dashboard-grid">
          <div class="metric-card">
            <n-icon :component="PeopleOutline" />
            <span></span>
            <span></span>
          </div>
          <div class="chart-card">
            <span v-for="height in chartHeights" :key="height" :style="{ height }"></span>
          </div>
          <div class="trend-card">
            <svg viewBox="0 0 170 76" role="presentation">
              <path d="M10 58 C38 14, 58 48, 86 28 S126 38, 160 12" />
              <circle cx="10" cy="58" r="5" />
              <circle cx="86" cy="28" r="5" />
              <circle cx="160" cy="12" r="5" />
            </svg>
          </div>
        </div>
      </div>
      <div class="floating-pie soft-panel">
        <div class="pie-ring"></div>
        <span></span>
        <span></span>
      </div>
      <div class="floating-note soft-panel">
        <n-icon :component="ReaderOutline" />
        <span></span>
        <span></span>
      </div>
      <div class="floating-ticket soft-panel">
        <n-icon :component="TicketOutline" />
      </div>
    </div>

    <div v-else-if="variant === 'rooms'" class="visual-scene rooms-scene">
      <div class="hotel-board soft-panel">
        <div class="hotel-top">
          <n-icon :component="BedOutline" />
          <span></span>
        </div>
        <div class="room-grid">
          <span v-for="index in 12" :key="index" :class="{ filled: index % 4 === 0, active: index === 6 }"></span>
        </div>
      </div>
      <div class="room-card room-card-a soft-panel">
        <n-icon :component="KeyOutline" />
        <span></span>
        <span></span>
      </div>
      <div class="room-card room-card-b soft-panel">
        <n-icon :component="PeopleOutline" />
        <span></span>
        <span></span>
      </div>
      <div class="luggage">
        <div class="luggage-handle"></div>
        <div class="luggage-body"></div>
      </div>
    </div>

    <div v-else class="visual-scene seating-scene">
      <div class="stage"></div>
      <div class="seat-map soft-panel">
        <div class="seat-arc">
          <span v-for="index in 32" :key="index" :class="{ active: index === 11 || index === 22, warm: index === 7 || index === 25 }"></span>
        </div>
      </div>
      <div class="seat-card soft-panel">
        <n-icon :component="BusinessOutline" />
        <span></span>
        <span></span>
      </div>
      <div class="scan-card soft-panel">
        <n-icon :component="ScanOutline" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from "vue";
import {
  BedOutline,
  BusinessOutline,
  CalendarOutline,
  CheckmarkCircleOutline,
  GridOutline,
  IdCardOutline,
  KeyOutline,
  PeopleOutline,
  ReaderOutline,
  ScanOutline,
  TicketOutline
} from "@vicons/ionicons5";
import type { LoginVisualVariant } from "@/theme/appThemes";

defineProps<{
  variant: LoginVisualVariant;
}>();

const checkinCards: Array<{ icon: Component; tone: string }> = [
  { icon: PeopleOutline, tone: "tone-primary" },
  { icon: CalendarOutline, tone: "tone-secondary" },
  { icon: GridOutline, tone: "tone-tertiary" }
];

const chartHeights = ["36px", "54px", "78px", "66px", "92px"];

function personClass(index: number) {
  return {
    warm: [5, 14, 19].includes(index),
    hot: [11, 17].includes(index),
    blank: [6, 21].includes(index)
  };
}
</script>

<style scoped>
.theme-visual {
  --visual-primary: var(--accent-color);
  --visual-primary-hover: var(--accent-hover);
  --visual-primary-pressed: var(--accent-pressed);
  --visual-soft: var(--login-visual-bg);
  --visual-surface: var(--login-visual-surface);
  --visual-line: var(--login-line);
  --visual-secondary: var(--accent-secondary);
  --visual-tertiary: var(--accent-tertiary);
  position: relative;
  width: min(440px, 100%);
  aspect-ratio: 1.16;
  display: grid;
  place-items: center;
}

.visual-halo {
  position: absolute;
  border-radius: 999px;
  filter: blur(2px);
  opacity: 0.72;
}

.visual-halo-a {
  width: 72%;
  height: 62%;
  left: 12%;
  top: 16%;
  background: color-mix(in srgb, var(--visual-primary), white 78%);
}

.visual-halo-b {
  width: 38%;
  height: 34%;
  right: 6%;
  bottom: 12%;
  background: color-mix(in srgb, var(--visual-secondary), white 76%);
}

.visual-scene {
  position: relative;
  width: 100%;
  height: 100%;
}

.soft-panel {
  border: 1px solid rgba(255, 255, 255, 0.72);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(255, 255, 255, 0.7)),
    var(--visual-surface);
  box-shadow:
    0 18px 34px rgba(60, 89, 130, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.95);
}

.kiosk {
  position: absolute;
  left: 5%;
  bottom: 10%;
  width: 28%;
  height: 55%;
  transform: rotate(-7deg);
}

.kiosk-screen {
  position: absolute;
  inset: 0 8% 32%;
  border: 7px solid color-mix(in srgb, var(--visual-primary-pressed), black 5%);
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, var(--visual-primary-hover), var(--visual-primary));
  box-shadow:
    0 18px 28px rgba(48, 92, 160, 0.2),
    inset 0 1px 10px rgba(255, 255, 255, 0.38);
}

.kiosk-screen :deep(.n-icon) {
  font-size: 64px;
  filter: drop-shadow(0 3px 5px rgba(37, 99, 235, 0.22));
}

.kiosk-slot {
  position: absolute;
  width: 44%;
  height: 5px;
  left: 28%;
  bottom: 30%;
  border-radius: 999px;
  background: var(--visual-primary-pressed);
}

.kiosk-stand {
  position: absolute;
  width: 38%;
  height: 30%;
  left: 31%;
  bottom: 6%;
  border-radius: 0 0 10px 10px;
  background: linear-gradient(180deg, #ffffff, color-mix(in srgb, var(--visual-primary-hover), white 52%));
}

.kiosk-base {
  position: absolute;
  width: 82%;
  height: 10%;
  left: 9%;
  bottom: 0;
  border-radius: 999px;
  background: color-mix(in srgb, var(--visual-primary-hover), white 50%);
}

.calendar-card {
  position: absolute;
  left: 27%;
  top: 14%;
  width: 52%;
  height: 44%;
  border-radius: 16px;
  padding: 58px 26px 22px;
  transform: rotate(5deg);
}

.panel-tab {
  position: absolute;
  left: 38%;
  top: -9px;
  width: 28%;
  height: 23px;
  border-radius: 7px;
  background: linear-gradient(145deg, var(--visual-primary-hover), var(--visual-primary));
  box-shadow: 0 8px 16px color-mix(in srgb, var(--visual-primary), transparent 68%);
}

.calendar-card::before {
  content: "";
  position: absolute;
  left: 12%;
  right: 10%;
  top: -28px;
  height: 42px;
  border-radius: 12px 12px 0 0;
  background: color-mix(in srgb, var(--visual-primary-hover), white 68%);
  z-index: -1;
}

.people-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px 10px;
}

.people-grid span {
  position: relative;
  height: 20px;
  border-radius: 7px 7px 5px 5px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--visual-primary-hover), white 28%), var(--visual-primary-hover));
  box-shadow: inset 0 1px 3px rgba(255, 255, 255, 0.62);
}

.people-grid span::before {
  content: "";
  position: absolute;
  width: 15px;
  height: 15px;
  left: 50%;
  top: -11px;
  border-radius: 999px;
  transform: translateX(-50%);
  background: inherit;
}

.people-grid span.warm,
.people-grid span.warm::before {
  background: linear-gradient(180deg, color-mix(in srgb, var(--visual-secondary), white 32%), var(--visual-secondary));
}

.people-grid span.hot,
.people-grid span.hot::before {
  background: linear-gradient(180deg, color-mix(in srgb, var(--visual-tertiary), white 20%), var(--visual-tertiary));
}

.people-grid span.blank,
.people-grid span.blank::before {
  background: #f5f8fc;
}

.stack-panel {
  position: absolute;
  right: 0;
  top: 30%;
  width: 35%;
  height: 41%;
  border-radius: 16px;
  padding: 12px;
  display: grid;
  gap: 9px;
}

.mini-row {
  min-height: 52px;
  border-radius: 12px;
  display: grid;
  grid-template-columns: 42px 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 7px 12px;
  align-items: center;
  padding: 8px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.84);
}

.mini-icon {
  grid-row: span 2;
  width: 42px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
}

.mini-icon :deep(.n-icon) {
  font-size: 22px;
}

.tone-primary {
  background: linear-gradient(145deg, var(--visual-primary-hover), var(--visual-primary));
}

.tone-secondary {
  background: linear-gradient(145deg, color-mix(in srgb, var(--visual-secondary), white 20%), var(--visual-secondary));
}

.tone-tertiary {
  background: linear-gradient(145deg, color-mix(in srgb, var(--visual-tertiary), white 20%), var(--visual-tertiary));
}

.mini-row span:not(.mini-icon) {
  height: 7px;
  border-radius: 999px;
  background: var(--visual-line);
}

.badge-card {
  position: absolute;
  left: 45%;
  bottom: 8%;
  width: 22%;
  height: 31%;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: var(--visual-primary);
  transform: rotate(-8deg);
}

.badge-card :deep(.n-icon) {
  font-size: 64px;
}

.lanyard {
  position: absolute;
  width: 72%;
  height: 86%;
  left: -32%;
  top: -48%;
  border: 14px solid var(--visual-primary);
  border-bottom: 0;
  border-radius: 44px 44px 0 0;
  transform: rotate(25deg);
}

.small-calendar {
  position: absolute;
  left: 20%;
  bottom: 3%;
  width: 34%;
  height: 26%;
  border-radius: 13px;
  padding: 24px 18px 16px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.small-calendar div {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  height: 28px;
  border-radius: 12px 12px 0 0;
  background: var(--visual-primary);
}

.small-calendar span {
  border-radius: 4px;
  background: color-mix(in srgb, var(--visual-primary), white 74%);
}

.browser-board {
  position: absolute;
  left: 6%;
  top: 13%;
  width: 76%;
  height: 62%;
  border-radius: 18px;
  overflow: hidden;
}

.browser-top {
  height: 42px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, var(--visual-primary), var(--visual-primary-hover));
}

.browser-top span {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.dashboard-grid {
  padding: 22px;
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 16px;
}

.metric-card,
.chart-card,
.trend-card {
  min-height: 100px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--visual-primary), white 88%);
}

.metric-card {
  padding: 20px;
  display: grid;
  align-content: center;
  gap: 12px;
  color: var(--visual-primary);
}

.metric-card :deep(.n-icon) {
  font-size: 44px;
}

.metric-card span,
.floating-note span,
.floating-pie span,
.room-card span,
.seat-card span {
  height: 8px;
  border-radius: 999px;
  background: var(--visual-line);
}

.chart-card {
  padding: 16px 18px;
  display: flex;
  align-items: end;
  gap: 11px;
}

.chart-card span {
  flex: 1;
  border-radius: 8px 8px 4px 4px;
  background: linear-gradient(180deg, var(--visual-secondary), var(--visual-primary));
}

.trend-card {
  grid-column: span 2;
  min-height: 96px;
  padding: 12px;
}

.trend-card svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.trend-card path {
  fill: none;
  stroke: var(--visual-primary);
  stroke-width: 7;
  stroke-linecap: round;
}

.trend-card circle {
  fill: var(--visual-secondary);
  stroke: #fff;
  stroke-width: 4;
}

.floating-pie {
  position: absolute;
  right: 3%;
  top: 10%;
  width: 31%;
  height: 30%;
  border-radius: 16px;
  padding: 18px;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: center;
}

.pie-ring {
  width: 58px;
  height: 58px;
  grid-row: span 2;
  border-radius: 999px;
  background: conic-gradient(var(--visual-primary) 0 42%, var(--visual-secondary) 42% 72%, var(--visual-tertiary) 72% 100%);
  mask: radial-gradient(circle, transparent 38%, #000 40%);
}

.floating-note {
  position: absolute;
  left: 10%;
  bottom: 7%;
  width: 35%;
  height: 24%;
  border-radius: 16px;
  padding: 18px;
  display: grid;
  grid-template-columns: 46px 1fr;
  gap: 10px;
  align-items: center;
}

.floating-note :deep(.n-icon) {
  grid-row: span 2;
  color: var(--visual-primary);
  font-size: 42px;
}

.floating-ticket {
  position: absolute;
  right: 12%;
  bottom: 11%;
  width: 22%;
  height: 22%;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: var(--visual-secondary);
}

.floating-ticket :deep(.n-icon) {
  font-size: 58px;
}

.hotel-board {
  position: absolute;
  left: 18%;
  top: 11%;
  width: 58%;
  height: 66%;
  border-radius: 20px;
  padding: 24px;
}

.hotel-top {
  height: 54px;
  border-radius: 14px;
  display: grid;
  grid-template-columns: 46px 1fr;
  align-items: center;
  gap: 12px;
  padding: 0 15px;
  color: var(--visual-primary);
  background: color-mix(in srgb, var(--visual-primary), white 88%);
}

.hotel-top :deep(.n-icon) {
  font-size: 34px;
}

.hotel-top span {
  height: 10px;
  border-radius: 999px;
  background: var(--visual-line);
}

.room-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.room-grid span {
  aspect-ratio: 1.1;
  border-radius: 10px;
  background: color-mix(in srgb, var(--visual-primary), white 74%);
  box-shadow: inset 0 1px 3px rgba(255, 255, 255, 0.72);
}

.room-grid span.filled {
  background: color-mix(in srgb, var(--visual-secondary), white 28%);
}

.room-grid span.active {
  background: var(--visual-tertiary);
}

.room-card {
  position: absolute;
  width: 34%;
  height: 22%;
  border-radius: 16px;
  padding: 16px;
  display: grid;
  grid-template-columns: 46px 1fr;
  gap: 10px;
  align-items: center;
}

.room-card :deep(.n-icon) {
  grid-row: span 2;
  font-size: 42px;
}

.room-card-a {
  left: 5%;
  bottom: 13%;
  color: var(--visual-primary);
}

.room-card-b {
  right: 5%;
  top: 22%;
  color: var(--visual-secondary);
}

.luggage {
  position: absolute;
  right: 17%;
  bottom: 7%;
  width: 18%;
  height: 26%;
}

.luggage-handle {
  position: absolute;
  left: 24%;
  top: 0;
  width: 52%;
  height: 32%;
  border: 7px solid var(--visual-primary-pressed);
  border-bottom: 0;
  border-radius: 16px 16px 0 0;
}

.luggage-body {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 76%;
  border-radius: 16px;
  background: linear-gradient(145deg, var(--visual-primary-hover), var(--visual-primary));
  box-shadow: 0 18px 30px rgba(60, 89, 130, 0.16);
}

.stage {
  position: absolute;
  left: 27%;
  top: 10%;
  width: 48%;
  height: 12%;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--visual-primary), var(--visual-primary-hover));
  box-shadow: 0 14px 28px color-mix(in srgb, var(--visual-primary), transparent 72%);
}

.seat-map {
  position: absolute;
  left: 12%;
  top: 25%;
  width: 72%;
  height: 54%;
  border-radius: 22px;
  padding: 28px 34px;
}

.seat-arc {
  height: 100%;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  align-content: center;
  gap: 12px;
  transform: perspective(440px) rotateX(16deg);
}

.seat-arc span {
  aspect-ratio: 1.05;
  border-radius: 8px;
  background: color-mix(in srgb, var(--visual-primary-hover), white 30%);
  box-shadow: inset 0 1px 3px rgba(255, 255, 255, 0.65);
}

.seat-arc span.active {
  background: var(--visual-primary);
}

.seat-arc span.warm {
  background: var(--visual-secondary);
}

.seat-card {
  position: absolute;
  left: 5%;
  bottom: 7%;
  width: 35%;
  height: 23%;
  border-radius: 16px;
  padding: 16px;
  display: grid;
  grid-template-columns: 46px 1fr;
  gap: 10px;
  align-items: center;
  color: var(--visual-primary);
}

.seat-card :deep(.n-icon) {
  grid-row: span 2;
  font-size: 42px;
}

.scan-card {
  position: absolute;
  right: 8%;
  bottom: 8%;
  width: 24%;
  height: 24%;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: var(--visual-secondary);
}

.scan-card :deep(.n-icon) {
  font-size: 64px;
}

@media (max-width: 520px) {
  .theme-visual {
    max-width: 360px;
  }

  .stack-panel {
    right: -3%;
  }
}
</style>
