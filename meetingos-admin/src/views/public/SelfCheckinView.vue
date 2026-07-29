<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card self-checkin-card">
      <template v-if="!result">
        <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
        <p v-if="publisher" class="public-kicker">{{ publisher }}</p>
        <h1>{{ pageTitleFor(settings, "checkin", "会议自助签到") }}</h1>
        <p class="public-subtitle">{{ subtitle }}</p>
      </template>

      <n-alert v-if="!settings" class="public-alert" :type="errorText ? 'warning' : 'info'" :bordered="false">
        {{ errorText || "签到设置加载中" }}
      </n-alert>
      <n-alert v-else-if="announcement && !result" class="public-alert" type="info" :bordered="false">
        {{ announcement }}
      </n-alert>
      <n-alert v-if="settings && !windowState.available && !result" class="public-alert" type="warning" :bordered="false">
        {{ windowState.message }}
      </n-alert>
      <n-alert v-if="settings && !settings.selfCheckinEnabled && !result" class="public-alert" type="warning" :bordered="false">
        签到独立页尚未开放。
      </n-alert>

      <n-form v-else-if="settings && settings.selfCheckinEnabled && windowState.available && !result && !preview" label-placement="top" class="public-form">
        <n-form-item v-for="field in activeFields" :key="field" :label="fieldLabel(field)" :required="requiredFields.includes(field)">
          <n-input v-model:value="values[field]" :placeholder="`请输入${fieldLabel(field)}`" />
        </n-form-item>
        <n-form-item v-if="settings.captchaEnabled" :label="captchaQuestion || '验证码'">
          <div class="captcha-row">
            <n-input v-model:value="captchaAnswer" placeholder="请输入计算结果" />
            <n-button secondary :loading="captchaLoading" @click="loadCaptcha">刷新</n-button>
          </div>
        </n-form-item>
        <n-button type="primary" block :color="settings.themeColor" :loading="loading" @click="submitCheckin">
          {{ autoCheckinEnabled ? buttonText : "查询签到信息" }}
        </n-button>
      </n-form>

      <section v-if="preview" class="self-checkin-result" :class="preview.ready ? 'self-checkin-result-success' : 'self-checkin-result-warning'">
        <div class="self-checkin-icon">{{ preview.ready ? "✓" : "!" }}</div>
        <h2>{{ preview.ready ? "身份核验通过" : "已签到" }}</h2>
        <p>{{ preview.message || (preview.ready ? "请确认是否完成签到。" : "您已完成签到，请勿重复提交。") }}</p>
        <div class="self-checkin-meta">
          <div v-if="preview.attendeeName">
            <span>参会人</span>
            <strong>{{ preview.attendeeName }}</strong>
          </div>
          <div v-if="preview.organization">
            <span>单位</span>
            <strong>{{ preview.organization }}</strong>
          </div>
          <div v-if="preview.phone">
            <span>手机号</span>
            <strong>{{ preview.phone }}</strong>
          </div>
          <div v-if="preview.checkedInAt">
            <span>签到时间</span>
            <strong>{{ formatDateTimeSecondLabel(preview.checkedInAt) }}</strong>
          </div>
        </div>
        <n-button v-if="preview.ready" type="primary" block :color="settings?.themeColor" :loading="loading" @click="confirmCheckin">
          确认签到
        </n-button>
        <n-button block secondary :color="settings?.themeColor" @click="resetForm">
          返回
        </n-button>
      </section>

      <section v-if="result" class="self-checkin-result" :class="`self-checkin-result-${resultTone}`">
        <div class="self-checkin-icon">{{ resultIcon }}</div>
        <h2>{{ resultTitle }}</h2>
        <p>{{ resultMessage }}</p>
        <div class="self-checkin-meta">
          <div v-if="result.attendeeName">
            <span>参会人</span>
            <strong>{{ result.attendeeName }}</strong>
          </div>
          <div v-if="result.organization">
            <span>单位</span>
            <strong>{{ result.organization }}</strong>
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
import type { MeetingQuerySettings, PublicCheckinPreview, PublicCheckinResult, PublicQueryField } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import { normalizePublicQueryError, pageAnnouncementFor, pageButtonTextFor, pageDescriptionFor, pagePublisherFor, pageTitleFor, publicPageStyle, queryWindowState } from "./queryDisplay";

const route = useRoute();
const store = useMeetingStore();
const message = useMessage();
const loading = ref(false);
const settings = ref<MeetingQuerySettings | null>(null);
const result = ref<PublicCheckinResult | null>(null);
const preview = ref<PublicCheckinPreview | null>(null);
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

const publicQueryToken = computed(() => String(route.query.token || ""));
const activeFields = computed(() => settings.value?.fields || []);
const requiredFields = computed(() => settings.value?.requiredFields || activeFields.value);
const publisher = computed(() => pagePublisherFor(settings.value, "checkin"));
const subtitle = computed(() => pageDescriptionFor(settings.value, "checkin", "请填写报名信息完成现场签到"));
const announcement = computed(() => pageAnnouncementFor(settings.value, "checkin"));
const buttonText = computed(() => pageButtonTextFor(settings.value, "checkin", "自助签到"));
const autoCheckinEnabled = computed(() => settings.value?.pageContents.checkin.autoCheckinEnabled !== false);
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
      publicQueryToken: publicQueryToken.value || undefined
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
  for (const field of requiredFields.value) {
    if (!values[field].trim()) {
      message.warning(`请输入${fieldLabel(field)}`);
      return;
    }
  }
  if (settings.value.captchaEnabled && !captchaAnswer.value.trim()) {
    message.warning("请输入验证码");
    return;
  }

  result.value = null;
  errorText.value = "";
  if (autoCheckinEnabled.value) {
    await confirmCheckin();
    return;
  }

  loading.value = true;
  try {
    preview.value = await store.publicSelfCheckinPreview({
      publicQueryToken: publicQueryToken.value || settings.value.publicQueryToken,
      values,
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value,
      checkinSource: "SELF"
    });
    if (preview.value.ready) {
      message.success("身份核验通过，请确认签到");
    } else {
      message.warning(preview.value.message || "您已完成签到");
    }
  } catch (error) {
    const rawMessage = error instanceof Error ? error.message : "签到信息核验失败";
    const failedResult = toFailedCheckinResult(rawMessage);
    if (failedResult) {
      result.value = failedResult;
      message.warning(failedResult.message || "暂不能签到");
    } else {
      errorText.value = normalizePublicQueryError(rawMessage, settings.value, "checkin");
      message.error(errorText.value);
      if (settings.value.captchaEnabled) {
        await loadCaptcha();
      }
    }
  } finally {
    loading.value = false;
  }
}

async function confirmCheckin() {
  if (!settings.value || loading.value) return;

  loading.value = true;
  errorText.value = "";
  try {
    result.value = await store.publicSelfCheckin({
      publicQueryToken: publicQueryToken.value || settings.value.publicQueryToken,
      values,
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value,
      checkinSource: "SELF"
    });
    preview.value = null;
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
      preview.value = null;
      message.warning(failedResult.message || "暂不能签到");
    } else {
      errorText.value = normalizePublicQueryError(rawMessage, settings.value, "checkin");
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
  preview.value = null;
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
    return { status: "NOT_FOUND", message: normalizePublicQueryError(message, settings.value, "checkin") };
  }
  return null;
}
</script>

<style scoped src="./public.css"></style>
