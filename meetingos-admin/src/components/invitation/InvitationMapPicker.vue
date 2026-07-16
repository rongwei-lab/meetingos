<template>
  <n-modal :show="show" preset="card" title="地图选点" class="map-picker-modal" style="width: 720px; max-width: calc(100vw - 24px)" @update:show="handleVisibleChange">
    <div class="map-picker">
      <n-alert v-if="!amapConfigured" type="warning" :bordered="false">
        当前构建未配置高德地图密钥，不能使用地图选点。
      </n-alert>
      <div class="map-search-row">
        <n-input v-model:value="keyword" placeholder="搜索地点、酒店、会场或详细地址" :disabled="!amapConfigured" @keyup.enter="searchKeyword" />
        <n-button type="primary" :loading="searching" :disabled="!amapConfigured || loadingMap" @click="searchKeyword">
          <template #icon><n-icon :component="SearchOutline" /></template>
          搜索
        </n-button>
      </div>

      <div v-if="searchResults.length" class="map-result-list">
        <button v-for="result in searchResults" :key="result.id" type="button" @click="selectSearchResult(result)">
          <strong>{{ result.name }}</strong>
          <span>{{ result.address || `${result.longitude}, ${result.latitude}` }}</span>
        </button>
      </div>
      <n-empty v-else-if="searched && !searching" size="small" description="未找到匹配地点，可直接在地图上点击选点" />

      <div class="map-surface-wrap">
        <div ref="mapElement" class="map-picker-surface" />
        <div v-if="loadingMap" class="map-picker-mask"><n-spin size="small" /><span>正在加载地图…</span></div>
        <div v-else-if="mapError" class="map-picker-mask warning"><n-icon :component="LocationOutline" /><span>{{ mapError }}</span></div>
      </div>

      <div class="picked-summary">
        <n-icon :component="LocationOutline" />
        <div>
          <strong>{{ pickedAddress }}</strong>
          <span>{{ coordinateSummary }}</span>
        </div>
      </div>
    </div>
    <template #footer>
      <n-space justify="end">
        <n-button @click="handleVisibleChange(false)">取消</n-button>
        <n-button type="primary" :disabled="!amapConfigured" @click="confirmSelection">确认位置</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { LocationOutline, SearchOutline } from "@vicons/ionicons5";
import { NAlert, NButton, NEmpty, NIcon, NInput, NModal, NSpace, NSpin } from "naive-ui";
import { createAmapNavigationUrl } from "@/utils/amapNavigation";

const props = defineProps<{
  show: boolean;
  address: string;
  longitude: number;
  latitude: number;
}>();

const emit = defineEmits<{
  "update:show": [value: boolean];
  confirm: [value: { address: string; longitude: number; latitude: number; navigationUrl: string }];
}>();

interface Coordinates {
  longitude: number;
  latitude: number;
}

interface AMapLngLat {
  lng?: number;
  lat?: number;
  getLng?: () => number;
  getLat?: () => number;
}

interface AMapClickEvent {
  lnglat?: AMapLngLat;
}

interface AMapMap {
  add: (target: unknown) => void;
  destroy?: () => void;
  on: (event: string, handler: (event: AMapClickEvent) => void) => void;
  setCenter?: (position: [number, number]) => void;
}

interface AMapMarker {
  setPosition: (position: [number, number]) => void;
  on: (event: string, handler: (event: AMapClickEvent) => void) => void;
}

interface AMapPlacePoi {
  id?: string;
  name?: string;
  address?: string;
  pname?: string;
  cityname?: string;
  adname?: string;
  location?: AMapLngLat;
}

interface AMapPlaceSearchResult {
  poiList?: {
    pois?: AMapPlacePoi[];
  };
}

interface AMapPlaceSearch {
  search: (keyword: string, callback: (status: string, result: AMapPlaceSearchResult) => void) => void;
}

interface AMapGeocoder {
  getAddress: (position: [number, number], callback: (status: string, result: { regeocode?: { formattedAddress?: string } }) => void) => void;
}

interface AMapApi {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AMapMap;
  Marker: new (options: Record<string, unknown>) => AMapMarker;
  ToolBar?: new () => unknown;
  PlaceSearch?: new (options: Record<string, unknown>) => AMapPlaceSearch;
  Geocoder?: new (options: Record<string, unknown>) => AMapGeocoder;
}

interface SearchResult extends Coordinates {
  id: string;
  name: string;
  address: string;
}

