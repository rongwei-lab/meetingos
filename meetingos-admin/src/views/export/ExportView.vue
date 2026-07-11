<template>
  <div class="export-page">
    <page-header title="数据导出">
      <n-button
        type="primary"
        :loading="exporting"
        :disabled="exporting || !canCreateExport"
        aria-label="生成并下载 XLSX 文件"
        @click="createAndDownload"
      >
        生成并下载 XLSX
      </n-button>
    </page-header>

    <section class="surface export-toolbar" aria-label="导出参数">
      <div class="field-group">
        <label for="export-type-select">导出类型</label>
        <n-select
          id="export-type-select"
          v-model:value="exportType"
          :options="exportOptions"
          aria-label="导出类型"
        />
      </div>
      <div class="field-group">
        <span id="export-format-label">文件格式</span>
        <div class="fixed-format" aria-labelledby="export-format-label">
          <n-tag type="info" :bordered="false">XLSX</n-tag>
          <span>适合 Excel 打开，手机号和证件号按文本导出</span>
        </div>
      </div>
      <div v-if="isStatisticsExport" class="field-group range-group">
        <span id="statistics-range-label">统计范围</span>
        <n-radio-group
          v-model:value="rangeMode"
          size="small"
          aria-labelledby="statistics-range-label"
          name="statistics-range"
        >
          <n-radio-button v-for="item in rangeOptions" :key="item.value" :value="item.value">{{ item.label }}</n-radio-button>
        </n-radio-group>
        <n-date-picker
          v-if="rangeMode === 'custom'"
          v-model:value="customRange"
          type="daterange"
          size="small"
          clearable
          aria-label="自定义统计起止日期"
        />
        <span v-if="customRangeMissing" class="field-error" role="alert">请选择起止日期后再生成导出文件</span>
      </div>
      <div class="export-hint">
        <strong>{{ activeOption?.label }}</strong>
        <span>{{ activeOption?.description }}</span>
      </div>
    </section>

    <n-alert v-if="currentTask && exporting" type="info" :show-icon="false" aria-live="polite">
      <div class="active-task-copy">
        <strong>{{ statusLabel(currentTask.status) }}</strong>
        <span>{{ currentTask.message || "服务器正在生成固定版本的 XLSX 文件" }}</span>
      </div>
      <n-progress
        type="line"
        :percentage="currentTask.progress || 0"
        :height="8"
        processing
        style="margin-top: 10px"
      />
    </n-alert>

    <section class="surface export-task-section" aria-label="导出记录">
      <n-alert v-if="loadError" type="error" :show-icon="false" class="load-error">
        <div class="load-error-content">
          <span>{{ loadError }}</span>
          <n-button size="small" secondary @click="loadTasks">重试</n-button>
        </div>
      </n-alert>

      <n-spin :show="loading">
        <n-data-table
          v-if="!isMobile"
          :columns="columns"
          :data="tasks"
          :bordered="false"
          :scroll-x="980"
          :row-key="(row: ExportTaskDto) => row.taskId"
        />
        <div v-else class="mobile-card-list export-task-cards">
          <article v-for="taskItem in tasks" :key="taskItem.taskId" class="mobile-data-card">
            <div class="mobile-card-head">
              <div class="mobile-card-title">
                <strong>{{ labelByType.get(taskItem.type) || taskItem.type }}</strong>
                <span>任务 #{{ taskItem.taskId }}</span>
              </div>
              <n-tag :type="statusTagType(taskItem.status)" :bordered="false">{{ statusLabel(taskItem.status) }}</n-tag>
            </div>
            <div class="mobile-card-fields">
              <div class="mobile-field">
                <span>申请人</span>
                <strong>{{ taskItem.requestedBy || "-" }}</strong>
              </div>
              <div class="mobile-field">
                <span>创建时间</span>
                <strong>{{ formatDateTimeLabel(taskItem.createdAt) }}</strong>
              </div>
              <div class="mobile-field">
                <span>生成文件</span>
                <strong>{{ generatedFileLabel(taskItem) }}</strong>
              </div>
              <div class="mobile-field">
                <span>导出范围</span>
                <strong>{{ taskRangeLabel(taskItem) }}</strong>
              </div>
              <div class="mobile-field">
                <span>说明</span>
                <strong>{{ taskItem.message || "-" }}</strong>
              </div>
            </div>
            <div class="mobile-card-actions">
              <n-button
                size="small"
                type="primary"
                secondary
                :loading="downloadingTaskId === taskItem.taskId"
                :disabled="!canDownload(taskItem) || Boolean(downloadingTaskId)"
                :aria-label="`下载任务 ${taskItem.taskId} 的固定 XLSX 文件`"
                @click="downloadTask(taskItem)"
              >
                下载 XLSX
              </n-button>
            </div>
          </article>
          <n-empty v-if="!loading && !tasks.length && !loadError" description="暂无导出记录" />
        </div>
      </n-spin>

      <n-pagination
        v-if="totalElements > pageSize"
        v-model:page="page"
        class="task-pagination"
        :page-count="Math.max(1, totalPages)"
        :page-size="pageSize"
        :item-count="totalElements"
        show-quick-jumper
        aria-label="导出记录分页"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  NAlert,
  NButton,
  NDataTable,
  NDatePicker,
  NEmpty,
  NPagination,
  NProgress,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  useMessage,
  type SelectOption
} from "naive-ui";
import { isRequestCanceled } from "@/api/http";
import PageHeader from "@/components/PageHeader.vue";
import { useExportTaskDownload } from "@/composables/useExportTaskDownload";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import { useMeetingStore, type ExportTaskDto } from "@/stores/meeting";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";
import { LatestRequestRegistry } from "@/utils/requestControl";

