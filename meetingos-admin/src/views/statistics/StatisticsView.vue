<template>
  <div class="statistics-page">
    <page-header title="数据统计">
      <div class="statistics-actions">
        <n-radio-group v-model:value="rangeMode" size="small">
          <n-radio-button v-for="item in rangeOptions" :key="item.value" :value="item.value">{{ item.label }}</n-radio-button>
        </n-radio-group>
        <component
          :is="NDatePicker"
          v-if="rangeMode === 'custom'"
          v-model:value="customRange"
          type="daterange"
          size="small"
          clearable
        />
        <n-button
          v-if="canManageDashboard"
          size="small"
          secondary
          :loading="refreshingDashboard"
          :disabled="!dashboardReady"
          @click="refreshDashboard()"
        >
          <template #icon><n-icon :component="RefreshOutline" /></template>
          刷新看板
        </n-button>
        <n-button
          v-if="canManageDashboard"
          size="small"
          type="primary"
          :disabled="!dashboardFields.length || dashboardComponents.length >= MAX_DASHBOARD_COMPONENTS"
          @click="openDashboardEditor()"
        >
          <template #icon><n-icon :component="AddOutline" /></template>
          添加组件
        </n-button>
      </div>
    </page-header>

    <section class="surface dashboard-workbench">
      <div class="section-head dashboard-section-head">
        <div>
          <h2>自定义统计看板</h2>
          <span>{{ rangeLabel }} · 所选周期统计</span>
        </div>
        <n-space align="center" :size="8">
          <n-tag v-if="dashboardQuery?.generatedAt" size="small" round type="success">已刷新</n-tag>
          <n-button
            v-if="canManageDashboard"
            size="small"
            secondary
            :loading="savingDashboard"
            :disabled="!dashboardDirty || loadingDashboard"
            @click="saveDashboard"
          >
            <template #icon><n-icon :component="SaveOutline" /></template>
            保存布局
          </n-button>
        </n-space>
      </div>

      <n-alert v-if="dashboardLoadError" type="warning" :bordered="false" class="dashboard-alert">
        {{ dashboardLoadError }}
      </n-alert>
      <n-alert v-else-if="!canManageDashboard" type="info" :bordered="false" class="dashboard-alert">
        统计看板包含完整报名字段，仅会议管理员可查看和配置。
      </n-alert>

      <n-spin :show="loadingDashboard">
        <div v-if="!canManageDashboard" class="dashboard-empty-state">
          <n-empty description="仅会议管理员可查看统计看板" />
        </div>
        <div v-else-if="dashboardComponents.length" class="dashboard-component-grid">
          <section
            v-for="(component, index) in dashboardComponents"
            :key="component.id"
            class="dashboard-component"
            :class="{ 'dashboard-component-wide': component.width === 'FULL' }"
          >
            <div class="dashboard-component-head">
              <div class="dashboard-component-title">
                <h3>{{ component.title }}</h3>
                <span>{{ dashboardComponentDescription(component) }}</span>
              </div>
              <div v-if="canManageDashboard" class="dashboard-component-actions">
                <n-tooltip>
                  <template #trigger>
                    <n-button
                      quaternary
                      circle
                      size="small"
                      :disabled="index === 0"
                      aria-label="上移组件"
                      title="上移组件"
                      @click="moveDashboardComponent(index, -1)"
                    >
                      <template #icon><n-icon :component="ArrowUpOutline" /></template>
                    </n-button>
                  </template>
                  上移
                </n-tooltip>
                <n-tooltip>
                  <template #trigger>
                    <n-button
                      quaternary
                      circle
                      size="small"
                      :aria-label="component.width === 'FULL' ? '恢复两列宽度' : '拉宽组件'"
                      :title="component.width === 'FULL' ? '恢复两列宽度' : '拉宽组件'"
                      @click="toggleDashboardComponentWidth(component.id)"
                    >
                      <template #icon>
                        <n-icon :component="component.width === 'FULL' ? ContractOutline : ExpandOutline" />
                      </template>
                    </n-button>
                  </template>
                  {{ component.width === 'FULL' ? '恢复两列宽度' : '拉宽组件' }}
                </n-tooltip>
                <n-tooltip>
                  <template #trigger>
                    <n-button
                      quaternary
                      circle
                      size="small"
                      :disabled="index === dashboardComponents.length - 1"
                      aria-label="下移组件"
                      title="下移组件"
                      @click="moveDashboardComponent(index, 1)"
                    >
                      <template #icon><n-icon :component="ArrowDownOutline" /></template>
                    </n-button>
                  </template>
                  下移
                </n-tooltip>
                <n-tooltip>
                  <template #trigger>
                    <n-button quaternary circle size="small" aria-label="编辑组件" title="编辑组件" @click="openDashboardEditor(component)">
                      <template #icon><n-icon :component="CreateOutline" /></template>
                    </n-button>
                  </template>
                  编辑
                </n-tooltip>
                <n-tooltip>
                  <template #trigger>
                    <n-button quaternary circle size="small" aria-label="复制组件" title="复制组件" @click="duplicateDashboardComponent(component, index)">
                      <template #icon><n-icon :component="CopyOutline" /></template>
                    </n-button>
                  </template>
                  复制
                </n-tooltip>
                <n-popconfirm @positive-click="removeDashboardComponent(component.id)">
                  <template #trigger>
                    <n-button quaternary circle size="small" type="error" aria-label="删除组件" title="删除组件">
                      <template #icon><n-icon :component="TrashOutline" /></template>
                    </n-button>
                  </template>
                  确定删除此统计组件？
                </n-popconfirm>
              </div>
            </div>

            <n-alert
              v-if="dashboardResultIsInvalid(dashboardResult(component))"
              type="warning"
              :bordered="false"
              class="dashboard-component-alert"
            >
              {{ dashboardResult(component)?.message || "当前组件配置不可用" }}
            </n-alert>

            <template v-else-if="component.visualization === 'NUMBER'">
              <div class="dashboard-number-result">
                <strong>{{ formatDashboardValue(dashboardResult(component)?.value) }}</strong>
                <span v-if="dashboardResult(component)?.total !== undefined">共 {{ formatDashboardValue(dashboardResult(component)?.total) }} 条记录</span>
              </div>
            </template>

            <template v-else-if="component.visualization === 'TABLE'">
              <div v-if="dashboardResultItems(component).length" class="dashboard-table-result">
                <div v-for="row in dashboardResultItems(component)" :key="`${component.id}-${row.key || row.label}`" class="dashboard-table-row">
                  <span>{{ row.label || "未填写" }}</span>
                  <strong>{{ formatDashboardValue(row.value) }}</strong>
                </div>
              </div>
              <n-empty v-else size="small" description="暂无符合条件的数据" />
            </template>

            <template v-else>
              <div v-if="dashboardResultItems(component).length" class="dashboard-chart-shell">
                <div :ref="(element) => setDashboardChartRef(component.id, element)" class="dashboard-chart-canvas"></div>
              </div>
              <n-empty v-else size="small" description="暂无符合条件的数据" />
            </template>
          </section>
        </div>

        <div v-else class="dashboard-empty-state">
          <n-empty description="尚未配置统计组件" />
          <n-button v-if="canManageDashboard && dashboardFields.length" type="primary" @click="openDashboardEditor()">
            <template #icon><n-icon :component="AddOutline" /></template>
            添加第一个组件
          </n-button>
        </div>
      </n-spin>
    </section>

    <div class="summary-grid">
      <section v-for="group in metricGroups" :key="group.title" class="surface metric-section">
        <div class="section-head">
          <div>
            <h2>{{ group.title }}</h2>
            <span>{{ group.subtitle }}</span>
          </div>
          <strong>{{ group.highlight }}</strong>
        </div>
        <div class="metric-grid">
          <div v-for="item in group.items" :key="item.label" class="metric-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small v-if="item.note">{{ item.note }}</small>
          </div>
        </div>
      </section>
    </div>

    <div class="chart-grid">
      <section class="surface chart-panel chart-panel-wide">
        <div class="section-head">
          <div>
            <h2>报名与签到趋势</h2>
            <span>{{ rangeLabel }}</span>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-canvas"></div>
      </section>
    </div>

    <n-modal
      v-model:show="dashboardEditorVisible"
      preset="card"
      :title="editingDashboardComponentId ? '编辑统计组件' : '添加统计组件'"
      :style="{ width: 'min(760px, calc(100vw - 32px))' }"
      :mask-closable="!savingDashboard"
    >
      <div class="dashboard-editor-body">
        <n-form label-placement="top" :show-feedback="false">
          <div class="dashboard-editor-grid">
            <n-form-item label="统计字段">
              <n-select
                v-model:value="dashboardDraft.fieldKey"
                filterable
                :options="dashboardFieldOptions"
                placeholder="选择系统字段或报名表字段"
                @update:value="handleDashboardDraftFieldChange"
              />
            </n-form-item>
            <n-form-item label="统计方式">
              <n-select
                v-model:value="dashboardDraft.aggregation"
                :options="dashboardAggregationOptions"
                :disabled="!dashboardDraftField"
                placeholder="选择统计方式"
                @update:value="handleDashboardDraftAggregationChange"
              />
            </n-form-item>
            <n-form-item label="展示形式">
              <n-select
                v-model:value="dashboardDraft.visualization"
                :options="dashboardVisualizationOptions"
                :disabled="!dashboardDraftField"
                placeholder="选择展示形式"
              />
            </n-form-item>
            <n-form-item v-if="dashboardDraft.visualization !== 'NUMBER'" label="显示数量">
              <n-input-number
                v-model:value="dashboardDraft.limit"
                :min="DASHBOARD_DISPLAY_LIMIT_MIN"
                :max="DASHBOARD_DISPLAY_LIMIT_MAX"
                :precision="0"
                :show-button="false"
              />
            </n-form-item>
            <n-form-item class="dashboard-editor-title-item" label="组件名称">
              <n-input v-model:value="dashboardDraft.title" maxlength="40" show-count @update:value="dashboardDraftTitleTouched = true" />
            </n-form-item>
          </div>

          <div class="dashboard-filter-editor">
            <div class="dashboard-filter-editor-head">
              <div>
                <h3>筛选条件</h3>
                <span>仅统计满足全部条件的报名记录</span>
              </div>
              <n-button
                size="small"
                secondary
                :disabled="!dashboardFields.length || dashboardDraft.filters.length >= MAX_DASHBOARD_FILTERS"
                @click="addDashboardDraftFilter"
              >
                <template #icon><n-icon :component="AddOutline" /></template>
                添加筛选
              </n-button>
            </div>

            <div v-if="dashboardDraft.filters.length" class="dashboard-filter-list">
              <div v-for="(filter, index) in dashboardDraft.filters" :key="`${filter.fieldKey}-${index}`" class="dashboard-filter-row">
                <n-select
                  v-model:value="filter.fieldKey"
                  filterable
                  :options="dashboardFieldOptions"
                  placeholder="筛选字段"
                  @update:value="(value) => handleDashboardDraftFilterFieldChange(filter, value)"
                />
                <n-select
                  v-model:value="filter.operator"
                  :options="dashboardFilterOperatorOptions(filter)"
                  placeholder="条件"
                />
                <n-select
                  v-if="dashboardFilterValueOptions(filter).length && dashboardFilterNeedsValues(filter.operator) && filter.operator !== 'BETWEEN'"
                  :value="dashboardFilterSelectValue(filter)"
                  :multiple="dashboardFilterAllowsMultipleValues(filter.operator)"
                  filterable
                  clearable
                  :options="dashboardFilterValueOptions(filter)"
                  placeholder="选择值"
                  @update:value="(value) => updateDashboardFilterValues(filter, value)"
                />
                <n-input
                  v-else-if="dashboardFilterNeedsValues(filter.operator)"
                  v-model:value="filter.valuesText"
                  :placeholder="dashboardFilterValuePlaceholder(filter)"
                />
                <span v-else class="dashboard-empty-filter-value">不需要填写值</span>
                <n-tooltip>
                  <template #trigger>
                    <n-button quaternary circle size="small" type="error" aria-label="删除筛选条件" title="删除筛选条件" @click="removeDashboardDraftFilter(index)">
                      <template #icon><n-icon :component="TrashOutline" /></template>
                    </n-button>
                  </template>
                  删除筛选
                </n-tooltip>
              </div>
            </div>
            <span v-else class="dashboard-filter-empty">未设置筛选条件</span>
          </div>
        </n-form>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button @click="dashboardEditorVisible = false">取消</n-button>
          <n-button type="primary" @click="saveDashboardDraft">{{ editingDashboardComponentId ? '应用修改' : '添加组件' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import {
  NAlert,
  NButton,
  NEmpty,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NTooltip,
  useMessage,
  type SelectOption
} from "naive-ui";
import {
  AddOutline,
  ArrowDownOutline,
  ArrowUpOutline,
  ContractOutline,
  CopyOutline,
  CreateOutline,
  ExpandOutline,
  RefreshOutline,
  SaveOutline,
  TrashOutline
} from "@vicons/ionicons5";
import { extractErrorMessage } from "@/api/http";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useAuthStore } from "@/stores/auth";
import {
  DASHBOARD_DISPLAY_LIMIT_DEFAULT,
  DASHBOARD_DISPLAY_LIMIT_MAX,
  DASHBOARD_DISPLAY_LIMIT_MIN,
  normalizeDashboardDisplayLimit
} from "@/utils/dashboardLimit";
import {
  useMeetingStore,
  type StatisticsDashboardAggregation,
  type StatisticsDashboardComponentDto,
  type StatisticsDashboardComponentWidth,
  type StatisticsDashboardFieldDto,
  type StatisticsDashboardFilterDto,
  type StatisticsDashboardFilterOperator,
  type StatisticsDashboardQueryComponentDto,
  type StatisticsDashboardQueryItemDto,
  type StatisticsDashboardQueryResultDto,
  type StatisticsDashboardVisualization,
  type StatisticsOverviewDto
} from "@/stores/meeting";

type RangeMode = "today" | "7d" | "30d" | "custom";
type ChartInstance = import("@/utils/echarts").ChartInstance;
type EChartsModule = typeof import("@/utils/echarts");

interface DashboardFilterDraft {
  fieldKey: string;
  operator: StatisticsDashboardFilterOperator;
  values: string[];
  valuesText: string;
}

interface DashboardComponentDraft {
  id: string;
  title: string;
  fieldKey: string;
  aggregation: StatisticsDashboardAggregation;
  visualization: StatisticsDashboardVisualization;
  limit: number | null;
  width: StatisticsDashboardComponentWidth;
  filters: DashboardFilterDraft[];
}

const NDatePicker = defineAsyncComponent(() => import("naive-ui/es/date-picker").then((module) => module.NDatePicker));
const store = useMeetingStore();
const auth = useAuthStore();
const message = useMessage();
const { routeMeetingId } = useRouteMeeting({ loadWorkspace: false });

const rangeOptions: Array<{ label: string; value: RangeMode }> = [
  { label: "今日", value: "today" },
  { label: "近 7 天", value: "7d" },
  { label: "近 30 天", value: "30d" },
  { label: "自定义", value: "custom" }
];

const aggregationLabels: Record<string, string> = {
  COUNT: "人数",
  FILLED_COUNT: "已填写人数",
  DISTINCT_COUNT: "去重人数",
  DISTRIBUTION: "分布",
  SUM: "求和",
  AVERAGE: "平均值",
  MIN: "最小值",
  MAX: "最大值"
};

const visualizationLabels: Record<string, string> = {
  NUMBER: "指标数值",
  BAR: "柱状图",
  PIE: "饼图",
  TABLE: "数据表"
};

const filterOperatorLabels: Record<string, string> = {
  EQUALS: "等于",
  NOT_EQUALS: "不等于",
  IN: "属于任一值",
  NOT_IN: "不属于任一值",
  GREATER_THAN: "大于",
  GREATER_THAN_OR_EQUAL: "大于等于",
  LESS_THAN: "小于",
  LESS_THAN_OR_EQUAL: "小于等于",
  BETWEEN: "介于范围",
  IS_EMPTY: "未填写",
  IS_NOT_EMPTY: "已填写"
};

const dashboardChartColors = ["#0f766e", "#2563eb", "#d97706", "#be123c", "#059669", "#7c3aed"];
const MAX_DASHBOARD_COMPONENTS = 12;
const MAX_DASHBOARD_FILTERS = 8;
const rangeMode = ref<RangeMode>("7d");
const customRange = ref<[number, number] | null>(null);
const echartsModule = shallowRef<EChartsModule | null>(null);
const trendChartRef = ref<HTMLDivElement | null>(null);
const chartInstances: ChartInstance[] = [];
const dashboardChartRefs = new Map<string, HTMLDivElement>();
const dashboardChartInstances = new Map<string, ChartInstance>();
const dashboardFields = ref<StatisticsDashboardFieldDto[]>([]);
const dashboardComponents = ref<StatisticsDashboardComponentDto[]>([]);
const dashboardQuery = ref<StatisticsDashboardQueryResultDto | null>(null);
const loadingDashboard = ref(false);
const refreshingDashboard = ref(false);
const savingDashboard = ref(false);
const dashboardDirty = ref(false);
const dashboardLoadError = ref("");
const dashboardLoadedMeetingId = ref("");
const dashboardCanManage = ref(false);
const dashboardEditorVisible = ref(false);
const editingDashboardComponentId = ref<string | null>(null);
const dashboardDraftTitleTouched = ref(false);
const dashboardDraft = ref<DashboardComponentDraft>(emptyDashboardDraft());
let dashboardLoadGeneration = 0;
let dashboardRefreshGeneration = 0;
let chartRenderGeneration = 0;
let lastRenderedOverview: StatisticsOverviewDto | null = null;

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

const rangeLabel = computed(() => `${activeRange.value.startDate} 至 ${activeRange.value.endDate}`);
const overview = computed(() => store.overview);
const dashboardReady = computed(() => dashboardLoadedMeetingId.value === routeMeetingId.value && !loadingDashboard.value);
const canManageDashboard = computed(() => auth.user.roles.includes("SUPER_ADMIN") || dashboardCanManage.value);
const dashboardFieldsByKey = computed(() => new Map(dashboardFields.value.map((field) => [field.key, field])));
const dashboardDraftField = computed(() => dashboardFieldsByKey.value.get(dashboardDraft.value.fieldKey));
const dashboardFieldOptions = computed<SelectOption[]>(() => dashboardFields.value.map((field) => ({
  label: field.category ? `${field.category} · ${field.label}` : field.label,
  value: field.key
})));
const dashboardAggregationOptions = computed<SelectOption[]>(() =>
  allowedAggregations(dashboardDraftField.value).map((value) => ({ label: aggregationLabel(value), value }))
);
const dashboardVisualizationOptions = computed<SelectOption[]>(() =>
  allowedVisualizationsForAggregation(dashboardDraftField.value, dashboardDraft.value.aggregation)
    .map((value) => ({ label: visualizationLabel(value), value }))
);

const metricGroups = computed(() => {
  const data = overview.value;
  const seatUsage = percent(data?.seatUsageRate);
  const checkinRate = percent(data?.checkinRate);
  const requiredRoomCount = data?.accommodationRequiredCount ?? 0;
  const requiredAssigned = data?.roomAssignedRequiredCount ?? 0;
  const requiredUnassigned = data?.roomUnassignedRequiredCount ?? 0;
  return [
    {
      title: "报名统计",
      subtitle: "审核进度与新增报名",
      highlight: `${data?.registrationCount ?? 0} 人`,
      items: [
        { label: "报名总人数", value: data?.registrationCount ?? 0 },
        { label: "时间范围新增", value: data?.rangeRegistrationCount ?? 0, note: rangeLabel.value },
        { label: "待审核", value: data?.pendingCount ?? 0 },
        { label: "已通过 / 已拒绝", value: `${data?.approvedCount ?? 0} / ${data?.rejectedCount ?? 0}` }
      ]
    },
    {
      title: "座位统计",
      subtitle: "座位库存与分配率",
      highlight: seatUsage,
      items: [
        { label: "总座位", value: data?.totalSeatCount ?? 0 },
        { label: "可用座位", value: data?.availableSeatCount ?? 0 },
        { label: "已分配", value: data?.seatedCount ?? 0 },
        { label: "空余 / 禁用", value: `${data?.emptySeatCount ?? 0} / ${data?.disabledSeatCount ?? 0}` }
      ]
    },
    {
      title: "住宿统计",
      subtitle: "房间与需住宿人员",
      highlight: `${requiredAssigned}/${requiredRoomCount}`,
      items: [
        { label: "房间总数", value: data?.roomCount ?? 0 },
        { label: "需住宿人数", value: requiredRoomCount },
        { label: "需住宿已安排", value: requiredAssigned },
        { label: "需住宿未安排", value: requiredUnassigned }
      ]
    },
    {
      title: "签到统计",
      subtitle: "现场到达进度",
      highlight: checkinRate,
      items: [
        { label: "应到人数", value: data?.approvedCount ?? 0 },
        { label: "已签到", value: data?.checkedInCount ?? 0 },
        { label: "未签到", value: data?.notCheckedInCount ?? 0 },
        { label: "时间范围签到", value: data?.rangeCheckinCount ?? 0, note: rangeLabel.value }
      ]
    }
  ];
});

function loadStatisticsPage(meetingId = routeMeetingId.value) {
  if (!meetingId) return;
  void store.loadStatisticsWithRange(meetingId, activeRange.value);
}

async function loadDashboardContext(meetingId = routeMeetingId.value) {
  if (!meetingId) return;
  const generation = ++dashboardLoadGeneration;
  dashboardRefreshGeneration += 1;
  dashboardLoadedMeetingId.value = "";
  dashboardQuery.value = null;
  dashboardFields.value = [];
  dashboardComponents.value = [];
  dashboardCanManage.value = false;
  dashboardDirty.value = false;
  dashboardLoadError.value = "";
  loadingDashboard.value = true;

  try {
    // 字段目录会返回完整报名值；先确认当前账号是会议管理员，再请求敏感统计数据。
    const config = await store.loadStatisticsDashboardConfig(meetingId);
    if (generation !== dashboardLoadGeneration || routeMeetingId.value !== meetingId) return;
    dashboardCanManage.value = Boolean(config?.canManage);
    dashboardLoadedMeetingId.value = meetingId;
    if (!dashboardCanManage.value) return;
    const fields = await store.loadStatisticsDashboardFields(meetingId);
    if (generation !== dashboardLoadGeneration || routeMeetingId.value !== meetingId) return;
    dashboardFields.value = fields;
    dashboardComponents.value = cloneDashboardComponents(config?.components || []);
    await refreshDashboard({ meetingId, silent: true });
  } catch (error) {
    if (generation !== dashboardLoadGeneration || routeMeetingId.value !== meetingId) return;
    dashboardLoadError.value = extractErrorMessage(error, "可配置统计看板暂时无法加载");
  } finally {
    if (generation === dashboardLoadGeneration) loadingDashboard.value = false;
  }
}

async function refreshDashboard(options: { meetingId?: string; silent?: boolean } = {}) {
  const meetingId = options.meetingId || routeMeetingId.value;
  if (!meetingId || dashboardLoadedMeetingId.value !== meetingId) return;
  const components = cloneDashboardComponents(dashboardComponents.value);
  const generation = ++dashboardRefreshGeneration;
  if (!components.length) {
    dashboardQuery.value = { components: [] };
    return;
  }

  refreshingDashboard.value = true;
  try {
    const result = await store.queryStatisticsDashboard(meetingId, components, activeRange.value);
    if (generation !== dashboardRefreshGeneration || routeMeetingId.value !== meetingId) return;
    if (result) dashboardQuery.value = result;
  } catch (error) {
    if (generation !== dashboardRefreshGeneration || routeMeetingId.value !== meetingId) return;
    dashboardLoadError.value = extractErrorMessage(error, "统计看板刷新失败");
    if (!options.silent) message.error(dashboardLoadError.value);
  } finally {
    if (generation === dashboardRefreshGeneration) refreshingDashboard.value = false;
  }
}

function openDashboardEditor(component?: StatisticsDashboardComponentDto) {
  if (!dashboardFields.value.length) {
    message.warning("统计字段尚未加载完成");
    return;
  }
  if (!component && dashboardComponents.value.length >= MAX_DASHBOARD_COMPONENTS) {
    message.warning(`一个看板最多添加 ${MAX_DASHBOARD_COMPONENTS} 个组件`);
    return;
  }
  editingDashboardComponentId.value = component?.id || null;
  dashboardDraft.value = component ? dashboardDraftFromComponent(component) : newDashboardDraft();
  dashboardDraftTitleTouched.value = Boolean(component);
  dashboardEditorVisible.value = true;
}

function saveDashboardDraft() {
  const field = dashboardDraftField.value;
  if (!field) {
    message.warning("请选择统计字段");
    return;
  }
  if (!allowedAggregations(field).includes(dashboardDraft.value.aggregation)) {
    message.warning("当前字段不支持所选统计方式");
    return;
  }
  if (!allowedVisualizationsForAggregation(field, dashboardDraft.value.aggregation).includes(dashboardDraft.value.visualization)) {
    message.warning("当前字段不支持所选展示形式");
    return;
  }
  const filterError = validateDashboardDraftFilters();
  if (filterError) {
    message.warning(filterError);
    return;
  }

  const component = dashboardComponentFromDraft(dashboardDraft.value, field);
  const existingIndex = dashboardComponents.value.findIndex((item) => item.id === editingDashboardComponentId.value);
  if (existingIndex < 0 && dashboardComponents.value.length >= MAX_DASHBOARD_COMPONENTS) {
    message.warning(`一个看板最多添加 ${MAX_DASHBOARD_COMPONENTS} 个组件`);
    return;
  }
  if (existingIndex >= 0) {
    dashboardComponents.value.splice(existingIndex, 1, component);
  } else {
    dashboardComponents.value.push(component);
  }
  dashboardDirty.value = true;
  dashboardEditorVisible.value = false;
  void refreshDashboard({ silent: true });
}

async function saveDashboard() {
  const meetingId = routeMeetingId.value;
  if (!meetingId || !canManageDashboard.value) return;
  savingDashboard.value = true;
  try {
    const config = await store.saveStatisticsDashboardConfig(meetingId, dashboardComponents.value);
    if (routeMeetingId.value !== meetingId) return;
    dashboardComponents.value = cloneDashboardComponents(config.components);
    dashboardCanManage.value = Boolean(config.canManage);
    dashboardDirty.value = false;
    message.success("统计看板已保存");
    await refreshDashboard({ meetingId, silent: true });
  } catch (error) {
    message.error(extractErrorMessage(error, "保存统计看板失败"));
  } finally {
    savingDashboard.value = false;
  }
}

function moveDashboardComponent(index: number, offset: -1 | 1) {
  const targetIndex = index + offset;
  if (targetIndex < 0 || targetIndex >= dashboardComponents.value.length) return;
  const next = [...dashboardComponents.value];
  [next[index], next[targetIndex]] = [next[targetIndex], next[index]];
  dashboardComponents.value = next;
  dashboardDirty.value = true;
  void refreshDashboard({ silent: true });
}

function toggleDashboardComponentWidth(componentId: string) {
  dashboardComponents.value = dashboardComponents.value.map((component) => (
    component.id === componentId
      ? { ...component, width: component.width === "FULL" ? "HALF" : "FULL" }
      : component
  ));
  dashboardDirty.value = true;
  // 栅格列变化不会触发 window resize，需要主动通知 ECharts 重算画布尺寸。
  void nextTick(resizeCharts);
}

function duplicateDashboardComponent(component: StatisticsDashboardComponentDto, index: number) {
  if (dashboardComponents.value.length >= MAX_DASHBOARD_COMPONENTS) {
    message.warning(`一个看板最多添加 ${MAX_DASHBOARD_COMPONENTS} 个组件`);
    return;
  }
  const duplicate = cloneDashboardComponents([component])[0];
  duplicate.id = createDashboardComponentId();
  duplicate.title = `${component.title}副本`.slice(0, 40);
  dashboardComponents.value.splice(index + 1, 0, duplicate);
  dashboardDirty.value = true;
  void refreshDashboard({ silent: true });
}

function removeDashboardComponent(componentId: string) {
  dashboardComponents.value = dashboardComponents.value.filter((component) => component.id !== componentId);
  disposeDashboardChart(componentId);
  dashboardDirty.value = true;
  void refreshDashboard({ silent: true });
}

function handleDashboardDraftFieldChange(value: string) {
  const field = dashboardFieldsByKey.value.get(value);
  if (!field) return;
  dashboardDraft.value.fieldKey = value;
  const aggregations = allowedAggregations(field);
  if (!aggregations.includes(dashboardDraft.value.aggregation)) {
    dashboardDraft.value.aggregation = preferredAggregation(aggregations);
  }
  const visualizations = allowedVisualizationsForAggregation(field, dashboardDraft.value.aggregation);
  if (!visualizations.includes(dashboardDraft.value.visualization)) {
    dashboardDraft.value.visualization = preferredVisualization(visualizations, dashboardDraft.value.aggregation);
  }
  if (!dashboardDraftTitleTouched.value) {
    dashboardDraft.value.title = defaultDashboardTitle(field, dashboardDraft.value.aggregation);
  }
}

function handleDashboardDraftAggregationChange(value: StatisticsDashboardAggregation) {
  const field = dashboardDraftField.value;
  if (!field) return;
  dashboardDraft.value.aggregation = value;
  const visualizations = allowedVisualizationsForAggregation(field, value);
  dashboardDraft.value.visualization = preferredVisualization(visualizations, value);
  if (!dashboardDraftTitleTouched.value) {
    dashboardDraft.value.title = defaultDashboardTitle(field, value);
  }
}

function addDashboardDraftFilter() {
  const field = dashboardFields.value.find((item) => allowedFilterOperators(item).length);
  if (!field) {
    message.warning("当前没有可用于筛选的字段");
    return;
  }
  if (dashboardDraft.value.filters.length >= MAX_DASHBOARD_FILTERS) {
    message.warning(`一个组件最多设置 ${MAX_DASHBOARD_FILTERS} 个筛选条件`);
    return;
  }
  const operator = allowedFilterOperators(field)[0] || "EQUALS";
  dashboardDraft.value.filters.push({ fieldKey: field.key, operator, values: [], valuesText: "" });
}

function removeDashboardDraftFilter(index: number) {
  dashboardDraft.value.filters.splice(index, 1);
}

function handleDashboardDraftFilterFieldChange(filter: DashboardFilterDraft, value: string) {
  const field = dashboardFieldsByKey.value.get(value);
  filter.fieldKey = value;
  filter.values = [];
  filter.valuesText = "";
  if (!field) return;
  const operators = allowedFilterOperators(field);
  if (!operators.includes(filter.operator)) filter.operator = operators[0] || "EQUALS";
}

function dashboardFilterOperatorOptions(filter: DashboardFilterDraft): SelectOption[] {
  return allowedFilterOperators(dashboardFieldsByKey.value.get(filter.fieldKey)).map((value) => ({
    label: filterOperatorLabels[value] || value,
    value
  }));
}

function dashboardFilterValueOptions(filter: DashboardFilterDraft): SelectOption[] {
  return (dashboardFieldsByKey.value.get(filter.fieldKey)?.options || []).map((option) => ({
    label: option.label,
    value: option.value
  }));
}

function dashboardFilterNeedsValues(operator: StatisticsDashboardFilterOperator) {
  return operator !== "IS_EMPTY" && operator !== "IS_NOT_EMPTY";
}

function dashboardFilterAllowsMultipleValues(operator: StatisticsDashboardFilterOperator) {
  return operator === "IN" || operator === "NOT_IN";
}

function dashboardFilterSelectValue(filter: DashboardFilterDraft) {
  return dashboardFilterAllowsMultipleValues(filter.operator) ? filter.values : filter.values[0] || null;
}

function updateDashboardFilterValues(filter: DashboardFilterDraft, value: string | number | Array<string | number> | null) {
  filter.values = Array.isArray(value)
    ? value.map((item) => String(item))
    : value === null ? [] : [String(value)];
  filter.valuesText = "";
}

function dashboardFilterValuePlaceholder(filter: DashboardFilterDraft) {
  return filter.operator === "BETWEEN" ? "使用逗号分隔两个边界" : "多个值使用逗号分隔";
}

function dashboardComponentDescription(component: StatisticsDashboardComponentDto) {
  const field = dashboardFieldsByKey.value.get(component.fieldKey);
  const limit = component.visualization !== "NUMBER" && component.limit ? ` · 前 ${component.limit} 项` : "";
  return `${field?.label || component.fieldKey} · ${aggregationLabel(component.aggregation)}${limit} · 所选周期`;
}

function dashboardResult(component: StatisticsDashboardComponentDto): StatisticsDashboardQueryComponentDto | undefined {
  return dashboardQuery.value?.components.find((result) => result.id === component.id);
}

function dashboardResultItems(component: StatisticsDashboardComponentDto): StatisticsDashboardQueryItemDto[] {
  return dashboardResult(component)?.items || [];
}

function dashboardResultIsInvalid(result?: StatisticsDashboardQueryComponentDto) {
  return Boolean(result?.status?.startsWith("INVALID"));
}

function cloneDashboardComponents(components: StatisticsDashboardComponentDto[]) {
  return components.map((component) => ({
    ...component,
    filters: (component.filters || []).map((filter) => ({ ...filter, values: [...(filter.values || [])] }))
  }));
}

function dashboardDraftFromComponent(component: StatisticsDashboardComponentDto): DashboardComponentDraft {
  return {
    id: component.id,
    title: component.title,
    fieldKey: component.fieldKey,
    aggregation: component.aggregation,
    visualization: component.visualization,
    limit: component.limit ?? DASHBOARD_DISPLAY_LIMIT_DEFAULT,
    width: component.width === "FULL" ? "FULL" : "HALF",
    filters: (component.filters || []).map((filter) => ({
      fieldKey: filter.fieldKey,
      operator: filter.operator,
      values: [...(filter.values || [])],
      valuesText: (filter.values || []).join(", ")
    }))
  };
}

function newDashboardDraft(): DashboardComponentDraft {
  const field = dashboardFields.value[0];
  if (!field) return emptyDashboardDraft();
  const aggregation = preferredAggregation(allowedAggregations(field));
  return {
    id: createDashboardComponentId(),
    title: defaultDashboardTitle(field, aggregation),
    fieldKey: field.key,
    aggregation,
    visualization: preferredVisualization(allowedVisualizationsForAggregation(field, aggregation), aggregation),
    limit: DASHBOARD_DISPLAY_LIMIT_DEFAULT,
    width: "HALF",
    filters: []
  };
}

function emptyDashboardDraft(): DashboardComponentDraft {
  return {
    id: "",
    title: "",
    fieldKey: "",
    aggregation: "COUNT",
    visualization: "NUMBER",
    limit: DASHBOARD_DISPLAY_LIMIT_DEFAULT,
    width: "HALF",
    filters: []
  };
}

function dashboardComponentFromDraft(draft: DashboardComponentDraft, field: StatisticsDashboardFieldDto): StatisticsDashboardComponentDto {
  return {
    id: draft.id || createDashboardComponentId(),
    title: draft.title.trim() || defaultDashboardTitle(field, draft.aggregation),
    fieldKey: draft.fieldKey,
    aggregation: draft.aggregation,
    visualization: draft.visualization,
    limit: draft.visualization === "NUMBER" ? undefined : normalizeDashboardDisplayLimit(draft.limit),
    width: draft.width,
    filters: draft.filters
      .filter((filter) => filter.fieldKey && filter.operator)
      .map((filter) => dashboardFilterFromDraft(filter))
  };
}

function dashboardFilterFromDraft(filter: DashboardFilterDraft): StatisticsDashboardFilterDto {
  const sourceValues = filter.values.length
    ? filter.values
    : filter.valuesText.split(",").map((value) => value.trim()).filter(Boolean);
  const values = filter.operator === "BETWEEN"
    ? sourceValues.slice(0, 2)
    : dashboardFilterAllowsMultipleValues(filter.operator) ? sourceValues : sourceValues.slice(0, 1);
  return {
    fieldKey: filter.fieldKey,
    operator: filter.operator,
    values: dashboardFilterNeedsValues(filter.operator) ? values : []
  };
}

function validateDashboardDraftFilters() {
  for (const filter of dashboardDraft.value.filters) {
    const field = dashboardFieldsByKey.value.get(filter.fieldKey);
    if (!field || !allowedFilterOperators(field).includes(filter.operator)) {
      return "筛选字段或筛选条件不可用";
    }
    const normalized = dashboardFilterFromDraft(filter);
    if (filter.operator === "BETWEEN" && normalized.values?.length !== 2) {
      return "区间筛选需要填写两个边界值";
    }
    if (dashboardFilterNeedsValues(filter.operator) && !normalized.values?.length) {
      return "请填写筛选值";
    }
    if ((normalized.values?.length || 0) > 20) {
      return "每个筛选条件最多填写 20 个值";
    }
  }
  return "";
}

function allowedAggregations(field?: StatisticsDashboardFieldDto) {
  return field?.allowedAggregations || [];
}

function allowedVisualizations(field?: StatisticsDashboardFieldDto) {
  return field?.allowedVisualizations || [];
}

function allowedVisualizationsForAggregation(
  field: StatisticsDashboardFieldDto | undefined,
  aggregation: StatisticsDashboardAggregation
): StatisticsDashboardVisualization[] {
  const visualizations = allowedVisualizations(field);
  if (aggregation === "DISTRIBUTION") {
    return visualizations.filter((value) => value !== "NUMBER");
  }
  return visualizations.filter((value) => value === "NUMBER");
}

function allowedFilterOperators(field?: StatisticsDashboardFieldDto) {
  if (field?.allowedOperators?.length) return field.allowedOperators;
  if (field?.dataType === "NUMBER" || field?.dataType === "DATE") {
    return ["EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL", "BETWEEN", "IS_EMPTY", "IS_NOT_EMPTY"] as StatisticsDashboardFilterOperator[];
  }
  return ["EQUALS", "NOT_EQUALS", "IN", "NOT_IN", "IS_EMPTY", "IS_NOT_EMPTY"] as StatisticsDashboardFilterOperator[];
}

function preferredAggregation(values: StatisticsDashboardAggregation[]) {
  return values.includes("COUNT") ? "COUNT" : values[0] || "COUNT";
}

function preferredVisualization(values: StatisticsDashboardVisualization[], aggregation: StatisticsDashboardAggregation) {
  if (aggregation === "DISTRIBUTION") {
    return values.includes("BAR") ? "BAR" : values[0] || "TABLE";
  }
  return values.includes("NUMBER") ? "NUMBER" : values[0] || "TABLE";
}

function defaultDashboardTitle(field: StatisticsDashboardFieldDto, aggregation: StatisticsDashboardAggregation) {
  return aggregation === "DISTRIBUTION" ? `${field.label}分布` : `${field.label}${aggregationLabel(aggregation)}`;
}

function aggregationLabel(value: StatisticsDashboardAggregation | string) {
  return aggregationLabels[value] || value;
}

function visualizationLabel(value: StatisticsDashboardVisualization | string) {
  return visualizationLabels[value] || value;
}

function createDashboardComponentId() {
  return `dashboard-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

watch([routeMeetingId, activeRange], ([meetingId]) => {
  if (meetingId) loadStatisticsPage(meetingId);
});

watch(routeMeetingId, (meetingId) => {
  if (meetingId) void loadDashboardContext(meetingId);
}, { immediate: true });

watch(activeRange, () => {
  if (dashboardReady.value) void refreshDashboard({ silent: true });
});

watch([overview, dashboardQuery], () => {
  void renderCharts();
});

watch(
  () => dashboardComponents.value.map((component) => `${component.id}:${component.visualization}`).join("|"),
  () => {
    void renderCharts();
  }
);

onMounted(() => {
  loadStatisticsPage();
  void renderCharts();
  window.addEventListener("resize", resizeCharts);
});

onBeforeUnmount(() => {
  chartRenderGeneration += 1;
  dashboardLoadGeneration += 1;
  dashboardRefreshGeneration += 1;
  window.removeEventListener("resize", resizeCharts);
  chartInstances.splice(0).forEach((chart) => chart.dispose());
  dashboardChartInstances.forEach((chart) => chart.dispose());
  dashboardChartInstances.clear();
  dashboardChartRefs.clear();
});

async function ensureEcharts() {
  if (!echartsModule.value) {
    echartsModule.value = await import("@/utils/echarts");
  }
  return echartsModule.value;
}

async function renderCharts() {
  const generation = ++chartRenderGeneration;
  const overviewData = overview.value;
  const dashboardData = dashboardQuery.value;
  if (!overviewData && !dashboardData) return;
  await nextTick();
  const echarts = await ensureEcharts();
  if (generation !== chartRenderGeneration) return;
  if (overviewData && overviewData !== lastRenderedOverview) {
    renderTrendChart(echarts, overviewData);
    lastRenderedOverview = overviewData;
  }
  if (dashboardData) renderDashboardCharts(echarts);
}

function chartFor(echarts: EChartsModule, element: HTMLDivElement | null) {
  if (!element) return null;
  const existing = echarts.getInstanceByDom(element);
  if (existing) return existing;
  const chart = echarts.init(element);
  chartInstances.push(chart);
  return chart;
}

function renderTrendChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, trendChartRef.value);
  if (!chart) return;
  const registrationTrend = toTrendRows(data.registrationTrend);
  const checkinTrend = toTrendRows(data.checkinTrend);
  const dates = registrationTrend.map((item) => item.date);
  chart.setOption({
    tooltip: { trigger: "axis" },
    legend: { top: 0, data: ["报名新增", "签到人数"] },
    grid: { top: 48, left: 42, right: 18, bottom: 34 },
    xAxis: { type: "category", boundaryGap: false, data: dates },
    yAxis: { type: "value", minInterval: 1 },
    series: [
      { name: "报名新增", type: "line", smooth: true, areaStyle: {}, data: registrationTrend.map((item) => item.count) },
      { name: "签到人数", type: "line", smooth: true, data: checkinTrend.map((item) => item.count) }
    ]
  });
}

function setDashboardChartRef(componentId: string, element: unknown) {
  if (element instanceof HTMLDivElement) {
    dashboardChartRefs.set(componentId, element);
    return;
  }
  dashboardChartRefs.delete(componentId);
  disposeDashboardChart(componentId);
}

function renderDashboardCharts(echarts: EChartsModule) {
  const activeComponentIds = new Set<string>();
  for (const component of dashboardComponents.value) {
    if (component.visualization !== "BAR" && component.visualization !== "PIE") continue;
    const element = dashboardChartRefs.get(component.id);
    const result = dashboardResult(component);
    const items = result?.items || [];
    if (!element || !items.length) continue;
    activeComponentIds.add(component.id);
    let chart = dashboardChartInstances.get(component.id);
    if (!chart || chart.getDom() !== element) {
      chart?.dispose();
      chart = echarts.init(element);
      dashboardChartInstances.set(component.id, chart);
    }
    if (component.visualization === "PIE") {
      chart.setOption({
        color: dashboardChartColors,
        tooltip: { trigger: "item", valueFormatter: (value: number | string) => formatDashboardValue(value) },
        series: [{
          type: "pie",
          radius: ["42%", "72%"],
          avoidLabelOverlap: true,
          label: { formatter: "{b}: {c}" },
          data: items.map((item) => ({ name: item.label || "未填写", value: item.value }))
        }]
      }, true);
    } else {
      chart.setOption({
        color: [dashboardChartColors[0]],
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        grid: { top: 18, left: 48, right: 16, bottom: 58 },
        xAxis: {
          type: "category",
          data: items.map((item) => item.label || "未填写"),
          axisLabel: { interval: 0, rotate: items.length > 5 ? 28 : 0 }
        },
        yAxis: { type: "value", minInterval: 1 },
        series: [{ type: "bar", barMaxWidth: 42, data: items.map((item) => item.value) }]
      }, true);
    }
  }
  for (const [componentId, chart] of dashboardChartInstances) {
    if (!activeComponentIds.has(componentId)) {
      chart.dispose();
      dashboardChartInstances.delete(componentId);
    }
  }
}

function disposeDashboardChart(componentId: string) {
  const chart = dashboardChartInstances.get(componentId);
  chart?.dispose();
  dashboardChartInstances.delete(componentId);
}

function resizeCharts() {
  chartInstances.forEach((chart) => chart.resize());
  dashboardChartInstances.forEach((chart) => chart.resize());
}

function formatDashboardValue(value: number | string | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return "-";
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(Number(value));
}

function toTrendRows(source?: Record<string, number>) {
  return Object.entries(source || {}).map(([date, count]) => ({ date, count }));
}

function percent(value?: number) {
  return `${Math.round(((value || 0) * 100))}%`;
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
.statistics-page {
  display: grid;
  gap: 16px;
}

.statistics-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.dashboard-workbench,
.metric-section,
.chart-panel {
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-head h2,
.dashboard-component-title h3,
.dashboard-filter-editor h3 {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
}

.section-head span,
.dashboard-component-title span,
.dashboard-filter-editor span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}

.section-head strong {
  color: var(--accent-pressed);
  font-size: 22px;
  line-height: 28px;
}

.dashboard-alert {
  margin-bottom: 14px;
}

.dashboard-component-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-component {
  min-height: 204px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-component-wide {
  grid-column: 1 / -1;
}

.dashboard-component-head,
.dashboard-filter-editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.dashboard-component-title {
  min-width: 0;
}

.dashboard-component-title h3 {
  overflow-wrap: anywhere;
}

.dashboard-component-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
}

.dashboard-component-alert {
  margin: 0;
}

.dashboard-number-result {
  min-height: 126px;
  display: grid;
  align-content: center;
  gap: 6px;
}

.dashboard-number-result strong {
  color: var(--text-primary);
  font-size: 36px;
  line-height: 44px;
  overflow-wrap: anywhere;
}

.dashboard-number-result span,
.dashboard-empty-filter-value,
.dashboard-filter-empty {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}

.dashboard-chart-shell {
  min-height: 176px;
  flex: 1;
}

.dashboard-chart-canvas {
  width: 100%;
  height: 216px;
}

.dashboard-table-result {
  display: grid;
  gap: 0;
  max-height: 360px;
  overflow-y: auto;
}

.dashboard-table-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
}

.dashboard-table-row:last-child {
  border-bottom: 0;
}

.dashboard-table-row span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.dashboard-table-row strong {
  flex: 0 0 auto;
}

.dashboard-empty-state {
  min-height: 178px;
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.metric-card {
  min-height: 82px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  display: grid;
  align-content: center;
  gap: 4px;
  background: var(--surface-muted);
}

.metric-card span,
.metric-card small {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.metric-card strong {
  color: var(--text-primary);
  font-size: 22px;
  line-height: 28px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-panel-wide {
  grid-column: 1 / -1;
}

.chart-canvas {
  width: 100%;
  height: 310px;
}

.dashboard-editor-body {
  max-height: min(640px, calc(100vh - 172px));
  overflow: auto;
  padding-right: 2px;
}

.dashboard-editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.dashboard-editor-title-item {
  grid-column: 1 / -1;
}

.dashboard-filter-editor {
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
}

.dashboard-filter-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.dashboard-filter-row {
  display: grid;
  grid-template-columns: minmax(130px, 1.2fr) minmax(112px, 0.9fr) minmax(150px, 1.2fr) 32px;
  align-items: center;
  gap: 8px;
}

.dashboard-empty-filter-value {
  min-height: 34px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border: 1px dashed var(--border-color);
  border-radius: 6px;
}

.dashboard-filter-empty {
  display: block;
  margin-top: 10px;
}

@media (max-width: 1120px) {
  .summary-grid,
  .chart-grid,
  .dashboard-component-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .dashboard-workbench,
  .metric-section,
  .chart-panel {
    padding: 14px;
  }

  .section-head,
  .dashboard-component-head,
  .dashboard-filter-editor-head {
    flex-direction: column;
    gap: 6px;
  }

  .dashboard-component-actions {
    align-self: flex-end;
  }

  .metric-grid,
  .dashboard-editor-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-editor-title-item {
    grid-column: auto;
  }

  .dashboard-filter-row {
    grid-template-columns: 1fr 1fr 32px;
  }

  .dashboard-filter-row > :nth-child(3) {
    grid-column: 1 / 3;
  }

  .dashboard-filter-row > :nth-child(4) {
    grid-column: 3;
    grid-row: 1;
  }

  .statistics-actions {
    justify-content: flex-start;
    width: 100%;
  }

  .statistics-actions :deep(.n-radio-group) {
    max-width: 100%;
    display: flex;
    flex-wrap: wrap;
  }

  .statistics-actions :deep(.n-date-picker) {
    width: 100%;
  }

  .chart-canvas {
    height: 250px;
  }

  .dashboard-chart-canvas {
    height: 196px;
  }
}
</style>
