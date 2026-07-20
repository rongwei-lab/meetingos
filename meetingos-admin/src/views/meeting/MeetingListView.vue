<template>
  <div class="meeting-workbench">
    <section class="workbench-hero">
      <div class="hero-copy">
        <div class="hero-icon">
          <n-icon :component="BriefcaseOutline" />
        </div>
        <div>
          <h1>欢迎回来</h1>
          <p>选择会议以进入您的工作空间</p>
        </div>
      </div>

      <div class="hero-tools">
        <n-input v-model:value="keyword" class="project-search" placeholder="搜索会议" clearable>
          <template #prefix>
            <n-icon :component="SearchOutline" />
          </template>
        </n-input>
        <n-select v-model:value="status" class="status-filter" :options="statusOptions" placeholder="会议状态" clearable />
      </div>
    </section>

    <section class="meeting-section">
      <div class="section-heading">
        <div>
          <h2>最近会议</h2>
          <p>{{ filteredMeetings.length }} 个会议可进入</p>
        </div>
        <n-button v-if="!isMobile" class="view-toggle" secondary type="primary" @click="toggleViewMode">
          <template #icon>
            <n-icon :component="viewToggleIcon" />
          </template>
          {{ viewToggleLabel }}
        </n-button>
      </div>

      <div v-if="effectiveViewMode === 'card'" class="meeting-grid">
        <article v-for="(meeting, index) in filteredMeetings" :key="meeting.id" class="meeting-card surface">
          <n-dropdown trigger="click" :options="actionOptions(meeting)" @select="handleActionSelect(meeting, $event)">
            <button class="card-menu" type="button" aria-label="会议操作">
              <n-icon :component="EllipsisVerticalOutline" />
            </button>
          </n-dropdown>

          <div class="meeting-icon" :class="`tone-${index % 3}`">
            <n-icon :component="cardIcon(index)" />
          </div>

          <div class="meeting-main">
            <div class="meeting-title-row">
              <h3>{{ meeting.name }}</h3>
              <status-tag :value="meeting.status" />
            </div>
            <p class="meeting-meta">
              <n-icon :component="TimeOutline" />
              最近访问：{{ lastVisitedLabel(meeting, index) }}
            </p>
            <p v-if="visibleMeetingSummary(meeting)" class="meeting-subtitle">{{ visibleMeetingSummary(meeting) }}</p>
          </div>

          <div class="meeting-card-footer">
            <div class="meeting-stats" aria-label="会议统计">
              <span><strong>{{ meeting.registeredCount }}</strong>报名</span>
              <span><strong>{{ meeting.approvedCount }}</strong>通过</span>
              <span><strong>{{ meeting.checkedInCount }}</strong>签到</span>
            </div>

            <n-button class="enter-button" secondary type="primary" @click="enterWorkspace(meeting)">
              进入会议
              <template #icon>
                <n-icon :component="ChevronForwardOutline" />
              </template>
            </n-button>
          </div>
        </article>

        <button class="create-card surface" type="button" @click="openCreateMeeting">
          <span class="create-icon">
            <n-icon :component="AddOutline" />
          </span>
          <strong>新建会议</strong>
          <small>创建一个新会议以开始</small>
        </button>
      </div>

      <div v-else class="meeting-table surface">
        <div class="table-toolbar">
          <span>共 {{ filteredMeetings.length }} 个会议</span>
          <n-button size="small" type="primary" @click="openCreateMeeting">
            <template #icon>
              <n-icon :component="AddOutline" />
            </template>
            新建会议
          </n-button>
        </div>
        <n-data-table
          :columns="meetingColumns"
          :data="filteredMeetings"
          :pagination="{ defaultPageSize: 10, showSizePicker: true, pageSizes: PAGINATION_PAGE_SIZES, prefix: paginationSummary }"
          :row-key="meetingRowKey"
          :scroll-x="1020"
        />
      </div>

      <n-empty v-if="effectiveViewMode === 'card' && !filteredMeetings.length" class="empty-state" description="没有匹配的会议">
        <template #extra>
          <n-button type="primary" @click="openCreateMeeting">新建会议</n-button>
        </template>
      </n-empty>
    </section>

    <n-modal v-model:show="showCreate" preset="card" :title="meetingModalTitle" style="width: 560px">
      <n-form label-placement="top">
        <n-form-item label="会议名称"><n-input v-model:value="draft.name" /></n-form-item>
        <n-form-item label="会议编号">
          <div class="readonly-code-box">
            <strong>{{ draft.code || "保存后自动生成" }}</strong>
            <span>系统自动生成，用于报名中转页和公开查询链接</span>
          </div>
        </n-form-item>
        <n-form-item label="主办方"><n-input v-model:value="draft.organizer" /></n-form-item>
        <n-form-item label="会议地点"><n-input v-model:value="draft.venue" /></n-form-item>
        <n-form-item label="人数上限"><n-input-number v-model:value="draft.capacity" :min="1" style="width: 100%" /></n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" :loading="savingMeeting || loadingMeetingDetail" @click="saveMeeting">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  AddOutline,
  BriefcaseOutline,
  ChevronForwardOutline,
  CubeOutline,
  EllipsisVerticalOutline,
  FolderOpenOutline,
  ImageOutline,
  ListOutline,
  SearchOutline,
  StatsChartOutline,
  TimeOutline
} from "@vicons/ionicons5";
import type { Component } from "vue";
import { NButton, NDataTable, NDropdown, NSpace, useMessage, type DataTableColumns, type DropdownOption } from "naive-ui";
import StatusTag from "@/components/StatusTag.vue";
import { useIsMobile } from "@/composables/useResponsive";
import { useAuthStore } from "@/stores/auth";
import { useMeetingStore } from "@/stores/meeting";
import { meetingSummaryLabel } from "@/utils/meetingDisplay";
import { PAGINATION_PAGE_SIZES, paginationSummary } from "@/utils/pagination";
import {
  DEFAULT_NEW_MEETING_STATUS,
  isMeetingRestartTransition,
  meetingStatusActions
} from "@/utils/meetingLifecycle";
import type { Meeting } from "@/utils/types";

