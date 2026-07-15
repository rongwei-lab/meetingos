<template>
  <main class="public-invitation-page">
    <section v-if="loading" class="public-invitation-state">
      <n-spin size="large" />
      <p>正在打开邀请函…</p>
    </section>

    <section v-else-if="unavailable || !payload" class="public-invitation-state unavailable-state">
      <div class="unavailable-mark"><n-icon :component="LinkOutline" /></div>
      <h1>邀请函暂不可用</h1>
      <p>链接可能已停用、过期或尚未发布，请向会议组织方确认。</p>
    </section>

    <template v-else>
      <header class="invitation-public-header">
        <div class="public-brand"><span />MeetingOS</div>
        <button type="button" aria-label="分享邀请函" @click="shareInvitation"><n-icon :component="ShareSocialOutline" /></button>
      </header>

      <article ref="invitationBody" class="invitation-document">
        <InvitationDomRenderer
          :canvas="payload.designDocument.h5"
          :meeting="payload.meeting"
          :assets="payload.assets"
          :share-url="currentUrl"
          :share-token="shareToken"
          :public-query-token="payload.publicQueryToken"
          :registration-disabled="!registrationAvailable || intentLoading"
          @register="startRegistration"
          @navigate="navigateToPublicAction"
        />
        <section v-if="registrationNotice" class="registration-closed-message" :class="{ error: intentFailure?.kind === 'closed' }">
          {{ registrationNotice }}
        </section>
        <footer class="invitation-footer">
          <span><n-icon :component="LockClosedOutline" />由 MeetingOS 提供安全访问</span>
          <span>© {{ new Date().getFullYear() }} {{ payload.meeting.organizer || "会议组委会" }}</span>
        </footer>
      </article>

      <div class="sticky-registration-bar" :class="{ disabled: !registrationAvailable }">
        <div><strong>{{ registrationBarTitle }}</strong><span>{{ registrationBarMessage }}</span></div>
        <button type="button" :disabled="!registrationAvailable || intentLoading" @click="startRegistration">
          <n-spin v-if="intentLoading" size="small" />
          <span v-else>{{ registrationButtonLabel }}</span>
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { LinkOutline, LockClosedOutline, ShareSocialOutline } from "@vicons/ionicons5";
import InvitationDomRenderer from "@/components/invitation/InvitationDomRenderer.vue";
import { createRegistrationIntent, getPublicInvitation, recordInvitationView } from "@/api/invitations";
import { extractErrorMessage } from "@/api/http";
import type { PublicInvitationPayload } from "@/types/invitation";
import type { InvitationNavigationTarget } from "@/types/invitation";
import { createClientEventId } from "@/utils/clientEventId";
import { resolveInvitationNavigationTarget } from "@/utils/invitationActions";

const route = useRoute();
const shareToken = computed(() => String(route.params.shareToken || ""));
const payload = ref<PublicInvitationPayload | null>(null);
const loading = ref(true);
const unavailable = ref(false);
const intentLoading = ref(false);
const intentFailure = ref<{ kind: "closed" | "retryable"; message: string } | null>(null);
const navigationNotice = ref("");
const invitationBody = ref<HTMLElement>();
const currentUrl = computed(() => {
  // shareToken 是响应式依赖，确保同一组件切换公开链接后二维码和分享地址同步刷新。
  void shareToken.value;
  return typeof window === "undefined" ? "" : window.location.href;
});
const VIEW_RETRY_DELAYS_MS = [500, 1500, 4000] as const;
let mounted = false;
let loadGeneration = 0;
let viewEventId = createClientEventId();
let viewRecorded = false;
let viewRecording = false;
let viewAttempts = 0;
let viewRetryTimer: number | undefined;
let firstViewFrame: number | undefined;
let secondViewFrame: number | undefined;
let pendingIntentEventId: string | null = null;

const registrationAvailable = computed(() => Boolean(payload.value?.registrationOpen) && intentFailure.value?.kind !== "closed");
const registrationBarTitle = computed(() => {
  if (intentFailure.value?.kind === "closed") return "报名通道已关闭";
  if (intentFailure.value?.kind === "retryable") return "报名请求失败";
  return payload.value?.registrationOpen ? "报名通道开放中" : "报名暂未开放";
});
const registrationBarMessage = computed(() => intentFailure.value?.message || payload.value?.registrationMessage || "请留意组织方通知");
const registrationButtonLabel = computed(() => {
  if (intentFailure.value?.kind === "closed") return "报名已关闭";
  if (intentFailure.value?.kind === "retryable") return "重试报名";
  return payload.value?.registrationOpen ? "立即报名" : "报名未开放";
});
const registrationNotice = computed(() => {
  if (navigationNotice.value) return navigationNotice.value;
  if (intentFailure.value?.kind === "closed") return intentFailure.value.message;
  if (!payload.value?.registrationOpen) return payload.value?.registrationMessage || "报名暂未开放";
  return "";
});