const DEFAULT_LONGITUDE = 121.49;
const DEFAULT_LATITUDE = 31.24;

const mapElement = ref<HTMLElement>();
const keyword = ref("");
const draftAddress = ref("");
const draftLongitude = ref(DEFAULT_LONGITUDE);
const draftLatitude = ref(DEFAULT_LATITUDE);
const loadingMap = ref(false);
const searching = ref(false);
const searched = ref(false);
const mapError = ref("");
const searchResults = ref<SearchResult[]>([]);
const amapConfigured = computed(() => Boolean(import.meta.env.VITE_AMAP_KEY));
const pickedAddress = computed(() => draftAddress.value.trim() || keyword.value.trim() || "会议地点");
const coordinateSummary = computed(() => `${formatCoordinate(draftLongitude.value)}, ${formatCoordinate(draftLatitude.value)}`);

let AMap: AMapApi | undefined;
let map: AMapMap | undefined;
let marker: AMapMarker | undefined;
let placeSearch: AMapPlaceSearch | undefined;
let geocoder: AMapGeocoder | undefined;
let initializeGeneration = 0;

watch(() => props.show, async (show) => {
  if (!show) {
    initializeGeneration += 1;
    disposeMap();
    return;
  }
  resetDraft();
  await nextTick();
  void initializeMap();
}, { immediate: true });

onBeforeUnmount(() => {
  initializeGeneration += 1;
  disposeMap();
});

function handleVisibleChange(value: boolean) {
  emit("update:show", value);
}

async function initializeMap() {
  const generation = ++initializeGeneration;
  disposeMap();
  mapError.value = "";
  const key = import.meta.env.VITE_AMAP_KEY;
  if (!key) {
    mapError.value = "地图服务未配置";
    return;
  }
  if (!mapElement.value) return;
  loadingMap.value = true;
  try {
    if (import.meta.env.VITE_AMAP_SECURITY_CODE) {
      window._AMapSecurityConfig = { securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE };
    }
    const { default: AMapLoader } = await import("@amap/amap-jsapi-loader");
    const loaded = await AMapLoader.load({
      key,
      version: "2.0",
      plugins: ["AMap.PlaceSearch", "AMap.Geocoder", "AMap.ToolBar"]
    });
    if (generation !== initializeGeneration || !mapElement.value) return;
    AMap = loaded as AMapApi;
    const center = [draftLongitude.value, draftLatitude.value] as [number, number];
    map = new AMap.Map(mapElement.value, {
      center,
      zoom: 15,
      viewMode: "2D",
      mapStyle: "amap://styles/whitesmoke"
    });
    marker = new AMap.Marker({ position: center, draggable: true, title: pickedAddress.value });
    map.add(marker);
    if (AMap.ToolBar) map.add(new AMap.ToolBar());
    placeSearch = AMap.PlaceSearch ? new AMap.PlaceSearch({ city: "全国", pageSize: 8 }) : undefined;
    geocoder = AMap.Geocoder ? new AMap.Geocoder({ city: "全国" }) : undefined;
    map.on("click", (event) => updateFromMapPoint(event.lnglat, true));
    marker.on("dragend", (event) => updateFromMapPoint(event.lnglat, true));
  } catch {
    if (generation !== initializeGeneration) return;
    mapError.value = "地图加载失败，请检查高德密钥或网络";
  } finally {
    if (generation === initializeGeneration) loadingMap.value = false;
  }
}

function disposeMap() {
  map?.destroy?.();
  map = undefined;
  marker = undefined;
  placeSearch = undefined;
  geocoder = undefined;
}

function resetDraft() {
  draftAddress.value = props.address || "会议地点";
  draftLongitude.value = normalizeCoordinate(props.longitude, DEFAULT_LONGITUDE);
  draftLatitude.value = normalizeCoordinate(props.latitude, DEFAULT_LATITUDE);
  keyword.value = props.address || "";
  searchResults.value = [];
  searched.value = false;
  mapError.value = "";
}

function searchKeyword() {
  const text = keyword.value.trim();
  if (!text || !amapConfigured.value || searching.value) return;
  if (!placeSearch) {
    mapError.value = loadingMap.value ? "地图仍在加载，请稍后再试" : "地图搜索服务暂不可用";
    return;
  }
  searching.value = true;
  searched.value = true;
  placeSearch.search(text, (status, result) => {
    searching.value = false;
    const pois = status === "complete" ? result.poiList?.pois || [] : [];
    searchResults.value = pois.map((poi, index) => toSearchResult(poi, index)).filter((item): item is SearchResult => Boolean(item));
    if (!searchResults.value.length) mapError.value = "未找到匹配地点，可点击地图选点";
  });
}

