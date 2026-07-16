<template>
  <div class="invitation-map" :class="{ 'has-error': errorMessage }">
    <div ref="mapElement" class="map-surface" />
    <div v-if="errorMessage" class="map-fallback">
      <n-icon :component="LocationOutline" />
      <strong>{{ address || "会议地点" }}</strong>
      <span>{{ errorMessage }}</span>
    </div>
    <a v-if="safeNavigationUrl" class="navigation-link" :href="safeNavigationUrl" target="_blank" rel="noopener noreferrer">
      <n-icon :component="NavigateOutline" />
      打开导航
    </a>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { LocationOutline, NavigateOutline } from "@vicons/ionicons5";
import { createAmapNavigationUrl, isDefaultAmapMarkerUrl, parseSafeHttpsUrl } from "@/utils/amapNavigation";

const props = defineProps<{
  longitude: number;
  latitude: number;
  address: string;
  zoom?: number;
  navigationUrl?: string;
}>();

const mapElement = ref<HTMLElement>();
const errorMessage = ref("");
let map: { destroy?: () => void; add: (marker: unknown) => void } | undefined;
let initializeGeneration = 0;
const safeNavigationUrl = computed(() => {
  const configuredUrl = parseSafeHttpsUrl(props.navigationUrl);
  return configuredUrl && !isDefaultAmapMarkerUrl(configuredUrl)
    ? configuredUrl.toString()
    : createAmapNavigationUrl(props.longitude, props.latitude, props.address);
});

onMounted(() => {
  void initializeMap();
});

onBeforeUnmount(() => {
  initializeGeneration += 1;
  map?.destroy?.();
});

watch(
  () => [props.longitude, props.latitude, props.address, props.zoom] as const,
  () => void initializeMap()
);

async function initializeMap() {
  const generation = ++initializeGeneration;
  map?.destroy?.();
  map = undefined;
  errorMessage.value = "";
  const key = import.meta.env.VITE_AMAP_KEY;
  if (!key) {
    errorMessage.value = "地图服务未配置，已显示地址信息";
    return;
  }
  if (!mapElement.value) return;
  try {
    if (import.meta.env.VITE_AMAP_SECURITY_CODE) {
      window._AMapSecurityConfig = { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE };
    }
    const { default: AMapLoader } = await import("@amap/amap-jsapi-loader");
    const AMap = await AMapLoader.load({ key, version: "2.0", plugins: ["AMap.Scale"] });
    if (generation !== initializeGeneration || !mapElement.value) return;
    const createdMap = new AMap.Map(mapElement.value, {
      center: [props.longitude, props.latitude],
      zoom: props.zoom || 15,
      viewMode: "2D",
      mapStyle: "amap://styles/whitesmoke"
    }) as { destroy?: () => void; add: (marker: unknown) => void };
    map = createdMap;
    const marker = new AMap.Marker({ position: [props.longitude, props.latitude], title: props.address });
    createdMap.add(marker);
  } catch {
    if (generation !== initializeGeneration) return;
    errorMessage.value = "地图加载失败，已降级为地址与导航链接";
  }
}

</script>

<style scoped>
.invitation-map {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #eef4f1;
}

.map-surface {
  width: 100%;
  height: 100%;
}

.map-fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 7px;
  padding: 18px;
  color: #174d37;
  background: #eef4f1;
  text-align: center;
}

.map-fallback .n-icon {
  font-size: 27px;
}

.map-fallback strong {
  font-size: 15px;
}

.map-fallback span {
  color: #6d8077;
  font-size: 12px;
  line-height: 1.5;
}

.navigation-link {
  position: absolute;
  right: 10px;
  bottom: 10px;
  min-height: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #08784f;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #70ad94;
  border-radius: 5px;
  font-size: 13px;
  font-weight: 650;
}
</style>
