<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card">
      <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
      <p v-if="publisher" class="public-kicker">{{ publisher }}</p>
      <h1>{{ pageTitleFor(settings, "entry", "参会人入口") }}</h1>
      <p class="public-subtitle">{{ subtitle }}</p>

      <n-alert v-if="announcement" class="public-alert" type="info" :bordered="false">
        {{ announcement }}
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
          :disabled="queryDisabled || !anyPublicEntryEnabled"
          @click="goPublic('query')"
        >
          综合查询
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
          :disabled="queryDisabled || !settings?.dinnerQueryEnabled"
          @click="goPublic('dinner-query')"
        >
          查询晚宴桌号
        </n-button>
        <n-button
          block
          size="large"
          :disabled="queryDisabled || !settings?.selfCheckinEnabled"
          @click="goPublic('checkin')"
        >
          自助签到
        </n-button>
        <n-button block size="large" @click="$router.push({ path: '/public/checkin-code', query: $route.query })">查看签到码</n-button>
      </n-space>

      <n-alert v-if="settings && !anyPublicEntryEnabled" type="warning" :bordered="false" class="public-alert">
        当前会议暂未开放公开查询和自助签到。
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
import { pageAnnouncementFor, pageDescriptionFor, pagePublisherFor, pageTitleFor, publicPageStyle, queryWindowState } from "./queryDisplay";

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
const anyPublicEntryEnabled = computed(() => Boolean(
  settings.value?.seatQueryEnabled ||
  settings.value?.roomQueryEnabled ||
  settings.value?.dinnerQueryEnabled ||
  settings.value?.selfCheckinEnabled
));
const publisher = computed(() => pagePublisherFor(settings.value, "entry"));
const subtitle = computed(() => pageDescriptionFor(settings.value, "entry", "查询座位、住宿、晚宴桌号，或完成自助签到。"));
const announcement = computed(() => pageAnnouncementFor(settings.value, "entry"));
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

function goPublic(target: "query" | "seat-query" | "room-query" | "dinner-query" | "checkin") {
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
