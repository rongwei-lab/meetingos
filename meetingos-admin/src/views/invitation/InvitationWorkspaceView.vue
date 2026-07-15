<template>
  <div class="invitation-workspace">
    <header class="workspace-header">
      <div>
        <h1 class="page-title">邀请函</h1>
        <p class="page-subtitle">为同一场会议并行管理多份邀请方案、分发渠道和转化效果</p>
      </div>
      <n-button v-if="activeTab === 'plans'" type="primary" :disabled="!routeMeetingReady" @click="showCreate = true">
        <template #icon><n-icon :component="AddOutline" /></template>
        新建方案
      </n-button>
    </header>

    <n-alert v-if="!tduckCapabilityLoading && !tduckAttributionAvailable" type="warning" :bordered="false" class="capability-alert">
      当前 TDuck 部署未检测到 `extValue` 归因补丁。邀请访问与点击仍可统计，但 TDuck 首次提交无法归因。
    </n-alert>

    <nav class="workspace-tabs" aria-label="邀请函工作区">
      <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
        <n-icon :component="tab.icon" />
        {{ tab.label }}
      </button>
    </nav>

    <section v-if="activeTab === 'plans'" class="workspace-panel surface">
      <div class="panel-toolbar">
        <div>
          <strong>邀请方案</strong>
          <span>{{ invitations.length }} 份方案，可同时发布</span>
        </div>
        <n-input v-model:value="keyword" clearable placeholder="搜索方案名称" class="search-field">
          <template #prefix><n-icon :component="SearchOutline" /></template>
        </n-input>
      </div>

      <n-spin :show="loadingInvitations">
        <n-data-table v-if="!isMobile" :columns="invitationColumns" :data="filteredInvitations" :row-key="rowKey" :pagination="{ pageSize: 10 }" :scroll-x="930" />
        <div v-else class="mobile-plan-list">
          <article v-for="item in filteredInvitations" :key="item.id" class="mobile-plan-item">
            <div>
              <strong>{{ item.name }}</strong>
              <small>{{ formatDateTime(item.updatedAt) }}</small>
            </div>
            <n-tag :type="statusMeta[item.status].type" size="small">{{ statusMeta[item.status].label }}</n-tag>
            <p>版本 {{ item.latestVersionNo || "未发布" }} · {{ item.shareLinkCount }} 个渠道</p>
            <div class="mobile-plan-actions">
              <n-button size="small" secondary @click="openPreview(item)">预览</n-button>
              <n-button size="small" type="primary" @click="openDesigner(item)">管理方案</n-button>
              <n-button size="small" quaternary @click="copyPlan(item)">复制</n-button>
            </div>
          </article>
        </div>
        <n-empty v-if="!filteredInvitations.length && !loadingInvitations" description="还没有邀请方案">
          <template #extra><n-button type="primary" @click="showCreate = true">创建第一份方案</n-button></template>
        </n-empty>
      </n-spin>
    </section>

    <section v-else-if="activeTab === 'channels'" class="workspace-panel surface">
      <div class="panel-toolbar channel-toolbar">
        <div>
          <strong>渠道管理</strong>
          <span>每个渠道拥有独立链接和二维码，停用不会删除历史数据</span>
        </div>
        <div class="toolbar-actions">
          <n-select v-model:value="selectedInvitationId" :options="invitationOptions" placeholder="选择方案" class="plan-select" />
          <n-button type="primary" :disabled="!selectedInvitationId" @click="showCreateLink = true">
            <template #icon><n-icon :component="LinkOutline" /></template>
            新建渠道
          </n-button>
        </div>
      </div>
      <n-spin :show="loadingLinks">
        <n-data-table :columns="shareLinkColumns" :data="shareLinks" :row-key="shareRowKey" :scroll-x="1100" />
        <n-empty v-if="!shareLinks.length && !loadingLinks" description="请选择已发布方案并创建分发渠道" />
      </n-spin>
    </section>

    <section v-else class="analytics-section">
      <div class="analytics-filters surface">
        <div>
          <strong>效果分析</strong>
          <span>PV/UV → 报名点击 → TDuck 首次提交</span>
        </div>
        <div class="toolbar-actions">
          <n-select v-model:value="analyticsInvitationId" clearable :options="invitationOptions" placeholder="全部方案" class="plan-select" />
          <n-date-picker v-model:value="dateRange" type="daterange" clearable />
          <n-button :loading="loadingAnalytics" @click="loadAnalytics">查询</n-button>
        </div>
      </div>

      <n-spin :show="loadingAnalytics">
        <div class="metric-grid">
          <article v-for="metric in analyticsMetrics" :key="metric.label" class="metric-item surface">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.hint }}</small>
          </article>
        </div>
        <div class="analytics-grid">
          <section class="chart-panel surface">
            <header><strong>按天趋势</strong><span>访问、点击与报名</span></header>
            <InvitationAnalyticsChart :data="analytics.daily" />
          </section>
          <section class="funnel-panel surface">
            <header><strong>渠道漏斗</strong><span>当前筛选范围</span></header>
            <div class="funnel-row"><span>访问 PV</span><strong>{{ analytics.pageViews }}</strong><i style="width: 100%" /></div>
            <div class="funnel-row"><span>独立访客</span><strong>{{ analytics.uniqueVisitors }}</strong><i :style="{ width: funnelWidth(analytics.uniqueVisitors, analytics.pageViews) }" /></div>
            <div class="funnel-row"><span>报名点击</span><strong>{{ analytics.ctaClicks }}</strong><i :style="{ width: funnelWidth(analytics.ctaClicks, analytics.pageViews) }" /></div>
            <div class="funnel-row"><span>归因报名</span><strong>{{ analytics.attributedRegistrations }}</strong><i :style="{ width: funnelWidth(analytics.attributedRegistrations, analytics.pageViews) }" /></div>
          </section>
        </div>
        <section class="channel-compare surface">
          <header><strong>方案 / 渠道对比</strong><span>{{ analytics.channels.length }} 个渠道</span></header>
          <n-data-table :columns="analyticsColumns" :data="analytics.channels" :row-key="analyticsRowKey" :scroll-x="950" />
        </section>
      </n-spin>
    </section>

    <n-modal v-model:show="showCreate" preset="card" title="新建邀请方案" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="方案名称"><n-input v-model:value="createDraft.name" placeholder="例如：主视觉 A 版" maxlength="100" show-count /></n-form-item>
        <n-form-item label="初始内容">
          <n-radio-group v-model:value="createDraft.mode">
            <n-radio-button value="default">会议信息模板</n-radio-button>
            <n-radio-button value="blank">空白画布</n-radio-button>
          </n-radio-group>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end"><n-button @click="showCreate = false">取消</n-button><n-button type="primary" :loading="creating" @click="submitCreate">创建并设计</n-button></n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showCreateLink" preset="card" title="新建渠道链接" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="渠道媒介"><n-select v-model:value="linkDraft.channelType" :options="channelTypeOptions" /></n-form-item>
        <n-form-item label="渠道名称"><n-input v-model:value="linkDraft.channelName" placeholder="例如：合作方社群 A" maxlength="100" /></n-form-item>
        <n-form-item label="失效时间（可选）">
          <n-date-picker
            v-model:formatted-value="linkDraft.expiresAt"
            type="datetime"
            value-format="yyyy-MM-dd'T'HH:mm:ss"
            clearable
            style="width: 100%"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end"><n-button @click="showCreateLink = false">取消</n-button><n-button type="primary" :loading="creatingLink" @click="submitCreateLink">创建渠道</n-button></n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showQr" preset="card" title="渠道二维码" style="width: 390px">
      <div class="qr-preview"><img v-if="activeQrDataUrl" :src="activeQrDataUrl" alt="渠道二维码" /><strong>{{ activeShareLink?.channelName }}</strong><span>{{ activeShareLink?.shareUrl }}</span></div>
      <template #footer><n-space justify="end"><n-button @click="copyShareUrl(activeShareLink)">复制链接</n-button><n-button type="primary" @click="downloadQr">下载二维码</n-button></n-space></template>
    </n-modal>

    <n-modal v-model:show="showPoster" preset="card" title="渠道海报导出" style="width: 520px">
      <n-spin :show="loadingPoster">
        <div class="poster-preview-shell">
          <InvitationFabricCanvas
            v-if="posterDocument && activeShareLink"
            :key="`${activeShareLink.id}:${posterVersionNo || 0}`"
            ref="posterCanvasRef"
            :canvas="posterDocument.poster"
            :meeting="posterMeeting"
            :assets="posterAssets"
            :share-url="activeShareLink.shareUrl"
            :share-token="activeShareLink.shareToken"
            :public-query-token="currentPublicQueryToken"
            :zoom="0.36"
            @ready="posterCanvasReady = true"
          />
        </div>
      </n-spin>
      <template #footer><n-space justify="end"><n-button @click="closePoster">取消</n-button><n-button type="primary" :loading="exportingPoster" :disabled="loadingPoster || exportingPoster || !posterDocument || !posterCanvasReady" @click="downloadPoster">导出 1080×1920 PNG</n-button></n-space></template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  AnalyticsOutline,
  AddOutline,
  BrushOutline,
  CopyOutline,
  DownloadOutline,
  EyeOutline,
  LinkOutline,
  OpenOutline,
  SearchOutline
} from "@vicons/ionicons5";
import {
  NButton,
  NDataTable,
  NDatePicker,
  NIcon,
  NPopconfirm,
  NSwitch,
  NTag,
  useMessage,
  type DataTableColumns
} from "naive-ui";
import InvitationAnalyticsChart from "@/components/invitation/InvitationAnalyticsChart.vue";
import InvitationFabricCanvas from "@/components/invitation/InvitationFabricCanvas.vue";
import {
  changeInvitationStatus,
  copyInvitation,
  createInvitation,
  createInvitationShareLink,
  detectTduckAttributionCapability,
  getInvitation,
  getInvitationAnalytics,
  listInvitationAssets,
  listInvitations,
  listInvitationShareLinks,
  updateInvitationShareLink
} from "@/api/invitations";
import { extractErrorMessage } from "@/api/http";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import { useMeetingStore } from "@/stores/meeting";
import type {
  InvitationAnalytics,
  InvitationAnalyticsChannel,
  InvitationAsset,
  InvitationChannelType,
  InvitationDesignDocumentV1,
  InvitationMeetingSnapshot,
  InvitationShareLink,
  InvitationStatus,
  InvitationSummary
} from "@/types/invitation";
import { createBlankInvitationDocument, createDefaultInvitationDocument, validateInvitationDocument } from "@/utils/invitationDocument";
import {
  invitationShareLinkAvailability,
  isInvitationShareLinkAvailable,
  normalizeInvitationLocalDateTime
} from "@/utils/invitationShareLink";