type RangeMode = "today" | "7d" | "30d" | "custom";

interface ExportOption extends SelectOption {
  label: string;
  value: string;
  description: string;
}

const store = useMeetingStore();
const message = useMessage();
const isMobile = useIsMobile();
const { routeMeetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });
const { exporting, currentTask, runExport, cancelExport } = useExportTaskDownload();
const exportType = ref("attendees");
const rangeMode = ref<RangeMode>("7d");
const customRange = ref<[number, number] | null>(null);
const tasks = ref<ExportTaskDto[]>([]);
const loading = ref(false);
const loadError = ref("");
const downloadingTaskId = ref("");
const page = ref(1);
const pageSize = 20;
const totalElements = ref(0);
const totalPages = ref(0);
const requests = new LatestRequestRegistry();
let refreshTimer: ReturnType<typeof setTimeout> | null = null;

const exportOptions: ExportOption[] = [
  { label: "参会名单", value: "attendees", description: "全部报名人员，包含审核、签到、住宿和动态报名字段。" },
  { label: "审核通过名单", value: "approved", description: "仅导出审核通过人员，适合会前确认名单。" },
  { label: "座位表", value: "seats", description: "座位库存、锁定状态和已分配人员。" },
  { label: "住宿安排", value: "rooms", description: "已分房人员、酒店房型和入住备注。" },
  { label: "房间库存", value: "room-inventory", description: "全部房间、房型、容量和房间状态。" },
  { label: "晚宴安排", value: "dinner", description: "晚宴桌号、人员分配和现场用餐备注。" },
  { label: "签到记录", value: "checkin-records", description: "现场签到流水，包含入口、方式、结果和操作人。" },
  { label: "未签到名单", value: "no-checkin", description: "当前仍未签到人员名单。" },
  { label: "统计汇总", value: "statistics", description: "报名、座位、住宿、签到指标，以及趋势和分布数据。" }
];

const rangeOptions: Array<{ label: string; value: RangeMode }> = [
  { label: "今日", value: "today" },
  { label: "近 7 天", value: "7d" },
  { label: "近 30 天", value: "30d" },
  { label: "自定义", value: "custom" }
];

