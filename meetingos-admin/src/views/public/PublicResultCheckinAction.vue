<template>
  <section v-if="settings?.checkinEnabled" class="result-checkin-action">
    <n-alert v-if="result" :type="resultAlertType" :bordered="false" class="result-checkin-message">
      <div class="result-checkin-message-body">
        <strong>{{ resultTitle }}</strong>
        <span>{{ resultMessage }}</span>
        <span v-if="result.checkedInAt">签到时间：{{ formatDateTimeSecondLabel(result.checkedInAt) }}</span>
      </div>
    </n-alert>

    <template v-else>
      <div v-if="settings.captchaEnabled" class="result-checkin-captcha">
        <span>{{ captchaQuestion || "验证码" }}</span>
        <div class="captcha-row">
          <n-input v-model:value="captchaAnswer" placeholder="请输入计算结果" />
          <n-button secondary :loading="captchaLoading" @click="loadCaptcha">刷新</n-button>
        </div>
      </div>

      <n-button type="primary" block :color="settings.themeColor" :loading="loading" @click="submitCheckin">
        立即签到
      </n-button>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";
import { formatDateTimeSecondLabel } from "@/utils/meetingDisplay";
import type { MeetingQuerySettings, PublicCheckinResult, PublicQueryField } from "@/utils/types";
import { normalizePublicQueryError } from "./queryDisplay";

const props = defineProps<{
  settings: MeetingQuerySettings | null;
  meetingId?: string | number | null;
  meetingCode?: string | null;
  values: Record<PublicQueryField, string>;
}>();

const store = useMeetingStore();
const message = useMessage();
const loading = ref(false);
const captchaId = ref<string | null>(null);
const captchaQuestion = ref("");
const captchaAnswer = ref("");
const captchaLoading = ref(false);
const result = ref<PublicCheckinResult | null>(null);

const resultAlertType = computed(() => {
  if (result.value?.status === "SUCCESS") return "success";
  if (result.value?.status === "DUPLICATE") return "warning";
  return "error";
});

const resultTitle = computed(() => {
  if (result.value?.status === "SUCCESS") return "签到成功";
  if (result.value?.status === "DUPLICATE") return "已签到";
  if (result.value?.status === "NOT_APPROVED") return "暂不能签到";
  return "签到失败";
});

const resultMessage = computed(() => {
  if (result.value?.status === "SUCCESS") return "您已完成签到，请按现场安排入场。";
  if (result.value?.status === "DUPLICATE") return "您已完成签到，请勿重复提交。";
  return result.value?.message || "请联系现场工作人员处理。";
});

onMounted(() => {
  /*
   * 查询页可能已经把验证码用于座位/住宿查询。结果页签到重新申请验证码，
   * 避免综合查询两次请求把同一个验证码的可用次数消耗完。
   */
  if (props.settings?.captchaEnabled) {
    void loadCaptcha();
  }
});

async function submitCheckin() {
  if (!props.settings || loading.value) return;
  if (props.settings.captchaEnabled && !captchaAnswer.value.trim()) {
    message.warning("请输入验证码");
    return;
  }

  loading.value = true;
  try {
    result.value = await store.publicSelfCheckin({
      meetingId: props.meetingId,
      meetingCode: props.meetingCode || undefined,
      values: { ...props.values },
      captchaId: captchaId.value,
      captchaAnswer: captchaAnswer.value,
      checkinSource: "RESULT"
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
    result.value = toFailedCheckinResult(rawMessage);
    if (result.value) {
      message.warning(result.value.message || "暂不能签到");
    } else {
      message.error(normalizePublicQueryError(rawMessage, props.settings));
    }
    if (props.settings.captchaEnabled) {
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
    message.error(error instanceof Error ? error.message : "验证码加载失败");
  } finally {
    captchaLoading.value = false;
  }
}

function toFailedCheckinResult(rawMessage: string): PublicCheckinResult | null {
  if (rawMessage.includes("审核")) {
    return { status: "NOT_APPROVED", message: rawMessage };
  }
  if (rawMessage.includes("未找到匹配") || rawMessage.includes("未找到参会人") || rawMessage.includes("未查询到")) {
    return { status: "NOT_FOUND", message: normalizePublicQueryError(rawMessage, props.settings) };
  }
  return null;
}
</script>

<style scoped>
.result-checkin-action {
  display: grid;
  gap: 12px;
  width: min(400px, 100%);
  margin: 16px auto 0;
}

.result-checkin-captcha {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
}

.result-checkin-captcha > span {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.result-checkin-message {
  text-align: left;
}

.result-checkin-message-body {
  display: grid;
  gap: 4px;
  line-height: 1.55;
}

.result-checkin-message-body strong {
  color: #0f172a;
  font-size: 16px;
}
</style>
