<template>
  <div ref="chartElement" class="analytics-chart" aria-label="邀请函按天趋势图" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { InvitationAnalyticsDaily } from "@/types/invitation";

const props = defineProps<{ data: InvitationAnalyticsDaily[] }>();
const chartElement = ref<HTMLElement>();
let chart: { setOption: (option: unknown) => void; resize: () => void; dispose: () => void } | undefined;

watch(() => props.data, renderChart, { deep: true });

onMounted(async () => {
  if (!chartElement.value) return;
  const echarts = await import("@/utils/echarts");
  chart = echarts.init(chartElement.value);
  renderChart();
  window.addEventListener("resize", resizeChart, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeChart);
  chart?.dispose();
});

function resizeChart() {
  chart?.resize();
}

function renderChart() {
  chart?.setOption({
    animationDuration: 420,
    color: ["#08784f", "#2d8f69", "#e9a23b", "#d65845"],
    tooltip: { trigger: "axis" },
    legend: { top: 0, textStyle: { color: "#66786f", fontSize: 12 }, data: ["PV", "UV", "报名点击", "归因报名"] },
    grid: { left: 40, right: 20, top: 46, bottom: 30 },
    xAxis: { type: "category", boundaryGap: false, data: props.data.map((item) => item.date.slice(5)), axisLine: { lineStyle: { color: "#dce8e2" } }, axisLabel: { color: "#718279" } },
    yAxis: { type: "value", minInterval: 1, splitLine: { lineStyle: { color: "#edf3f0" } }, axisLabel: { color: "#718279" } },
    series: [
      { name: "PV", type: "line", smooth: true, showSymbol: false, areaStyle: { opacity: 0.06 }, data: props.data.map((item) => item.pageViews) },
      { name: "UV", type: "line", smooth: true, showSymbol: false, data: props.data.map((item) => item.uniqueVisitors) },
      { name: "报名点击", type: "line", smooth: true, showSymbol: false, data: props.data.map((item) => item.ctaClicks) },
      { name: "归因报名", type: "line", smooth: true, showSymbol: false, data: props.data.map((item) => item.attributedRegistrations) }
    ]
  });
}
</script>

<style scoped>
.analytics-chart {
  width: 100%;
  height: 320px;
}
</style>
