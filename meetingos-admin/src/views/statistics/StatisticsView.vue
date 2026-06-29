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
      </div>
    </page-header>

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

      <section class="surface chart-panel">
        <div class="section-head">
          <div>
            <h2>审核状态分布</h2>
            <span>当前会议全量</span>
          </div>
        </div>
        <div ref="reviewChartRef" class="chart-canvas"></div>
      </section>

      <section class="surface chart-panel">
        <div class="section-head">
          <div>
            <h2>酒店房间分布</h2>
            <span>按房间数量统计</span>
          </div>
        </div>
        <div ref="hotelChartRef" class="chart-canvas"></div>
      </section>

      <section class="surface chart-panel">
        <div class="section-head">
          <div>
            <h2>签到入口分布</h2>
            <span>成功签到记录</span>
          </div>
        </div>
        <div ref="checkinEntranceChartRef" class="chart-canvas"></div>
      </section>

      <section class="surface chart-panel">
        <div class="section-head">
          <div>
            <h2>参会类型分布</h2>
            <span>报名人员结构</span>
          </div>
        </div>
        <div class="distribution-list">
          <div v-for="item in attendeeTypeRows" :key="item.label" class="distribution-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <n-empty v-if="!attendeeTypeRows.length" size="small" description="暂无数据" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useMeetingStore, type StatisticsOverviewDto } from "@/stores/meeting";
import { attendeeTypeLabel } from "@/utils/meetingDisplay";

type RangeMode = "today" | "7d" | "30d" | "custom";
type ChartInstance = import("echarts").ECharts;
type EChartsModule = typeof import("echarts");

const NDatePicker = defineAsyncComponent(() => import("naive-ui/es/date-picker").then((module) => module.NDatePicker));
const store = useMeetingStore();
const { routeMeetingId } = useRouteMeeting({ loadWorkspace: false });

const rangeOptions: Array<{ label: string; value: RangeMode }> = [
  { label: "今日", value: "today" },
  { label: "近 7 天", value: "7d" },
  { label: "近 30 天", value: "30d" },
  { label: "自定义", value: "custom" }
];

const rangeMode = ref<RangeMode>("7d");
const customRange = ref<[number, number] | null>(null);
const echartsModule = shallowRef<EChartsModule | null>(null);
const trendChartRef = ref<HTMLDivElement | null>(null);
const reviewChartRef = ref<HTMLDivElement | null>(null);
const hotelChartRef = ref<HTMLDivElement | null>(null);
const checkinEntranceChartRef = ref<HTMLDivElement | null>(null);
const chartInstances: ChartInstance[] = [];

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

const attendeeTypeRows = computed(() => toRows(overview.value?.attendeeTypeDistribution, attendeeTypeLabel));

function loadStatisticsPage(meetingId = routeMeetingId.value) {
  if (!meetingId) return;
  void store.loadStatisticsWithRange(meetingId, activeRange.value).then(renderCharts);
}

watch([routeMeetingId, activeRange], ([meetingId]) => {
  if (meetingId) loadStatisticsPage(meetingId);
});

watch(overview, () => {
  void renderCharts();
});

onMounted(() => {
  loadStatisticsPage();
  window.addEventListener("resize", resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  chartInstances.splice(0).forEach((chart) => chart.dispose());
});

async function ensureEcharts() {
  if (!echartsModule.value) {
    echartsModule.value = await import("echarts");
  }
  return echartsModule.value;
}

async function renderCharts() {
  await nextTick();
  const data = overview.value;
  if (!data) return;
  const echarts = await ensureEcharts();
  renderTrendChart(echarts, data);
  renderReviewChart(echarts, data);
  renderHotelChart(echarts, data);
  renderCheckinEntranceChart(echarts, data);
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

function renderReviewChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, reviewChartRef.value);
  if (!chart) return;
  const rows = toRows(data.reviewStatusDistribution, reviewStatusLabel);
  chart.setOption({
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [
      {
        type: "pie",
        radius: ["44%", "70%"],
        center: ["50%", "44%"],
        data: rows.map((item) => ({ name: item.label, value: item.value }))
      }
    ]
  });
}

function renderHotelChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, hotelChartRef.value);
  if (!chart) return;
  const rows = toRows(data.hotelDistribution).slice(0, 8);
  chart.setOption(barOption(rows, "房间数"));
}

function renderCheckinEntranceChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, checkinEntranceChartRef.value);
  if (!chart) return;
  const rows = toRows(data.checkinEntranceDistribution).slice(0, 8);
  chart.setOption(barOption(rows, "签到数"));
}

function barOption(rows: Array<{ label: string; value: number }>, seriesName: string) {
  return {
    tooltip: { trigger: "axis" },
    grid: { top: 18, left: 42, right: 18, bottom: 46 },
    xAxis: { type: "category", data: rows.map((item) => item.label), axisLabel: { interval: 0, rotate: rows.length > 4 ? 30 : 0 } },
    yAxis: { type: "value", minInterval: 1 },
    series: [{ name: seriesName, type: "bar", barMaxWidth: 34, data: rows.map((item) => item.value) }]
  };
}

function resizeCharts() {
  chartInstances.forEach((chart) => chart.resize());
}

function toRows(source?: Record<string, number>, formatLabel: (value: string) => string = (value) => value) {
  return Object.entries(source || {})
    .map(([label, value]) => ({ label: formatLabel(label), value }))
    .sort((a, b) => b.value - a.value);
}

function toTrendRows(source?: Record<string, number>) {
  return Object.entries(source || {}).map(([date, count]) => ({ date, count }));
}

function reviewStatusLabel(value: string) {
  const labels: Record<string, string> = {
    PENDING: "待审核",
    APPROVED: "已通过",
    REJECTED: "已拒绝",
    CANCELLED: "已取消"
  };
  return labels[value] || value || "-";
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

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

.section-head h2 {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
}

.section-head span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}

.section-head strong {
  color: var(--accent-pressed);
  font-size: 22px;
  line-height: 28px;
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

.distribution-list {
  min-height: 310px;
  display: grid;
  align-content: start;
}

.distribution-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.distribution-row strong {
  color: var(--text-primary);
}

@media (max-width: 1120px) {
  .summary-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .metric-section,
  .chart-panel {
    padding: 14px;
  }

  .section-head {
    flex-direction: column;
    gap: 6px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
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

  .chart-canvas,
  .distribution-list {
    height: 250px;
    min-height: 250px;
  }
}
</style>
