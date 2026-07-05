<template>
  <div class="seating-page">
    <page-header title="座位管理">
      <n-button secondary size="large" @click="openLayoutModal">
        <template #icon><n-icon :component="SettingsOutline" /></template>
        布局配置
      </n-button>
      <n-button secondary size="large" @click="openExportModal">
        <template #icon><n-icon :component="DownloadOutline" /></template>
        导出座位表
      </n-button>
      <n-button type="primary" size="large" :loading="autoAssigning" @click="autoAssign">
        <template #icon><n-icon :component="FlashOutline" /></template>
        自动排座
      </n-button>
    </page-header>

    <n-spin :show="bootstrapping">
      <section class="seat-stats" aria-label="座位统计">
        <div v-for="item in statsCards" :key="item.label" class="seat-stat-card surface">
          <div class="seat-stat-value" :class="item.tone">{{ item.value }}</div>
          <div class="seat-stat-label">{{ item.label }}</div>
        </div>
      </section>

      <section class="seat-toolbar">
        <n-button-group>
          <n-button
            v-for="option in areaOptions"
            :key="option.value"
            size="large"
            :type="activeArea === option.value ? 'primary' : 'default'"
            :secondary="activeArea !== option.value"
            @click="activeArea = option.value"
          >
            {{ option.label }}
          </n-button>
        </n-button-group>

        <div class="seat-toolbar-right">
          <n-radio-group v-model:value="seatLabelMode" size="large" class="seat-label-mode" aria-label="座位显示内容">
            <n-radio-button value="SEAT_NO">座位号</n-radio-button>
            <n-radio-button value="NAME">姓名</n-radio-button>
          </n-radio-group>

          <div class="seat-legend" aria-label="座位状态图例">
            <span><i class="empty"></i>空座</span>
            <span><i class="assigned"></i>已分配</span>
            <span><i class="checked-in"></i>已签到</span>
            <span><i class="disabled"></i>禁用</span>
          </div>
        </div>
      </section>

      <section ref="seatBoardRef" class="seat-board surface">
        <div class="seat-board-controls" aria-label="座位图缩放">
          <n-button circle quaternary size="small" title="缩小座位图" :disabled="seatZoom <= MIN_SEAT_ZOOM" @click="zoomSeatBoard(-ZOOM_STEP)">
            <template #icon><n-icon :component="RemoveOutline" /></template>
          </n-button>
          <n-button quaternary size="small" class="zoom-reset" title="重置为适应宽度" @click="resetSeatZoom">
            {{ zoomPercent }}
          </n-button>
          <n-button circle quaternary size="small" title="放大座位图" :disabled="seatZoom >= MAX_SEAT_ZOOM" @click="zoomSeatBoard(ZOOM_STEP)">
            <template #icon><n-icon :component="AddOutline" /></template>
          </n-button>
        </div>

        <div class="seat-board-viewport" :style="seatMetricsStyle">
          <div class="stage-pill">主席台 / 舞台</div>
          <div class="seat-areas">
            <article v-for="area in visibleAreas" :key="area.name" class="seat-area-block">
              <div class="seat-grid-shell" :style="areaGridStyle(area)">
                <template v-for="rowNo in area.rowCount" :key="`${area.name}-${rowNo}`">
                  <div class="row-index left">{{ rowNo }}</div>
                  <button
                    v-for="seat in seatsByAreaRow(area.name, rowNo)"
                    :key="seat.id"
                    class="seat-cell"
                    :class="[seat.status.toLowerCase().replace('_', '-'), { locked: seat.locked }]"
                    :title="seatTooltip(seat)"
                    @click="openSeatModal(seat)"
                  >
                    <span :class="{ 'name-label': seatLabelMode === 'NAME' }">{{ seatLabel(seat) }}</span>
                  </button>
                  <div class="row-index right">{{ rowNo }}</div>
                </template>
              </div>
            </article>
          </div>
        </div>
      </section>
    </n-spin>

    <n-modal v-model:show="layoutModalVisible" preset="card" class="layout-modal" :mask-closable="!savingLayout">
      <template #header>
        <div class="layout-modal-title">会场布局配置</div>
      </template>

      <div class="layout-config">
        <div class="layout-summary-row">
          <span>共 {{ layoutDrafts.length }} 个区域 · {{ layoutDraftTotalSeats }} 个座位</span>
          <n-button class="add-area-button" round secondary type="primary" @click="addLayoutArea">
            <template #icon><n-icon :component="AddOutline" /></template>
            添加区域
          </n-button>
        </div>

        <div class="layout-area-list">
          <section
            v-for="draft in layoutDrafts"
            :key="draft.uid"
            :data-layout-uid="draft.uid"
            class="layout-area-card"
            :class="{ dragging: draggingLayoutUid === draft.uid, 'drop-target': draggingLayoutUid && draggingLayoutUid !== draft.uid }"
          >
            <header class="layout-area-card-header">
              <button
                type="button"
                class="layout-drag-handle"
                title="拖动调整区域顺序"
                @pointerdown="startLayoutPointerDrag(draft.uid, $event)"
              >
                <n-icon :component="ReorderThreeOutline" />
              </button>
              <div class="layout-area-main">
                <div class="layout-area-name">
                  <span class="layout-area-dot" :style="{ background: draft.color }"></span>
                  <n-input
                    v-model:value="draft.areaName"
                    class="layout-area-name-input"
                    placeholder="区域名称"
                  />
                </div>
                <div class="layout-area-meta">
                  <span>{{ layoutAreaSeatCount(draft) }} 座</span>
                  <span>{{ layoutAreaOccupancySummary(draft) }}</span>
                </div>
              </div>
              <n-button
                quaternary
                circle
                size="small"
                class="layout-delete-button"
                :loading="deletingAreaUid === draft.uid"
                title="删除区域"
                @click="requestRemoveLayoutArea(draft)"
              >
                <template #icon><n-icon :component="TrashOutline" /></template>
              </n-button>
            </header>

            <div class="layout-area-fields">
              <n-form-item label="排数" :show-feedback="false">
                <n-input-number v-model:value="draft.rowCount" :min="1" :max="80" />
              </n-form-item>
              <n-form-item label="每排座位数" :show-feedback="false">
                <n-input-number v-model:value="draft.seatsPerRow" :min="1" :max="80" />
              </n-form-item>
            </div>

            <div class="seat-type-selector">
              <span class="seat-type-label">默认座位类型</span>
              <div class="seat-type-chip-group" role="radiogroup" aria-label="默认座位类型">
                <button
                  v-for="option in seatTypeOptions"
                  :key="option.value"
                  type="button"
                  class="seat-type-chip"
                  :class="{ active: draft.defaultSeatType === option.value }"
                  :aria-checked="draft.defaultSeatType === option.value"
                  role="radio"
                  @click="draft.defaultSeatType = option.value"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <div class="layout-area-total">共 {{ layoutAreaSeatCount(draft) }} 个座位</div>
          </section>
        </div>

        <n-button block size="large" type="primary" class="generate-layout-button" :loading="savingLayout" @click="saveLayouts">
          <template #icon><n-icon :component="GridOutline" /></template>
          生成座位图
        </n-button>
      </div>
    </n-modal>

    <n-modal
      v-model:show="seatModalVisible"
      preset="card"
      :title="selectedSeatTitle"
      class="seat-assign-modal"
      :mask-closable="!seatModalBusy"
    >
      <template v-if="selectedSeat">
        <div class="seat-detail-card">
          <div>
            <span class="detail-label">区域</span>
            <strong>{{ selectedSeat.areaName }}</strong>
          </div>
          <div>
            <span class="detail-label">座位</span>
            <strong>{{ selectedSeat.rowNo }}排{{ selectedSeat.seatNo }}座</strong>
          </div>
          <div>
            <span class="detail-label">状态</span>
            <n-tag :type="selectedSeatStatusMeta.type" round>{{ selectedSeatStatusMeta.text }}</n-tag>
          </div>
        </div>

        <n-alert v-if="selectedSeat.status === 'DISABLED'" type="warning" :show-icon="false">
          该座位已禁用，不能分配参会人。
        </n-alert>

        <div class="seat-maintenance-panel">
          <n-form label-placement="top" :show-feedback="false">
            <n-form-item label="座位类型">
              <n-select
                v-model:value="selectedSeatType"
                :options="seatTypeSelectOptions"
                :disabled="selectedSeat.status === 'DISABLED' || seatModalBusy"
                @update:value="changeSelectedSeatType"
              />
            </n-form-item>
            <n-form-item label="锁定状态">
              <n-switch
                :value="selectedSeat.locked"
                :disabled="selectedSeat.status === 'DISABLED' || seatModalBusy"
                @update:value="toggleSelectedSeatLocked"
              />
            </n-form-item>
          </n-form>
        </div>

        <template v-if="selectedSeat.status !== 'DISABLED' && selectedSeat.attendeeId">
          <div class="current-assignee">
            <span class="assignee-avatar">{{ currentAssigneeInitial }}</span>
            <div>
              <strong>{{ currentAssignee?.name || selectedSeat.attendeeName || '已分配参会人' }}</strong>
              <p>{{ currentAssignee?.company || '当前座位已有分配' }} · {{ selectedSeatAssigneeTypeLabel }}</p>
            </div>
          </div>
          <n-alert type="info" :show-icon="false">
            如需重新分配，请先解除当前座位，再选择新的参会人。
          </n-alert>
          <n-form label-placement="top" :show-feedback="false" class="swap-seat-form">
            <n-form-item label="交换座位">
              <n-select
                v-model:value="swapTargetAttendeeId"
                filterable
                clearable
                placeholder="选择另一个已分配参会人"
                :options="swapTargetOptions"
                :disabled="selectedSeat.locked || seatModalBusy"
              />
            </n-form-item>
          </n-form>
        </template>

        <template v-else-if="selectedSeat.status !== 'DISABLED'">
          <n-form label-placement="top" :show-feedback="false">
            <n-form-item label="选择参会人">
              <n-select
                v-model:value="selectedAttendeeId"
                filterable
                clearable
                placeholder="搜索姓名 / 手机号 / 单位"
                :options="assignableAttendeeOptions"
              />
            </n-form-item>
          </n-form>
          <div class="candidate-summary">
            当前可分配 {{ assignableAttendeeOptions.length }} 人，仅展示已审核通过且未排座人员。
          </div>
        </template>
      </template>

      <template #footer>
        <n-space justify="space-between" align="center">
          <n-space>
            <n-button
              v-if="selectedSeat?.attendeeId"
              type="error"
              secondary
              :loading="clearingSeat"
              :disabled="assigningSeat || togglingSeatStatus"
              @click="clearSelectedSeat"
            >
              解除分配
            </n-button>
            <n-button
              v-if="selectedSeat?.attendeeId"
              type="primary"
              secondary
              :loading="swappingSeat"
              :disabled="!swapTargetAttendeeId || seatModalBusy"
              @click="swapSelectedSeat"
            >
              交换座位
            </n-button>
            <n-button
              v-else-if="selectedSeat"
              :type="selectedSeat.status === 'DISABLED' ? 'primary' : 'error'"
              secondary
              :loading="togglingSeatStatus"
              :disabled="assigningSeat || clearingSeat"
              @click="toggleSelectedSeatDisabled"
            >
              {{ selectedSeat.status === "DISABLED" ? "启用该座位" : "停用该座位" }}
            </n-button>
          </n-space>
          <n-space>
            <n-button :disabled="seatModalBusy" @click="seatModalVisible = false">取消</n-button>
            <n-button
              v-if="selectedSeat && !selectedSeat.attendeeId && selectedSeat.status !== 'DISABLED'"
              type="primary"
              :disabled="!selectedAttendeeId"
              :loading="assigningSeat"
              @click="assignSelected"
            >
              确认分配
            </n-button>
          </n-space>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="exportModalVisible" preset="card" title="导出座位表" class="export-modal" :mask-closable="!exporting">
      <div class="export-options">
        <n-alert type="info" :show-icon="false" style="margin-bottom: 16px">
          将当前座位布局导出为图片或 PDF 文件，方便打印和分享。
        </n-alert>

        <div class="export-format-selector">
          <span class="export-label">选择格式</span>
          <n-radio-group v-model:value="exportFormat" size="large">
            <n-radio-button value="png">PNG 图片</n-radio-button>
            <n-radio-button value="jpeg">JPEG 图片</n-radio-button>
            <n-radio-button value="pdf">PDF 文档</n-radio-button>
          </n-radio-group>
        </div>

        <div class="export-tips">
          <p v-if="exportFormat === 'png'">
            <strong>PNG 格式：</strong>无损压缩，适合高质量打印，文件稍大
          </p>
          <p v-else-if="exportFormat === 'jpeg'">
            <strong>JPEG 格式：</strong>有损压缩，文件较小，适合网络传输
          </p>
          <p v-else>
            <strong>PDF 格式：</strong>矢量文档，适合正式场合打印和存档
          </p>
        </div>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button :disabled="exporting" @click="exportModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="exporting" @click="handleExport">
            <template #icon><n-icon :component="DownloadOutline" /></template>
            导出
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { NIcon, useDialog, useMessage } from "naive-ui";
import { AddOutline, DownloadOutline, FlashOutline, GridOutline, RemoveOutline, ReorderThreeOutline, SettingsOutline, TrashOutline } from "@vicons/ionicons5";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useMeetingStore } from "@/stores/meeting";
import type { Seat, SeatType } from "@/utils/types";
import { extractErrorMessage } from "@/api/http";
import { attendeeTypeLabel, seatTypeLabel } from "@/utils/meetingDisplay";
import type { ExportFormat } from "@/utils/exportSeating";