type WorkspaceTab = "plans" | "channels" | "analytics";
type PosterCanvasExpose = { exportPng: () => string };

const route = useRoute();
const router = useRouter();
const message = useMessage();
const meetingStore = useMeetingStore();
const isMobile = useIsMobile();
const { routeMeetingId: meetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });
const routeMeetingReady = computed(() => String(meetingStore.currentMeeting?.id || "") === meetingId.value);
const activeTab = ref<WorkspaceTab>(readTab(route.query.tab));
const invitations = ref<InvitationSummary[]>([]);
const keyword = ref("");
const loadingInvitations = ref(false);
const showCreate = ref(false);
const creating = ref(false);
const createDraft = reactive({ name: "", mode: "default" as "default" | "blank" });
const selectedInvitationId = ref<number | null>(null);
const shareLinks = ref<InvitationShareLink[]>([]);
const loadingLinks = ref(false);
const showCreateLink = ref(false);
const creatingLink = ref(false);
const linkDraft = reactive<{ channelType: InvitationChannelType; channelName: string; expiresAt: string | null }>({ channelType: "WECHAT_FRIENDS", channelName: "", expiresAt: null });
const showQr = ref(false);
const activeShareLink = ref<InvitationShareLink | null>(null);
const currentPublicQueryToken = computed(() => meetingStore.currentMeeting?.publicQueryToken || "");
const activeQrDataUrl = ref("");
const showPoster = ref(false);
const loadingPoster = ref(false);
const exportingPoster = ref(false);
const posterCanvasReady = ref(false);
const posterVersionNo = ref<number | null>(null);
const posterDocument = ref<InvitationDesignDocumentV1 | null>(null);
const posterAssets = ref<InvitationAsset[]>([]);
const posterMeeting = ref<Partial<InvitationMeetingSnapshot>>({});
const posterCanvasRef = ref<PosterCanvasExpose>();
const tduckCapabilityLoading = ref(true);
const tduckAttributionAvailable = ref(false);
const analyticsInvitationId = ref<number | null>(null);
const dateRange = ref<[number, number] | null>(defaultDateRange());
const loadingAnalytics = ref(false);
const analytics = ref<InvitationAnalytics>(emptyAnalytics());
let workspaceRouteGeneration = 0;
let invitationLoadGeneration = 0;
let shareLinkLoadGeneration = 0;
let analyticsLoadGeneration = 0;
let posterLoadGeneration = 0;

