<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card">
      <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
      <p v-if="settings?.publisher" class="public-kicker">{{ settings.publisher }}</p>
      <h1>{{ pageTitle(settings, "晚宴桌号查询") }}</h1>
      <p v-if="subtitle" class="public-subtitle">{{ subtitle }}</p>

      <n-alert v-if="!settings" class="public-alert" :type="settingsError ? 'warning' : 'info'" :bordered="false">
        {{ settingsError || "查询设置加载中" }}
      </n-alert>
      <n-alert v-else-if="settings.announcement" class="public-alert" type="info" :bordered="false">
        {{ settings.announcement }}
      </n-alert>
      <n-alert v-if="settings && !settings.dinnerQueryEnabled" class="public-alert" type="warning" :bordered="false">
        当前会议暂未开放晚宴查询
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
          {{ settings?.buttonText || "立即查询" }}
        </n-button>
      </n-form>

      <public-result-ticket
        v-if="result && resultRows.length"
        kind="dinner"
        :attendee-name="result.attendeeName"
        :rows="resultRows"
        :notice="settings?.dinnerNotice"
      />
      <n-alert v-else-if="messageText" class="public-alert" type="warning" :bordered="false">
        {{ messageText }}
      </n-alert>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";
import type { DinnerQueryResult, MeetingQuerySettings, PublicQueryField } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import PublicResultTicket from "./PublicResultTicket.vue";
import {
  buildDinnerRows,
  normalizePublicQueryError,
  pageTitle,
  publicPageStyle,
  queryWindowState
} from "./queryDisplay";

const route = useRoute();
const store = useMeetingStore();
const message = useMessage();
const loading = ref(false);
const settings = ref<MeetingQuerySettings | null>(null);
const settingsError = ref("");
const result = ref<DinnerQueryResult | null>(null);
const messageText = ref("");
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
const subtitle = computed(() => settings.value?.pageDescription || settings.value?.meetingName || "");
const windowState = computed(() => queryWindowState(settings.value));
const resultRows = computed(() => buildDinnerRows(settings.value, result.value));
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.value?.logoUrl));

onMounted(loadSettings);

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
    const text = error instanceof Error ? error.message : "查询设置加载失败";
    settingsError.value = text;
    message.error(text);
  }
}

async function query() {
  if (!settings.value || !settings.value.dinnerQueryEnabled) return;
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
  messageText.value = "";
  try {
    result.value = await store.publicDinnerQuery({
      meetingId: queryMeetingId.value,
      meetingCode: queryMeetingCode.value || undefined,
      values,
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value
    });
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : "晚宴查询失败";
    messageText.value = normalizePublicQueryError(rawMessage, settings.value);
    if (settings.value?.captchaEnabled) {
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
    const text = error instanceof Error ? error.message : "验证码加载失败";
    settingsError.value = text;
  } finally {
    captchaLoading.value = false;
  }
}

function fieldLabel(field: PublicQueryField) {
  return settings.value?.fieldOptions.find((item) => item.field === field)?.label || field;
}
</script>

<style scoped src="./public.css"></style>