interface LayoutAreaDraft {
  uid: string;
  originalAreaName?: string;
  areaName: string;
  rowCount: number;
  seatsPerRow: number;
  defaultSeatType: Exclude<SeatType, "DISABLED">;
  color: string;
  persisted: boolean;
}

type SeatLabelMode = "SEAT_NO" | "NAME";

const store = useMeetingStore();
const message = useMessage();
const dialog = useDialog();
const { routeMeetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });
const selectedAttendeeId = ref<string | null>(null);
const selectedSeat = ref<Seat | null>(null);
const selectedSeatType = ref<Exclude<SeatType, "DISABLED">>("NORMAL");
const swapTargetAttendeeId = ref<string | null>(null);
const activeArea = ref("ALL");
const seatLabelMode = ref<SeatLabelMode>("SEAT_NO");
const layoutModalVisible = ref(false);
const seatModalVisible = ref(false);
const exportModalVisible = ref(false);
const savingLayout = ref(false);
const assigningSeat = ref(false);
const clearingSeat = ref(false);
const togglingSeatStatus = ref(false);
const lockingSeat = ref(false);
const updatingSeatType = ref(false);
const swappingSeat = ref(false);
const autoAssigning = ref(false);
const exporting = ref(false);
const deletingAreaUid = ref<string | null>(null);
const bootstrapping = ref(true);
const exportFormat = ref<ExportFormat>("pdf");
const MIN_SEAT_ZOOM = 0.6;
const MAX_SEAT_ZOOM = 1.2;
const ZOOM_STEP = 0.1;
const DEFAULT_SEAT_ZOOM = 0.9;
const seatBoardRef = ref<HTMLElement | null>(null);
const seatZoom = ref(DEFAULT_SEAT_ZOOM);
const manualSeatZoom = ref(false);
let seatBoardResizeObserver: ResizeObserver | undefined;
let seatingPageLoadToken = 0;
const layoutDrafts = ref<LayoutAreaDraft[]>([]);
const draggingLayoutUid = ref<string | null>(null);
const layoutDragStartY = ref(0);
const areaPalette = ["#3b82f6", "#60a5fa", "#f6bd60", "#f28482", "#a7c7e7", "#8ecae6"];
const seatTypeOptions: Array<{ label: string; value: LayoutAreaDraft["defaultSeatType"] }> = [
  { label: "普通座", value: "NORMAL" },
  { label: "VIP座", value: "VIP" },
  { label: "嘉宾座", value: "GUEST" },
  { label: "媒体座", value: "MEDIA" },
  { label: "工作座", value: "STAFF" }
];
const seatTypeSelectOptions = seatTypeOptions.map((item) => ({ label: item.label, value: item.value }));

