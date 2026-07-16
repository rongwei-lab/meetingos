<template>
  <div>
    <page-header title="操作日志" />
    <n-card class="surface" :bordered="false">
      <div class="log-filter-bar" role="search" aria-label="筛选操作日志">
        <n-select
          v-model:value="filterForm.category"
          class="log-filter-category"
          clearable
          size="small"
          :options="categoryOptions"
          placeholder="全部分类"
          aria-label="操作日志分类"
        />
        <n-select
          v-model:value="filterForm.success"
          class="log-filter-success"
          clearable
          size="small"
          :options="successOptions"
          placeholder="全部结果"
          aria-label="操作日志结果"
        />
        <n-date-picker
          v-model:value="filterForm.dateRange"
          class="log-filter-date-range"
          type="daterange"
          clearable
          size="small"
          placeholder="选择日期范围"
          aria-label="操作日志日期范围"
        />
        <n-input
          v-model:value="filterForm.keyword"
          class="log-filter-keyword"
          clearable
          size="small"
          maxlength="100"
          placeholder="搜索操作人、动作、对象或备注"
          aria-label="操作日志关键词"
          @keyup.enter="applyFilters"
        />
        <div class="log-filter-actions">
          <label class="log-draft-switch">
            <n-switch v-model:value="filterForm.includeDraftSaves" size="small" aria-label="显示邀请函草稿保存" />
            <span>显示邀请函草稿保存</span>
          </label>
          <n-button class="log-filter-submit" type="primary" size="small" :loading="loading" @click="applyFilters">查询</n-button>
          <n-button class="log-filter-reset" size="small" secondary @click="resetFilters">重置</n-button>
        </div>
      </div>

      <div v-if="loadError" class="log-load-error">
        <n-alert type="warning" :show-icon="false">{{ loadError }}</n-alert>
        <n-button v-if="!accessForbidden" class="log-retry-button" size="small" secondary @click="retryLoad">重试</n-button>
      </div>
      <div class="log-summary" aria-live="polite">
        <span>共 {{ totalElements }} 条记录</span>
        <span v-if="logs.length">本页成功 {{ successCount }} 条，失败 {{ failureCount }} 条</span>
      </div>
      <n-spin :show="loading">
        <n-data-table
          v-if="!isMobile"
          :columns="columns"
          :data="logs"
          :pagination="false"
          :loading="loading"
          :scroll-x="1120"
        />
        <div v-else class="mobile-card-list log-card-list">
          <article v-for="log in logs" :key="log.id" class="mobile-data-card">
            <div class="mobile-card-head">
              <div class="mobile-card-title">
                <strong>{{ log.type }}</strong>
                <span class="log-mobile-category">分类：{{ log.category }}</span>
                <span>{{ formatDateTimeLabel(log.time) }}</span>
              </div>
              <n-tag :type="log.success ? 'success' : 'error'" size="small" :bordered="false">
                {{ log.success ? "成功" : "失败" }}
              </n-tag>
            </div>
            <div class="mobile-card-fields">
              <div class="mobile-field">
                <span>操作人</span>
                <strong>{{ log.operator || "-" }}</strong>
              </div>
              <div class="mobile-field">
                <span>对象</span>
                <strong>{{ log.target || "-" }}</strong>
              </div>
              <div class="mobile-field">
                <span>备注</span>
                <strong>{{ log.remark || "-" }}</strong>
              </div>
            </div>
          </article>
          <n-empty v-if="!loading && !logs.length" description="暂无操作日志" />
        </div>
      </n-spin>
      <n-pagination
        v-if="totalElements > pageSize"
        :page="currentPage"
        :page-size="pageSize"
        :item-count="totalElements"
        show-quick-jumper
        class="log-pagination"
        aria-label="操作日志分页"
        @update:page="handlePageChange"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from "vue";
import { NAlert, NButton, NDataTable, NDatePicker, NInput, NPagination, NSelect, NSpin, NSwitch, NTag } from "naive-ui";
import { extractErrorMessage, http, isForbidden, type PagePayload, unwrapApi } from "@/api/http";
import PageHeader from "@/components/PageHeader.vue";
import { useIsMobile } from "@/composables/useResponsive";
import {
  formatDateTimeLabel,
  operationActionLabel,
  operationLogCategories,
  operationLogCategoryLabel,
  operationMessageLabel,
  operationTargetLabel,
  type OperationLogCategory
} from "@/utils/meetingDisplay";

