<template>
  <section v-if="validMap" class="public-seat-map" aria-label="我的座位示意图">
    <header class="public-seat-map-header">
      <div>
        <h2>我的座位示意图</h2>
        <span>{{ seatMap.areaName }} · 第 {{ seatMap.currentRowNo }} 排 · {{ seatMap.currentSeatNo }} 座</span>
      </div>
    </header>

    <div class="public-seat-map-stage">主席台 / 舞台</div>

    <div ref="scrollBoxRef" class="public-seat-map-scroll">
      <div class="public-seat-map-grid" :style="gridStyle">
        <template v-for="rowNo in seatMap.rowCount" :key="`row-${rowNo}`">
          <div class="public-seat-map-row-index">{{ rowNo }}</div>
          <div
            v-for="seatNo in seatMap.seatsPerRow"
            :key="`${rowNo}-${seatNo}`"
            class="public-seat-map-cell"
            :class="{ current: isCurrentSeat(rowNo, seatNo) }"
            :aria-label="isCurrentSeat(rowNo, seatNo) ? `我的座位，第 ${rowNo} 排第 ${seatNo} 座` : `第 ${rowNo} 排第 ${seatNo} 座`"
          >
            <span v-if="isCurrentSeat(rowNo, seatNo)">我的座位</span>
          </div>
          <div class="public-seat-map-row-index">{{ rowNo }}</div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import type { CSSProperties } from "vue";
import type { PublicSeatMap } from "@/utils/types";

const props = defineProps<{
  map?: PublicSeatMap | null;
}>();

const scrollBoxRef = ref<HTMLElement | null>(null);

const validMap = computed(() => {
  const map = props.map;
  if (!map) return false;
  return map.rowCount > 0
    && map.seatsPerRow > 0
    && map.currentRowNo > 0
    && map.currentSeatNo > 0
    && map.currentRowNo <= map.rowCount
    && map.currentSeatNo <= map.seatsPerRow;
});

const seatMap = computed(() => props.map as PublicSeatMap);

const gridStyle = computed<CSSProperties>(() => ({
  gridTemplateColumns: `28px repeat(${seatMap.value.seatsPerRow}, minmax(34px, 1fr)) 28px`
}));

function isCurrentSeat(rowNo: number, seatNo: number) {
  return rowNo === seatMap.value.currentRowNo && seatNo === seatMap.value.currentSeatNo;
}

async function scrollCurrentSeatIntoView(behavior: ScrollBehavior = "smooth") {
  if (!validMap.value) return;
  await nextTick();
  const scrollBox = scrollBoxRef.value;
  const currentCell = scrollBox?.querySelector<HTMLElement>(".public-seat-map-cell.current");
  if (!scrollBox || !currentCell) return;

  // 座位图在手机端会横向滚动，这里只移动内部滚动条，避免把整页撑出横向滚动。
  const targetLeft = currentCell.offsetLeft - (scrollBox.clientWidth - currentCell.clientWidth) / 2;
  scrollBox.scrollTo({
    left: Math.max(0, targetLeft),
    behavior
  });
}

onMounted(() => {
  void scrollCurrentSeatIntoView("auto");
});

watch(
  () => [
    props.map?.areaName,
    props.map?.rowCount,
    props.map?.seatsPerRow,
    props.map?.currentRowNo,
    props.map?.currentSeatNo
  ],
  () => {
    void scrollCurrentSeatIntoView();
  }
);
</script>

<style scoped>
.public-seat-map {
  width: min(400px, 100%);
  min-width: 0;
  margin: 16px auto 0;
  padding: 18px;
  border: 1px solid rgba(79, 70, 229, 0.14);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 45px rgba(46, 54, 103, 0.12);
  box-sizing: border-box;
}

.public-seat-map-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.public-seat-map-header h2 {
  margin: 0 0 4px;
  color: #172033;
  font-size: 18px;
  font-weight: 900;
  line-height: 1.3;
}

.public-seat-map-header span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
}

.public-seat-map-stage {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 999px;
  background: linear-gradient(135deg, #e8e9ff 0%, #f2efff 100%);
  color: #4c39df;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.3;
  text-align: center;
}

.public-seat-map-scroll {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
  padding-bottom: 2px;
}

.public-seat-map-grid {
  display: grid;
  gap: 8px;
  min-width: max-content;
}

.public-seat-map-row-index,
.public-seat-map-cell {
  min-width: 0;
  min-height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  line-height: 1;
}

.public-seat-map-row-index {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 800;
}

.public-seat-map-cell {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.public-seat-map-cell.current {
  min-width: 76px;
  border-color: rgba(76, 57, 223, 0.45);
  background: linear-gradient(135deg, #5f63f1 0%, #4c39df 100%);
  box-shadow: 0 10px 24px rgba(76, 57, 223, 0.22);
}

.public-seat-map-cell.current span {
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .public-seat-map {
    width: 100%;
    padding: 16px;
    border-radius: 18px;
  }

  .public-seat-map-grid {
    gap: 7px;
  }
}
</style>