onMounted(() => {
  void syncSeatingWorkspace();

  if (typeof ResizeObserver !== "undefined") {
    seatBoardResizeObserver = new ResizeObserver(() => {
      void nextTick(fitSeatBoardToWidth);
    });
    if (seatBoardRef.value) {
      seatBoardResizeObserver.observe(seatBoardRef.value);
    }
  }
});

watch(routeMeetingId, () => {
  selectedSeat.value = null;
  selectedAttendeeId.value = null;
  swapTargetAttendeeId.value = null;
  activeArea.value = "ALL";
  void syncSeatingWorkspace();
});

onBeforeUnmount(() => {
  seatBoardResizeObserver?.disconnect();
});

watch(
  () => store.currentSeats.map((seat) => `${seat.areaName}:${seat.rowNo}-${seat.seatNo}:${seat.status}:${seat.attendeeId || ""}`).join("|"),
  () => {
    syncLayoutDraftFromSeats();
    void nextTick(fitSeatBoardToWidth);
  }
);

async function syncSeatingWorkspace() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  const token = ++seatingPageLoadToken;
  if (!meetingId) return;
  bootstrapping.value = true;
  try {
    await syncMeetingFromRoute();
    const results = await Promise.allSettled([
      store.loadSeats(meetingId),
      store.loadAttendees(meetingId),
      store.loadStatistics(meetingId)
    ]);
    if (token !== seatingPageLoadToken) return;
    const failed = results.find((result) => result.status === "rejected") as PromiseRejectedResult | undefined;
    if (failed) {
      message.error(failed.reason instanceof Error ? failed.reason.message : "座位数据加载失败");
    }
    /*
     * 座位页只需要座位、参会人和统计概览。住宿、同步日志、签到记录由各自页面按需加载，
     * 避免快速从座位页切到数据统计时，旧页面还在回写整套工作区数据。
     */
    store.reconcileAssignments();
    store.refreshMeetingCounters();
    syncLayoutDraftFromSeats();
  } finally {
    if (token === seatingPageLoadToken) {
      bootstrapping.value = false;
      void nextTick(fitSeatBoardToWidth);
    }
  }
}

const sortedSeats = computed(() =>
  [...store.currentSeats].sort(
    (a, b) =>
      (a.areaSortOrder ?? 0) - (b.areaSortOrder ?? 0) ||
      a.areaName.localeCompare(b.areaName, "zh-Hans-CN") ||
      a.rowNo - b.rowNo ||
      a.seatNo - b.seatNo
  )
);

const areaGroups = computed(() => {
  const names = Array.from(new Set(sortedSeats.value.map((seat) => seat.areaName || "A区")));
  return names.map((name) => {
    const seats = sortedSeats.value.filter((seat) => seat.areaName === name);
    return {
      name,
      seats,
      rowCount: Math.max(...seats.map((seat) => seat.rowNo), 1),
      seatsPerRow: Math.max(...seats.map((seat) => seat.seatNo), 1)
    };
  });
});

const areaOptions = computed(() => [
  { label: "全部", value: "ALL" },
  ...areaGroups.value.map((area) => ({ label: area.name, value: area.name }))
]);

const visibleAreas = computed(() => (activeArea.value === "ALL" ? areaGroups.value : areaGroups.value.filter((area) => area.name === activeArea.value)));

watch(
  [activeArea, visibleAreas],
  () => {
    void nextTick(fitSeatBoardToWidth);
  },
  { flush: "post" }
);

const stats = computed(() => {
  const total = store.currentSeats.length;
  const assigned = store.currentSeats.filter((seat) => seat.status === "ASSIGNED" || seat.status === "CHECKED_IN").length;
  const checkedIn = store.currentSeats.filter((seat) => seat.status === "CHECKED_IN").length;
  const disabled = store.currentSeats.filter((seat) => seat.status === "DISABLED").length;
  return {
    total,
    assigned,
    checkedIn,
    disabled,
    empty: Math.max(total - assigned - disabled, 0)
  };
});

const statsCards = computed(() => [
  { label: "总座位", value: stats.value.total, tone: "neutral" },
  { label: "已分配", value: stats.value.assigned, tone: "blue" },
  { label: "空余", value: stats.value.empty, tone: "green" },
  { label: "已签到", value: stats.value.checkedIn, tone: "success" },
  { label: "禁用", value: stats.value.disabled, tone: "danger" }
]);

