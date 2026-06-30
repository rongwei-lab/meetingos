<template>
  <div class="dashboard-page">
    <page-header :title="`${store.currentMeeting?.name || '会议'}工作区`">
      <n-button secondary type="primary" @click="goRegistration">报名配置</n-button>
    </page-header>

    <div class="stat-grid dashboard-stat-grid">
      <div class="surface stat-card dashboard-stat-card" v-for="item in stats" :key="item.label">
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </div>
    </div>

    <div class="dashboard-grid">
      <section class="surface chart-panel trend-panel">
        <div class="panel-head">
          <div>
            <h3>近 7 日报名趋势</h3>
            <span>报名新增与签到同步对比</span>
          </div>
          <n-tag type="info" round>{{ trendTotal }} 人新增</n-tag>
        </div>
        <div class="chart-shell trend-chart-shell" aria-label="近 7 日报名趋势">
          <div ref="trendChartRef" class="dashboard-chart-canvas" :class="{ 'is-chart-empty': !hasTrendData }"></div>
          <div v-if="!hasTrendData" class="chart-empty-overlay">暂无近 7 日报名数据</div>
        </div>
      </section>

      <div class="side-stack">
        <section class="surface chart-panel compact-panel">
          <div class="panel-head">
            <div>
              <h3>参会人类型分布</h3>
              <span>报名人员结构</span>
            </div>
          </div>
          <div class="compact-chart-shell">
            <div ref="attendeeTypeChartRef" class="dashboard-chart-canvas" :class="{ 'is-chart-empty': !attendeeTypeTotal }"></div>
            <div v-if="!attendeeTypeTotal" class="chart-empty-overlay mini-empty">暂无参会人类型数据</div>
          </div>
        </section>

        <section class="surface chart-panel compact-panel">
          <div class="panel-head">
            <div>
              <h3>住宿需求概览</h3>
              <span>报名住宿意向与安排情况</span>
            </div>
          </div>
          <div class="room-summary">
            <div class="compact-chart-shell room-chart-shell" aria-label="住宿需求概览">
              <div ref="roomChartRef" class="dashboard-chart-canvas" :class="{ 'is-chart-empty': !accommodationRequiredCount }"></div>
              <div v-if="!accommodationRequiredCount" class="chart-empty-overlay mini-empty">暂无住宿需求数据</div>
            </div>
            <div class="room-numbers">
              <div>
                <strong>{{ accommodationRequiredCount }}</strong>
                <span>需要住宿</span>
              </div>
              <div>
                <strong>{{ roomAssignedRequiredCount }}</strong>
                <span>已安排</span>
              </div>
              <div>
                <strong>{{ roomUnassignedRequiredCount }}</strong>
                <span>待安排</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <div class="insight-grid">
      <section class="surface insight-panel">
        <div class="panel-head">
          <div>
            <h3>报名单位 Top 5</h3>
            <span>按报名人数排序</span>
          </div>
        </div>
        <div class="organization-chart-shell">
          <div
            ref="organizationChartRef"
            class="dashboard-chart-canvas"
            :class="{ 'is-chart-empty': !organizationRows.length }"
          ></div>
          <div v-if="!organizationRows.length" class="chart-empty-overlay mini-empty">暂无单位数据</div>
        </div>
      </section>

      <section class="surface insight-panel">
        <div class="panel-head">
          <div>
            <h3>报名情况概览</h3>
            <span>当前会议报名数据快照</span>
          </div>
        </div>
        <div class="overview-list">
          <div v-for="item in overviewItems" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import { useRouter } from "vue-router";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useMeetingStore, type StatisticsOverviewDto } from "@/stores/meeting";
import { attendeeTypeLabel } from "@/utils/meetingDisplay";

type ChartInstance = import("echarts").ECharts;
type EChartsModule = typeof import("echarts");
type EChartsOption = Parameters<ChartInstance["setOption"]>[0];

const store = useMeetingStore();
const router = useRouter();
const { routeMeetingId } = useRouteMeeting({ loadWorkspace: false });