const tabs = [
  { key: "plans" as const, label: "方案列表", icon: BrushOutline },
  { key: "channels" as const, label: "渠道管理", icon: LinkOutline },
  { key: "analytics" as const, label: "效果分析", icon: AnalyticsOutline }
];

const statusMeta: Record<InvitationStatus, { label: string; type: "default" | "success" | "warning" | "error" }> = {
  DRAFT: { label: "草稿", type: "default" },
  PUBLISHED: { label: "已发布", type: "success" },
  UNPUBLISHED: { label: "已下线", type: "warning" },
  ARCHIVED: { label: "已归档", type: "error" }
};

const channelTypeOptions = [
  { label: "微信好友", value: "WECHAT_FRIENDS" },
  { label: "朋友圈", value: "WECHAT_MOMENTS" },
  { label: "邮件", value: "EMAIL" },
  { label: "线下海报", value: "OFFLINE_POSTER" },
  { label: "其他", value: "OTHER" }
] satisfies Array<{ label: string; value: InvitationChannelType }>;

const filteredInvitations = computed(() => {
  const normalized = keyword.value.trim().toLocaleLowerCase("zh-CN");
  return normalized ? invitations.value.filter((item) => item.name.toLocaleLowerCase("zh-CN").includes(normalized)) : invitations.value;
});
const invitationOptions = computed(() => invitations.value.map((item) => ({ label: item.name, value: item.id })));
const analyticsMetrics = computed(() => [
  { label: "页面浏览 PV", value: analytics.value.pageViews.toLocaleString(), hint: `${analytics.value.uniqueVisitors.toLocaleString()} 独立访客` },
  { label: "报名点击", value: analytics.value.ctaClicks.toLocaleString(), hint: `${formatRate(analytics.value.clickThroughRate)} 点击率` },
  { label: "已归因报名", value: analytics.value.attributedRegistrations.toLocaleString(), hint: `${analytics.value.activeAttributedRegistrations.toLocaleString()} 当前有效` },
  { label: "报名转化率", value: formatRate(analytics.value.registrationConversionRate), hint: `${analytics.value.currentActiveRegistrations.toLocaleString()} 当前总报名` },
  { label: "独立点击访客", value: analytics.value.uniqueClickVisitors.toLocaleString(), hint: "按访客 Cookie 去重" },
  { label: "直接 / 历史未归因", value: (analytics.value.directUnattributedRegistrations + analytics.value.legacyUnattributedRegistrations).toLocaleString(), hint: `${analytics.value.invalidTokenRegistrations} 个令牌无效` }
]);