const layoutDraftTotalSeats = computed(() => layoutDrafts.value.reduce((total, draft) => total + layoutAreaSeatCount(draft), 0));

const zoomPercent = computed(() => `${Math.round(seatZoom.value * 100)}%`);

const seatMetricsStyle = computed(() => {
  // 用尺寸变量缩放座位图，滚动区域会按缩放后的真实尺寸重新计算，避免 transform 缩放造成“看起来变小但仍按原尺寸占位”。
  const width = Math.round(72 * seatZoom.value);
  const height = Math.round(46 * seatZoom.value);
  const gap = Math.max(6, Math.round(10 * seatZoom.value));
  return {
    "--seat-width": `${width}px`,
    "--seat-height": `${height}px`,
    "--seat-gap": `${gap}px`,
    "--row-index-width": `${Math.max(18, Math.round(24 * seatZoom.value))}px`,
    "--seat-font-size": `${Math.max(12, Math.round(15 * seatZoom.value))}px`,
    "--stage-width": `${Math.round(330 * seatZoom.value)}px`,
    "--stage-height": `${Math.round(46 * seatZoom.value)}px`,
    "--stage-font-size": `${Math.max(13, Math.round(16 * seatZoom.value))}px`
  };
});

const seatedAttendeeIds = computed(() => new Set(store.currentSeats.map((seat) => seat.attendeeId).filter(Boolean)));
const assignableAttendees = computed(() =>
  store.currentAttendees.filter((attendee) => attendee.auditStatus === "APPROVED" && !seatedAttendeeIds.value.has(attendee.id))
);

const assignableAttendeeOptions = computed(() =>
  assignableAttendees.value.map((attendee) => ({
    label: `${attendee.name} · ${attendee.phone || "未填写号码"} · ${attendee.company || "未填写单位"}`,
    value: attendee.id
  }))
);

const swapTargetOptions = computed(() => {
  const currentAttendeeId = selectedSeat.value?.attendeeId;
  return store.currentSeats
    .filter((seat) => seat.attendeeId && seat.attendeeId !== currentAttendeeId && seat.status !== "DISABLED" && !seat.locked)
    .map((seat) => {
      const attendee = seat.attendeeId ? store.currentAttendeeById.get(seat.attendeeId) : undefined;
      return {
        label: `${attendee?.name || seat.attendeeName || "已分配参会人"} · ${seat.areaName} ${seat.rowNo}排${seat.seatNo}座`,
        value: seat.attendeeId as string
      };
    });
});

const selectedSeatTitle = computed(() =>
  selectedSeat.value ? `分配座位：${selectedSeat.value.areaName} ${selectedSeat.value.rowNo}排${selectedSeat.value.seatNo}座` : "分配座位"
);

const currentAssignee = computed(() => {
  if (!selectedSeat.value?.attendeeId) return null;
  return selectedSeat.value?.attendeeId ? store.currentAttendeeById.get(selectedSeat.value.attendeeId) || null : null;
});

const currentAssigneeInitial = computed(() => (currentAssignee.value?.name || selectedSeat.value?.attendeeName || "已").slice(0, 1));
const selectedSeatStatusMeta = computed(() => seatStatusMeta(selectedSeat.value?.status || "EMPTY"));
const selectedSeatAssigneeTypeLabel = computed(() => {
  if (currentAssignee.value?.attendeeType) return attendeeTypeLabel(currentAssignee.value.attendeeType);
  return seatTypeLabel(selectedSeat.value?.seatType);
});
const seatModalBusy = computed(() => assigningSeat.value || clearingSeat.value || togglingSeatStatus.value || lockingSeat.value || updatingSeatType.value || swappingSeat.value);

function seatsByAreaRow(areaName: string, rowNo: number) {
  return sortedSeats.value.filter((seat) => seat.areaName === areaName && seat.rowNo === rowNo);
}

function areaGridStyle(area: { seatsPerRow: number }) {
  return {
    gridTemplateColumns: `var(--row-index-width) repeat(${Math.max(1, area.seatsPerRow)}, var(--seat-width)) var(--row-index-width)`
  };
}

function zoomSeatBoard(delta: number) {
  manualSeatZoom.value = true;
  const next = Math.round((seatZoom.value + delta) * 10) / 10;
  seatZoom.value = Math.min(MAX_SEAT_ZOOM, Math.max(MIN_SEAT_ZOOM, next));
}

function resetSeatZoom() {
  manualSeatZoom.value = false;
  fitSeatBoardToWidth();
}

function fitSeatBoardToWidth() {
  if (manualSeatZoom.value || !seatBoardRef.value || !visibleAreas.value.length) return;

  const boardStyle = getComputedStyle(seatBoardRef.value);
  const horizontalPadding = Number.parseFloat(boardStyle.paddingLeft) + Number.parseFloat(boardStyle.paddingRight);
  const availableWidth = seatBoardRef.value.clientWidth - horizontalPadding;
  const widestArea = Math.max(...visibleAreas.value.map((area) => area.seatsPerRow), 1);

  // 默认缩放按“最宽区域完整进入当前画布”计算，保证座位矩阵先居中显示；用户点 +/- 后则保留手动缩放。
  for (let zoom = DEFAULT_SEAT_ZOOM; zoom >= MIN_SEAT_ZOOM; zoom -= 0.01) {
    if (seatGridWidthForZoom(widestArea, zoom) <= availableWidth) {
      seatZoom.value = Math.round(zoom * 100) / 100;
      return;
    }
  }
  seatZoom.value = MIN_SEAT_ZOOM;
}

function seatGridWidthForZoom(seatsPerRow: number, zoom: number) {
  const width = Math.round(72 * zoom);
  const gap = Math.max(6, Math.round(10 * zoom));
  const rowIndexWidth = Math.max(18, Math.round(24 * zoom));
  return rowIndexWidth * 2 + width * seatsPerRow + gap * (seatsPerRow + 1);
}

function seatLabel(seat: Seat) {
  if (seatLabelMode.value === "SEAT_NO") {
    return String(seat.seatNo);
  }
  const assigneeName = seatAssigneeName(seat);

  // 姓名模式只在座位已分配时展示姓名；最多保留 4 个字，且保持单行，避免座位图阅读时出现上下断行。
  return assigneeName ? formatSeatNameLabel(assigneeName) : String(seat.seatNo);
}

function seatAssigneeName(seat: Seat) {
  const attendee = seat.attendeeId ? store.currentAttendeeById.get(seat.attendeeId) : undefined;
  return (seat.attendeeName || attendee?.name || "").trim();
}

function formatSeatNameLabel(name: string) {
  return Array.from(name.trim()).slice(0, 4).join("");
}

