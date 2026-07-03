<template>
  <main class="public-page">
    <section class="public-card">
      <h1 class="registration-title">{{ registrationTitle }}</h1>
      <n-alert :type="alertType" :bordered="false">
        {{ displayMessage }}
      </n-alert>
      <n-button type="primary" block class="action" :loading="loading" :disabled="!registration?.open || !registration.registrationUrl" @click="goTduck">
        前往报名表
      </n-button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { http, unwrapApi } from "@/api/http";
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

onMounted(() => {
  void loadRegistration();
});

function goTduck() {
  const safeUrl = sanitizeExternalRedirectUrl(registration.value?.registrationUrl);
  if (safeUrl) {
    window.location.href = safeUrl;
  } else {
    errorMessage.value = "报名链接不在允许的安全范围内，请联系会议管理员";
  }
}

async function loadRegistration() {
  if (!meetingCode.value) {
    errorMessage.value = "报名链接缺少会议编号";
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  try {
    registration.value = unwrapApi(await http.get<RegistrationInfo>(`/public/meetings/${encodeURIComponent(meetingCode.value)}/registration`));
    // 保持 /r/{code} 作为真正中转页：开放报名时自动进入 TDuck，用户仍可在跳转前看到会议和状态。
    if (registration.value.open && registration.value.registrationUrl) {
      window.setTimeout(goTduck, 700);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "报名配置读取失败";
  } finally {
    loading.value = false;
  }
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