interface OperationLogDto {
  id: number;
  actor: string;
  action: string;
  category?: string;
  targetType?: string;
  targetId?: string;
  message?: string;
  createdAt?: string;
  success: boolean;
}

interface OperationLogRow {
  id: number;
  time: string;
  operator: string;
  category: string;
  type: string;
  target: string;
  remark: string;
  success: boolean;
}

interface OperationLogFilterForm {
  category: OperationLogCategory | null;
  success: "SUCCESS" | "FAILURE" | null;
  dateRange: [number, number] | null;
  keyword: string;
  includeDraftSaves: boolean;
}

interface AppliedOperationLogFilters {
  category?: OperationLogCategory;
  success?: boolean;
  fromDate?: string;
  toDate?: string;
  keyword?: string;
  includeDraftSaves: boolean;
}

interface OperationLogRequestParams extends AppliedOperationLogFilters {
  page: number;
  size: number;
}

const categoryOptions = operationLogCategories.map((value) => ({
  label: operationLogCategoryLabel(value),
  value
}));
const successOptions = [
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILURE" }
];
const logs = ref<OperationLogRow[]>([]);
const isMobile = useIsMobile();
const filterForm = reactive<OperationLogFilterForm>(createFilterForm());
const appliedFilters = ref<AppliedOperationLogFilters>(snapshotFilters(filterForm));
const loadError = ref("");
const loading = ref(false);
const currentPage = ref(1);
const pageSize = 20;
const totalElements = ref(0);
const logSnapshotId = ref<string | null>(null);
const lastLoadedPage = ref(1);
const failedPage = ref<number | null>(null);
const accessForbidden = ref(false);
let latestRequestId = 0;
const successCount = computed(() => logs.value.filter((item) => item.success).length);
const failureCount = computed(() => logs.value.length - successCount.value);
const columns = [
  { title: "时间", key: "time", minWidth: 180, render: (row: { time?: string }) => formatDateTimeLabel(row.time) },
  { title: "操作人", key: "operator", minWidth: 120 },
  { title: "分类", key: "category", minWidth: 130 },
  { title: "操作", key: "type", minWidth: 160 },
  { title: "对象", key: "target", minWidth: 160 },
  {
    title: "结果",
    key: "success",
    width: 88,
    render: (row: OperationLogRow) => h(
      NTag,
      { type: row.success ? "success" : "error", size: "small", bordered: false },
      { default: () => row.success ? "成功" : "失败" }
    )
  },
  { title: "备注", key: "remark", minWidth: 160 }
];

onMounted(() => void loadLogs());

function createFilterForm(): OperationLogFilterForm {
  return {
    category: null,
    success: null,
    dateRange: null,
    keyword: "",
    includeDraftSaves: false
  };
}

function snapshotFilters(form: OperationLogFilterForm): AppliedOperationLogFilters {
  const [fromTimestamp, toTimestamp] = form.dateRange || [];
  const keyword = form.keyword.trim();
  return {
    category: form.category || undefined,
    success: form.success === "SUCCESS" ? true : form.success === "FAILURE" ? false : undefined,
    fromDate: localDate(fromTimestamp),
    toDate: localDate(toTimestamp),
    keyword: keyword || undefined,
    includeDraftSaves: form.includeDraftSaves
  };
}