function seatTooltip(seat: Seat) {
  const status = seatStatusMeta(seat.status).text;
  const assignee = seatAssigneeName(seat);
  return `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座 · ${status}${assignee ? ` · ${assignee}` : ""}`;
}

function seatStatusMeta(status: Seat["status"]) {
  const dictionary = {
    EMPTY: { text: "空座", type: "default" as const },
    ASSIGNED: { text: "已分配", type: "info" as const },
    CHECKED_IN: { text: "已签到", type: "success" as const },
    DISABLED: { text: "禁用", type: "error" as const }
  };
  return dictionary[status] || dictionary.EMPTY;
}

function openLayoutModal() {
  syncLayoutDraftsFromSeats();
  layoutModalVisible.value = true;
}

function openSeatModal(seat: Seat) {
  selectedSeat.value = seat;
  selectedAttendeeId.value = null;
  selectedSeatType.value = seat.seatType === "DISABLED" ? "NORMAL" : seat.seatType;
  swapTargetAttendeeId.value = null;
  seatModalVisible.value = true;
}

function openExportModal() {
  exportModalVisible.value = true;
}

async function handleExport() {
  if (!seatBoardRef.value) {
    message.error("未找到座位图元素");
    return;
  }

  exporting.value = true;
  try {
    /*
     * 导出依赖会继续动态加载 html2canvas/jsPDF，只有用户确认导出时才解析。
     * 这里不要在页面顶部静态导入 exportSeating，否则座位页 chunk 会提前关联导出工具。
     */
    const { exportSeating } = await import("@/utils/exportSeating");
    await exportSeating(
      seatBoardRef.value,
      exportFormat.value,
      store.currentMeeting?.name
    );
    message.success("座位表已导出");
    exportModalVisible.value = false;
  } catch (error) {
    message.error(error instanceof Error ? error.message : "导出失败");
  } finally {
    exporting.value = false;
  }
}

function assignSelected() {
  if (!selectedSeat.value || !selectedAttendeeId.value) {
    message.warning("请选择座位和参会人");
    return;
  }
  assigningSeat.value = true;
  void store
    .assignSeat(selectedAttendeeId.value, selectedSeat.value.id)
    .then(() => {
      message.success("座位已分配");
      seatModalVisible.value = false;
      selectedSeat.value = null;
      selectedAttendeeId.value = null;
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "座位分配失败");
    })
    .finally(() => {
      assigningSeat.value = false;
    });
}

function clearSelectedSeat() {
  if (!selectedSeat.value?.attendeeId) return;
  clearingSeat.value = true;
  void store
    .clearSeat(selectedSeat.value.attendeeId)
    .then(() => {
      message.success("已解除座位分配");
      seatModalVisible.value = false;
      selectedSeat.value = null;
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "解除分配失败");
    })
    .finally(() => {
      clearingSeat.value = false;
    });
}

function swapSelectedSeat() {
  if (!selectedSeat.value?.attendeeId || !swapTargetAttendeeId.value) return;
  swappingSeat.value = true;
  const sourceAttendeeId = selectedSeat.value.attendeeId;
  void store
    .swapSeats(sourceAttendeeId, swapTargetAttendeeId.value)
    .then(() => {
      message.success("座位已交换");
      seatModalVisible.value = false;
      selectedSeat.value = null;
      swapTargetAttendeeId.value = null;
    })
    .catch((error) => {
      message.error(extractErrorMessage(error, "交换座位失败"));
    })
    .finally(() => {
      swappingSeat.value = false;
    });
}

function toggleSelectedSeatLocked(locked: boolean) {
  if (!selectedSeat.value) return;
  lockingSeat.value = true;
  const seatId = selectedSeat.value.id;
  void store
    .setSeatLocked(seatId, locked)
    .then(() => {
      message.success(locked ? "座位已锁定" : "座位已解锁");
      selectedSeat.value = store.currentSeats.find((seat) => seat.id === seatId) || selectedSeat.value;
    })
    .catch((error) => {
      message.error(extractErrorMessage(error, locked ? "锁定座位失败" : "解锁座位失败"));
    })
    .finally(() => {
      lockingSeat.value = false;
    });
}

function changeSelectedSeatType(seatType: Exclude<SeatType, "DISABLED">) {
  if (!selectedSeat.value || selectedSeat.value.seatType === seatType) return;
  updatingSeatType.value = true;
  const seatId = selectedSeat.value.id;
  void store
    .updateSeatType(seatId, seatType)
    .then(() => {
      message.success("座位类型已更新");
      selectedSeat.value = store.currentSeats.find((seat) => seat.id === seatId) || selectedSeat.value;
      selectedSeatType.value = seatType;
    })
    .catch((error) => {
      message.error(extractErrorMessage(error, "更新座位类型失败"));
      selectedSeatType.value = selectedSeat.value?.seatType === "DISABLED" ? "NORMAL" : selectedSeat.value?.seatType || "NORMAL";
    })
    .finally(() => {
      updatingSeatType.value = false;
    });
}

function toggleSelectedSeatDisabled() {
  if (!selectedSeat.value) return;
  if (selectedSeat.value.attendeeId) {
    message.warning("请先解除当前座位分配，再停用该座位");
    return;
  }

  const nextDisabled = selectedSeat.value.status !== "DISABLED";
  const applyToggle = () => {
    if (!selectedSeat.value) return;
    const seatId = selectedSeat.value.id;
    togglingSeatStatus.value = true;
    void store
      .setSeatDisabled(seatId, nextDisabled)
      .then(() => {
        message.success(nextDisabled ? "座位已停用" : "座位已启用");
        selectedSeat.value = store.currentSeats.find((seat) => seat.id === seatId) || null;
      })
      .catch((error) => {
        message.error(extractErrorMessage(error, nextDisabled ? "停用座位失败" : "启用座位失败"));
      })
      .finally(() => {
        togglingSeatStatus.value = false;
      });
  };

  if (!nextDisabled) {
    applyToggle();
    return;
  }

  dialog.warning({
    title: "停用该座位",
    content: `${selectedSeat.value.areaName} ${selectedSeat.value.rowNo}排${selectedSeat.value.seatNo}座停用后不会参与自动排座，也不能再分配参会人。`,
    positiveText: "停用该座位",
    negativeText: "取消",
    onPositiveClick: applyToggle
  });
}

function autoAssign() {
  autoAssigning.value = true;
  void store
    .autoAssignSeats()
    .then(() => {
      message.success("自动排座完成");
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "自动排座失败");
    })
    .finally(() => {
      autoAssigning.value = false;
    });
}

function addLayoutArea() {
  const index = layoutDrafts.value.length;
  layoutDrafts.value.push({
    uid: `new-${Date.now()}-${index}`,
    originalAreaName: undefined,
    areaName: nextAreaName(),
    rowCount: 8,
    seatsPerRow: 10,
    defaultSeatType: "NORMAL",
    color: areaPalette[index % areaPalette.length],
    persisted: false
  });
}