onMounted(() => {
  mounted = true;
  document.addEventListener("visibilitychange", handleVisibilityChange);
  void loadInvitation();
});

onBeforeUnmount(() => {
  mounted = false;
  loadGeneration += 1;
  clearViewScheduling();
  document.removeEventListener("visibilitychange", handleVisibilityChange);
});

watch(shareToken, (nextToken, previousToken) => {
  if (!mounted || nextToken === previousToken) return;
  void loadInvitation();
});

async function loadInvitation() {
  const token = shareToken.value;
  const generation = ++loadGeneration;
  resetEventState();
  loading.value = true;
  unavailable.value = false;
  intentFailure.value = null;
  navigationNotice.value = "";
  payload.value = null;
  let loaded = false;
  try {
    const response = await getPublicInvitation(token);
    if (!isCurrentRoute(generation, token)) return;
    payload.value = response;
    document.title = `${response.meeting.name} · 邀请函`;
    loaded = true;
  } catch {
    if (!isCurrentRoute(generation, token)) return;
    unavailable.value = true;
    payload.value = null;
  } finally {
    if (isCurrentRoute(generation, token)) loading.value = false;
  }
  if (loaded && isCurrentRoute(generation, token)) {
    await nextTick();
    if (isCurrentRoute(generation, token)) scheduleInitialViewRecord(generation, token);
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === "visible") void recordVisibleView();
}

async function recordVisibleView() {
  if (
    viewRecorded
    || viewRecording
    || viewAttempts >= VIEW_RETRY_DELAYS_MS.length + 1
    || document.visibilityState !== "visible"
    || !payload.value
    || !invitationBody.value
  ) return;
  const rect = invitationBody.value.getBoundingClientRect();
  if (rect.bottom <= 0 || rect.top >= window.innerHeight) return;
  const generation = loadGeneration;
  const token = shareToken.value;
  const eventId = viewEventId;
  viewRecording = true;
  viewAttempts += 1;
  try {
    await recordInvitationView(token, eventId);
    if (!isCurrentViewAttempt(generation, token, eventId)) return;
    viewRecorded = true;
    clearViewRetryTimer();
  } catch {
    if (isCurrentViewAttempt(generation, token, eventId)) {
      scheduleViewRetry(generation, token, eventId);
    }
  } finally {
    if (isCurrentViewAttempt(generation, token, eventId)) viewRecording = false;
  }
}

async function startRegistration() {
  if (!registrationAvailable.value || intentLoading.value) return;
  const generation = loadGeneration;
  const token = shareToken.value;
  const eventId = pendingIntentEventId || createClientEventId();
  pendingIntentEventId = eventId;
  intentLoading.value = true;
  intentFailure.value = null;
  try {
    const intent = await createRegistrationIntent(token, eventId);
    if (!isCurrentRoute(generation, token)) return;
    pendingIntentEventId = null;
    window.location.assign(intent.redirectUrl);
  } catch (error) {
    if (!isCurrentRoute(generation, token)) return;
    const status = responseStatus(error);
    const registrationClosed = status === 400 || status === 404;
    // 瞬时失败保留事件 ID，确保“重试报名”不会重复写入 CTA_CLICK；明确关闭后结束本次意图。
    if (registrationClosed) pendingIntentEventId = null;
    intentFailure.value = {
      kind: registrationClosed ? "closed" : "retryable",
      message: status === null
        ? "网络连接异常，请稍后重试"
        : extractErrorMessage(error, registrationClosed ? "报名入口当前不可用，请联系会议组织方" : "报名请求暂时失败，请稍后重试")
    };
  } finally {
    if (isCurrentRoute(generation, token)) intentLoading.value = false;
  }
}

function navigateToPublicAction(target: InvitationNavigationTarget) {
  if (!payload.value) return;
  navigationNotice.value = "";
  const resolved = resolveInvitationNavigationTarget(target, {
    publicQueryToken: payload.value.publicQueryToken
  });
  if (!resolved.url) {
    navigationNotice.value = resolved.unavailableReason || "当前入口不可用";
    return;
  }
  window.location.assign(resolved.url);
}

function resetEventState() {
  clearViewScheduling();
  viewEventId = createClientEventId();
  viewRecorded = false;
  viewRecording = false;
  viewAttempts = 0;
  pendingIntentEventId = null;
  intentLoading.value = false;
}

function isCurrentRoute(generation: number, token: string) {
  return mounted && generation === loadGeneration && token === shareToken.value;
}

function isCurrentViewAttempt(generation: number, token: string, eventId: string) {
  return isCurrentRoute(generation, token) && eventId === viewEventId;
}

function scheduleInitialViewRecord(generation: number, token: string) {
  cancelViewFrames();
  firstViewFrame = window.requestAnimationFrame(() => {
    firstViewFrame = undefined;
    if (!isCurrentRoute(generation, token)) return;
    secondViewFrame = window.requestAnimationFrame(() => {
      secondViewFrame = undefined;
      if (isCurrentRoute(generation, token)) void recordVisibleView();
    });
  });
}