watch(activeTab, (tab) => {
  void router.replace({ query: { ...route.query, tab } });
  if (tab === "analytics") void loadAnalytics();
});

watch([activeTab, selectedInvitationId], ([tab, invitationId]) => {
  if (tab === "channels") void loadShareLinks(invitationId);
});

const invitationColumns: DataTableColumns<InvitationSummary> = [
  { title: "方案名称", key: "name", minWidth: 210, render: (row) => h("div", { class: "table-title" }, [h("strong", { title: row.name }, row.name), h("small", `草稿修订 ${row.draftRevision}`)]) },
  { title: "状态", key: "status", width: 100, render: (row) => h(NTag, { size: "small", type: statusMeta[row.status].type }, { default: () => statusMeta[row.status].label }) },
  { title: "发布版本", key: "latestVersionNo", width: 100, render: (row) => row.latestVersionNo ? `V${row.latestVersionNo}` : "—" },
  { title: "渠道", key: "shareLinkCount", width: 80, render: (row) => `${row.shareLinkCount} 个` },
  { title: "更新时间", key: "updatedAt", width: 170, render: (row) => formatDateTime(row.updatedAt) },
  {
    title: "操作", key: "actions", width: 330, fixed: "right", render: (row) => h("div", { class: "table-actions" }, [
      h(NButton, { size: "small", secondary: true, onClick: () => openDesigner(row) }, { icon: () => h(NIcon, { component: BrushOutline }), default: () => "设计" }),
      h(NButton, { size: "small", quaternary: true, onClick: () => openPreview(row) }, { icon: () => h(NIcon, { component: EyeOutline }), default: () => "预览" }),
      h(NButton, { size: "small", quaternary: true, onClick: () => copyPlan(row) }, { icon: () => h(NIcon, { component: CopyOutline }), default: () => "复制" }),
      row.status === "PUBLISHED"
        ? h(NButton, { size: "small", quaternary: true, onClick: () => runStatusAction(row, "archive") }, { default: () => "归档" })
        : h(NButton, { size: "small", type: "primary", disabled: row.status === "ARCHIVED", onClick: () => runStatusAction(row, "publish") }, { default: () => "发布" })
    ])
  }
];

const shareLinkColumns: DataTableColumns<InvitationShareLink> = [
  { title: "渠道", key: "channelName", minWidth: 190, render: (row) => h("div", { class: "table-title" }, [h("strong", { title: row.channelName }, row.channelName), h("small", channelLabel(row.channelType))]) },
  { title: "当前版本", key: "currentVersionNo", width: 100, render: (row) => row.currentVersionNo ? `V${row.currentVersionNo}` : "未发布" },
  {
    title: "状态",
    key: "enabled",
    width: 165,
    render: (row) => {
      const availability = invitationShareLinkAvailability(row, invitations.value);
      return h("div", { class: "channel-state" }, [
        h(NSwitch, { value: row.enabled, "aria-label": `${row.channelName}渠道启用状态`, onUpdateValue: (enabled: boolean) => toggleShareLink(row, enabled) }),
        h(NTag, { size: "small", type: availability.available ? "success" : "warning" }, { default: () => availability.available ? "可用" : availability.reason })
      ]);
    }
  },
  { title: "失效时间", key: "expiresAt", width: 170, render: (row) => row.expiresAt ? formatDateTime(row.expiresAt) : "长期有效" },
  { title: "链接", key: "shareUrl", minWidth: 230, ellipsis: { tooltip: true } },
  { title: "操作", key: "actions", width: 290, fixed: "right", render: (row) => h("div", { class: "table-actions" }, [
    h(NButton, { size: "small", quaternary: true, disabled: !isShareLinkAvailable(row), onClick: () => copyShareUrl(row) }, { icon: () => h(NIcon, { component: CopyOutline }), default: () => "复制" }),
    h(NButton, { size: "small", quaternary: true, disabled: !isShareLinkAvailable(row), onClick: () => openQr(row) }, { default: () => "二维码" }),
    h(NButton, { size: "small", type: "primary", disabled: !isShareLinkAvailable(row), onClick: () => openPoster(row) }, { icon: () => h(NIcon, { component: DownloadOutline }), default: () => "海报" }),
    h(NButton, { size: "small", quaternary: true, disabled: !isShareLinkAvailable(row), "aria-label": "打开渠道链接", title: "打开渠道链接", onClick: () => openShareUrl(row) }, { icon: () => h(NIcon, { component: OpenOutline }) })
  ]) }
];

const analyticsColumns: DataTableColumns<InvitationAnalyticsChannel> = [
  { title: "方案", key: "invitationName", minWidth: 190 },
  { title: "渠道", key: "channelName", minWidth: 160 },
  { title: "PV", key: "pageViews", width: 90 },
  { title: "UV", key: "uniqueVisitors", width: 90 },
  { title: "点击", key: "ctaClicks", width: 90 },
  { title: "归因报名", key: "attributedRegistrations", width: 110 },
  { title: "有效报名", key: "activeAttributedRegistrations", width: 110 },
  { title: "转化率", key: "rate", width: 100, render: (row) => formatRate(row.uniqueVisitors ? row.attributedRegistrations / row.uniqueVisitors : 0) }
];

onMounted(() => {
  void checkTduckCapability();
  void syncWorkspaceFromRoute();
});

