<template>
  <main class="public-page" :style="publicPageStyle(settings)">
    <section class="public-card public-card-wide">
      <img v-if="safeLogoUrl" class="public-logo" :src="safeLogoUrl" alt="会议 Logo" />
      <p v-if="publisher" class="public-kicker">{{ publisher }}</p>
      <h1>{{ pageTitleFor(settings, "combined", "综合查询") }}</h1>
      <p v-if="subtitle" class="public-subtitle">{{ subtitle }}</p>

      <n-alert v-if="!settings" class="public-alert" :type="settingsError ? 'warning' : 'info'" :bordered="false">
        {{ settingsError || "查询设置加载中" }}
      </n-alert>
      <n-alert v-else-if="announcement" class="public-alert" type="info" :bordered="false">
        {{ announcement }}
      </n-alert>
      <n-alert v-if="settings && !anyPublicEntryEnabled" class="public-alert" type="warning" :bordered="false">
        当前会议暂未开放公开查询和自助签到
      </n-alert>
      <n-alert v-else-if="settings && !windowState.available" class="public-alert" type="warning" :bordered="false">
        {{ windowState.message }}
      </n-alert>

      <n-form v-else-if="settings && !hasFinalResult" label-placement="top" class="public-form">
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
          {{ primaryButtonText }}
        </n-button>
      </n-form>

      <div v-if="hasQueried && queryResultEnabled" class="combined-results">
        <template v-if="combinedSeatEnabled">
          <template v-if="seatItems.length">
            <public-result-ticket
              v-for="item in seatItems"
              :key="item.result.attendeeId"
              kind="seat"
              :attendee-name="item.result.attendeeName"
              :rows="item.rows"
              :notice="seatNotice"
            />
          </template>
          <section v-else class="public-result">
            <n-alert v-if="seatResults.length" type="info" :bordered="false">当前未配置可展示的座位结果字段</n-alert>
            <n-alert v-else type="warning" :bordered="false">{{ seatMessage }}</n-alert>
          </section>
        </template>

        <template v-if="combinedRoomEnabled">
          <template v-if="roomItems.length">
            <public-result-ticket
              v-for="item in roomItems"
              :key="item.result.attendeeId"
              kind="room"
              :attendee-name="item.result.attendeeName"
              :rows="item.rows"
              :notice="roomNotice"
            />
          </template>
          <section v-else class="public-result">
            <n-alert v-if="roomResults.length" type="info" :bordered="false">当前未配置可展示的住宿结果字段</n-alert>
            <n-alert v-else type="warning" :bordered="false">{{ roomMessage }}</n-alert>
          </section>
        </template>

        <template v-if="combinedDinnerEnabled">
          <template v-if="dinnerItems.length">
            <public-result-ticket
              v-for="item in dinnerItems"
              :key="item.result.attendeeId"
              kind="dinner"
              :attendee-name="item.result.attendeeName"
              :rows="item.rows"
              :notice="dinnerNotice"
            />
          </template>
          <section v-else class="public-result">
            <n-alert v-if="dinnerResults.length" type="info" :bordered="false">当前未配置可展示的晚宴结果字段</n-alert>
            <n-alert v-else type="warning" :bordered="false">{{ dinnerMessage }}</n-alert>
          </section>
        </template>
      </div>

      <public-result-checkin-action
        v-if="canCheckinAfterQuery && pageResultCheckinEnabled(settings, 'combined')"
        :settings="settings"
        :public-query-token="publicQueryToken || settings?.publicQueryToken"
        :values="values"
        page-key="combined"
      />

      <section v-if="checkinResult" class="self-checkin-result" :class="`self-checkin-result-${checkinResultTone}`">
        <div class="self-checkin-icon">{{ checkinResultIcon }}</div>
        <h2>{{ checkinResultTitle }}</h2>
        <p>{{ checkinResultMessage }}</p>
        <div class="self-checkin-meta">
          <div v-if="checkinResult.attendeeName">
            <span>参会人</span>
            <strong>{{ checkinResult.attendeeName }}</strong>
          </div>
          <div v-if="checkinResult.phone">
            <span>手机号</span>
            <strong>{{ checkinResult.phone }}</strong>
          </div>
          <div v-if="checkinResult.checkedInAt">
            <span>签到时间</span>
            <strong>{{ formatDateTimeSecondLabel(checkinResult.checkedInAt) }}</strong>
          </div>
        </div>
        <n-button block secondary :color="settings?.themeColor" @click="resetCheckinResult">
          返回
        </n-button>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from "vue";
