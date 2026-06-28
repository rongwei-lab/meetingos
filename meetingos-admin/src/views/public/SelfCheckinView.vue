<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card self-checkin-card">
      <template v-if="!result">
        <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
        <p v-if="settings?.publisher" class="public-kicker">{{ settings.publisher }}</p>
        <h1>{{ pageTitle(settings, "会议自助签到") }}</h1>
        <p class="public-subtitle">{{ subtitle }}</p>
      </template>

      <n-alert v-if="!settings" class="public-alert" :type="errorText ? 'warning' : 'info'" :bordered="false">
        {{ errorText || "签到设置加载中" }}
      </n-alert>
      <n-alert v-else-if="settings.announcement && !result" class="public-alert" type="info" :bordered="false">
        {{ settings.announcement }}
      </n-alert>
      <n-alert v-if="settings && !windowState.available && !result" class="public-alert" type="warning" :bordered="false">
        {{ windowState.message }}
      </n-alert>
      <n-alert v-if="settings && !settings.checkinEnabled && !result" class="public-alert" type="warning" :bordered="false">
        签到尚未开放。
      </n-alert>

      <n-form v-else-if="settings && settings.checkinEnabled && windowState.available && !result" label-placement="top" class="public-form">
        <n-form-item v-for="field in activeFields" :key="field" :label="fieldLabel(field)">
          <n-input v-model:value="values[field]" :placeholder="`请输入${fieldLabel(field)}`" />
        </n-form-item>
        <n-form-item v-if="settings.captchaEnabled" :label="captchaQuestion || '验证码'">
          <div class="captcha-row">
            <n-input v-model:value="captchaAnswer" placeholder="请输入计算结果" />
            <n-button secondary :loading="captchaLoading" @click="loadCaptcha">刷新</n-button>
          </div>
        </n-form-item>
        <n-button type="primary" block :color="settings.themeColor" :loading="loading" @click="submitCheckin">
          自助签到
        </n-button>
      </n-form>

      <section v-if="result" class="self-checkin-result" :class="`self-checkin-result-${resultTone}`">
        <div class="self-checkin-icon">{{ resultIcon }}</div>
        <h2>{{ resultTitle }}</h2>
        <p>{{ resultMessage }}</p>
        <div class="self-checkin-meta">
          <div v-if="result.attendeeName">
            <span>参会人</span>
            <strong>{{ result.attendeeName }}</strong>
          </div>
          <div v-if="result.phone">
            <span>手机号</span>
            <strong>{{ result.phone }}</strong>
          </div>
          <div v-if="result.checkedInAt">
            <span>签到时间</span>
            <strong>{{ formatDateTimeSecondLabel(result.checkedInAt) }}</strong>
          </div>
        </div>
        <n-button block secondary :color="settings?.themeColor" @click="resetForm">
          返回
        </n-button>
      </section>

      <n-alert v-if="settings && errorText && !result" class="public-alert" type="warning" :bordered="false">
        {{ errorText }}
      </n-alert>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";
import { formatDateTimeSecondLabel } from "@/utils/meetingDisplay";
import type { MeetingQuerySettings, PublicCheckinResult, PublicQueryField } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import { normalizePublicQueryError, pageTitle, publicPageStyle, queryWindowState } from "./queryDisplay";

const route = useRoute();
const store = useMeetingStore();
const message = useMessage();
const loading = ref(false);
const settings = ref<MeetingQuerySettings | null>(null);
const result = ref<PublicCheckinResult | null>(null);
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
const subtitle = computed(() => settings.value?.pageDescription || settings.value?.meetingName || "请填写报名信息完成现场签到");
const windowState = computed(() => queryWindowState(settings.value));
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.value?.logoUrl));
const resultTone = computed(() => {
  if (result.value?.status === "SUCCESS") return "success";
  if (result.value?.status === "DUPLICATE") return "warning";
  return "error";
});
const resultIcon = computed(() => (resultTone.value === "success" ? "✓" : resultTone.value === "warning" ? "!" : "×"));
const resultTitle = computed(() => {
  if (result.value?.status === "SUCCESS") return "签到成功";
  if (result.value?.status === "DUPLICATE") return "已签到";
  if (result.value?.status === "NOT_APPROVED") return "暂不能签到";
  return "签到失败";
});
const resultMessage = computed(() => {
  if (result.value?.status === "SUCCESS") return "请进入会场并按现场安排就座。";
  /*
   * 后端重复签到文案会带首次签到时间，LocalDateTime 可能包含 T 和微秒。
   * 页面已经在“签到时间”字段里做统一格式化，这里使用固定提示避免再次暴露接口原始格式。
   */
  if (result.value?.status === "DUPLICATE") return "您已完成签到，请勿重复提交。";
  return result.value?.message || "请联系现场工作人员处理。";
});

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
    errorText.value = error instanceof Error ? error.message : "签到设置加载失败";
    message.error(errorText.value);
  }
}

async function submitCheckin() {
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
    result.value = await store.publicSelfCheckin({
      meetingId: queryMeetingId.value,
      meetingCode: queryMeetingCode.value || undefined,
      values,
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value
    });
    if (result.value.status === "SUCCESS") {
      message.success("签到成功");
    } else if (result.value.status === "DUPLICATE") {
      message.warning("您已完成签到");
    } else {
      message.warning(result.value.message || "暂不能签到");
    }
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : "签到失败";
    const failedResult = toFailedCheckinResult(rawMessage);
    if (failedResult) {
      result.value = failedResult;
      message.warning(failedResult.message || "暂不能签到");
    } else {
      errorText.value = normalizePublicQueryError(rawMessage, settings.value);
      message.error(errorText.value);
      if (settings.value.captchaEnabled) {
        await loadCaptcha();
      }
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

async function resetForm() {
  result.value = null;
  errorText.value = "";
  if (settings.value?.captchaEnabled) {
    await loadCaptcha();
  }
}

function fieldLabel(field: PublicQueryField) {
  return settings.value?.fieldOptions.find((item) => item.field === field)?.label || field;
}

function toFailedCheckinResult(message: string): PublicCheckinResult | null {
  if (message.includes("审核")) {
    return { status: "NOT_APPROVED", message };
  }
  if (message.includes("未找到匹配") || message.includes("未找到参会人") || message.includes("未查询到")) {
    return { status: "NOT_FOUND", message: normalizePublicQueryError(message, settings.value) };
  }
  return null;
}
</script>

<style scoped src="./public.css"></style>