watch(meetingId, (value, previousValue) => {
  if (value && value !== previousValue) void syncWorkspaceFromRoute();
});

watch(showPoster, (visible) => {
  if (!visible) resetPosterState();
});

async function syncWorkspaceFromRoute() {
  const targetMeetingId = meetingId.value;
  const generation = ++workspaceRouteGeneration;
  resetMeetingScopedState();
  const meeting = await syncMeetingFromRoute();
  if (!meeting || generation !== workspaceRouteGeneration || meetingId.value !== targetMeetingId) return;
  await loadInvitations(targetMeetingId);
  if (generation !== workspaceRouteGeneration || meetingId.value !== targetMeetingId) return;
  if (activeTab.value === "analytics") await loadAnalytics();
}

function resetMeetingScopedState() {
  invitationLoadGeneration += 1;
  shareLinkLoadGeneration += 1;
  analyticsLoadGeneration += 1;
  invitations.value = [];
  selectedInvitationId.value = null;
  analyticsInvitationId.value = null;
  shareLinks.value = [];
  analytics.value = emptyAnalytics();
  loadingInvitations.value = false;
  loadingLinks.value = false;
  loadingAnalytics.value = false;
  showCreate.value = false;
  showCreateLink.value = false;
  linkDraft.channelName = "";
  linkDraft.expiresAt = null;
  showQr.value = false;
  activeQrDataUrl.value = "";
  closePoster();
}

async function loadInvitations(targetMeetingId = meetingId.value) {
  const generation = ++invitationLoadGeneration;
  loadingInvitations.value = true;
  try {
    const nextInvitations = await listInvitations(targetMeetingId);
    if (generation !== invitationLoadGeneration || meetingId.value !== targetMeetingId) return;
    invitations.value = nextInvitations;
    if (!selectedInvitationId.value && invitations.value.length) selectedInvitationId.value = invitations.value[0]!.id;
  } catch (error) {
    if (generation === invitationLoadGeneration && meetingId.value === targetMeetingId) message.error(extractErrorMessage(error, "邀请方案加载失败"));
  } finally {
    if (generation === invitationLoadGeneration) loadingInvitations.value = false;
  }
}

async function checkTduckCapability() {
  try { tduckAttributionAvailable.value = await detectTduckAttributionCapability(); }
  catch { tduckAttributionAvailable.value = false; }
  finally { tduckCapabilityLoading.value = false; }
}

async function submitCreate() {
  if (!createDraft.name.trim()) { message.warning("请输入方案名称"); return; }
  if (!routeMeetingReady.value) { message.warning("会议上下文仍在同步，请稍后重试"); return; }
  creating.value = true;
  try {
    const document = createDraft.mode === "blank" ? createBlankInvitationDocument() : createDefaultInvitationDocument(currentMeetingSnapshot());
    const invitation = await createInvitation(meetingId.value, { name: createDraft.name.trim(), document });
    showCreate.value = false;
    createDraft.name = "";
    await loadInvitations();
    void router.push({ name: "meeting-invitation-designer", params: { meetingId: meetingId.value, invitationId: invitation.id } });
  } catch (error) { message.error(extractErrorMessage(error, "邀请方案创建失败")); }
  finally { creating.value = false; }
}

async function copyPlan(item: InvitationSummary) {
  const targetMeetingId = meetingId.value;
  try {
    await copyInvitation(targetMeetingId, item.id, `${item.name} - 副本`);
    if (meetingId.value !== targetMeetingId) return;
    message.success("已复制邀请方案");
    await loadInvitations(targetMeetingId);
  }
  catch (error) { if (meetingId.value === targetMeetingId) message.error(extractErrorMessage(error, "复制失败")); }
}

async function runStatusAction(item: InvitationSummary, action: "publish" | "archive") {
  const targetMeetingId = meetingId.value;
  try {
    if (action === "publish") {
      const detail = await getInvitation(targetMeetingId, item.id);
      if (meetingId.value !== targetMeetingId) return;
      const errors = validateInvitationDocument(detail.draftDocument, Boolean(import.meta.env.VITE_AMAP_KEY));
      if (errors.length) { message.error(errors[0]!); return; }
    }
    await changeInvitationStatus(targetMeetingId, item.id, action);
    if (meetingId.value !== targetMeetingId) return;
    message.success(action === "publish" ? "方案已发布" : "方案已归档");
    await loadInvitations(targetMeetingId);
  }
  catch (error) { if (meetingId.value === targetMeetingId) message.error(extractErrorMessage(error, "状态更新失败")); }
}

function openDesigner(item: InvitationSummary) {
  void router.push({ name: "meeting-invitation-designer", params: { meetingId: meetingId.value, invitationId: item.id } });
}

function openPreview(item: InvitationSummary) {
  void router.push({ name: "meeting-invitation-designer", params: { meetingId: meetingId.value, invitationId: item.id }, query: { preview: "1" } });
}

