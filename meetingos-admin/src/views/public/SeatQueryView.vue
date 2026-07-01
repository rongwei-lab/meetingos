<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card" :class="{ 'public-card-result-only': result && resultRows.length, 'public-card-seat-map': result?.seatMap }">
      <template v-if="!result || !resultRows.length">
        <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
        <p v-if="publisher" class="public-kicker">{{ publisher }}</p>
        <h1>{{ pageTitleFor(settings, "seat", "座位查询") }}</h1>
        <p v-if="subtitle" class="public-subtitle">{{ subtitle }}</p>
      </template>

      <n-alert v-if="!settings" class="public-alert" :type="errorText ? 'warning' : 'info'" :bordered="false">
        {{ errorText || "查询设置加载中" }}
      </n-alert>
      <n-alert v-else-if="announcement" class="public-alert" type="info" :bordered="false">
        {{ announcement }}
      </n-alert>
      <n-alert v-if="settings && !settings.seatQueryEnabled" class="public-alert" type="warning" :bordered="false">
        座位查询尚未开放
      </n-alert>
      <n-alert v-else-if="settings && !windowState.available" class="public-alert" type="warning" :bordered="false">
        {{ windowState.message }}
      </n-alert>

      <n-form v-else-if="settings && !result" label-placement="top" class="public-form">
        <n-form-item v-for="field in activeFields" :key="field" :label="fieldLabel(field)">
          <n-input v-model:value="values[field]" :placeholder="`请输入${fieldLabel(field)}`" />
        </n-form-item>
        <n-form-item v-if="settings.captchaEnabled" :label="captchaQuestion || '验证码'">
          <div class="captcha-row">
            <n-input v-model:value="captchaAnswer" placeholder="请输入计算结果" />
            <n-button secondary :loading="captchaLoading" @click="loadCaptcha">刷新</n-button>
          </div>
        </n-form-item>
        <n-button type="primary" block :color="settings?.themeColor" :loading="loading" @click="query">
          {{ buttonText }}
        </n-button>
      </n-form>

      <n-alert v-if="settings && errorText" class="public-alert" type="warning" :bordered="false">
        {{ errorText }}
      </n-alert>

      <public-result-ticket
        v-if="result && resultRows.length"
        kind="seat"
        :attendee-name="result.attendeeName"
        :rows="resultRows"
        :notice="seatNotice"
      />
      <public-result-checkin-action
        v-if="result && resultRows.length && settings?.checkinEnabled"
        :settings="settings"
        :meeting-id="queryMeetingId"
        :meeting-code="queryMeetingCode || null"
        :values="values"
      />
      <public-seat-map v-if="result && resultRows.length" :map="result.seatMap" />
      <section v-else-if="result" class="public-result">
        <n-alert type="info" :bordered="false">当前未配置可展示的座位结果字段</n-alert>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from "vue";
import { useRoute } from "vue-router";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";
import type { MeetingQuerySettings, PublicQueryField, SeatQueryResult } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import PublicSeatMap from "./PublicSeatMap.vue";
import PublicResultCheckinAction from "./PublicResultCheckinAction.vue";
import PublicResultTicket from "./PublicResultTicket.vue";
import {
  buildSeatRows,
  normalizePublicQueryError,
  pageAnnouncementFor,
  pageButtonTextFor,
  pageDescriptionFor,
  pageNoticeFor,
  pagePublisherFor,
  pageTitleFor,
  publicPageStyle,
  queryWindowState
} from "./queryDisplay";

const route = useRoute();
const store = useMeetingStore();
const message = useMessage();
const loading = ref(false);
const settings = ref<MeetingQuerySettings | null>(null);
const result = ref<SeatQueryResult | null>(null);
const errorText = ref("");
const captchaId = ref<string | null>(null);
const captchaQuestion = ref("");
const captchaAnswer = ref("");
const captchaLoading = ref(false);
const values = reactive<Record<PublicQueryField, string>>({
  NAME: "",
  PHONE: "",
  PHONE_LAST4: "",
  ATTENDEE_CODE: "",
  ORGANIZATION: "",
  TITLE: "",
  EMAIL: ""
});
const queryMeetingId = computed(() => {
  const raw = route.query.meetingId ?? route.query.mid;
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw);
  return Number.isFinite(parsed) ? parsed : null;
});
const queryMeetingCode = computed(() => String(route.query.meetingCode || route.params.meetingCode || ""));
const activeFields = computed(() => settings.value?.fields || []);
const publisher = computed(() => pagePublisherFor(settings.value, "seat"));
const subtitle = computed(() => pageDescriptionFor(settings.value, "seat"));
const announcement = computed(() => pageAnnouncementFor(settings.value, "seat"));
const buttonText = computed(() => pageButtonTextFor(settings.value, "seat"));
const seatNotice = computed(() => pageNoticeFor(settings.value, "seat"));
const windowState = computed(() => queryWindowState(settings.value));
const resultRows = computed(() => buildSeatRows(settings.value, result.value));
const hasTicketResult = computed(() => Boolean(result.value && resultRows.value.length));
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.value?.logoUrl));

onMounted(loadSettings);

watchEffect(() => {
  // 成功结果页需要贴近手机截图的“整张票据”视觉，因此隐藏公共页外层品牌头。
  document.body.classList.toggle("public-result-mode", hasTicketResult.value);
});

onBeforeUnmount(() => {
  document.body.classList.remove("public-result-mode");
});

async function loadSettings() {
  try {
    settings.value = await store.loadPublicQuerySettings({
      meetingId: queryMeetingId.value,
      meetingCode: queryMeetingCode.value || undefined
    });
    if (settings.value.captchaEnabled) {
      await loadCaptcha();
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : "查询设置加载失败";
    message.error(errorText.value);
  }
}

async function query() {
  if (!settings.value) return;
  for (const field of activeFields.value) {
    if (!values[field].trim()) {
      message.warning(`请输入${fieldLabel(field)}`);
      return;
    }
  }
  if (settings.value.captchaEnabled && !captchaAnswer.value.trim()) {
    message.warning("请输入验证码");
    return;
  }
  loading.value = true;
  result.value = null;
  errorText.value = "";
  try {
    result.value = await store.publicSeatQuery({
      meetingId: queryMeetingId.value,
      meetingCode: queryMeetingCode.value || undefined,
      values,
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value
    });
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : "座位查询失败";
    errorText.value = normalizePublicQueryError(rawMessage, settings.value, "seat");
    message.error(errorText.value);
    if (settings.value.captchaEnabled) {
      await loadCaptcha();
    }
  } finally {
    loading.value = false;
  }
}

async function loadCaptcha() {
  captchaLoading.value = true;
  try {
    const captcha = await store.loadPublicCaptcha();
    captchaId.value = captcha.captchaId || null;
    captchaQuestion.value = captcha.question || "验证码";
    captchaAnswer.value = "";
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : "验证码加载失败";
  } finally {
    captchaLoading.value = false;
  }
}

function fieldLabel(field: PublicQueryField) {
  return settings.value?.fieldOptions.find((item) => item.field === field)?.label || field;
}
</script>

<style scoped src="./public.css"></style>
