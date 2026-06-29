<template>
  <div class="export-page">
    <page-header title="数据导出">
      <div class="export-actions">
        <n-button secondary @click="createTask">创建记录</n-button>
        <n-button type="primary" @click="downloadSelected">下载</n-button>
      </div>
    </page-header>

    <section class="surface export-toolbar">
      <div class="field-group">
        <span>导出类型</span>
        <n-select v-model:value="exportType" :options="exportOptions" />
      </div>
      <div class="field-group">
        <span>文件格式</span>
        <n-radio-group v-model:value="exportFormat" size="small">
          <n-radio-button value="csv">CSV</n-radio-button>
          <n-radio-button value="xlsx">XLSX</n-radio-button>
        </n-radio-group>
      </div>
      <div v-if="isStatisticsExport" class="field-group range-group">
        <span>统计范围</span>
        <n-radio-group v-model:value="rangeMode" size="small">
          <n-radio-button v-for="item in rangeOptions" :key="item.value" :value="item.value">{{ item.label }}</n-radio-button>
        </n-radio-group>
        <n-date-picker v-if="rangeMode === 'custom'" v-model:value="customRange" type="daterange" size="small" clearable />
      </div>
      <div class="export-hint">
        <strong>{{ activeOption?.label }}</strong>
        <span>{{ activeOption?.description }}</span>
      </div>
    </section>

    <section class="surface export-task-section">
      <n-data-table v-if="!isMobile" :columns="columns" :data="tasks" :loading="loading" :bordered="false" />
      <div v-else class="mobile-card-list export-task-cards">
        <article v-for="task in tasks" :key="task.taskId" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <strong>{{ labelByType.get(task.type) || task.type }}</strong>
              <span>任务 #{{ task.taskId }}</span>
            </div>
            <n-tag :type="statusTagType(task.status)" :bordered="false">{{ statusLabel(task.status) }}</n-tag>
          </div>
          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>申请人</span>
              <strong>{{ task.requestedBy || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>创建时间</span>
              <strong>{{ formatDateTimeLabel(task.createdAt) }}</strong>
            </div>
            <div class="mobile-field">
              <span>说明</span>
              <strong>{{ task.message || "-" }}</strong>
            </div>
          </div>
          <div class="mobile-card-actions">
            <n-button size="small" type="primary" secondary :disabled="!canDownload(task)" @click="download(task.type)">下载</n-button>
          </div>
        </article>
        <n-empty v-if="!loading && !tasks.length" description="暂无导出记录" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from "vue";
import { NButton, NDataTable, NDatePicker, NRadioButton, NRadioGroup, NSelect, NSpace, NTag, useMessage, type SelectOption } from "naive-ui";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import { useMeetingStore, type ExportTaskDto } from "@/stores/meeting";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";

type ExportFormat = "csv" | "xlsx";
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
const exportType = ref("attendees");
const exportFormat = ref<ExportFormat>("csv");
const rangeMode = ref<RangeMode>("7d");
const customRange = ref<[number, number] | null>(null);
const tasks = ref<ExportTaskDto[]>([]);
const localTasks = ref<ExportTaskDto[]>([]);
const loading = ref(false);

const exportOptions: ExportOption[] = [
  { label: "参会名单", value: "attendees", description: "全部报名人员，包含审核、签到、住宿和动态报名字段。" },
  { label: "审核通过名单", value: "approved", description: "仅导出审核通过人员，适合会前确认名单。" },
  { label: "座位表", value: "seats", description: "座位库存、锁定状态和已分配人员。" },
  { label: "住宿安排", value: "rooms", description: "已分房人员、酒店房型和入住备注。" },
  { label: "房间库存", value: "room-inventory", description: "全部房间、房型、容量和房间状态。" },
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
const activeRange = computed(() => {
  if (rangeMode.value === "custom" && customRange.value) {
    return {
      startDate: formatTimestampDate(customRange.value[0]),
      endDate: formatTimestampDate(customRange.value[1])
    };
  }
  const today = new Date();
  const days = rangeMode.value === "today" ? 0 : rangeMode.value === "30d" ? 29 : 6;
  const start = new Date(today);
  start.setDate(today.getDate() - days);
  return {
    startDate: formatLocalDate(start),
    endDate: formatLocalDate(today)
  };
});

onMounted(() => {
  void syncExportContext();
});

watch(routeMeetingId, () => {
  localTasks.value = [];
  void syncExportContext();
});

const columns = [
  { title: "任务 ID", key: "taskId", minWidth: 90 },
  { title: "导出类型", key: "type", minWidth: 130, render: (row: ExportTaskDto) => labelByType.value.get(row.type) || row.type },
  {
    title: "状态",
    key: "status",
    minWidth: 100,
    render: (row: ExportTaskDto) =>
      h(NTag, { type: statusTagType(row.status), bordered: false }, { default: () => statusLabel(row.status) })
  },
  { title: "申请人", key: "requestedBy", minWidth: 120, render: (row: ExportTaskDto) => row.requestedBy || "-" },
  { title: "创建时间", key: "createdAt", minWidth: 170, render: (row: ExportTaskDto) => formatDateTimeLabel(row.createdAt) },
  { title: "说明", key: "message", minWidth: 180, render: (row: ExportTaskDto) => row.message || "-" },
  {
    title: "操作",
    key: "actions",
    width: 120,
    render: (row: ExportTaskDto) =>
      h(
        NSpace,
        { size: "small" },
        {
          default: () => [
            h(
              NButton,
              { text: true, type: "primary", disabled: !canDownload(row), onClick: () => download(row.type) },
              { default: () => "下载" }
            )
          ]
        }
      )
  }
];

function loadTasks() {
  loading.value = true;
  return store.loadExportTasks()
    .then((remoteTasks) => {
      tasks.value = remoteTasks ?? localTasks.value;
    })
    .catch(() => {
      tasks.value = localTasks.value;
    })
    .finally(() => {
      loading.value = false;
    });
}

async function syncExportContext() {
  await syncMeetingFromRoute();
  await loadTasks();
}

function createTask() {
  void store.createExportTask(exportType.value).then((task) => {
    localTasks.value = [task, ...localTasks.value.filter((item) => item.taskId !== task.taskId)];
    tasks.value = localTasks.value;
    message.success("导出记录已创建，可直接下载");
    void loadTasks();
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "导出记录创建失败");
  });
}

function downloadSelected() {
  void download(exportType.value);
}

function download(type: string) {
  const options = downloadOptions(type);
  return store.downloadExport(type, options).then(() => {
    message.success(`${options.format?.toUpperCase()} 已开始下载`);
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "下载失败，请确认后端服务已启动");
  });
}

function downloadOptions(type: string) {
  if (type !== "statistics") {
    return { format: exportFormat.value };
  }
  return {
    format: exportFormat.value,
    startDate: activeRange.value.startDate,
    endDate: activeRange.value.endDate
  };
}

function canDownload(row: ExportTaskDto) {
  return row.status !== "FAILED" && row.status !== "REJECTED";
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: "已记录",
    RUNNING: "生成中",
    APPROVED: "已完成",
    REJECTED: "不可用",
    COMPLETED: "已完成",
    FAILED: "失败",
    LOCAL: "本地"
  };
  return labels[status] || status;
}

function statusTagType(status: string) {
  if (status === "APPROVED" || status === "COMPLETED" || status === "LOCAL") return "success";
  if (status === "PENDING" || status === "RUNNING") return "warning";
  if (status === "REJECTED" || status === "FAILED") return "error";
  return "default";
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
</script>

<style scoped>
.export-page {
  display: grid;
  gap: 16px;
}

.export-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.export-toolbar {
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(180px, 260px);
  gap: 16px;
  align-items: end;
}

.field-group {
  display: grid;
  gap: 8px;
}

.field-group > span {
  color: var(--text-secondary);
  font-size: 12px;
}

.range-group {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.range-group > span {
  width: 100%;
}

.export-hint {
  grid-column: 1 / -1;
  display: grid;
  gap: 4px;
  padding-top: 4px;
}

.export-hint strong {
  color: var(--text-primary);
}

.export-hint span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 760px) {
  .export-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
