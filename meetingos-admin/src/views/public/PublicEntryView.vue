<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card">
      <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
      <p v-if="settings?.publisher" class="public-kicker">{{ settings.publisher }}</p>
      <h1>{{ pageTitle(settings, "参会人入口") }}</h1>
      <p class="public-subtitle">{{ settings?.pageDescription || "查询座位、住宿房间，或打开个人签到码。" }}</p>

      <n-alert v-if="settings?.announcement" class="public-alert" type="info" :bordered="false">
        {{ settings.announcement }}
      </n-alert>
      <n-alert v-if="settings && !windowState.available" class="public-alert" type="warning" :bordered="false">
        {{ windowState.message }}
      </n-alert>

      <n-space vertical size="large">
        <n-button
          type="primary"
          block
          size="large"
          :color="settings?.themeColor"
          :disabled="queryDisabled || !settings || (!settings.seatQueryEnabled && !settings.roomQueryEnabled)"
          @click="goPublic('query')"
        >
          查询座位和房间号
        </n-button>
        <n-button
          block
          size="large"
          :disabled="queryDisabled || !settings?.seatQueryEnabled"
          @click="goPublic('seat-query')"
        >
          查询座位
        </n-button>
        <n-button
          block
          size="large"
          :disabled="queryDisabled || !settings?.roomQueryEnabled"
          @click="goPublic('room-query')"
        >
          查询房间号
        </n-button>
        <n-button
          block
          size="large"
          :disabled="queryDisabled || !settings?.checkinEnabled"
          @click="goPublic('checkin')"
        >
          自助签到
        </n-button>
        <n-button block size="large" @click="$router.push({ path: '/public/checkin-code', query: $route.query })">查看签到码</n-button>
      </n-space>

      <n-alert v-if="settings && !settings.seatQueryEnabled && !settings.roomQueryEnabled && !settings.checkinEnabled" type="warning" :bordered="false" class="public-alert">
        当前会议暂未开放座位、住宿查询和自助签到。
      </n-alert>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";
import type { MeetingQuerySettings } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import { pageTitle, publicPageStyle, queryWindowState } from "./queryDisplay";

const route = useRoute();
const router = useRouter();
const message = useMessage();
const store = useMeetingStore();
const settings = ref<MeetingQuerySettings | null>(null);
const meetingCode = computed(() => String(route.params.meetingCode || route.query.meetingCode || ""));
const meetingId = computed(() => {
  const raw = route.query.meetingId ?? route.query.mid;
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw);
  return Number.isFinite(parsed) ? parsed : null;
});
const windowState = computed(() => queryWindowState(settings.value));
const queryDisabled = computed(() => Boolean(settings.value && !windowState.value.available));
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.value?.logoUrl));

onMounted(async () => {
  try {
    settings.value = await store.loadPublicQuerySettings({
      meetingId: meetingId.value,
      meetingCode: meetingCode.value || undefined
    });
  } catch (error) {
    message.error(error instanceof Error ? error.message : "查询设置加载失败");
  }
});

function goPublic(target: "query" | "seat-query" | "room-query" | "checkin") {
  void router.push({
    path: `/public/${target}`,
    query: {
      ...route.query,
      meetingCode: meetingCode.value || settings.value?.meetingCode || undefined
    }
  });
}
</script>

<style scoped src="./public.css"></style>
