<template>
  <n-modal
    :show="show"
    preset="card"
    class="import-history-modal"
    style="width: min(1080px, calc(100vw - 32px))"
    title="导入记录"
    @update:show="emit('update:show', $event)"
  >
    <div class="history-toolbar">
      <n-tabs v-if="typeOptions.length > 1" v-model:value="selectedType" type="segment" size="small">
        <n-tab-pane v-for="option in typeOptions" :key="option.value" :name="option.value" :tab="option.label" />
      </n-tabs>
      <span v-else class="history-type-label">{{ typeOptions[0]?.label || "导入" }}</span>
      <n-button
        quaternary
        circle
        :loading="loading"
        :disabled="!meetingId"
        title="刷新导入记录"
        aria-label="刷新导入记录"
        @click="loadHistory"
      >
        <template #icon><n-icon :component="RefreshOutline" /></template>
      </n-button>
    </div>

    <n-alert v-if="loadError" type="error" :show-icon="false" class="history-error">
      <div class="history-error-content">
        <span>{{ loadError }}</span>
        <n-button size="small" secondary @click="loadHistory">重试</n-button>
      </div>
    </n-alert>

    <n-spin :show="loading">
      <n-data-table
        v-if="!isMobile"
        :columns="columns"
        :data="visibleTasks"
        :bordered="false"
        :scroll-x="960"
        :row-key="(row: ImportTaskDto) => row.id"
      />

      <div v-else class="mobile-history-list">
        <article v-for="task in visibleTasks" :key="task.id" class="mobile-history-row">
          <div class="mobile-history-head">
            <div>
              <strong :title="task.originalFilename">{{ task.originalFilename || `任务 #${task.id}` }}</strong>
              <span>{{ typeLabel(task.type) }} · {{ formatDateTimeLabel(task.createdAt) }}</span>
            </div>
            <n-tag :type="statusTagType(task.status)" :bordered="false">{{ importTaskStatusText(task.status) }}</n-tag>
          </div>
          <dl class="mobile-history-fields">
            <div><dt>导入结果</dt><dd>{{ resultText(task) }}</dd></div>
            <div><dt>说明</dt><dd>{{ task.message || "-" }}</dd></div>
          </dl>
          <div v-if="task.failureFileAvailable" class="mobile-history-actions">
            <n-button
              size="small"
              secondary
              :loading="downloadingTaskId === task.id"
              :disabled="Boolean(downloadingTaskId)"
              @click="downloadFailures(task)"
            >
              下载失败明细 XLSX
            </n-button>
          </div>
        </article>
        <n-empty v-if="!loading && !visibleTasks.length && !loadError" description="暂无导入记录" />
      </div>
    </n-spin>

    <n-pagination
      v-if="filteredTasks.length > 0"
      v-model:page="page"
      v-model:page-size="pageSize"
      class="history-pagination"
      :page-sizes="PAGINATION_PAGE_SIZES"
      :item-count="filteredTasks.length"
      :prefix="paginationSummary"
      show-size-picker
      show-quick-jumper
      aria-label="导入记录分页"
    />
  </n-modal>
</template>

<script setup lang="ts">
import type { DataTableColumns } from "naive-ui";
import { NButton, NDataTable, NPagination, NTabPane, NTabs, NTag, useMessage } from "naive-ui";
import { RefreshOutline } from "@vicons/ionicons5";
import { computed, h, onBeforeUnmount, ref, watch } from "vue";
import { isRequestCanceled } from "@/api/http";
import { useIsMobile } from "@/composables/useResponsive";
import { useMeetingStore, type ImportTaskDto, type ImportTaskType } from "@/stores/meeting";
import { importTaskStatusText } from "@/utils/fileTasks";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";
import { PAGINATION_PAGE_SIZES, paginationSummary } from "@/utils/pagination";
import { LatestRequestRegistry } from "@/utils/requestControl";

interface ImportTypeOption {
  label: string;
  value: ImportTaskType;
}

const props = defineProps<{
  show: boolean;
  meetingId: string;
  typeOptions: readonly ImportTypeOption[];
}>();

const emit = defineEmits<{
  "update:show": [show: boolean];
}>();

const store = useMeetingStore();
const message = useMessage();
const isMobile = useIsMobile();
const selectedType = ref<ImportTaskType>(props.typeOptions[0]?.value || "attendees");
const tasks = ref<ImportTaskDto[]>([]);
const loading = ref(false);
const loadError = ref("");
const downloadingTaskId = ref("");
const page = ref(1);
const pageSize = ref(10);
const requests = new LatestRequestRegistry();
let failureDownloadController: AbortController | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
const activeStatuses = new Set(["PENDING", "PREFLIGHTING", "PREVIEW_READY", "RUNNING"]);