async function loadShareLinks(invitationId: number | null) {
  if (!invitationId) { shareLinks.value = []; return; }
  const targetMeetingId = meetingId.value;
  const generation = ++shareLinkLoadGeneration;
  loadingLinks.value = true;
  try {
    const nextLinks = await listInvitationShareLinks(targetMeetingId, invitationId);
    if (generation !== shareLinkLoadGeneration || meetingId.value !== targetMeetingId || selectedInvitationId.value !== invitationId) return;
    shareLinks.value = nextLinks;
  }
  catch (error) {
    if (generation === shareLinkLoadGeneration && meetingId.value === targetMeetingId) message.error(extractErrorMessage(error, "渠道链接加载失败"));
  }
  finally { if (generation === shareLinkLoadGeneration) loadingLinks.value = false; }
}

async function submitCreateLink() {
  if (!selectedInvitationId.value || !linkDraft.channelName.trim()) { message.warning("请选择方案并填写渠道名称"); return; }
  creatingLink.value = true;
  try {
    await createInvitationShareLink(meetingId.value, selectedInvitationId.value, {
      channelType: linkDraft.channelType,
      channelName: linkDraft.channelName.trim(),
      expiresAt: normalizeInvitationLocalDateTime(linkDraft.expiresAt)
    });
    showCreateLink.value = false;
    linkDraft.channelName = "";
    linkDraft.expiresAt = null;
    await loadShareLinks(selectedInvitationId.value);
    message.success("渠道链接已创建");
  } catch (error) { message.error(extractErrorMessage(error, "渠道创建失败")); }
  finally { creatingLink.value = false; }
}

async function toggleShareLink(link: InvitationShareLink, enabled: boolean) {
  try {
    const updated = await updateInvitationShareLink(meetingId.value, link.invitationId, link.id, { enabled });
    Object.assign(link, updated);
    message.success(enabled ? "渠道已启用" : "渠道已停用，历史数据保留");
  }
  catch (error) { message.error(extractErrorMessage(error, "渠道状态更新失败")); }
}

async function copyShareUrl(link: InvitationShareLink | null | undefined) {
  if (!link) return;
  if (!ensureShareLinkAvailable(link)) return;
  await navigator.clipboard.writeText(link.shareUrl);
  message.success("渠道链接已复制");
}

async function openQr(link: InvitationShareLink) {
  if (!ensureShareLinkAvailable(link)) return;
  activeShareLink.value = link;
  const { default: QRCode } = await import("qrcode");
  activeQrDataUrl.value = await QRCode.toDataURL(link.shareUrl, { width: 960, margin: 3, errorCorrectionLevel: "H", color: { dark: "#0b3425", light: "#ffffff" } });
  showQr.value = true;
}

function downloadQr() {
  if (!activeQrDataUrl.value || !activeShareLink.value || !ensureShareLinkAvailable(activeShareLink.value)) return;
  downloadDataUrl(activeQrDataUrl.value, `${activeShareLink.value.channelName}-二维码.png`);
}

async function openPoster(link: InvitationShareLink) {
  if (!ensureShareLinkAvailable(link)) return;
  resetPosterState();
  showPoster.value = true;
  loadingPoster.value = true;
  const targetMeetingId = meetingId.value;
  const generation = posterLoadGeneration;
  try {
    const [detail, assets, links] = await Promise.all([
      getInvitation(targetMeetingId, link.invitationId),
      listInvitationAssets(targetMeetingId),
      listInvitationShareLinks(targetMeetingId, link.invitationId)
    ]);
    if (generation !== posterLoadGeneration || meetingId.value !== targetMeetingId) return;
    const freshLink = links.find((item) => item.id === link.id);
    if (!freshLink || !isInvitationShareLinkAvailable(freshLink, [detail])) throw new Error("渠道链接已停用、过期或方案未发布，不能导出海报");
    const publishedVersion = detail.latestPublishedVersion;
    const publishedDocument = publishedVersion?.designDocument || publishedVersion?.document;
    if (!publishedDocument || !publishedVersion?.meetingSnapshot || publishedVersion.versionNo !== freshLink.currentVersionNo) {
      throw new Error("渠道未指向当前发布版本，请刷新后重试");
    }
    activeShareLink.value = freshLink;
    posterVersionNo.value = publishedVersion.versionNo;
    posterDocument.value = publishedDocument;
    posterAssets.value = assets;
    // 正式海报必须使用发布快照，不能被发布后的会议资料变更污染。
    posterMeeting.value = publishedVersion.meetingSnapshot;
    if (selectedInvitationId.value === link.invitationId) shareLinks.value = links;
  } catch (error) {
    if (generation === posterLoadGeneration) {
      message.error(extractErrorMessage(error, "海报预览加载失败"));
      closePoster();
    }
  }
  finally { if (generation === posterLoadGeneration) loadingPoster.value = false; }
}