import { useRoute } from "vue-router";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";
import { formatDateTimeSecondLabel } from "@/utils/meetingDisplay";
import type { DinnerQueryResult, MeetingQuerySettings, PublicCheckinResult, PublicQueryField, RoomQueryResult, SeatQueryResult } from "@/utils/types";
import { sanitizeImageUrl } from "@/utils/safeUrl";
import PublicResultCheckinAction from "./PublicResultCheckinAction.vue";
import PublicResultTicket from "./PublicResultTicket.vue";
import {
  buildDinnerRows,
  buildRoomRows,
  buildSeatRows,
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
const hasQueried = ref(false);
const settings = ref<MeetingQuerySettings | null>(null);
const settingsError = ref("");
const seatResults = ref<SeatQueryResult[]>([]);
const roomResults = ref<RoomQueryResult[]>([]);
const dinnerResults = ref<DinnerQueryResult[]>([]);
const checkinResult = ref<PublicCheckinResult | null>(null);
const seatMessage = ref("尚未查询座位");
const roomMessage = ref("尚未查询住宿");
const dinnerMessage = ref("尚未查询晚宴");
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
const combinedQueryTypeSet = computed(() => new Set(settings.value?.combinedQueryTypes || ["SEAT", "ROOM", "DINNER"]));
const combinedSeatEnabled = computed(() => Boolean(settings.value?.seatQueryEnabled && combinedQueryTypeSet.value.has("SEAT")));
const combinedRoomEnabled = computed(() => Boolean(settings.value?.roomQueryEnabled && combinedQueryTypeSet.value.has("ROOM")));
const combinedDinnerEnabled = computed(() => Boolean(settings.value?.dinnerQueryEnabled && combinedQueryTypeSet.value.has("DINNER")));
const queryResultEnabled = computed(() => Boolean(combinedSeatEnabled.value || combinedRoomEnabled.value || combinedDinnerEnabled.value));
const anyPublicEntryEnabled = computed(() => Boolean(queryResultEnabled.value || settings.value?.selfCheckinEnabled));
const checkinOnlyEnabled = computed(() => Boolean(settings.value?.selfCheckinEnabled && !queryResultEnabled.value));
const publisher = computed(() => pagePublisherFor(settings.value, "combined"));
const subtitle = computed(() => pageDescriptionFor(settings.value, "combined", "查询会议安排，或完成自助签到"));
const announcement = computed(() => pageAnnouncementFor(settings.value, "combined"));
const windowState = computed(() => queryWindowState(settings.value));
const seatItems = computed(() => seatResults.value
  .map((result) => ({ result, rows: buildSeatRows(settings.value, result) }))
  .filter((item) => item.rows.length));
const roomItems = computed(() => roomResults.value
  .map((result) => ({ result, rows: buildRoomRows(settings.value, result) }))
  .filter((item) => item.rows.length));
const dinnerItems = computed(() => dinnerResults.value
  .map((result) => ({ result, rows: buildDinnerRows(settings.value, result) }))
  .filter((item) => item.rows.length));
const hasQueryResult = computed(() => Boolean(
  seatItems.value.length ||
  roomItems.value.length ||
  dinnerItems.value.length
));
const canCheckinAfterQuery = computed(() => hasQueryResult.value && ![seatResults.value, roomResults.value, dinnerResults.value].some((items) => items.length > 1));
const hasFinalResult = computed(() => Boolean(hasQueryResult.value || checkinResult.value));
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.value?.logoUrl));
const primaryButtonText = computed(() => (checkinOnlyEnabled.value ? "自助签到" : pageButtonTextFor(settings.value, "combined")));
const seatNotice = computed(() => pageNoticeFor(settings.value, "seat"));
const roomNotice = computed(() => pageNoticeFor(settings.value, "room"));
const dinnerNotice = computed(() => pageNoticeFor(settings.value, "dinner"));
const checkinResultTone = computed(() => {
  if (checkinResult.value?.status === "SUCCESS") return "success";
  if (checkinResult.value?.status === "DUPLICATE") return "warning";
  return "error";
});
const checkinResultIcon = computed(() => (checkinResultTone.value === "success" ? "✓" : checkinResultTone.value === "warning" ? "!" : "×"));
const checkinResultTitle = computed(() => {
  if (checkinResult.value?.status === "SUCCESS") return "签到成功";
  if (checkinResult.value?.status === "DUPLICATE") return "已签到";
  if (checkinResult.value?.status === "NOT_APPROVED") return "暂不能签到";
  return "签到失败";
});
const checkinResultMessage = computed(() => {
  if (checkinResult.value?.status === "SUCCESS") return "您已完成签到，请按现场安排入场。";
  if (checkinResult.value?.status === "DUPLICATE") return "您已完成签到，请勿重复提交。";
  return checkinResult.value?.message || "请联系现场工作人员处理。";
});