function localDate(timestamp?: number): string | undefined {
  if (typeof timestamp !== "number" || !Number.isFinite(timestamp)) return undefined;
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function applyFilters() {
  appliedFilters.value = snapshotFilters(filterForm);
  resetForNewFilters();
  void loadLogs();
}

function resetFilters() {
  Object.assign(filterForm, createFilterForm());
  applyFilters();
}

function resetForNewFilters() {
  // 让已发出的旧筛选请求失效，避免其迟到响应重新写入新筛选结果。
  latestRequestId += 1;
  currentPage.value = 1;
  logs.value = [];
  totalElements.value = 0;
  logSnapshotId.value = null;
  lastLoadedPage.value = 1;
  failedPage.value = null;
  loadError.value = "";
}

function handlePageChange(page: number) {
  if (page === currentPage.value && !loadError.value) return;
  currentPage.value = page;
  void loadLogs();
}

function retryLoad() {
  if (accessForbidden.value) return;
  currentPage.value = failedPage.value ?? currentPage.value;
  void loadLogs();
}

function requestParams(filters: AppliedOperationLogFilters, page: number): OperationLogRequestParams {
  const params: OperationLogRequestParams = {
    page: page - 1,
    size: pageSize,
    includeDraftSaves: filters.includeDraftSaves
  };
  if (filters.category) params.category = filters.category;
  if (filters.success !== undefined) params.success = filters.success;
  if (filters.fromDate) params.fromDate = filters.fromDate;
  if (filters.toDate) params.toDate = filters.toDate;
  if (filters.keyword) params.keyword = filters.keyword;
  return params;
}

function readLogSnapshotHeader(headers: unknown): string | undefined {
  if (!headers || typeof headers !== "object") return undefined;
  const source = headers as {
    get?: (name: string) => unknown;
    [name: string]: unknown;
  };
  /*
   * Axios 1.x 默认返回 AxiosHeaders，响应头通过 get() 读取；单元测试和部分适配器
   * 则可能给普通对象。两种形态都支持，避免第一页拿不到快照导致后续分页不稳定。
   */
  const fromGetter = typeof source.get === "function"
    ? source.get("x-meetingos-log-snapshot")
    : undefined;
  const value = fromGetter ?? source["x-meetingos-log-snapshot"] ?? source["X-MeetingOS-Log-Snapshot"];
  return typeof value === "string" ? value : undefined;
}

async function loadLogs() {
  const requestId = ++latestRequestId;
  const requestedPage = currentPage.value;
  const filters = appliedFilters.value;
  loading.value = true;
  try {
    loadError.value = "";
    accessForbidden.value = false;
    const config: {
      params: OperationLogRequestParams;
      headers?: Record<string, string>;
    } = {
      params: requestParams(filters, requestedPage)
    };
    if (logSnapshotId.value !== null) {
      config.headers = { "X-MeetingOS-Log-Snapshot": logSnapshotId.value };
    }
    const response = await http.get<PagePayload<OperationLogDto>>("/admin/operation-logs", config);
    if (requestId !== latestRequestId) return;
    const returnedSnapshotId = readLogSnapshotHeader(response.headers);
    if (typeof returnedSnapshotId === "string" && /^\d+$/.test(returnedSnapshotId)) {
      logSnapshotId.value = returnedSnapshotId;
    }
    const result = unwrapApi(response);
    logs.value = result.content.map((item) => ({
      id: item.id,
      time: item.createdAt || "",
      operator: item.actor,
      category: operationLogCategoryLabel(item.category),
      type: operationActionLabel(item.action),
      target: operationTargetLabel(item.targetType, item.targetId),
      remark: operationMessageLabel(item.message),
      success: item.success
    }));
    totalElements.value = result.totalElements;
    lastLoadedPage.value = requestedPage;
    failedPage.value = null;
  } catch (error) {
    if (requestId !== latestRequestId) return;
    if (isForbidden(error)) {
      // 权限已被收回时，不能为了“失败可恢复”继续保留此前加载的系统级敏感日志。
      logs.value = [];
      totalElements.value = 0;
      currentPage.value = 1;
      lastLoadedPage.value = 1;
      failedPage.value = null;
      logSnapshotId.value = null;
      accessForbidden.value = true;
      loadError.value = "当前账号无权查看操作日志";
      return;
    }
    // 保留上一页已成功加载的数据，且让分页器回到该页，避免内容与页码错位。
    if (currentPage.value === requestedPage) currentPage.value = lastLoadedPage.value;
    failedPage.value = requestedPage;
    /*
     * 权限拒绝已在上方清空。这里仅针对短暂网络或服务端故障保留上一页真实结果，
     * 管理员可重试恢复目标页，避免表格与分页器同时退化为无数据状态。
     */
    loadError.value = extractErrorMessage(error, "操作日志加载失败");
  } finally {
    if (requestId === latestRequestId) loading.value = false;
  }
}
</script>

<style scoped>
.log-filter-bar {
  display: grid;
  grid-template-columns: minmax(150px, .8fr) minmax(120px, .65fr) minmax(250px, 1.2fr) minmax(230px, 1.45fr) auto;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.log-filter-bar > * { min-width: 0; }
.log-filter-date-range { width: 100%; }

.log-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-draft-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.log-load-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.log-load-error :deep(.n-alert) { flex: 1; }
.log-retry-button { flex: 0 0 auto; }

.log-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.log-mobile-category {
  color: var(--text-secondary);
  font-size: 12px;
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1050px) {
  .log-filter-bar {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .log-filter-actions {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .log-filter-bar {
    grid-template-columns: minmax(0, 1fr);
  }

  .log-filter-actions {
    grid-column: auto;
  }

  .log-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .log-pagination {
    justify-content: center;
  }

  .log-load-error {
    align-items: stretch;
  }
}
</style>