const auth = useAuthStore();
const store = useMeetingStore();
const route = useRoute();
const router = useRouter();
const message = useMessage();
const isMobile = useIsMobile();
const keyword = ref("");
const status = ref<Meeting["status"] | null>(null);
const showCreate = ref(false);
const viewMode = ref<"card" | "table">("table");
const editingMeeting = ref<Meeting | null>(null);
const savingMeeting = ref(false);
const loadingMeetingDetail = ref(false);
const enteringMeetingId = ref("");
const draft = reactive<Pick<Meeting, "name" | "code" | "organizer" | "venue" | "capacity" | "status">>({
  name: "新会议",
  code: "",
  organizer: "主办方",
  venue: "",
  capacity: 300,
  status: DEFAULT_NEW_MEETING_STATUS
});
const icons = [FolderOpenOutline, StatsChartOutline, CubeOutline] as Component[];
const viewToggleLabel = computed(() => (viewMode.value === "card" ? "表格视图" : "图片视图"));
const viewToggleIcon = computed(() => (viewMode.value === "card" ? ListOutline : ImageOutline));
const meetingModalTitle = computed(() => (editingMeeting.value ? "编辑会议" : "新建会议"));
const effectiveViewMode = computed(() => (isMobile.value ? "card" : viewMode.value));

watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = Array.isArray(value) ? value[0] || "" : value || "";
  },
  { immediate: true }
);

const statusOptions = [
  { label: "草稿", value: "DRAFT" },
  { label: "报名中", value: "REGISTRATION_OPEN" },
  { label: "报名截止", value: "REGISTRATION_CLOSED" },
  { label: "待开始", value: "READY" },
  { label: "进行中", value: "IN_PROGRESS" },
  { label: "已结束", value: "ENDED" },
  { label: "已归档", value: "ARCHIVED" }
];

const filteredMeetings = computed(() =>
  store.meetings.filter((meeting) => {
    const normalizedKeyword = keyword.value.trim().toLocaleLowerCase("zh-CN");
    const hitKeyword =
      !normalizedKeyword ||
      [meeting.name, meeting.code, meeting.venue, meeting.organizer].some((field) =>
        field.toLocaleLowerCase("zh-CN").includes(normalizedKeyword)
      );
    const hitStatus = !status.value || meeting.status === status.value;
    return hitKeyword && hitStatus;
  })
);

function visibleMeetingSummary(meeting: Meeting) {
  const hasRealTime = Boolean(meeting.startTime?.trim());
  const venue = meeting.venue?.trim() || "";
  const hasRealVenue = venue !== "" && venue !== "-" && venue !== "待定" && venue !== "未定" && venue !== "地点待定";
  return hasRealTime || hasRealVenue ? meetingSummaryLabel(meeting) : "";
}