function selectSearchResult(result: SearchResult) {
  draftAddress.value = result.address ? `${result.name} · ${result.address}` : result.name;
  setCoordinates(result);
  mapError.value = "";
}

function updateFromMapPoint(point: AMapLngLat | undefined, updateAddress: boolean) {
  const coordinates = toCoordinates(point);
  if (!coordinates) return;
  setCoordinates(coordinates);
  if (updateAddress) reverseGeocode(coordinates);
}

function setCoordinates(coordinates: Coordinates) {
  draftLongitude.value = roundCoordinate(coordinates.longitude);
  draftLatitude.value = roundCoordinate(coordinates.latitude);
  const position: [number, number] = [draftLongitude.value, draftLatitude.value];
  marker?.setPosition(position);
  map?.setCenter?.(position);
}

function reverseGeocode(coordinates: Coordinates) {
  if (!geocoder) return;
  geocoder.getAddress([coordinates.longitude, coordinates.latitude], (status, result) => {
    const address = status === "complete" ? result.regeocode?.formattedAddress : "";
    if (address) draftAddress.value = address;
  });
}

function confirmSelection() {
  const address = pickedAddress.value;
  const longitude = roundCoordinate(draftLongitude.value);
  const latitude = roundCoordinate(draftLatitude.value);
  emit("confirm", {
    address,
    longitude,
    latitude,
    navigationUrl: createAmapNavigationUrl(longitude, latitude, address)
  });
  handleVisibleChange(false);
}

function toSearchResult(poi: AMapPlacePoi, index: number): SearchResult | null {
  const coordinates = toCoordinates(poi.location);
  if (!coordinates || !poi.name) return null;
  const region = [poi.pname, poi.cityname, poi.adname].filter(Boolean).join("");
  const address = [region, poi.address].filter(Boolean).join(" ");
  return {
    id: poi.id || `${poi.name}-${coordinates.longitude}-${coordinates.latitude}-${index}`,
    name: poi.name,
    address,
    longitude: coordinates.longitude,
    latitude: coordinates.latitude
  };
}

function toCoordinates(point: AMapLngLat | undefined): Coordinates | null {
  if (!point) return null;
  const longitude = typeof point.getLng === "function" ? point.getLng() : point.lng;
  const latitude = typeof point.getLat === "function" ? point.getLat() : point.lat;
  if (typeof longitude !== "number" || typeof latitude !== "number") return null;
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null;
  return { longitude: roundCoordinate(longitude), latitude: roundCoordinate(latitude) };
}

function normalizeCoordinate(value: number, fallback: number) {
  return Number.isFinite(value) ? roundCoordinate(value) : fallback;
}

function roundCoordinate(value: number) {
  return Math.round(value * 1_000_000) / 1_000_000;
}

function formatCoordinate(value: number) {
  return String(roundCoordinate(value));
}
</script>

<style scoped>
.map-picker {
  display: grid;
  gap: 12px;
}

.map-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.map-result-list {
  max-height: 150px;
  display: grid;
  gap: 6px;
  overflow-y: auto;
}

.map-result-list button {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #dce8e2;
  border-radius: 6px;
  display: grid;
  gap: 2px;
  color: #24372f;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.map-result-list button:hover {
  border-color: #74aa93;
  background: #f3faf7;
}

.map-result-list strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.map-result-list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #6d8077;
  font-size: 12px;
}

.map-surface-wrap {
  position: relative;
  height: 360px;
  overflow: hidden;
  border: 1px solid #dce8e2;
  border-radius: 7px;
  background: #eef4f1;
}

.map-picker-surface {
  width: 100%;
  height: 100%;
}

.map-picker-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  color: #174d37;
  background: rgba(238, 244, 241, .86);
  font-size: 13px;
}

.map-picker-mask.warning {
  color: #835b16;
}

.map-picker-mask .n-icon {
  font-size: 26px;
}

.picked-summary {
  min-height: 54px;
  padding: 9px 11px;
  border: 1px solid #dce8e2;
  border-radius: 6px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  background: #f7faf8;
  color: #174d37;
}

.picked-summary .n-icon {
  font-size: 20px;
}

.picked-summary div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.picked-summary strong,
.picked-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-summary strong {
  font-size: 13px;
}

.picked-summary span {
  color: #718279;
  font-size: 12px;
}
</style>