function requestRemoveLayoutArea(draft: LayoutAreaDraft) {
  if (!draft.persisted) {
    layoutDrafts.value = layoutDrafts.value.filter((item) => item.uid !== draft.uid);
    return;
  }

  const lookupName = areaLookupName(draft);
  const occupiedSeats = seatsForDraft(draft).filter((seat) => seat.attendeeId || seat.status === "ASSIGNED" || seat.status === "CHECKED_IN");
  if (occupiedSeats.length) {
    dialog.warning({
      title: "该区域已有人员入座",
      content: `${lookupName} 当前有 ${occupiedSeats.length} 个已分配或已签到座位。请先解除座位分配后再删除该区域。`,
      positiveText: "知道了"
    });
    return;
  }

  dialog.warning({
    title: `删除 ${lookupName}`,
    content: `该区域暂无人员入座。删除后会移除 ${layoutAreaSeatCount(draft)} 个座位，此操作会写入当前会议数据。`,
    positiveText: "删除区域",
    negativeText: "取消",
    onPositiveClick: () => {
      deletingAreaUid.value = draft.uid;
      void store
        .deleteSeatArea(lookupName)
        .then(() => {
          message.success(`已删除 ${lookupName}`);
          if (activeArea.value === lookupName) activeArea.value = "ALL";
          syncLayoutDraftsFromSeats();
          void nextTick(fitSeatBoardToWidth);
        })
        .catch((error) => {
          message.error(extractErrorMessage(error, "区域删除失败"));
        })
        .finally(() => {
          deletingAreaUid.value = null;
        });
    }
  });
}

function layoutAreaSeatCount(draft: LayoutAreaDraft) {
  return Math.max(1, Math.floor(draft.rowCount || 1)) * Math.max(1, Math.floor(draft.seatsPerRow || 1));
}

function seatsForArea(areaName: string) {
  return store.currentSeats.filter((seat) => seat.areaName === areaName);
}

function areaLookupName(draft: LayoutAreaDraft) {
  /*
   * 已保存区域允许改名。保存前，页面上的输入值已经是“新名称”，
   * 但占用统计、缩容保护和删除确认都必须继续按原区域名查当前座位。
   */
  return draft.originalAreaName?.trim() || draft.areaName.trim();
}

function seatsForDraft(draft: LayoutAreaDraft) {
  return seatsForArea(areaLookupName(draft));
}

function isProtectedSeat(seat: Seat) {
  return Boolean(seat.locked || seat.attendeeId || seat.status === "ASSIGNED" || seat.status === "CHECKED_IN");
}

function layoutAreaOccupancySummary(draft: LayoutAreaDraft) {
  if (!draft.persisted) return "新区域";
  const areaSeats = seatsForDraft(draft);
  const assigned = areaSeats.filter((seat) => seat.status === "ASSIGNED" || seat.status === "CHECKED_IN").length;
  const locked = areaSeats.filter((seat) => seat.locked).length;
  if (!assigned && !locked) return "未入座";
  return `${assigned} 已入座${locked ? ` · ${locked} 锁定` : ""}`;
}

function seatsRemovedByDraft(draft: LayoutAreaDraft) {
  if (!draft.persisted) return [];
  const nextRows = Math.max(1, Math.floor(draft.rowCount || 1));
  const nextSeatsPerRow = Math.max(1, Math.floor(draft.seatsPerRow || 1));
  return seatsForDraft(draft).filter((seat) => seat.rowNo > nextRows || seat.seatNo > nextSeatsPerRow);
}

function formatSeatList(seats: Seat[]) {
  return seats
    .slice(0, 6)
    .map((seat) => `${seat.areaName}${seat.rowNo}排${seat.seatNo}座${seat.attendeeName ? `（${seat.attendeeName}）` : seat.locked ? "（锁定）" : ""}`)
    .join("、");
}

function nextAreaName() {
  const existing = new Set(layoutDrafts.value.map((draft) => draft.areaName.trim()).filter(Boolean));
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  for (const letter of alphabet) {
    const name = `${letter}区`;
    if (!existing.has(name)) return name;
  }
  return `区域${layoutDrafts.value.length + 1}`;
}

function normalizeLayoutDrafts() {
  const normalized = layoutDrafts.value.map((draft) => ({
    ...draft,
    originalAreaName: draft.originalAreaName?.trim() || undefined,
    areaName: draft.areaName.trim(),
    rowCount: Math.max(1, Math.floor(draft.rowCount || 1)),
    seatsPerRow: Math.max(1, Math.floor(draft.seatsPerRow || 1))
  }));
  const names = normalized.map((draft) => draft.areaName);
  const duplicate = names.find((name, index) => name && names.indexOf(name) !== index);
  if (normalized.some((draft) => !draft.areaName)) {
    throw new Error("请填写所有区域名称");
  }
  if (duplicate) {
    throw new Error(`区域名称重复：${duplicate}`);
  }
  return normalized;
}

function saveLayouts() {
  let drafts: LayoutAreaDraft[];
  try {
    drafts = normalizeLayoutDrafts();
  } catch (error) {
    message.warning(error instanceof Error ? error.message : "请检查布局配置");
    return;
  }
  const protectedRemovedSeats = drafts.flatMap((draft) => seatsRemovedByDraft(draft).filter(isProtectedSeat));
  if (protectedRemovedSeats.length) {
    dialog.warning({
      title: "座位数量减少会影响已入座人员",
      content: `本次调整会删除 ${protectedRemovedSeats.length} 个已分配、已签到或锁定座位：${formatSeatList(protectedRemovedSeats)}${protectedRemovedSeats.length > 6 ? " 等" : ""}。请先解除这些座位后再调整排数或每排座位数。`,
      positiveText: "知道了"
    });
    return;
  }
  const emptyRemovedSeats = drafts.flatMap((draft) => seatsRemovedByDraft(draft));
  if (emptyRemovedSeats.length) {
    dialog.warning({
      title: "确认缩小座位数量",
      content: `本次会删除 ${emptyRemovedSeats.length} 个空座位。确认后将重新生成对应区域的座位图。`,
      positiveText: "继续生成",
      negativeText: "取消",
      onPositiveClick: () => persistLayouts(drafts)
    });
    return;
  }
  persistLayouts(drafts);
}

function persistLayouts(drafts: LayoutAreaDraft[]) {
  savingLayout.value = true;
  void Promise.all(
    drafts.map((draft, index) =>
      store.saveSeatLayout({
        originalAreaName: draft.originalAreaName,
        areaName: draft.areaName,
        rowCount: draft.rowCount,
        seatsPerRow: draft.seatsPerRow,
        sortOrder: index,
        venueName: store.currentMeeting?.venue,
        defaultSeatType: draft.defaultSeatType
      })
    )
  )
    .then(() => Promise.allSettled([store.loadSeats(store.currentMeetingId), store.loadStatistics(store.currentMeetingId)]))
    .then(() => {
      activeArea.value = "ALL";
      layoutModalVisible.value = false;
      message.success("座位图已生成");
      void nextTick(fitSeatBoardToWidth);
    })
    .catch((error) => {
      message.error(extractErrorMessage(error, "布局保存失败"));
    })
    .finally(() => {
      savingLayout.value = false;
    });
}