function scheduleViewRetry(generation: number, token: string, eventId: string) {
  const delay = VIEW_RETRY_DELAYS_MS[viewAttempts - 1];
  if (delay === undefined) return;
  clearViewRetryTimer();
  viewRetryTimer = window.setTimeout(() => {
    viewRetryTimer = undefined;
    if (isCurrentViewAttempt(generation, token, eventId) && !viewRecorded) void recordVisibleView();
  }, delay);
}

function clearViewRetryTimer() {
  if (viewRetryTimer !== undefined) window.clearTimeout(viewRetryTimer);
  viewRetryTimer = undefined;
}

function cancelViewFrames() {
  if (firstViewFrame !== undefined) window.cancelAnimationFrame(firstViewFrame);
  if (secondViewFrame !== undefined) window.cancelAnimationFrame(secondViewFrame);
  firstViewFrame = undefined;
  secondViewFrame = undefined;
}

function clearViewScheduling() {
  clearViewRetryTimer();
  cancelViewFrames();
}

function responseStatus(error: unknown) {
  if (!error || typeof error !== "object" || !("response" in error)) return null;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : null;
}

async function shareInvitation() {
  if (navigator.share) {
    try { await navigator.share({ title: payload.value?.meeting.name || "会议邀请函", url: currentUrl.value }); return; }
    catch { return; }
  }
  await navigator.clipboard.writeText(currentUrl.value);
}

</script>

<style scoped>
.public-invitation-page {
  min-height: 100svh;
  padding-bottom: calc(82px + env(safe-area-inset-bottom));
  color: #14271e;
  background: #edf2ef;
}

.public-invitation-state {
  width: min(100%, 420px);
  min-height: 100svh;
  margin: 0 auto;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 14px;
  padding: 32px;
  color: #6a7d73;
  background: #ffffff;
  text-align: center;
}

.public-invitation-state p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
}

.unavailable-state h1 {
  margin: 2px 0 0;
  color: #193b2d;
  font-size: 24px;
  line-height: 1.3;
}

.unavailable-mark {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #08784f;
  background: #e9f5f0;
  font-size: 27px;
}

.invitation-public-header {
  position: fixed;
  z-index: 100;
  top: 0;
  left: 50%;
  width: min(100%, 430px);
  height: 48px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transform: translateX(-50%);
  color: #103c2a;
  background: rgba(255, 255, 255, 0.88);
  border-bottom: 1px solid rgba(211, 226, 218, .82);
  backdrop-filter: blur(12px);
}

.public-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 750;
}

.public-brand span {
  width: 18px;
  height: 18px;
  border: 5px solid #08784f;
  border-right-color: #63c5a0;
  border-radius: 50%;
}

.invitation-public-header button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #174d37;
  background: #edf6f2;
  font-size: 18px;
}

.invitation-document {
  width: min(100%, 430px);
  min-height: 100svh;
  margin: 0 auto;
  padding-top: 48px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 0 36px rgba(21, 55, 40, 0.1);
}

.registration-closed-message {
  margin: 0 20px;
  padding: 14px;
  color: #7d5c20;
  background: #fff8e8;
  border-top: 1px solid #f0ddb8;
  border-bottom: 1px solid #f0ddb8;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.registration-closed-message.error {
  color: #8a3b31;
  background: #fff1ef;
  border-color: #efc8c2;
}

.invitation-footer {
  min-height: 96px;
  padding: 24px 20px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 7px;
  color: #87958e;
  border-top: 1px solid #e8efeb;
  font-size: 11px;
}

.invitation-footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.sticky-registration-bar {
  position: fixed;
  z-index: 110;
  left: 50%;
  bottom: 0;
  width: min(100%, 430px);
  min-height: 74px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  align-items: center;
  gap: 12px;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, .96);
  border-top: 1px solid #dce9e3;
  box-shadow: 0 -8px 24px rgba(20, 63, 45, .08);
  backdrop-filter: blur(14px);
}

.sticky-registration-bar div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.sticky-registration-bar strong {
  color: #173b2d;
  font-size: 13px;
}

.sticky-registration-bar div span {
  overflow: hidden;
  color: #74857c;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sticky-registration-bar button {
  min-height: 48px;
  border: 0;
  border-radius: 6px;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: #08784f;
  font-size: 17px;
  font-weight: 750;
  cursor: pointer;
}

.sticky-registration-bar.disabled button,
.sticky-registration-bar button:disabled {
  cursor: not-allowed;
  background: #a8b6af;
}

@media (min-width: 431px) {
  .public-invitation-page {
    padding-top: 18px;
  }

  .invitation-public-header {
    top: 18px;
    border-radius: 6px 6px 0 0;
  }
}
</style>
