<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card" :class="{ 'public-card-result-only': hasSingleVisibleResult, 'public-card-wide': hasMultipleVisibleResults }">
      <template v-if="!hasRawResults">
        <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
        <p v-if="publisher" class="public-kicker">{{ publisher }}</p>
        <h1>{{ pageTitleFor(settings, "dinner", "晚宴桌号查询") }}</h1>
        <p v-if="subtitle" class="public-subtitle">{{ subtitle }}</p>
      </template>

      <n-alert v-if="!settings" class="public-alert" :type="settingsError ? 'warning' : 'info'" :bordered="false">
        {{ settingsError || "查询设置加载中" }}
      </n-alert>
      <n-alert v-else-if="announcement" class="public-alert" type="info" :bordered="false">
        {{ announcement }}
      </n-alert>
      <n-alert v-if="settings && !settings.dinnerQueryEnabled" class="public-alert" type="warning" :bordered="false">
        当前会议暂未开放晚宴查询
      </n-alert>
      <n-alert v-else-if="settings && !windowState.available" class="public-alert" type="warning" :bordered="false">
        {{ windowState.message }}
      </n-alert>

      <n-form v-else-if="settings && !hasRawResults" label-placement="top" class="public-form">
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

      <div v-if="resultItems.length" class="public-multi-results">
        <public-result-ticket
          v-for="item in resultItems"
          :key="item.result.attendeeId"
          kind="dinner"
          :attendee-name="item.result.attendeeName"
          :rows="item.rows"
          :notice="dinnerNotice"
        />
      </div>
      <public-result-checkin-action
        v-if="resultItems.length === 1 && pageResultCheckinEnabled(settings, 'dinner')"
        :settings="settings"
        :public-query-token="publicQueryToken || settings?.publicQueryToken"
        :values="values"
        page-key="dinner"
      />
      <n-alert v-if="messageText" class="public-alert" type="warning" :bordered="false">
        {{ messageText }}
      </n-alert>
      <section v-if="hasRawResults && !resultItems.length" class="public-result">
        <n-alert type="info" :bordered="false">当前未配置可展示的晚宴结果字段</n-alert>
      </section>
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
import PublicResultCheckinAction from "./PublicResultCheckinAction.vue";
import PublicResultTicket from "./PublicResultTicket.vue";
import {
  buildDinnerRows,
  normalizePublicQueryError,
  pageAnnouncementFor,
  pageButtonTextFor,
  pageDescriptionFor,
  pageNoticeFor,
  pagePublisherFor,
  pageResultCheckinEnabled,
  pageTitleFor,
  publicPageStyle,
  publicQueryResults,
  queryWindowState
} from "./queryDisplay";

const route = useRoute();
const store = useMeetingStore();
const message = useMessage();
const loading = ref(false);
const settings = ref<MeetingQuerySettings | null>(null);
const settingsError = ref("");
const results = ref<DinnerQueryResult[]>([]);
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

const publicQueryToken = computed(() => String(route.query.token || ""));
const activeFields = computed(() => settings.value?.fields || []);
const publisher = computed(() => pagePublisherFor(settings.value, "dinner"));
const subtitle = computed(() => pageDescriptionFor(settings.value, "dinner"));
const announcement = computed(() => pageAnnouncementFor(settings.value, "dinner"));
const buttonText = computed(() => pageButtonTextFor(settings.value, "dinner"));
const dinnerNotice = computed(() => pageNoticeFor(settings.value, "dinner"));
const windowState = computed(() => queryWindowState(settings.value));
const resultItems = computed(() => results.value
  .map((result) => ({ result, rows: buildDinnerRows(settings.value, result) }))
  .filter((item) => item.rows.length));
const hasRawResults = computed(() => results.value.length > 0);
const hasSingleVisibleResult = computed(() => resultItems.value.length === 1);
const hasMultipleVisibleResults = computed(() => resultItems.value.length > 1);
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.value?.logoUrl));

onMounted(loadSettings);

async function loadSettings() {
  try {
    settings.value = await store.loadPublicQuerySettings({
      publicQueryToken: publicQueryToken.value || undefined
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
  results.value = [];
  messageText.value = "";
  try {
    results.value = publicQueryResults(await store.publicDinnerQuery({
      publicQueryToken: publicQueryToken.value || settings.value.publicQueryToken,
      values,
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value
    }));
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : "晚宴查询失败";
    messageText.value = normalizePublicQueryError(rawMessage, settings.value, "dinner");
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