const labelByType = computed(() => new Map(exportOptions.map((item) => [item.value, item.label])));
const activeOption = computed(() => exportOptions.find((item) => item.value === exportType.value));
const isStatisticsExport = computed(() => exportType.value === "statistics");
const customRangeMissing = computed(() => isStatisticsExport.value && rangeMode.value === "custom" && !customRange.value);
const canCreateExport = computed(() => !customRangeMissing.value);
const activeRange = computed(() => {
  if (!isStatisticsExport.value) return {};
  if (rangeMode.value === "custom") {
    if (!customRange.value) return {};
    return {
      startDate: formatTimestampDate(customRange.value[0]),
      endDate: formatTimestampDate(customRange.value[1])
    };
  }
  const today = new Date();
  const days = rangeMode.value === "today" ? 0 : rangeMode.value === "30d" ? 29 : 6;
  const start = new Date(today);
  start.setDate(today.getDate() - days);
  return { startDate: formatLocalDate(start), endDate: formatLocalDate(today) };
});

const columns = [
  { title: "任务 ID", key: "taskId", minWidth: 100 },
  { title: "导出类型", key: "type", minWidth: 130, render: (row: ExportTaskDto) => labelByType.value.get(row.type) || row.type },
  {
    title: "状态",
    key: "status",
    minWidth: 100,
    render: (row: ExportTaskDto) => h(NTag, { type: statusTagType(row.status), bordered: false }, { default: () => statusLabel(row.status) })
  },
  { title: "申请人", key: "requestedBy", minWidth: 120, render: (row: ExportTaskDto) => row.requestedBy || "-" },
  { title: "创建时间", key: "createdAt", minWidth: 170, render: (row: ExportTaskDto) => formatDateTimeLabel(row.createdAt) },
  { title: "生成文件", key: "generatedFile", minWidth: 190, render: generatedFileLabel },
  { title: "导出范围", key: "range", minWidth: 180, render: taskRangeLabel },
  { title: "说明", key: "message", minWidth: 180, render: (row: ExportTaskDto) => row.message || "-" },
  {
    title: "操作",
    key: "actions",
    width: 130,
    fixed: "right" as const,
    render: (row: ExportTaskDto) =>
      h(NSpace, { size: "small" }, {
        default: () => [
          h(NButton, {
            text: true,
            type: "primary",
            loading: downloadingTaskId.value === row.taskId,
            disabled: !canDownload(row) || Boolean(downloadingTaskId.value),
            "aria-label": `下载任务 ${row.taskId} 的固定 XLSX 文件`,
            onClick: () => downloadTask(row)
          }, { default: () => "下载 XLSX" })
        ]
      })
  }
];

onMounted(() => void syncExportContext());

watch(routeMeetingId, () => {
  cancelExport();
  page.value = 1;
  tasks.value = [];
  totalElements.value = 0;
  stopScheduledRefresh();
  requests.cancel("export-task-list");
  void syncExportContext();
});

watch(page, () => void loadTasks());

async function syncExportContext() {
  await syncMeetingFromRoute();
  await loadTasks();
}

async function loadTasks() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;
  const request = requests.begin("export-task-list");
  loading.value = true;
  loadError.value = "";
  try {
    const result = await store.loadExportTasks({ meetingId, page: page.value - 1, size: pageSize, signal: request.signal });
    if (!requests.isCurrent(request) || meetingId !== (routeMeetingId.value || store.currentMeetingId)) return;
    tasks.value = result.content;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
    scheduleRefreshIfNeeded();
  } catch (error) {
    if (isRequestCanceled(error) || !requests.isCurrent(request)) return;
    loadError.value = error instanceof Error ? error.message : "导出任务加载失败";
  } finally {
    if (requests.isCurrent(request)) loading.value = false;
    requests.finish(request);
  }
}