const attendeeTypeColors = ["#3b82f6", "#60a5fa", "#14b8a6", "#f6bd60", "#f28482", "#94a3b8"];
const roomColors = ["#14b8a6", "#f59e0b"];
const echartsModule = shallowRef<EChartsModule | null>(null);
const trendChartRef = ref<HTMLDivElement | null>(null);
const attendeeTypeChartRef = ref<HTMLDivElement | null>(null);
const roomChartRef = ref<HTMLDivElement | null>(null);
const organizationChartRef = ref<HTMLDivElement | null>(null);
const chartInstances: ChartInstance[] = [];

function loadDashboardSummary() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;

  /*
   * 工作区首屏只展示会议报名概览，不需要完整工作区列表。
   * 这里单独拉统计概览，避免刚进入会议后触发参会人/座位/住宿等大列表请求。
   */
  void store.loadStatistics(meetingId).then(renderDashboardCharts);
}

const overview = computed(() => store.overview);
const registrationCount = computed(() => overview.value?.registrationCount ?? store.currentMeeting?.registeredCount ?? 0);
const todayRegistrationCount = computed(() => overview.value?.todayRegistrationCount ?? 0);
const rangeRegistrationCount = computed(() => overview.value?.rangeRegistrationCount ?? 0);
const checkedInCount = computed(() => overview.value?.checkedInCount ?? store.currentMeeting?.checkedInCount ?? 0);
const checkinRate = computed(() => formatPercent(overview.value?.checkinRate));
const accommodationRequiredCount = computed(() => overview.value?.accommodationRequiredCount ?? 0);
const roomAssignedRequiredCount = computed(() => overview.value?.roomAssignedRequiredCount ?? 0);
const roomUnassignedRequiredCount = computed(() => overview.value?.roomUnassignedRequiredCount ?? 0);
const stats = computed(() => [
  { label: "报名总人数", value: registrationCount.value },
  { label: "今日新增", value: todayRegistrationCount.value },
  { label: "近 7 日新增", value: rangeRegistrationCount.value },
  { label: "已签到人数", value: checkedInCount.value }
]);

function goRegistration() {
  if (!store.currentMeetingId) return;
  void router.push({ name: "meeting-registration", params: { meetingId: store.currentMeetingId } });
}

onMounted(() => {
  loadDashboardSummary();
  window.addEventListener("resize", resizeDashboardCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeDashboardCharts);
  chartInstances.splice(0).forEach((chart) => chart.dispose());
});

watch(overview, () => {
  void renderDashboardCharts();
});

function formatPercent(value?: number) {
  if (!value) return "0%";
  return `${Math.round(value * 100)}%`;
}

function normalizeTrend(registrationTrend?: Record<string, number>, checkinTrend?: Record<string, number>) {
  const keys = Array.from(new Set([...Object.keys(registrationTrend || {}), ...Object.keys(checkinTrend || {})])).sort();
  return keys.map((date) => ({
    date,
    registration: registrationTrend?.[date] ?? 0,
    checkin: checkinTrend?.[date] ?? 0
  }));
}

const trendSeries = computed(() => normalizeTrend(overview.value?.registrationTrend, overview.value?.checkinTrend));
const hasTrendData = computed(() => trendSeries.value.some((item) => item.registration > 0 || item.checkin > 0));
const trendTotal = computed(() => trendSeries.value.reduce((total, item) => total + item.registration, 0));

function distributionRows(distribution?: Record<string, number>, labeler: (value: string) => string = (value) => value) {
  return Object.entries(distribution || {})
    .map(([key, value]) => ({ label: labeler(key), value }))
    .filter((item) => item.value > 0)
    .sort((a, b) => b.value - a.value);
}

const attendeeTypeSegments = computed(() =>
  distributionRows(overview.value?.attendeeTypeDistribution, attendeeTypeLabel).map((item, index) => ({
    label: item.label,
    value: item.value,
    color: attendeeTypeColors[index % attendeeTypeColors.length]
  }))
);
const attendeeTypeTotal = computed(() => attendeeTypeSegments.value.reduce((total, item) => total + item.value, 0));

const organizationRows = computed(() => {
  const rows = distributionRows(overview.value?.organizationDistribution).slice(0, 5);
  return rows.map((item) => ({ ...item }));
});