const allowedTypes = computed(() => new Set(props.typeOptions.map((option) => option.value)));
const filteredTasks = computed(() => tasks.value.filter((task) => task.type === selectedType.value));
const pageCount = computed(() => Math.max(1, Math.ceil(filteredTasks.value.length / pageSize.value)));
const visibleTasks = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return filteredTasks.value.slice(start, start + pageSize.value);
});

const columns: DataTableColumns<ImportTaskDto> = [
  {
    title: "文件名",
    key: "originalFilename",
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (task) => task.originalFilename || `任务 #${task.id}`
  },
  { title: "类型", key: "type", minWidth: 110, render: (task) => typeLabel(task.type) },
  {
    title: "状态",
    key: "status",
    minWidth: 150,
    render: (task) => h(NTag, { type: statusTagType(task.status), bordered: false }, { default: () => importTaskStatusText(task.status) })
  },
  { title: "导入结果", key: "result", minWidth: 190, render: resultText },
  { title: "创建时间", key: "createdAt", minWidth: 170, render: (task) => formatDateTimeLabel(task.createdAt) },
  {
    title: "说明",
    key: "message",
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (task) => task.message || "-"
  },
  {
    title: "操作",
    key: "actions",
    width: 160,
    fixed: "right",
    render: (task) => task.failureFileAvailable
      ? h(NButton, {
          text: true,
          type: "primary",
          loading: downloadingTaskId.value === task.id,
          disabled: Boolean(downloadingTaskId.value),
          onClick: () => downloadFailures(task)
        }, { default: () => "失败明细 XLSX" })
      : "-"
  }
];

watch(
  () => [props.show, props.meetingId] as const,
  ([show]) => {
    resetHistory();
    if (show && props.meetingId) void loadHistory();
  }
);

watch(
  () => props.typeOptions.map((option) => option.value).join(","),
  () => {
    if (!allowedTypes.value.has(selectedType.value)) selectedType.value = props.typeOptions[0]?.value || "attendees";
  },
  { immediate: true }
);

watch(selectedType, () => {
  page.value = 1;
  scheduleRefreshIfNeeded();
});

watch(page, scheduleRefreshIfNeeded);

watch(pageSize, () => {
  page.value = 1;
  scheduleRefreshIfNeeded();
});

watch(pageCount, (count) => {
  if (page.value > count) page.value = count;
});

async function loadHistory() {
  const meetingId = props.meetingId;
  if (!props.show || !meetingId) return;
  stopScheduledRefresh();
  const request = requests.begin("import-history");
  loading.value = true;
  loadError.value = "";
  try {
    const first = await store.loadImportTasks({ meetingId, page: 0, size: 200, signal: request.signal });
    assertCurrentRequest(request, meetingId);
    const collected = [...first.content];
    publishTasks(collected, request, meetingId);

    for (let backendPage = 1; backendPage < first.totalPages; backendPage += 1) {
      const next = await store.loadImportTasks({ meetingId, page: backendPage, size: 200, signal: request.signal });
      assertCurrentRequest(request, meetingId);
      collected.push(...next.content);
      publishTasks(collected, request, meetingId);
    }
    scheduleRefreshIfNeeded();
  } catch (error) {
    if (isRequestCanceled(error) || !requests.isCurrent(request)) return;
    loadError.value = error instanceof Error ? error.message : "导入任务加载失败";
  } finally {
    if (requests.isCurrent(request)) loading.value = false;
    requests.finish(request);
  }
}

function publishTasks(collected: ImportTaskDto[], request: ReturnType<LatestRequestRegistry["begin"]>, meetingId: string) {
  assertCurrentRequest(request, meetingId);
  tasks.value = collected.filter((task) => allowedTypes.value.has(task.type));
}

function assertCurrentRequest(request: ReturnType<LatestRequestRegistry["begin"]>, meetingId: string) {
  if (!requests.isCurrent(request) || !props.show || props.meetingId !== meetingId) {
    throw new DOMException("导入记录请求已取消", "AbortError");
  }
}

