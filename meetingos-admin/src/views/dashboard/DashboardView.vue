<template>
  <div>
    <page-header :title="`${store.currentMeeting?.name || '会议'}工作区`" :subtitle="workspaceSubtitle">
      <n-button secondary type="primary" @click="goRegistration">报名配置</n-button>
    </page-header>

    <div class="stat-grid">
      <div class="surface stat-card" v-for="item in stats" :key="item.label">
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
        <n-tag size="small" :type="item.type" round>{{ item.trend }}</n-tag>
      </div>
    </div>

    <div class="content-grid dashboard-grid">
      <div class="surface chart-panel">
        <div class="panel-head">
          <div>
            <h3>今日签到趋势</h3>
            <span>按小时统计</span>
          </div>
          <n-tag type="success" round>进行中</n-tag>
        </div>
        <div class="chart line-chart" aria-label="今日签到趋势">
          <svg viewBox="0 0 360 210" role="img">
            <title>今日签到趋势</title>
            <polyline v-for="line in gridLines" :key="line" class="chart-grid-line" :points="`28,${line} 344,${line}`" />
            <polygon class="line-area" :points="lineAreaPoints" />
            <polyline class="line-stroke" :points="linePolyline" />
            <circle v-for="point in linePoints" :key="point.time" class="line-dot" :cx="point.x" :cy="point.y" r="3.5" />
            <text v-for="point in lineAxisLabels" :key="point.time" class="axis-label" :x="point.x" y="202">
              {{ point.time }}
            </text>
          </svg>
        </div>
      </div>
      <div class="surface chart-panel">
        <div class="panel-head">
          <div>
            <h3>参会人类型分布</h3>
            <span>{{ store.currentMeeting?.name || "当前会议" }}</span>
          </div>
        </div>
        <div class="chart pie-chart">
          <div class="donut-chart" :style="{ background: attendeeDonutGradient }">
            <div class="donut-hole">
              <strong>{{ attendeeTypeTotal }}</strong>
              <span>样本</span>
            </div>
          </div>
          <div class="donut-legend">
            <span v-for="item in attendeeTypeSegments" :key="item.name">
              <i :style="{ background: item.color }"></i>
              {{ item.name }} {{ item.value }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useMeetingStore } from "@/stores/meeting";
import { meetingSummaryLabel } from "@/utils/meetingDisplay";

const store = useMeetingStore();
const router = useRouter();
const { routeMeetingId } = useRouteMeeting({ loadWorkspace: false });

const trendSeries = [
  { time: "08:00", value: 2 },
  { time: "09:00", value: 14 },
  { time: "10:00", value: 36 },
  { time: "11:00", value: 28 },
  { time: "12:00", value: 16 },
  { time: "13:00", value: 9 },
  { time: "14:00", value: 42 },
  { time: "15:00", value: 18 },
  { time: "16:00", value: 7 },
  { time: "17:00", value: 3 }
];

const attendeeTypeSegments = [
  { name: "VIP", value: 18, color: "#3b82f6" },
  { name: "嘉宾", value: 45, color: "#60a5fa" },
  { name: "媒体", value: 22, color: "#f6bd60" },
  { name: "工作人员", value: 30, color: "#f28482" },
  { name: "普通参会人", value: 127, color: "#a7c7e7" }
];

const gridLines = [36, 72, 108, 144, 180];

function loadDashboardSummary() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;

  /*
   * 工作区首屏只展示会议摘要、统计卡片和静态图表，不需要完整工作区列表。
   * 这里单独拉统计概览，避免刚进入会议后立刻触发参会人/座位/住宿/签到等整套请求。
   */
  void store.loadStatistics(meetingId);
}

const workspaceSubtitle = computed(() => {
  const meeting = store.currentMeeting;
  if (!meeting) return "请选择会议后管理报名、参会人、座位、住宿和签到";
  return meetingSummaryLabel(meeting, { includeCode: true });
});