const overviewItems = computed(() => [
  { label: "报名总量", value: `${registrationCount.value} 人` },
  { label: "今日新增", value: `${todayRegistrationCount.value} 人` },
  { label: "近 7 日新增", value: `${rangeRegistrationCount.value} 人` },
  { label: "需要住宿", value: `${accommodationRequiredCount.value} 人` },
  { label: "已签到", value: `${checkedInCount.value} 人` },
  { label: "签到率", value: checkinRate.value }
]);

async function ensureEcharts() {
  if (!echartsModule.value) {
    echartsModule.value = await import("echarts");
  }
  return echartsModule.value;
}

async function renderDashboardCharts() {
  await nextTick();
  const data = overview.value;
  if (!data) return;

  const echarts = await ensureEcharts();
  renderTrendChart(echarts, data);
  renderAttendeeTypeChart(echarts, data);
  renderRoomChart(echarts, data);
  renderOrganizationChart(echarts, data);
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

  const rows = normalizeTrend(data.registrationTrend, data.checkinTrend);
  chart.setOption(
    {
      ...animatedChartBase(1200),
      color: ["#3b82f6", "#14b8a6"],
      tooltip: { trigger: "axis", confine: true },
      legend: { top: 0, right: 4, icon: "roundRect", itemWidth: 12, itemHeight: 8, textStyle: { color: "#64748b" } },
      grid: { top: 44, left: 36, right: 18, bottom: 30 },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: rows.map((item) => item.date),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: "#dbe7f3" } },
        axisLabel: { color: "#64748b", formatter: shortDateLabel }
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        splitLine: { lineStyle: { color: "#e5edf7" } },
        axisLabel: { color: "#64748b" }
      },
      series: [
        {
          name: "报名新增",
          type: "line",
          smooth: true,
          symbol: "circle",
          symbolSize: 7,
          areaStyle: { color: "rgba(59, 130, 246, 0.14)" },
          lineStyle: { width: 3 },
          emphasis: { focus: "series" },
          data: rows.map((item) => item.registration)
        },
        {
          name: "签到人数",
          type: "line",
          smooth: true,
          symbol: "circle",
          symbolSize: 6,
          lineStyle: { width: 3 },
          emphasis: { focus: "series" },
          data: rows.map((item) => item.checkin)
        }
      ]
    },
    true
  );
}

function renderAttendeeTypeChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, attendeeTypeChartRef.value);
  if (!chart) return;

  const rows = distributionRows(data.attendeeTypeDistribution, attendeeTypeLabel);
  chart.setOption(
    {
      ...animatedChartBase(950),
      color: attendeeTypeColors,
      tooltip: { trigger: "item", confine: true },
      legend: {
        type: "scroll",
        orient: "vertical",
        right: 0,
        top: "middle",
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: "#64748b", fontSize: 12 }
      },
      series: [
        {
          name: "参会人类型",
          type: "pie",
          radius: ["46%", "72%"],
          center: ["32%", "50%"],
          avoidLabelOverlap: true,
          label: { show: false },
          emphasis: { label: { show: true, fontWeight: 700 } },
          data: rows.map((item) => ({ name: item.label, value: item.value }))
        }
      ]
    },
    true
  );
}

function renderRoomChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, roomChartRef.value);
  if (!chart) return;

  const assigned = data.roomAssignedRequiredCount ?? 0;
  const unassigned = data.roomUnassignedRequiredCount ?? 0;
  chart.setOption(
    {
      ...animatedChartBase(900),
      color: roomColors,
      tooltip: { trigger: "item", confine: true },
      legend: { bottom: 0, icon: "roundRect", itemWidth: 12, itemHeight: 8, textStyle: { color: "#64748b", fontSize: 12 } },
      series: [
        {
          name: "住宿安排",
          type: "pie",
          radius: ["50%", "74%"],
          center: ["50%", "42%"],
          label: { formatter: "{b}\\n{c} 人", color: "#334155", fontSize: 12 },
          labelLine: { length: 8, length2: 6 },
          data: [
            { name: "已安排", value: assigned },
            { name: "待安排", value: unassigned }
          ]
        }
      ]
    },
    true
  );
}