async function downloadFailures(task: ImportTaskDto) {
  if (!task.failureFileAvailable || downloadingTaskId.value) return;
  const meetingId = props.meetingId;
  failureDownloadController?.abort();
  const controller = new AbortController();
  failureDownloadController = controller;
  downloadingTaskId.value = task.id;
  try {
    await store.downloadImportFailures(task.id, { meetingId, signal: controller.signal });
    if (controller.signal.aborted || meetingId !== props.meetingId || !props.show) return;
    message.success("失败明细 XLSX 已开始下载");
  } catch (error) {
    if (isRequestCanceled(error) || controller.signal.aborted || meetingId !== props.meetingId) return;
    message.error(error instanceof Error ? error.message : "失败明细下载失败");
  } finally {
    if (failureDownloadController === controller) {
      failureDownloadController = null;
      downloadingTaskId.value = "";
    }
  }
}

function scheduleRefreshIfNeeded() {
  stopScheduledRefresh();
  if (!props.show || !visibleTasks.value.some((task) => activeStatuses.has(task.status))) return;
  refreshTimer = globalThis.setTimeout(() => void refreshVisibleActiveTasks(), 2500);
}

async function refreshVisibleActiveTasks() {
  const meetingId = props.meetingId;
  const activeTasks = visibleTasks.value.filter((task) => activeStatuses.has(task.status));
  if (!props.show || !meetingId || !activeTasks.length) return;

  const request = requests.begin("import-history");
  try {
    const settled = await Promise.allSettled(
      activeTasks.map((task) => store.loadImportTask(task.id, { meetingId, signal: request.signal }))
    );
    assertCurrentRequest(request, meetingId);
    const updatesById = new Map(
      settled.flatMap((result) => result.status === "fulfilled" ? [[result.value.id, result.value] as const] : [])
    );
    tasks.value = tasks.value.map((task) => updatesById.get(task.id) || task);
    const failed = settled.find((result) => result.status === "rejected");
    loadError.value = failed?.status === "rejected"
      ? (failed.reason instanceof Error ? failed.reason.message : "部分导入任务状态刷新失败")
      : "";
  } catch (error) {
    if (isRequestCanceled(error) || !requests.isCurrent(request)) return;
    loadError.value = error instanceof Error ? error.message : "导入任务状态刷新失败";
  } finally {
    if (requests.isCurrent(request)) {
      requests.finish(request);
      scheduleRefreshIfNeeded();
    }
  }
}

function stopScheduledRefresh() {
  if (!refreshTimer) return;
  globalThis.clearTimeout(refreshTimer);
  refreshTimer = null;
}

function resetHistory() {
  stopScheduledRefresh();
  requests.reset();
  failureDownloadController?.abort();
  failureDownloadController = null;
  tasks.value = [];
  loading.value = false;
  loadError.value = "";
  downloadingTaskId.value = "";
  page.value = 1;
}

function typeLabel(type: ImportTaskType) {
  return props.typeOptions.find((option) => option.value === type)?.label || type;
}

function resultText(task: ImportTaskDto) {
  if (activeStatuses.has(task.status)) {
    return `已处理 ${task.processedRows} / ${task.totalRows || "-"}`;
  }
  return `成功 ${task.importedRows}，失败 ${task.failedRows}，共 ${task.totalRows}`;
}

function statusTagType(status: string) {
  if (status === "COMPLETED") return "success" as const;
  if (activeStatuses.has(status)) return "warning" as const;
  if (["FAILED", "EXPIRED"].includes(status)) return "error" as const;
  return "default" as const;
}

onBeforeUnmount(resetHistory);
</script>

<style scoped>
.history-toolbar,
.history-error-content,
.mobile-history-head,
.mobile-history-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.history-toolbar {
  min-height: 34px;
  margin-bottom: 14px;
}

.history-toolbar :deep(.n-tabs) {
  max-width: 420px;
}

.history-type-label {
  font-weight: 600;
}

.history-error {
  margin-bottom: 12px;
}

.history-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.mobile-history-list {
  display: grid;
}

.mobile-history-row {
  min-width: 0;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color);
}

.mobile-history-row:first-child {
  padding-top: 0;
}

.mobile-history-head {
  align-items: flex-start;
}

.mobile-history-head > div {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.mobile-history-head strong,
.mobile-history-fields dd {
  overflow-wrap: anywhere;
}

.mobile-history-head span,
.mobile-history-fields dt {
  color: var(--text-secondary);
  font-size: 12px;
}

.mobile-history-fields {
  margin: 12px 0 0;
  display: grid;
  gap: 8px;
}

.mobile-history-fields > div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
}

.mobile-history-fields dt,
.mobile-history-fields dd {
  margin: 0;
}

.mobile-history-actions {
  margin-top: 12px;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .history-toolbar :deep(.n-tabs) {
    min-width: 0;
    flex: 1;
  }

  .history-pagination {
    justify-content: center;
  }
}
</style>