const meetingColumns: DataTableColumns<Meeting> = [
  {
    title: "会议名称",
    key: "name",
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) =>
      h(NButton, { text: true, type: "primary", onClick: () => enterWorkspace(row) }, { default: () => row.name })
  },
  { title: "状态", key: "status", width: 110, render: (row) => h(StatusTag, { value: row.status }) },
  {
    title: "时间地点",
    key: "summary",
    minWidth: 240,
    ellipsis: { tooltip: true },
    render: (row) => visibleMeetingSummary(row) || "-"
  },
  { title: "报名", key: "registeredCount", width: 82, align: "center" },
  { title: "通过", key: "approvedCount", width: 82, align: "center" },
  { title: "签到", key: "checkedInCount", width: 82, align: "center" },
  { title: "最近访问", key: "lastVisited", width: 110, render: (row, index) => lastVisitedLabel(row, index) },
  {
    title: "操作",
    key: "actions",
    width: 190,
    fixed: "right",
    render: (row) =>
      h(NSpace, { justify: "end", wrap: false, size: 8 }, {
        default: () => [
          h(NButton, { size: "small", secondary: true, type: "primary", onClick: () => enterWorkspace(row) }, { default: () => "进入会议" }),
          h(NDropdown, { trigger: "click", options: actionOptions(row), onSelect: (key) => handleActionSelect(row, key) }, {
            default: () => h(NButton, { size: "small", quaternary: true }, { default: () => "操作" })
          })
        ]
      })
  }
];

function toggleViewMode() {
  viewMode.value = viewMode.value === "card" ? "table" : "card";
}

function meetingRowKey(row: Meeting) {
  return row.id;
}

function cardIcon(index: number) {
  return icons[index % icons.length];
}

function lastVisitedLabel(meeting: Meeting, index: number) {
  if (meeting.id === store.currentMeetingId) return "刚刚";
  return index === 1 ? "昨天" : `${index + 1} 天前`;
}

function actionOptions(meeting: Meeting): DropdownOption[] {
  const actions = meetingStatusActions(meeting.status, auth.canAccessSystemSettings).map((action) => ({
    label: action.label,
    key: action.status
  }));
  return [
    { label: "编辑", key: "edit" },
    { label: "查询设置", key: "query-settings" },
    ...actions
  ];
}

function resetDraft(meeting?: Meeting) {
  // 新建和编辑共用同一个弹窗，打开前统一重置草稿，避免上一次编辑的残留值污染下一次操作。
  draft.name = meeting?.name || "新会议";
  draft.code = meeting?.code || "";
  draft.organizer = meeting?.organizer || "主办方";
  draft.venue = meeting?.venue || "";
  draft.capacity = meeting?.capacity || 300;
  draft.status = meeting?.status || DEFAULT_NEW_MEETING_STATUS;
}

function openCreateMeeting() {
  editingMeeting.value = null;
  resetDraft();
  showCreate.value = true;
}

async function openEditMeeting(meeting: Meeting) {
  editingMeeting.value = meeting;
  resetDraft(meeting);
  showCreate.value = true;
  loadingMeetingDetail.value = true;
  try {
    const detail = await store.loadMeetingDetail(meeting.id, { loadWorkspace: false });
    if (detail && editingMeeting.value?.id === meeting.id) {
      editingMeeting.value = detail;
      resetDraft(detail);
    }
  } finally {
    loadingMeetingDetail.value = false;
  }
}

async function saveMeeting() {
  if (savingMeeting.value || loadingMeetingDetail.value) return;
  if (!draft.name.trim()) {
    message.warning("请输入会议名称");
    return;
  }

  savingMeeting.value = true;
  try {
    if (editingMeeting.value) {
      await store.updateMeeting(editingMeeting.value.id, draft);
      showCreate.value = false;
      editingMeeting.value = null;
      message.success("会议信息已保存");
      return;
    }

    const meeting = await store.createMeeting(draft);
    showCreate.value = false;
    await store.loadMeetingWorkspace(meeting.id);
    await router.push({ name: "meeting-workspace", params: { meetingId: meeting.id } });
  } catch (error) {
    message.error(error instanceof Error ? error.message : "会议保存失败");
  } finally {
    savingMeeting.value = false;
  }
}

function enterWorkspace(meeting: Meeting) {
  if (enteringMeetingId.value) return;
  enteringMeetingId.value = meeting.id;

  /*
   * 先响应路由跳转，再由工作区页面加载完整数据；store 会复用同会议的工作区加载 Promise。
   * 这样连续点击时不会把大量接口请求堆在会议列表页，降低浏览器主线程无响应风险。
   */
  store.setCurrentMeetingId(meeting.id);
  void router.push({ name: "meeting-workspace", params: { meetingId: meeting.id } }).finally(() => {
    enteringMeetingId.value = "";
  });
}

function handleActionSelect(meeting: Meeting, key: string | number) {
  if (key === "empty") return;
  if (key === "edit") {
    void openEditMeeting(meeting);
    return;
  }
  if (key === "query-settings") {
    store.setCurrentMeetingId(meeting.id);
    void router.push({ name: "meeting-detail", params: { meetingId: meeting.id } });
    return;
  }
  transition(meeting, key as Meeting["status"]);
}