onMounted(loadSettings);

watchEffect(() => {
  // 综合查询只要有一类结果成功，就进入结果展示模式，减少外层导航对票据视觉的干扰。
  document.body.classList.toggle("public-result-mode", hasFinalResult.value);
});

onBeforeUnmount(() => {
  document.body.classList.remove("public-result-mode");
});

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
  if (!settings.value || !anyPublicEntryEnabled.value) return;
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
  hasQueried.value = true;
  seatResults.value = [];
  roomResults.value = [];
  dinnerResults.value = [];
  checkinResult.value = null;
  seatMessage.value = combinedSeatEnabled.value ? "座位查询中" : "综合查询未展示座位结果";
  roomMessage.value = combinedRoomEnabled.value ? "住宿查询中" : "综合查询未展示住宿结果";
  dinnerMessage.value = combinedDinnerEnabled.value ? "晚宴查询中" : "综合查询未展示晚宴结果";

  /*
   * 综合查询页允许座位和住宿各自成功或失败。比如会议只安排座位没有住宿时，
   * 座位结果仍应展示，住宿区域则显示后端返回的明确原因。
   */
  const payload = {
    publicQueryToken: publicQueryToken.value || settings.value.publicQueryToken,
    values,
    captchaId: captchaId.value,
    captchaAnswer: captchaAnswer.value
  };
  const tasks: Promise<void>[] = [];

  if (checkinOnlyEnabled.value) {
    try {
      checkinResult.value = await store.publicSelfCheckin({ ...payload, checkinSource: "SELF" });
      if (checkinResult.value.status === "SUCCESS") {
        message.success("签到成功");
      } else if (checkinResult.value.status === "DUPLICATE") {
        message.warning("您已完成签到");
      } else {
        message.warning(checkinResult.value.message || "暂不能签到");
      }
    } catch (error) {
      const rawMessage = error instanceof Error ? error.message : "签到失败";
      const failedResult = toFailedCheckinResult(rawMessage);
      if (failedResult) {
        checkinResult.value = failedResult;
        message.warning(failedResult.message || "暂不能签到");
      } else {
        message.error(normalizePublicQueryError(rawMessage, settings.value, "combined"));
        if (settings.value.captchaEnabled) {
          await loadCaptcha();
        }
      }
    } finally {
      loading.value = false;
    }
    return;
  }

  if (combinedSeatEnabled.value) {
    tasks.push(
      store.publicSeatQuery(payload)
        .then((result) => {
          seatResults.value = publicQueryResults(result);
        })
        .catch((error) => {
          const rawMessage = error instanceof Error ? error.message : "座位查询失败";
          seatMessage.value = normalizePublicQueryError(rawMessage, settings.value, "seat");
        })
    );
  }

  if (combinedRoomEnabled.value) {
    tasks.push(
      store.publicRoomQuery(payload)
        .then((result) => {
          roomResults.value = publicQueryResults(result);
        })
        .catch((error) => {
          const rawMessage = error instanceof Error ? error.message : "住宿查询失败";
          roomMessage.value = normalizePublicQueryError(rawMessage, settings.value, "room");
        })
    );
  }

  if (combinedDinnerEnabled.value) {
    tasks.push(
      store.publicDinnerQuery(payload)
        .then((result) => {
          dinnerResults.value = publicQueryResults(result);
        })
        .catch((error) => {
          const rawMessage = error instanceof Error ? error.message : "晚宴查询失败";
          dinnerMessage.value = normalizePublicQueryError(rawMessage, settings.value, "dinner");
        })
    );
  }

  await Promise.allSettled(tasks);
  const enabledResults = [
    combinedSeatEnabled.value ? seatResults.value.length : true,
    combinedRoomEnabled.value ? roomResults.value.length : true,
    combinedDinnerEnabled.value ? dinnerResults.value.length : true
  ];
  if (settings.value.captchaEnabled && enabledResults.some((result) => !result)) {
    await loadCaptcha();
  }
  loading.value = false;
}

async function resetCheckinResult() {
  checkinResult.value = null;
  hasQueried.value = false;
  if (settings.value?.captchaEnabled) {
    await loadCaptcha();
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

function toFailedCheckinResult(message: string): PublicCheckinResult | null {
  if (message.includes("审核")) {
    return { status: "NOT_APPROVED", message };
  }
  if (message.includes("未找到匹配") || message.includes("未找到参会人") || message.includes("未查询到")) {
    return { status: "NOT_FOUND", message: normalizePublicQueryError(message, settings.value, "combined") };
  }
  return null;
}
</script>

<style scoped src="./public.css"></style>