function moveLayoutDraft(sourceUid: string, targetUid: string) {
  if (sourceUid === targetUid) return;
  const sourceIndex = layoutDrafts.value.findIndex((draft) => draft.uid === sourceUid);
  const targetIndex = layoutDrafts.value.findIndex((draft) => draft.uid === targetUid);
  if (sourceIndex < 0 || targetIndex < 0) return;
  const next = [...layoutDrafts.value];
  const [moved] = next.splice(sourceIndex, 1);
  next.splice(targetIndex, 0, moved);
  layoutDrafts.value = next.map((draft, index) => ({ ...draft, color: areaPalette[index % areaPalette.length] }));
}

function startLayoutPointerDrag(uid: string, event: PointerEvent) {
  draggingLayoutUid.value = uid;
  layoutDragStartY.value = event.clientY;
  (event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", handleLayoutPointerMove);
  window.addEventListener("pointerup", stopLayoutPointerDrag, { once: true });
}

function handleLayoutPointerMove(event: PointerEvent) {
  const sourceUid = draggingLayoutUid.value;
  if (!sourceUid || Math.abs(event.clientY - layoutDragStartY.value) < 12) return;
  const cardElements = Array.from(document.querySelectorAll<HTMLElement>(".layout-area-card[data-layout-uid]"));
  const target = cardElements.find((element) => {
    const rect = element.getBoundingClientRect();
    return event.clientY >= rect.top && event.clientY <= rect.bottom;
  });
  const targetUid = target?.dataset.layoutUid;
  if (!targetUid || targetUid === sourceUid) return;
  moveLayoutDraft(sourceUid, targetUid);
  layoutDragStartY.value = event.clientY;
}

function stopLayoutPointerDrag() {
  window.removeEventListener("pointermove", handleLayoutPointerMove);
  draggingLayoutUid.value = null;
}

function syncLayoutDraftFromSeats() {
  syncLayoutDraftsFromSeats();
}

function syncLayoutDraftsFromSeats() {
  if (!areaGroups.value.length) {
    if (!layoutDrafts.value.length) addLayoutArea();
    return;
  }
  // 布局配置是全场级设置，不跟随页面上方的区域筛选；否则用户在 A 区时打开弹窗会误以为只有一个区域。
  layoutDrafts.value = areaGroups.value.map((area, index) => {
    const firstSeat = area.seats.find((seat) => seat.seatType !== "DISABLED") || area.seats[0];
    return {
      uid: `area-${area.name}`,
      originalAreaName: area.name,
      areaName: area.name,
      rowCount: area.rowCount,
      seatsPerRow: area.seatsPerRow,
      defaultSeatType: (firstSeat?.seatType === "DISABLED" ? "NORMAL" : firstSeat?.seatType || "NORMAL") as LayoutAreaDraft["defaultSeatType"],
      color: areaPalette[index % areaPalette.length],
      persisted: true
    };
  });
}
</script>

<style scoped>
.seating-page {
  color: #0f172a;
}

.seat-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.seat-stat-card {
  min-height: 92px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 4px;
}

.seat-stat-value {
  font-size: 28px;
  line-height: 34px;
  font-weight: 800;
}

.seat-stat-value.neutral { color: #64748b; }
.seat-stat-value.blue { color: #3b82f6; }
.seat-stat-value.green { color: #10b981; }
.seat-stat-value.success { color: #16a34a; }
.seat-stat-value.danger { color: #ef4444; }

.seat-stat-label {
  color: #8ba0bb;
  font-size: 13px;
  font-weight: 650;
}

.seat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.seat-toolbar-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 16px;
}

.seat-label-mode {
  flex-shrink: 0;
}

.seat-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  color: #8ba0bb;
  font-size: 14px;
  font-weight: 650;
}

.seat-legend i {
  width: 14px;
  height: 14px;
  display: inline-block;
  margin-right: 6px;
  vertical-align: -2px;
  border-radius: 4px;
}

.seat-legend .empty { background: #e8eef6; }
.seat-legend .assigned { background: #93c5fd; }
.seat-legend .checked-in { background: #86efac; }
.seat-legend .disabled { background: #fca5a5; }

.seat-board {
  min-height: 560px;
  position: relative;
  padding: 36px 32px 44px;
  overflow: auto;
}

.seat-board-controls {
  position: sticky;
  top: 0;
  right: 0;
  z-index: 2;
  width: max-content;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  margin-bottom: 18px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.zoom-reset {
  min-width: 52px;
  color: #64748b;
  font-weight: 800;
}

.seat-board-viewport {
  min-width: max-content;
  display: grid;
  justify-content: center;
  justify-items: center;
}

.stage-pill {
  width: min(var(--stage-width), 70vw);
  height: var(--stage-height);
  display: grid;
  place-items: center;
  margin: 0 auto 34px;
  border-radius: 12px;
  color: #64748b;
  background: #f1f5f9;
  font-size: var(--stage-font-size);
  font-weight: 750;
}

.seat-areas {
  display: grid;
  gap: 32px;
}

.seat-area-block {
  display: grid;
  justify-items: center;
}

.seat-grid-shell {
  display: grid;
  gap: var(--seat-gap);
  align-items: center;
  justify-content: center;
}

.row-index {
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 750;
  text-align: center;
}

.seat-cell {
  width: var(--seat-width);
  min-width: var(--seat-width);
  max-width: var(--seat-width);
  height: var(--seat-height);
  min-height: var(--seat-height);
  max-height: var(--seat-height);
  box-sizing: border-box;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  padding: 0;
  display: grid;
  place-items: center;
  background: #fff;
  color: #cbd5e1;
  font-size: var(--seat-font-size);
  line-height: 1;
  font-weight: 800;
  cursor: pointer;
  transition: transform 120ms ease, box-shadow 120ms ease, border-color 120ms ease;
}

.seat-cell span {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seat-cell .name-label {
  padding: 0 6px;
  font-size: max(11px, calc(var(--seat-font-size) - 2px));
  line-height: 1;
  letter-spacing: 0;
  white-space: nowrap;
  word-break: keep-all;
}

.seat-cell:hover {
  transform: translateY(-1px);
  border-color: #93c5fd;
  box-shadow: 0 8px 18px rgba(59, 130, 246, 0.16);
}

.seat-cell.assigned {
  color: #3b82f6;
  border-color: #93c5fd;
  background: #eff6ff;
}

.seat-cell.checked-in {
  color: #16a34a;
  border-color: #86efac;
  background: #f0fdf4;
}

.seat-cell.disabled {
  color: #ef4444;
  border-color: #fca5a5;
  background: #fff5f5;
}

.seat-cell.disabled:hover {
  border-color: #ef4444;
  box-shadow: 0 8px 18px rgba(239, 68, 68, 0.14);
}

.seat-cell.locked {
  border-style: dashed;
}

.seat-detail-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 14px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.detail-label {
  display: block;
  margin-bottom: 4px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 650;
}

.current-assignee {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  margin-bottom: 14px;
  border: 1px solid var(--accent-border);
  border-radius: 8px;
  background: var(--accent-soft);
}

.assignee-avatar {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  color: var(--accent-pressed);
  background: #fff;
  border: 1px solid var(--accent-border);
  font-weight: 800;
}

.current-assignee p,
.candidate-summary {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 1180px) {
  .seat-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .seat-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .seat-stat-card {
    min-height: 78px;
  }

  .seat-stat-value {
    font-size: 24px;
    line-height: 30px;
  }

  .seat-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .seat-toolbar :deep(.n-button-group) {
    width: 100%;
    display: flex;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .seat-toolbar :deep(.n-button-group .n-button) {
    flex: 0 0 auto;
  }

  .seat-toolbar-right {
    width: 100%;
    justify-content: flex-start;
    gap: 10px;
  }

  .seat-label-mode {
    max-width: 100%;
    overflow-x: auto;
  }

  .seat-legend {
    gap: 8px 12px;
    font-size: 12px;
  }

  .seat-board {
    min-height: 430px;
    padding: 18px 12px 28px;
    overscroll-behavior: contain;
  }

  .seat-board-controls {
    margin-bottom: 12px;
  }

  .stage-pill {
    width: min(var(--stage-width), 240px);
    margin-bottom: 24px;
  }

  .seat-detail-card {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.layout-modal {
  width: min(520px, calc(100vw - 28px));
  border-radius: 16px;
}

.layout-modal .n-card-header {
  padding: 24px 28px 10px;
}

.layout-modal .n-card__content {
  padding: 0 28px 24px;
}

.layout-modal-title {
  color: #26364d;
  font-size: 24px;
  font-weight: 900;
}

.layout-config {
  display: grid;
  gap: 16px;
}

.layout-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  color: #64748b;
  font-size: 17px;
  font-weight: 750;
}

.add-area-button {
  height: 44px;
  padding: 0 18px;
  background: var(--accent-soft);
  color: var(--accent-pressed);
  font-size: 16px;
  font-weight: 850;
}

.layout-area-list {
  max-height: min(56vh, 560px);
  display: grid;
  gap: 12px;
  overflow: auto;
  padding-right: 4px;
}

.layout-area-card {
  padding: 16px 18px;
  border: 1px solid #dce4f0;
  border-radius: 14px;
  background: #fff;
  transition: border-color 120ms ease, box-shadow 120ms ease, opacity 120ms ease;
}

.layout-area-card.dragging {
  opacity: 0.62;
}

.layout-area-card.drop-target:hover {
  border-color: var(--accent-border);
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.12);
}

.layout-area-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.layout-drag-handle {
  width: 28px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 0;
  border-radius: 8px;
  color: #94a3b8;
  background: transparent;
  cursor: grab;
}

.layout-drag-handle:active {
  cursor: grabbing;
}

.layout-drag-handle:hover {
  color: var(--accent-pressed);
  background: var(--accent-soft);
}

.layout-area-main {
  min-width: 0;
  display: grid;
  gap: 4px;
  flex: 1;
}

.layout-area-name {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.layout-area-dot {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  border-radius: 999px;
}

.layout-area-name-input {
  flex: 1;
}

.layout-area-name-input .n-input {
  --n-height: 38px !important;
  --n-border-radius: 10px !important;
  --n-box-shadow-focus: none !important;
}

.layout-area-name-input .n-input__input-el {
  color: #26364d;
  font-size: 18px;
  font-weight: 900;
}

.layout-area-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-left: 24px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 750;
}

.layout-delete-button {
  color: #94a3b8;
}

.layout-delete-button:hover {
  color: #ef4444;
  background: #fef2f2;
}

.layout-area-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.layout-area-fields .n-form-item-label,
.seat-type-label {
  color: #8fa0b9;
  font-size: 13px;
  font-weight: 850;
}

.layout-area-fields .n-input-number {
  width: 100%;
}

.layout-area-fields .n-input-number .n-input {
  --n-height: 44px !important;
  --n-border-radius: 10px !important;
}

.layout-area-fields .n-input__input-el {
  color: #26364d;
  font-size: 16px;
  font-weight: 750;
}

.seat-type-selector {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.seat-type-chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.seat-type-chip {
  height: 34px;
  padding: 0 14px;
  border: 1px solid #d7dee9;
  border-radius: 999px;
  color: #8fa0b9;
  background: #fff;
  font-size: 14px;
  font-weight: 850;
  cursor: pointer;
  transition: color 120ms ease, background 120ms ease, border-color 120ms ease;
}

.seat-type-chip.active {
  border-color: var(--accent-border);
  color: var(--accent-pressed);
  background: var(--accent-soft);
}

.seat-type-chip:hover {
  border-color: var(--accent-border);
  color: var(--accent-pressed);
}

.layout-area-total {
  margin-top: 12px;
  color: #8fa0b9;
  font-size: 14px;
  font-weight: 800;
}

.generate-layout-button {
  height: 52px;
  border-radius: 14px;
  font-size: 17px;
  font-weight: 900;
}

.seat-assign-modal {
  width: min(480px, calc(100vw - 28px));
  border-radius: 14px;
}

.seat-assign-modal .n-card-header {
  padding: 18px 22px 8px;
}

.seat-assign-modal .n-card__content {
  padding: 14px 22px 8px;
}

.seat-assign-modal .n-card__footer {
  padding: 12px 22px 20px;
}

.seat-assign-modal .seat-detail-card {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.export-modal {
  width: min(460px, calc(100vw - 28px));
  border-radius: 14px;
}

.export-modal .n-card-header {
  padding: 18px 22px 8px;
}

.export-modal .n-card__content {
  padding: 14px 22px 8px;
}

.export-modal .n-card__footer {
  padding: 12px 22px 20px;
}

.export-options {
  display: grid;
  gap: 16px;
}

.export-format-selector {
  display: grid;
  gap: 10px;
}

.export-label {
  color: #8fa0b9;
  font-size: 13px;
  font-weight: 850;
}

.export-tips {
  padding: 12px 16px;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.export-tips p {
  margin: 0;
}

.export-tips strong {
  color: #475569;
}

@media (max-width: 760px) {
  .layout-modal,
  .seat-assign-modal {
    width: min(430px, calc(100vw - 20px));
  }

  .layout-area-fields,
  .seat-assign-modal .seat-detail-card {
    grid-template-columns: 1fr;
  }
}
</style>