function transition(row: Meeting, status: Meeting["status"]) {
  const restarting = isMeetingRestartTransition(row.status, status);
  if (restarting && !auth.canAccessSystemSettings) {
    message.error("仅超级管理员可以重新开启已结束或已归档会议");
    return;
  }
  const request = restarting
    ? store.restartMeeting(row.id)
    : store.transitionMeeting(row.id, status);
  void request.then(() => {
    message.success(restarting ? "会议已重新开启，当前状态为报名中" : "会议状态已更新");
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "状态更新失败");
  });
}
</script>

<style scoped>
.meeting-workbench {
  min-height: 100%;
}

.workbench-hero {
  min-height: 150px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px 2px 32px;
}

.hero-copy {
  display: flex;
  align-items: center;
  gap: 20px;
}

.hero-icon {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--accent-pressed);
  background: #dbeafe;
  font-size: 32px;
}

.hero-copy h1 {
  margin: 0;
  font-size: 32px;
  line-height: 42px;
  font-weight: 760;
  color: var(--text-primary);
}

.hero-copy p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 16px;
  line-height: 24px;
}

.hero-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.project-search {
  width: 360px;
}

.status-filter {
  width: 168px;
}

.readonly-code-box {
  min-height: 34px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 10px;
  display: grid;
  gap: 2px;
  background: var(--surface-muted);
}

.readonly-code-box strong {
  color: var(--text-primary);
  font-size: 14px;
  line-height: 20px;
  font-weight: 650;
}

.readonly-code-box span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.meeting-section {
  display: grid;
  gap: 20px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-heading h2 {
  margin: 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 720;
  color: var(--text-primary);
}

.section-heading p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.view-toggle {
  flex: 0 0 auto;
  white-space: nowrap;
}

.meeting-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 320px));
  gap: 14px;
}

.meeting-table {
  padding: 16px;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--text-secondary);
  font-size: 13px;
}

.meeting-card,
.create-card {
  min-height: 220px;
}

.meeting-card {
  position: relative;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  overflow: hidden;
}

.meeting-card:hover,
.create-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(59, 130, 246, 0.09);
}

.card-menu {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
}

.card-menu:hover {
  background: var(--surface-muted);
}

.meeting-icon {
  width: 48px;
  height: 48px;
  margin: 2px auto 0;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 27px;
}

.tone-0 {
  color: var(--accent-pressed);
  background: var(--accent-soft);
}

.tone-1 {
  color: #1d4ed8;
  background: #dbeafe;
}

.tone-2 {
  color: #e17a2f;
  background: #fff1d6;
}

.meeting-main {
  min-width: 0;
  text-align: center;
}

.meeting-title-row {
  display: grid;
  justify-items: center;
  gap: 5px;
}

.meeting-title-row h3 {
  max-width: 100%;
  min-height: 42px;
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  line-height: 21px;
  font-weight: 760;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.meeting-meta,
.meeting-subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.meeting-meta {
  margin-top: 5px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.meeting-subtitle {
  min-height: 18px;
  margin-top: 3px;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.meeting-card-footer {
  margin-top: auto;
  display: grid;
  gap: 9px;
}

.meeting-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 7px 0;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);
}

.meeting-stats span {
  display: grid;
  justify-items: center;
  gap: 2px;
  color: var(--text-secondary);
  font-size: 12px;
}

.meeting-stats strong {
  color: var(--text-primary);
  font-size: 13px;
}

.enter-button {
  width: 100%;
}

.create-card {
  border-style: dashed;
  border-color: var(--accent-border);
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 8px;
  padding: 18px;
  color: var(--text-primary);
  background: var(--surface-bg);
  cursor: pointer;
}

.create-icon {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  color: var(--accent-pressed);
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  font-size: 30px;
}

.create-card strong {
  font-size: 18px;
  line-height: 24px;
}

.create-card small {
  color: var(--text-secondary);
  font-size: 13px;
}

.empty-state {
  padding: 40px 0;
}

@media (max-width: 920px) {
  .workbench-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-tools {
    width: 100%;
  }

  .project-search {
    flex: 1;
    width: auto;
  }
}

@media (max-width: 640px) {
  .workbench-hero {
    min-height: auto;
    padding-top: 4px;
  }

  .hero-copy {
    align-items: flex-start;
  }

  .hero-copy h1 {
    font-size: 28px;
    line-height: 36px;
  }

  .hero-tools {
    flex-direction: column;
    align-items: stretch;
  }

  .status-filter {
    width: 100%;
  }

  .meeting-card,
  .create-card {
    min-height: 220px;
  }

  .section-heading {
    align-items: flex-start;
  }

  .meeting-table {
    padding: 12px;
  }
}
</style>