async function downloadPoster() {
  if (!posterCanvasReady.value || !posterCanvasRef.value || !activeShareLink.value || !posterVersionNo.value) {
    message.warning("海报画布仍在生成，请稍后重试");
    return;
  }
  const link = activeShareLink.value;
  const targetMeetingId = meetingId.value;
  const generation = posterLoadGeneration;
  exportingPoster.value = true;
  try {
    // 最终导出前重新读取渠道与发布版本，避免弹窗打开后渠道被停用、过期或切换版本。
    const [detail, links] = await Promise.all([
      getInvitation(targetMeetingId, link.invitationId),
      listInvitationShareLinks(targetMeetingId, link.invitationId)
    ]);
    if (generation !== posterLoadGeneration || meetingId.value !== targetMeetingId) return;
    const freshLink = links.find((item) => item.id === link.id);
    const currentVersionNo = detail.latestPublishedVersion?.versionNo;
    if (!freshLink || !isInvitationShareLinkAvailable(freshLink, [detail])) {
      throw new Error("渠道链接已不可用，海报未导出");
    }
    if (currentVersionNo !== freshLink.currentVersionNo || currentVersionNo !== posterVersionNo.value) {
      throw new Error("方案已发布新版本，请重新打开海报预览后导出");
    }
    if (!posterCanvasReady.value || !posterCanvasRef.value) throw new Error("海报画布尚未准备完成");
    activeShareLink.value = freshLink;
    downloadDataUrl(posterCanvasRef.value.exportPng(), `${freshLink.channelName}-邀请海报.png`);
  }
  catch (error) { if (generation === posterLoadGeneration) message.error(extractErrorMessage(error, "海报导出失败")); }
  finally { if (generation === posterLoadGeneration) exportingPoster.value = false; }
}

async function loadAnalytics() {
  const targetMeetingId = meetingId.value;
  const generation = ++analyticsLoadGeneration;
  loadingAnalytics.value = true;
  try {
    const from = dateRange.value ? localDate(dateRange.value[0]) : undefined;
    const to = dateRange.value ? localDate(dateRange.value[1]) : undefined;
    const nextAnalytics = await getInvitationAnalytics(targetMeetingId, { invitationId: analyticsInvitationId.value || undefined, from, to });
    if (generation !== analyticsLoadGeneration || meetingId.value !== targetMeetingId) return;
    analytics.value = nextAnalytics;
  } catch (error) {
    if (generation === analyticsLoadGeneration && meetingId.value === targetMeetingId) message.error(extractErrorMessage(error, "效果数据加载失败"));
  }
  finally { if (generation === analyticsLoadGeneration) loadingAnalytics.value = false; }
}

function currentMeetingSnapshot(): Partial<InvitationMeetingSnapshot> {
  const meeting = meetingStore.currentMeeting;
  if (!meeting || String(meeting.id) !== meetingId.value) return {};
  return { id: Number(meeting.id), code: meeting.code, name: meeting.name, organizer: meeting.organizer, coOrganizer: meeting.coOrganizer, startTime: meeting.startTime, endTime: meeting.endTime, registrationStartTime: meeting.registrationStartTime, registrationEndTime: meeting.registrationEndTime, location: meeting.venue, venueName: meeting.venue, description: meeting.description, agenda: meeting.agenda, coverImageUrl: meeting.coverImageUrl, contactName: meeting.contactName, contactPhone: meeting.contactPhone };
}

function isShareLinkAvailable(link: InvitationShareLink) {
  return isInvitationShareLinkAvailable(link, invitations.value);
}

function ensureShareLinkAvailable(link: InvitationShareLink) {
  const availability = invitationShareLinkAvailability(link, invitations.value);
  if (availability.available) return true;
  message.warning(`${availability.reason || "渠道链接不可用"}，请刷新后重试`);
  return false;
}

function openShareUrl(link: InvitationShareLink) {
  if (!ensureShareLinkAvailable(link)) return;
  window.open(link.shareUrl, "_blank", "noopener,noreferrer");
}

function closePoster() {
  showPoster.value = false;
  resetPosterState();
}

function resetPosterState() {
  posterLoadGeneration += 1;
  posterCanvasReady.value = false;
  posterVersionNo.value = null;
  posterDocument.value = null;
  posterAssets.value = [];
  posterMeeting.value = {};
  posterCanvasRef.value = undefined;
  activeShareLink.value = null;
  loadingPoster.value = false;
  exportingPoster.value = false;
}