async function createAndDownload() {
  if (customRangeMissing.value) {
    message.warning("请选择自定义统计起止日期");
    return;
  }
  try {
    await runExport(exportType.value, {
      ...activeRange.value,
      onUpdate: upsertVisibleTask
    });
    message.success("XLSX 已生成并开始下载");
    page.value = 1;
    await loadTasks();
  } catch (error) {
    if (isRequestCanceled(error)) return;
    message.error(error instanceof Error ? error.message : "导出失败");
    void loadTasks();
  }
}

async function downloadTask(task: ExportTaskDto) {
  if (!canDownload(task) || downloadingTaskId.value) return;
  downloadingTaskId.value = task.taskId;
  try {
    await store.downloadExportTask(task.taskId, {
      meetingId: routeMeetingId.value || store.currentMeetingId,
      filename: task.filename
    });
    message.success("固定版本 XLSX 已开始下载");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "下载失败");
  } finally {
    downloadingTaskId.value = "";
  }
}

function upsertVisibleTask(task: ExportTaskDto) {
  const index = tasks.value.findIndex((item) => item.taskId === task.taskId);
  if (index >= 0) tasks.value[index] = task;
  else if (page.value === 1) tasks.value = [task, ...tasks.value].slice(0, pageSize);
}

function scheduleRefreshIfNeeded() {
  stopScheduledRefresh();
  if (!tasks.value.some((task) => ["PENDING", "RUNNING"].includes(task.status))) return;
  refreshTimer = globalThis.setTimeout(() => void loadTasks(), 1200);
}

function stopScheduledRefresh() {
  if (refreshTimer) globalThis.clearTimeout(refreshTimer);
  refreshTimer = null;
}

function canDownload(row: ExportTaskDto) {
  return row.status === "COMPLETED" && row.fileAvailable === true;
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: "等待生成",
    RUNNING: "生成中",
    COMPLETED: "已完成",
    FAILED: "失败",
    EXPIRED: "已过期"
  };
  return labels[status] || status;
}

function statusTagType(status: string) {
  if (status === "COMPLETED") return "success" as const;
  if (status === "PENDING" || status === "RUNNING") return "warning" as const;
  if (status === "FAILED" || status === "EXPIRED") return "error" as const;
  return "default" as const;
}

function taskRangeLabel(task: ExportTaskDto) {
  return task.startDate && task.endDate ? `${task.startDate} 至 ${task.endDate}` : "全部数据";
}

function generatedFileLabel(task: ExportTaskDto) {
  const generatedAt = task.generatedAt ? formatDateTimeLabel(task.generatedAt) : "尚未生成";
  return task.fileSize == null ? generatedAt : `${generatedAt} · ${formatFileSize(task.fileSize)}`;
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatTimestampDate(value: number) {
  return formatLocalDate(new Date(value));
}

function formatLocalDate(value: Date) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

onBeforeUnmount(() => {
  stopScheduledRefresh();
  requests.reset();
});
</script>

<style scoped>
.export-page {
  min-width: 0;
  display: grid;
  gap: 16px;
}

.export-toolbar {
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(260px, 1fr);
  gap: 16px;
  align-items: end;
}

.field-group {
  display: grid;
  gap: 8px;
}

.field-group > label,
.field-group > span {
  color: var(--text-secondary);
  font-size: 12px;
}

.fixed-format {
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 13px;
}

.range-group {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.range-group > span:first-child {
  width: 100%;
}

.field-error {
  width: 100%;
  color: #dc2626 !important;
  font-size: 13px !important;
}

.export-hint {
  grid-column: 1 / -1;
  display: grid;
  gap: 4px;
  padding-top: 4px;
}

.export-hint span,
.active-task-copy span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.active-task-copy {
  display: grid;
  gap: 3px;
}

.export-task-section {
  min-width: 0;
  min-height: 180px;
  padding: 12px;
}

.load-error {
  margin-bottom: 12px;
}

.load-error-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .export-toolbar {
    grid-template-columns: 1fr;
  }

  .fixed-format {
    align-items: flex-start;
  }

  .task-pagination {
    justify-content: center;
  }
}
</style>