function renderOrganizationChart(echarts: EChartsModule, data: StatisticsOverviewDto) {
  const chart = chartFor(echarts, organizationChartRef.value);
  if (!chart) return;

  const rows = distributionRows(data.organizationDistribution).slice(0, 5).reverse();
  chart.setOption(
    {
      ...animatedChartBase(1000),
      color: ["#3b82f6"],
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, confine: true },
      grid: { top: 8, left: 98, right: 24, bottom: 10 },
      xAxis: {
        type: "value",
        minInterval: 1,
        splitLine: { lineStyle: { color: "#e5edf7" } },
        axisLabel: { color: "#64748b" }
      },
      yAxis: {
        type: "category",
        data: rows.map((item) => item.label),
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: {
          color: "#475569",
          width: 86,
          overflow: "truncate"
        }
      },
      series: [
        {
          name: "报名人数",
          type: "bar",
          barMaxWidth: 18,
          itemStyle: { borderRadius: [0, 8, 8, 0] },
          label: { show: true, position: "right", color: "#334155" },
          data: rows.map((item) => item.value)
        }
      ]
    },
    true
  );
}

function animatedChartBase(duration = 900): EChartsOption {
  return {
    animation: true,
    animationDuration: duration,
    animationDurationUpdate: 650,
    animationEasing: "cubicOut",
    animationEasingUpdate: "cubicOut"
  };
}

function shortDateLabel(date: string) {
  const match = date.match(/^\d{4}-(\d{2})-(\d{2})$/);
  return match ? `${Number(match[1])}/${Number(match[2])}` : date;
}

function resizeDashboardCharts() {
  chartInstances.forEach((chart) => chart.resize());
}
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 16px;
}

.dashboard-stat-grid {
  grid-template-columns: repeat(4, minmax(150px, 1fr));
}

.dashboard-stat-card {
  min-height: 126px;
  display: grid;
  align-content: center;
  gap: 4px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(340px, 0.9fr);
  gap: 16px;
  align-items: stretch;
}

.chart-panel {
  padding: 20px;
}

.trend-panel {
  min-height: 360px;
}

.side-stack {
  display: grid;
  gap: 16px;
}

.compact-panel {
  min-height: 172px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-head h3 {
  margin: 0;
  font-size: 17px;
  line-height: 24px;
}

.panel-head span {
  color: var(--text-secondary);
  font-size: 13px;
}

.chart-shell,
.compact-chart-shell,
.organization-chart-shell {
  position: relative;
}

.trend-chart-shell {
  height: 270px;
}

.compact-chart-shell {
  height: 154px;
}

.room-chart-shell {
  height: 150px;
}

.organization-chart-shell {
  height: 240px;
}

.dashboard-chart-canvas {
  width: 100%;
  height: 100%;
  min-height: 120px;
}

.dashboard-chart-canvas.is-chart-empty {
  opacity: 0;
  pointer-events: none;
}

.chart-empty-overlay,
.mini-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--text-secondary);
  background: var(--surface-muted);
  border-radius: 8px;
  font-size: 13px;
}

.room-summary {
  display: grid;
  gap: 12px;
}

.room-numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.room-numbers div {
  padding: 10px;
  border-radius: 8px;
  background: var(--surface-muted);
}

.room-numbers strong,
.room-numbers span {
  display: block;
}

.room-numbers strong {
  color: var(--text-primary);
  font-size: 20px;
}

.room-numbers span {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 12px;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(320px, 0.7fr);
  gap: 16px;
}

.insight-panel {
  padding: 20px;
}

.overview-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.overview-list div {
  padding: 12px;
  border-radius: 8px;
  background: var(--surface-muted);
}

.overview-list span,
.overview-list strong {
  display: block;
}

.overview-list span {
  color: var(--text-secondary);
  font-size: 12px;
}

.overview-list strong {
  margin-top: 4px;
  font-size: 18px;
}

@media (max-width: 1100px) {
  .dashboard-stat-grid,
  .dashboard-grid,
  .insight-grid {
    grid-template-columns: 1fr 1fr;
  }

  .trend-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .dashboard-stat-grid,
  .dashboard-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-grid {
    gap: 12px;
  }

  .chart-panel,
  .insight-panel {
    padding: 14px;
  }

  .trend-chart-shell {
    min-height: 220px;
    height: 230px;
  }

  .compact-chart-shell,
  .room-chart-shell {
    height: 180px;
  }

  .organization-chart-shell {
    height: 220px;
  }

  .overview-list,
  .room-numbers {
    grid-template-columns: 1fr;
  }
}
</style>