const stats = computed(() => [
  { label: "报名人数", value: store.currentMeeting?.registeredCount || 0, trend: "当前会议", type: "success" as const },
  { label: "审核通过", value: store.currentMeeting?.approvedCount || 0, trend: "可参与排座", type: "success" as const },
  { label: "已签到", value: store.currentMeeting?.checkedInCount || 0, trend: "现场进度", type: "info" as const },
  {
    label: "待审核报名",
    value: store.currentAttendees.filter((item) => item.auditStatus === "PENDING").length,
    trend: "需及时处理",
    type: "warning" as const
  }
]);

function goRegistration() {
  if (!store.currentMeetingId) return;
  void router.push({ name: "meeting-registration", params: { meetingId: store.currentMeetingId } });
}

onMounted(() => {
  loadDashboardSummary();
});

const linePoints = computed(() => {
  const values = trendSeries.map((item) => item.value);
  const maxValue = Math.max(...values, 1);
  const minX = 36;
  const maxX = 336;
  const minY = 32;
  const maxY = 180;
  const stepX = (maxX - minX) / Math.max(trendSeries.length - 1, 1);

  /*
   * 首页图表只需要传达趋势，不需要完整图表库能力。
   * 用原生 SVG/CSS 可以避免进入工作区后再解析 ECharts/zrender 大包，
   * 这是快速切换菜单时最容易挤占浏览器主线程的部分。
   */
  return trendSeries.map((item, index) => ({
    ...item,
    x: Math.round(minX + index * stepX),
    y: Math.round(maxY - (item.value / maxValue) * (maxY - minY))
  }));
});

const linePolyline = computed(() => linePoints.value.map((point) => `${point.x},${point.y}`).join(" "));
const lineAreaPoints = computed(() => `36,180 ${linePolyline.value} 336,180`);
const lineAxisLabels = computed(() => linePoints.value.filter((_, index) => index % 2 === 0 || index === linePoints.value.length - 1));
const attendeeTypeTotal = computed(() => attendeeTypeSegments.reduce((total, item) => total + item.value, 0));
const attendeeDonutGradient = computed(() => {
  let cursor = 0;
  const total = attendeeTypeTotal.value || 1;
  const stops = attendeeTypeSegments.map((item) => {
    const start = cursor;
    cursor += (item.value / total) * 100;
    return `${item.color} ${start.toFixed(2)}% ${cursor.toFixed(2)}%`;
  });
  return `conic-gradient(${stops.join(", ")})`;
});
</script>

<style scoped>
.dashboard-grid {
  margin-top: 16px;
}

.chart-panel {
  padding: 20px;
  margin-top: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.panel-head h3 {
  margin: 0;
}

.panel-head span {
  color: #64748b;
  font-size: 13px;
}

.chart {
  height: 260px;
}

.line-chart {
  display: flex;
  align-items: center;
}

.line-chart svg {
  width: 100%;
  height: 100%;
}

.chart-grid-line {
  fill: none;
  stroke: #e5edf7;
  stroke-width: 1;
}

.line-area {
  fill: rgba(59, 130, 246, 0.12);
}

.line-stroke {
  fill: none;
  stroke: #3b82f6;
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.line-dot {
  fill: #ffffff;
  stroke: #3b82f6;
  stroke-width: 3;
}

.axis-label {
  fill: #64748b;
  font-size: 11px;
  text-anchor: middle;
}

.pie-chart {
  height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
}

.donut-chart {
  width: 168px;
  height: 168px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
}

.donut-hole {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: #ffffff;
  display: grid;
  place-items: center;
  align-content: center;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
}

.donut-hole strong,
.donut-hole span {
  display: block;
}

.donut-hole strong {
  font-size: 24px;
}

.donut-hole span {
  color: #64748b;
  font-size: 12px;
}

.donut-legend {
  display: grid;
  gap: 9px;
  color: #475569;
  font-size: 13px;
}

.donut-legend span {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.donut-legend i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex: 0 0 auto;
}

@media (max-width: 760px) {
  .dashboard-grid {
    gap: 12px;
  }

  .chart-panel {
    padding: 14px;
  }

  .chart {
    min-height: 220px;
  }

  .pie-chart {
    flex-direction: column;
    gap: 12px;
  }

  .donut-legend {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .donut-legend span {
    white-space: normal;
  }
}
</style>
