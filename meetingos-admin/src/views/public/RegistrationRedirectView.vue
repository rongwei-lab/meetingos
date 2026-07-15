<template>
  <main class="public-page">
    <section class="public-card">
      <h1 class="registration-title">{{ registrationTitle }}</h1>
      <n-alert :type="alertType" :bordered="false">
        {{ displayMessage }}
      </n-alert>
      <n-button type="primary" block class="action" :loading="loading || redirecting" :disabled="!registration?.open || !registration.registrationUrl || redirecting" @click="goTduck">
        前往报名表
      </n-button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { http, unwrapApi } from "@/api/http";
import { createRegistrationIntent } from "@/api/invitations";
import { extractInvitationShareTokenFragment } from "@/utils/invitationActions";
import { appendMeetingAttributionFragment } from "@/utils/invitationAttribution";
import { createClientEventId } from "@/utils/clientEventId";
import { sanitizeExternalRedirectUrl } from "@/utils/safeUrl";

interface RegistrationInfo {
  meetingId: number;
  meetingCode: string;
  meetingName: string;
  meetingStatus: string;
  open: boolean;
  message: string;
  formName?: string;
  formKey?: string;
  registrationUrl?: string;
}

const route = useRoute();
const loading = ref(false);
const redirecting = ref(false);
const registration = ref<RegistrationInfo | null>(null);
const errorMessage = ref("");
const meetingCode = computed(() => String(route.params.meetingCode || ""));
const alertType = computed(() => (registration.value?.open ? "success" : errorMessage.value ? "error" : "warning"));
const registrationTitle = computed(() => formatRegistrationTitle(registration.value?.formName || registration.value?.meetingName || "会议报名"));
const displayMessage = computed(() => {
  if (loading.value) return "正在读取报名配置...";
  if (errorMessage.value) return errorMessage.value;
  if (!registration.value) return "未找到会议报名配置";
  return registration.value.message || (registration.value.open ? "报名开放，即将跳转到 TDuck 报名表" : "报名暂未开放");
});

let mounted = false;
let loadGeneration = 0;
let redirectTimer: number | undefined;
let pendingIntentEventId: string | null = null;
let pendingIntentShareToken = "";

onMounted(() => {
  mounted = true;
  void loadRegistration();
});

onBeforeUnmount(() => {
  mounted = false;
  loadGeneration += 1;
  clearRedirectTimer();
});

watch(meetingCode, (nextCode, previousCode) => {
  if (!mounted || nextCode === previousCode) return;
  void loadRegistration();
});

async function goTduck() {
  clearRedirectTimer();
  const safeUrl = sanitizeExternalRedirectUrl(registration.value?.registrationUrl);
  if (!safeUrl) {
    errorMessage.value = "报名链接不在允许的安全范围内，请联系会议管理员";
    return;
  }

  const sourceHash = route.hash || window.location.hash;
  const shareToken = extractInvitationShareTokenFragment(sourceHash);
  if (!shareToken) {
    navigateToTduck(safeUrl, sourceHash);
    return;
  }
  if (redirecting.value) return;

  const generation = loadGeneration;
  const code = meetingCode.value;
  const eventId = pendingIntentEventId && pendingIntentShareToken === shareToken
    ? pendingIntentEventId
    : createClientEventId();
  pendingIntentEventId = eventId;
  pendingIntentShareToken = shareToken;
  redirecting.value = true;
  let attributionHash = "";
  try {
    const intent = await createRegistrationIntent(shareToken, eventId);
    if (!isCurrentLoad(generation, code)) return;
    attributionHash = `#mos_at=${intent.attributionToken}`;
    pendingIntentEventId = null;
    pendingIntentShareToken = "";
  } catch {
    if (!isCurrentLoad(generation, code)) return;
    // 渠道归因是增强能力，换取失败时仍按普通报名进入 TDuck，不能影响主报名流程。
  } finally {
    if (isCurrentLoad(generation, code)) redirecting.value = false;
  }
  if (isCurrentLoad(generation, code)) navigateToTduck(safeUrl, attributionHash);
}

async function loadRegistration() {
  const code = meetingCode.value;
  const generation = ++loadGeneration;
  clearRedirectTimer();
  redirecting.value = false;
  pendingIntentEventId = null;
  pendingIntentShareToken = "";
  registration.value = null;
  if (!code) {
    errorMessage.value = "报名链接缺少会议编号";
    loading.value = false;
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  try {
    const response = unwrapApi(await http.get<RegistrationInfo>(`/public/meetings/${encodeURIComponent(code)}/registration`));
    if (!isCurrentLoad(generation, code)) return;
    registration.value = response;
    // 保持 /r/{code} 作为真正中转页：开放报名时自动进入 TDuck，用户仍可在跳转前看到会议和状态。
    if (response.open && response.registrationUrl) {
      redirectTimer = window.setTimeout(() => {
        redirectTimer = undefined;
        if (isCurrentLoad(generation, code)) void goTduck();
      }, 700);
    }
  } catch (error) {
    if (!isCurrentLoad(generation, code)) return;
    errorMessage.value = error instanceof Error ? error.message : "报名配置读取失败";
  } finally {
    if (isCurrentLoad(generation, code)) loading.value = false;
  }
}

function isCurrentLoad(generation: number, code: string) {
  return mounted && generation === loadGeneration && code === meetingCode.value;
}

function clearRedirectTimer() {
  if (redirectTimer !== undefined) window.clearTimeout(redirectTimer);
  redirectTimer = undefined;
}

function navigateToTduck(registrationUrl: string, hash: string) {
  // 归因令牌只允许出现在 fragment，既不发送给 MeetingOS/TDuck 的页面 GET，也不会污染服务端访问日志。
  // TDuck 部署补丁会在前端读取后写入 sessionStorage，并在提交时放入 extValue。
  window.location.href = appendMeetingAttributionFragment(registrationUrl, hash);
}

function formatRegistrationTitle(title: string) {
  const trimmed = title.trim();
  if (!trimmed) return "会议报名";
  /*
   * TDuck 标题里常把“报名回执”作为独立行展示。当前 TDuck API 可能只返回空格，
   * 这里仅在展示层把这个固定尾缀独立成行；如果后续 API 返回真实换行，CSS 会原样保留。
   */
  return trimmed.replace(/\s+报名回执$/, "\n报名回执");
}
</script>

<style scoped src="./public.css"></style>
<style scoped>
.registration-title {
  text-align: center;
  white-space: pre-line;
}
</style>