function formatDateTime(value?: string | null) { return value ? new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—"; }
function formatRate(value: number) { const ratio = value > 1 ? value / 100 : value; return `${(ratio * 100).toFixed(1)}%`; }
function funnelWidth(value: number, total: number) { return `${Math.max(value ? 8 : 0, Math.min(100, total ? value / total * 100 : 0))}%`; }
function channelLabel(type: InvitationChannelType) { return channelTypeOptions.find((item) => item.value === type)?.label || "其他"; }
function rowKey(row: InvitationSummary) { return row.id; }
function shareRowKey(row: InvitationShareLink) { return row.id; }
function analyticsRowKey(row: InvitationAnalyticsChannel) { return row.shareLinkId; }
function readTab(value: unknown): WorkspaceTab { return value === "channels" || value === "analytics" ? value : "plans"; }
function defaultDateRange(): [number, number] { const end = new Date(); const start = new Date(); start.setDate(start.getDate() - 29); return [start.getTime(), end.getTime()]; }
function localDate(timestamp: number) { const date = new Date(timestamp); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`; }
function downloadDataUrl(url: string, filename: string) { const link = document.createElement("a"); link.href = url; link.download = filename; link.click(); }
function emptyAnalytics(): InvitationAnalytics { return { pageViews: 0, uniqueVisitors: 0, ctaClicks: 0, uniqueClickVisitors: 0, attributedRegistrations: 0, activeAttributedRegistrations: 0, directUnattributedRegistrations: 0, invalidTokenRegistrations: 0, legacyUnattributedRegistrations: 0, currentActiveRegistrations: 0, clickThroughRate: 0, registrationConversionRate: 0, daily: [], channels: [] }; }
</script>

<style scoped>
.invitation-workspace { display: grid; gap: 16px; }
.workspace-header, .panel-toolbar, .analytics-filters, .toolbar-actions, .workspace-tabs { display: flex; align-items: center; }
.workspace-header, .panel-toolbar, .analytics-filters { justify-content: space-between; gap: 18px; }
.capability-alert { border: 1px solid #f1dbab; }
.workspace-tabs { width: fit-content; padding: 4px; gap: 3px; background: #edf4f0; border: 1px solid #dce8e2; border-radius: 7px; }
.workspace-tabs button { min-height: 36px; padding: 0 15px; border: 0; border-radius: 5px; display: inline-flex; align-items: center; gap: 7px; color: #62736b; background: transparent; font-size: 13px; cursor: pointer; }
.workspace-tabs button.active { color: #08784f; background: #fff; box-shadow: 0 1px 4px rgba(19, 63, 45, .1); font-weight: 700; }
.workspace-panel { overflow: hidden; }
.panel-toolbar, .analytics-filters { padding: 16px 18px; border-bottom: 1px solid var(--border-color); }
.panel-toolbar > div:first-child, .analytics-filters > div:first-child, .chart-panel header, .funnel-panel header, .channel-compare header { display: grid; gap: 3px; }
.panel-toolbar strong, .analytics-filters strong, .chart-panel header strong, .funnel-panel header strong, .channel-compare header strong { font-size: 15px; }
.panel-toolbar span, .analytics-filters span, .chart-panel header span, .funnel-panel header span, .channel-compare header span { color: var(--text-secondary); font-size: 12px; }
.search-field { width: 250px; }
.toolbar-actions { gap: 10px; }
.plan-select { width: 220px; }
.workspace-panel :deep(.table-title) { min-width: 0; display: grid; gap: 3px; line-height: 1.35; }
.workspace-panel :deep(.table-title strong) { display: block; max-width: 100%; overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.workspace-panel :deep(.table-title small) { display: block; color: var(--text-secondary); font-size: 11px; }
.workspace-panel :deep(.table-actions) { display: flex; align-items: center; gap: 5px; white-space: nowrap; }
.workspace-panel :deep(.channel-state) { display: flex; align-items: center; gap: 7px; }
.analytics-section { display: grid; gap: 16px; }
.analytics-filters { border-bottom: 0; border-radius: 8px; }
.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.metric-item { min-width: 0; padding: 16px; display: grid; gap: 4px; }
.metric-item span { color: var(--text-secondary); font-size: 12px; }
.metric-item strong { color: #173b2d; font-size: 24px; line-height: 32px; }
.metric-item small { color: #86978f; font-size: 11px; overflow-wrap: anywhere; }
.analytics-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(280px, .8fr); gap: 16px; }
.chart-panel, .funnel-panel, .channel-compare { padding: 18px; }
.chart-panel header, .funnel-panel header, .channel-compare header { margin-bottom: 12px; }
.funnel-panel { display: flex; flex-direction: column; }
.funnel-row { position: relative; min-height: 55px; padding: 12px 0 14px; display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 12px; border-bottom: 1px solid #edf3f0; }
.funnel-row span { color: #63756c; font-size: 13px; }
.funnel-row strong { color: #173b2d; font-size: 18px; }
.funnel-row i { position: absolute; left: 0; bottom: 7px; height: 4px; max-width: 100%; border-radius: 2px; background: #16805a; }
.qr-preview { display: grid; justify-items: center; gap: 10px; text-align: center; }
.qr-preview img { width: 260px; height: 260px; }
.qr-preview span { max-width: 100%; color: var(--text-secondary); font-size: 12px; overflow-wrap: anywhere; }
.poster-preview-shell { min-height: 460px; display: grid; place-items: center; overflow: auto; background: #edf2ef; border: 1px solid #dce8e2; }
.mobile-plan-list { display: grid; gap: 10px; padding: 12px; }
.mobile-plan-item { border-bottom: 1px solid var(--border-color); padding: 4px 0 14px; display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.mobile-plan-item > div:first-child { min-width: 0; display: grid; }
.mobile-plan-item strong { overflow-wrap: anywhere; }
.mobile-plan-item small, .mobile-plan-item p { color: var(--text-secondary); font-size: 12px; }
.mobile-plan-item p, .mobile-plan-actions { grid-column: 1 / -1; }
.mobile-plan-actions { display: flex; gap: 7px; flex-wrap: wrap; }

@media (max-width: 1120px) { .metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 800px) {
  .invitation-workspace { gap: 12px; }
  .workspace-header, .panel-toolbar, .analytics-filters, .channel-toolbar { align-items: stretch; flex-direction: column; }
  .workspace-tabs { width: 100%; }
  .workspace-tabs button { flex: 1; justify-content: center; padding: 0 8px; }
  .search-field, .plan-select { width: 100%; }
  .toolbar-actions { align-items: stretch; flex-direction: column; }
  .metric-grid, .analytics-grid { grid-template-columns: 1fr 1fr; }
  .chart-panel { grid-column: 1 / -1; }
}
@media (max-width: 520px) { .metric-grid, .analytics-grid { grid-template-columns: 1fr; } .chart-panel { grid-column: auto; } }
</style>
