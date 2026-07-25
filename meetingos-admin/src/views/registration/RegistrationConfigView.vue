<template>
  <div>
    <page-header title="TDuck 报名配置">
      <n-button type="primary" :loading="savingBinding" :disabled="!canSaveBinding" @click="saveBindingAndGenerateQr">
        {{ bindingActionLabel }}
      </n-button>
    </page-header>

    <div class="registration-grid">
      <n-card class="surface binding-card" :bordered="false">
        <template #header>
          <div class="card-title-row">
            <div>
              <h2>表单绑定</h2>
              <span>选择已发布 TDuck 表单，系统会生成固定中转入口</span>
            </div>
            <div class="binding-header-actions">
              <n-tag round :type="bindingStatusType">{{ bindingStatusLabel }}</n-tag>
              <n-dropdown
                v-if="!currentBoundFormKey"
                trigger="click"
                :options="createTduckFormOptions"
                :disabled="!currentMeeting"
                @select="handleCreateTduckFormSelect"
              >
                <n-button size="small" secondary :loading="openingTduck" :disabled="!currentMeeting">
                  <template #icon><n-icon :component="AddOutline" /></template>
                  新建 TDuck 表单
                </n-button>
              </n-dropdown>
              <n-button size="small" secondary :loading="openingTduckEditor" :disabled="!canOpenTduckEditor" @click="openTduckEditor">
                <template #icon><n-icon :component="CreateOutline" /></template>
                编辑当前表单
              </n-button>
            </div>
          </div>
        </template>

        <n-form class="binding-form" label-placement="top" :show-feedback="false">
          <div class="binding-form-grid">
            <n-form-item label="当前会议">
              <div class="current-meeting-box">
                <strong>{{ currentMeeting?.name || "未选择会议" }}</strong>
                <span v-if="!currentMeeting">请先在左侧选择会议</span>
              </div>
            </n-form-item>

            <n-form-item label="选择 TDuck 表单">
              <n-select
                v-model:value="selectedFormKey"
                filterable
                remote
                :clearable="canEditBinding"
                :disabled="!canEditBinding"
                :loading="loadingForms"
                :options="formSelectOptions"
                :placeholder="canEditBinding ? '搜索并选择已发布 TDuck 表单' : '当前会议已绑定 TDuck 表单'"
                @search="searchTduckForms"
                @update:value="handleFormSelect"
              />
            </n-form-item>
          </div>

          <n-alert v-if="currentBoundFormKey && bindingValidationStatus !== 'INVALID'" type="success" :bordered="false" class="binding-alert">
            已绑定 TDuck 表单。为避免误操作，当前会议不允许在此处改选其他表单。
          </n-alert>

          <n-alert v-if="bindingValidationStatus === 'INVALID'" type="error" :bordered="false" class="binding-alert">
            当前绑定失效：{{ bindingValidationMessage || "请重新选择 TDuck 表单后保存" }}
          </n-alert>
          <n-alert v-else-if="bindingValidationStatus === 'UNKNOWN'" type="warning" :bordered="false" class="binding-alert">
            暂无法校验当前 TDuck 绑定：{{ bindingValidationMessage || "请确认 TDuck 管理员账号和服务状态" }}
          </n-alert>

          <div v-if="currentBoundFormKey" class="entry-toggle-panel">
            <div>
              <span>报名入口</span>
              <strong>{{ registrationEntryDescription }}</strong>
            </div>
            <div class="entry-toggle-action">
              <n-tag round size="small" :type="registrationEntryStatusType">{{ registrationEntryStatusLabel }}</n-tag>
              <n-switch
                :value="registrationEnabled"
                :loading="togglingRegistration"
                :disabled="!canToggleRegistration"
                @update:value="toggleRegistrationEntry"
              />
            </div>
          </div>

          <div v-if="activeForm" class="form-summary">
            <div>
              <span>表单名称</span>
              <strong>{{ activeForm.formName }}</strong>
            </div>
            <div>
              <span>formKey</span>
              <strong>{{ activeForm.formKey }}</strong>
            </div>
            <div>
              <span>发布状态</span>
              <n-tag :type="activeForm.published ? 'success' : 'warning'" size="small">
                {{ activeForm.published ? "已发布" : "未发布" }}
              </n-tag>
            </div>
            <div>
              <span>字段数量</span>
              <strong>{{ activeForm.itemCount }} 个</strong>
            </div>
            <div class="summary-url">
              <span>TDuck 原始报名地址</span>
              <strong>{{ activeForm.registrationUrl }}</strong>
            </div>
          </div>
          <n-empty v-else size="small" description="请先从 TDuck 表单列表选择一个报名表" />

        </n-form>
      </n-card>

      <n-card class="surface qr-card" :bordered="false">
        <div class="qr-frame">
          <canvas ref="qrCanvas" width="220" height="220"></canvas>
        </div>
        <div class="qr-copy">
          <strong>{{ activeForm?.formName || currentMeeting?.tduckFormName || currentMeeting?.name || "未选择会议" }}</strong>
          <span>{{ redirectUrl || "选择会议后生成中转地址" }}</span>
        </div>
        <n-space vertical class="qr-actions">
          <n-button block secondary :disabled="!canOpenRegistrationEntry" @click="openRegistrationEntry">打开中转页</n-button>
          <n-button block secondary :disabled="!redirectUrl" @click="copyRedirectUrl">复制中转链接</n-button>
        </n-space>
      </n-card>
    </div>

    <n-card class="surface section-card" :bordered="false">
      <template #header>
        <div class="card-title-row">
          <div>
            <h2>字段与补偿同步</h2>
            <span>检查 TDuck 字段，并在 Webhook 漏投时按数据 ID 拉取单条报名</span>
          </div>
          <n-space align="center" :size="8">
            <n-button
              size="small"
              secondary
              :aria-expanded="syncWorkbenchExpanded"
              aria-controls="sync-workbench"
              @click="syncWorkbenchExpanded = !syncWorkbenchExpanded"
            >
              <template #icon>
                <n-icon :component="syncWorkbenchExpanded ? ChevronUpOutline : ChevronDownOutline" />
              </template>
              {{ syncWorkbenchExpanded ? "收起" : "展开" }}
            </n-button>
            <n-button size="small" :loading="syncingFields" :disabled="!currentMeeting" @click="() => syncFields()">同步字段</n-button>
          </n-space>
        </div>
      </template>

      <div v-if="syncWorkbenchExpanded" id="sync-workbench" class="sync-workbench">
        <div class="field-panel">
          <div class="mini-section-head">
            <div>
              <h3>报名字段</h3>
              <span>{{ fieldMappingSummary }}</span>
            </div>
            <n-space align="center">
              <n-tag round size="small">{{ fieldMappingStats.total }} 个字段</n-tag>
              <n-button size="small" type="primary" secondary :loading="savingMappings" :disabled="!canSaveMappings" @click="saveMappings">
                保存映射
              </n-button>
            </n-space>
          </div>
          <div class="mobile-scroll-table">
            <n-data-table
              :columns="mappingColumns"
              :data="fieldMappings"
              :pagination="{ defaultPageSize: 10, showSizePicker: true, pageSizes: PAGINATION_PAGE_SIZES, prefix: paginationSummary }"
            />
          </div>
        </div>

        <div class="manual-sync-panel">
          <div class="mini-section-head">
            <div>
              <h3>补偿同步</h3>
              <span>用于处理 Webhook 漏投或失败重试</span>
            </div>
          </div>
          <n-button block secondary :loading="pullingAllSync" :disabled="!currentMeeting || pullingSync" @click="pullAllSyncSubmissions">
            一键同步全部报名
          </n-button>
          <n-input v-model:value="pullSyncDataId" placeholder="输入 TDuck 数据 ID，例如 211748" clearable />
          <n-button block type="primary" :loading="pullingSync" :disabled="!currentMeeting || pullingAllSync || !pullSyncDataId.trim()" @click="pullSyncSubmission">
            同步这条报名
          </n-button>
          <div class="hint-box">
            一键同步会拉取当前绑定表单的最新报名；也可从 TDuck 后台复制数据 ID 后单条补拉。
          </div>
        </div>
      </div>
    </n-card>

    <n-card class="surface section-card" :bordered="false">
      <template #header>
        <div class="card-title-row">
          <div>
            <h2>重复报名待处理</h2>
            <span>同一手机号出现新的 TDuck 报名时，请核对资料后决定保留当前报名或采用新提交</span>
          </div>
          <n-space align="center">
            <n-tag round size="small" :type="pendingConflictCount ? 'warning' : 'success'">
              {{ pendingConflictCount ? `${pendingConflictCount} 条待处理` : "暂无待处理" }}
            </n-tag>
            <n-button size="small" secondary :loading="loadingSubmissionConflicts" :disabled="!currentMeeting" @click="() => refreshTduckSubmissionConflicts()">
              刷新
            </n-button>
          </n-space>
        </div>
      </template>

      <n-alert v-if="pendingConflictCount" type="warning" :bordered="false" class="conflict-alert">
        采用新提交只会更新报名资料和当前 TDuck 数据 ID；审核、签到、座位、住宿和晚宴安排会保留在原参会人记录上。
      </n-alert>
      <div v-if="currentTduckSubmissionConflicts.content.length" class="mobile-scroll-table conflict-table">
        <n-data-table
          :columns="conflictColumns"
          :data="currentTduckSubmissionConflicts.content"
          :loading="loadingSubmissionConflicts"
          :pagination="false"
        />
      </div>
      <div v-if="pendingConflictCount" class="conflict-pagination">
        <n-pagination
          :page="submissionConflictPage"
          :page-size="submissionConflictPageSize"
          show-size-picker
          :page-sizes="PAGINATION_PAGE_SIZES"
          :item-count="pendingConflictCount"
          :prefix="paginationSummary"
          @update:page="handleSubmissionConflictPageChange"
          @update:page-size="handleSubmissionConflictPageSizeChange"
        />
      </div>
      <n-empty v-else size="small" description="暂无待处理的重复 TDuck 报名" />
    </n-card>

    <n-card class="surface section-card" :bordered="false">
      <template #header>
        <div class="card-title-row">
          <div>
            <h2>Webhook 同步日志</h2>
            <span>{{ lastSyncLabel }}</span>
          </div>
          <n-tag round size="small" :type="webhookStatusType">{{ webhookStatusLabel }}</n-tag>
        </div>
      </template>
      <div class="sync-log-toolbar">
        <n-select
          :value="syncLogStatusFilter"
          :options="syncLogStatusOptions"
          clearable
          size="small"
          placeholder="全部状态"
          aria-label="同步状态筛选"
          @update:value="handleSyncLogStatusChange"
        />
      </div>
      <div v-if="currentSyncLogs.length" class="mobile-scroll-table">
        <n-data-table :columns="logColumns" :data="currentSyncLogs" :loading="loadingSyncLogs" :pagination="false" />
      </div>
      <n-empty v-else-if="!loadingSyncLogs" size="small" description="暂无同步日志" />
      <div v-if="syncLogTotal" class="sync-log-pagination">
        <n-pagination
          :page="syncLogPage"
          :page-size="syncLogPageSize"
          show-size-picker
          :page-sizes="PAGINATION_PAGE_SIZES"
          :item-count="syncLogTotal"
          :prefix="paginationSummary"
          @update:page="handleSyncLogPageChange"
          @update:page-size="handleSyncLogPageSizeChange"
        />
      </div>
    </n-card>

    <n-modal
      v-model:show="syncLogDetailModalVisible"
      preset="card"
      title="同步内容"
      class="sync-log-detail-modal"
      style="width: min(760px, calc(100vw - 32px))"
    >
      <n-spin :show="loadingSyncLogDetail">
        <template v-if="selectedSyncLogDetail">
          <div class="sync-log-detail-meta">
            <div>
              <span>数据 ID</span>
              <strong>{{ selectedSyncLogDetail.dataId || "-" }}</strong>
            </div>
            <div>
              <span>同步时间</span>
              <strong>{{ formatDateTimeLabel(selectedSyncLogDetail.createdAt) }}</strong>
            </div>
          </div>
          <div v-if="syncLogDetailFields.length" class="sync-log-data-grid">
            <template v-for="field in syncLogDetailFields" :key="field.key">
              <span>{{ field.label }}</span>
              <strong>{{ syncLogDetailValue(field) }}</strong>
            </template>
          </div>
          <n-empty v-else size="small" description="该同步日志未保存可展示的报名内容" />
        </template>
        <n-empty v-else-if="!loadingSyncLogDetail" size="small" description="未找到同步日志" />
      </n-spin>
    </n-modal>

    <n-modal
      v-model:show="submissionConflictModalVisible"
      preset="card"
      title="核对重复 TDuck 报名"
      class="submission-conflict-modal"
      style="width: min(980px, calc(100vw - 32px))"
      :mask-closable="!resolvingSubmissionConflict"
    >
      <n-spin :show="loadingSubmissionConflictDetail">
        <template v-if="selectedSubmissionConflict">
          <n-alert type="warning" :bordered="false" class="conflict-modal-alert">
            两条报名使用同一手机号。请核对左右资料后再处理，避免覆盖现场业务数据。
          </n-alert>

          <div class="conflict-meta-grid">
            <div>
              <span>关联参会人 ID</span>
              <strong>{{ selectedSubmissionConflict.attendeeId || "-" }}</strong>
            </div>
            <div>
              <span>新提交时间</span>
              <strong>{{ formatDateTimeLabel(selectedSubmissionConflict.submittedAt || selectedSubmissionConflict.createdAt) }}</strong>
            </div>
          </div>

          <div class="submission-comparison">
            <section class="submission-side existing-submission">
              <div class="submission-side-heading">
                <div>
                  <h3>当前生效报名</h3>
                  <span>当前 TDuck 数据 ID：{{ selectedSubmissionConflict.currentTduckDataId || "-" }}</span>
                </div>
                <n-tag size="small" type="success">当前保留</n-tag>
              </div>
              <div class="submission-field-grid">
                <template v-for="field in submissionFormFields" :key="field.key">
                  <span>{{ field.label }}</span>
                  <strong>{{ submissionFormValue(selectedSubmissionConflict.existing, field) }}</strong>
                </template>
              </div>
            </section>

            <section class="submission-side incoming-submission">
              <div class="submission-side-heading">
                <div>
                  <h3>新的 TDuck 提交</h3>
                  <span>新 TDuck 数据 ID：{{ selectedSubmissionConflict.incomingTduckDataId || "-" }}</span>
                </div>
                <n-tag size="small" type="warning">待处理</n-tag>
              </div>
              <div class="submission-field-grid">
                <template v-for="field in submissionFormFields" :key="field.key">
                  <span>{{ field.label }}</span>
                  <strong>{{ submissionFormValue(selectedSubmissionConflict.incoming, field) }}</strong>
                </template>
              </div>
            </section>
          </div>

          <n-form-item v-if="canResolveSubmissionConflict" label="处理说明（可选）" class="conflict-note-field">
            <n-input
              v-model:value="submissionConflictNote"
              type="textarea"
              :maxlength="500"
              show-count
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="例如：已与参会人确认采用最新资料"
              :disabled="resolvingSubmissionConflict"
            />
          </n-form-item>
        </template>
        <n-empty v-else-if="!loadingSubmissionConflictDetail" size="small" description="未找到重复报名记录" />
      </n-spin>

      <template #footer>
        <n-space justify="end">
          <n-button :disabled="resolvingSubmissionConflict" @click="submissionConflictModalVisible = false">
            {{ canResolveSubmissionConflict ? "取消" : "关闭" }}
          </n-button>
          <template v-if="canResolveSubmissionConflict">
            <n-button
              :disabled="!selectedSubmissionConflict"
              :loading="resolvingSubmissionConflict"
              @click="confirmSubmissionConflictResolution('KEEP_EXISTING')"
            >
              保留现有
            </n-button>
            <n-button
              type="primary"
              :disabled="!selectedSubmissionConflict"
              :loading="resolvingSubmissionConflict"
              @click="confirmSubmissionConflictResolution('ADOPT_INCOMING')"
            >
              采用新提交
            </n-button>
          </template>
        </n-space>
      </template>
    </n-modal>

  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { extractErrorMessage, isRequestCanceled } from "@/api/http";
import { NButton, NDataTable, NIcon, NModal, NPagination, NSelect, NSwitch, useDialog, useMessage, type DropdownOption } from "naive-ui";
import { AddOutline, ChevronDownOutline, ChevronUpOutline, CreateOutline, SparklesOutline } from "@vicons/ionicons5";
import { useRouter } from "vue-router";
import PageHeader from "@/components/PageHeader.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useAuthStore } from "@/stores/auth";
import {
  useMeetingStore,
  type TduckBindingDto,
  type TduckFieldMappingDto,
  type TduckFieldSyncItemDto,
  type TduckFormOptionDto,
  type TduckSyncLogDetailDto,
  type TduckSubmissionConflictDto,
  type TduckSubmissionParticipantDto,
  type TduckSubmissionResolution
} from "@/stores/meeting";
import { attendeeFieldOptions, inferAttendeeField } from "@/utils/attendeeFields";
import { attendeeTypeLabel, formatDateTimeLabel } from "@/utils/meetingDisplay";
import { PAGINATION_PAGE_SIZES, paginationSummary } from "@/utils/pagination";
import { closeTduckLaunchTab, navigateTduckLaunchTab, openTduckLaunchTab, persistTduckBrowserSession } from "@/utils/tduckNavigation";
import type { SyncLog } from "@/utils/types";

type BindingValidationStatus = "VALID" | "INVALID" | "UNKNOWN" | "";
type CreateTduckFormAction = "blank" | "ai";
type SyncLogStatusFilter = Exclude<SyncLog["status"], "PENDING">;

const store = useMeetingStore();
const auth = useAuthStore();
const message = useMessage();
const dialog = useDialog();
const router = useRouter();
const { routeMeetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });
const qrCanvas = ref<HTMLCanvasElement>();
const currentMeeting = computed(() => store.currentMeeting);
const selectedFormKey = ref<string | null>(currentMeeting.value?.tduckFormKey || null);
const formOptions = ref<TduckFormOptionDto[]>([]);
const loadingForms = ref(false);
const savingBinding = ref(false);
const syncingFields = ref(false);
const savingMappings = ref(false);
const pullingSync = ref(false);
const pullingAllSync = ref(false);
const syncWorkbenchExpanded = ref(false);
const openingTduck = ref(false);
const openingTduckEditor = ref(false);
const togglingRegistration = ref(false);
const pullSyncDataId = ref("");
const bindingValidationStatus = ref<BindingValidationStatus>("");
const bindingValidationMessage = ref("");
const currentBinding = ref<TduckBindingDto | null>(null);
const loadingSubmissionConflicts = ref(false);
const loadingSubmissionConflictDetail = ref(false);
const resolvingSubmissionConflict = ref(false);
const loadingSyncLogs = ref(false);
const loadingSyncLogDetail = ref(false);
const syncLogDetailModalVisible = ref(false);
const selectedSyncLogDetail = ref<TduckSyncLogDetailDto | null>(null);
const syncLogPage = ref(1);
const syncLogPageSize = ref(20);
const syncLogTotal = ref(0);
const syncLogStatusFilter = ref<SyncLogStatusFilter | null>(null);
const latestSyncLog = ref<SyncLog | null>(null);
const submissionConflictModalVisible = ref(false);
const selectedSubmissionConflict = ref<TduckSubmissionConflictDto | null>(null);
const submissionConflictNote = ref("");
const submissionConflictPage = ref(1);
const submissionConflictPageSize = ref(20);
let pullAllSyncController: AbortController | null = null;
let submissionConflictLoadGeneration = 0;
let submissionConflictDetailGeneration = 0;
let syncLogLoadGeneration = 0;

const redirectUrl = computed(() => (currentMeeting.value ? `${window.location.origin}/r/${currentMeeting.value.code}` : ""));
const tduckFrontBaseUrl = computed(() => `${window.location.origin}/tduck`);

const activeForm = computed<TduckFormOptionDto | null>(() => {
  const selected = formOptions.value.find((item) => item.formKey === selectedFormKey.value);
  if (selected) return selected;
  const meeting = currentMeeting.value;
  if (!meeting?.tduckFormKey || selectedFormKey.value !== meeting.tduckFormKey) return null;
  return {
    formKey: meeting.tduckFormKey,
    formName: meeting.tduckFormName || "已绑定 TDuck 表单",
    status: bindingValidationStatus.value === "VALID" ? "2" : undefined,
    published: currentBinding.value?.tduckPublished ?? bindingValidationStatus.value !== "INVALID",
    itemCount: 0,
    registrationUrl: currentBinding.value?.registrationUrl || meeting.registrationUrl || ""
  };
});

const formSelectOptions = computed(() =>
  formOptions.value.map((item) => ({
    label: `${item.formName}（${item.formKey}）${item.published ? "" : " - 未发布"}`,
    value: item.formKey,
    disabled: !item.published
  }))
);

const currentBoundFormKey = computed(() => currentBinding.value?.formKey || currentMeeting.value?.tduckFormKey || "");
const activeTduckFormKey = computed(() => activeForm.value?.formKey || selectedFormKey.value || currentBoundFormKey.value);
const canOpenTduckEditor = computed(() => Boolean(activeTduckFormKey.value));
const tduckEditorUrl = computed(() =>
  activeTduckFormKey.value ? `${tduckFrontBaseUrl.value}/project/form/editor?key=${encodeURIComponent(activeTduckFormKey.value)}` : ""
);
const canEditBinding = computed(() => !currentBoundFormKey.value || bindingValidationStatus.value === "INVALID");
const isBindingLocked = computed(() => Boolean(currentBoundFormKey.value && !canEditBinding.value));
const canSaveBinding = computed(() => Boolean(currentMeeting.value && selectedFormKey.value && !savingBinding.value && canEditBinding.value));
const registrationEnabled = computed(() => currentBinding.value?.registrationEnabled ?? Boolean(currentBoundFormKey.value));
const canToggleRegistration = computed(() =>
  Boolean(currentMeeting.value && currentBoundFormKey.value && bindingValidationStatus.value !== "INVALID" && !togglingRegistration.value)
);
const canOpenRegistrationEntry = computed(() => Boolean(currentMeeting.value && registrationEnabled.value && bindingValidationStatus.value !== "INVALID"));
const bindingActionLabel = computed(() => {
  if (bindingValidationStatus.value === "INVALID") return "重新绑定并生成二维码";
  return isBindingLocked.value ? "已绑定" : "保存绑定并生成二维码";
});
const createTduckFormOptions: DropdownOption[] = [
  {
    key: "blank",
    label: "空白表单",
    icon: () => h(NIcon, null, { default: () => h(AddOutline) })
  },
  {
    key: "ai",
    label: "AI 智能建表",
    icon: () => h(NIcon, null, { default: () => h(SparklesOutline) })
  }
];

const fieldMappings = ref<FieldMappingRow[]>([]);

const bindingStatusLabel = computed(() => {
  if (!currentMeeting.value) return "未选择会议";
  if (bindingValidationStatus.value === "INVALID") return "绑定失效";
  if (bindingValidationStatus.value === "UNKNOWN") return currentBoundFormKey.value ? "待校验" : "未绑定";
  if (currentBoundFormKey.value && !registrationEnabled.value) return "报名关闭";
  return currentBoundFormKey.value ? "已绑定" : "未绑定";
});

const bindingStatusType = computed<"success" | "warning" | "error" | "default">(() => {
  if (bindingValidationStatus.value === "INVALID") return "error";
  if (bindingValidationStatus.value === "UNKNOWN" || !currentBoundFormKey.value) return "warning";
  if (!registrationEnabled.value) return "warning";
  return "success";
});

const registrationEntryStatusLabel = computed(() => {
  if (!currentBoundFormKey.value) return "未绑定";
  if (registrationEnabled.value && activeForm.value?.published) return "已开启";
  if (!registrationEnabled.value && !activeForm.value?.published) return "已关闭";
  return registrationEnabled.value ? "待同步开启" : "待同步关闭";
});

const registrationEntryStatusType = computed<"success" | "warning" | "error" | "default">(() => {
  if (!currentBoundFormKey.value) return "default";
  if (registrationEnabled.value && activeForm.value?.published) return "success";
  if (!registrationEnabled.value && !activeForm.value?.published) return "warning";
  return "error";
});

const registrationEntryDescription = computed(() => {
  if (!currentBoundFormKey.value) return "绑定表单后可控制报名入口";
  if (registrationEnabled.value) return "中转页和 TDuck 原始报名页同步开启";
  return "中转页和 TDuck 原始报名页同步关闭";
});

const fieldMappingStats = computed(() => {
  const validRows = fieldMappings.value.filter((item) => item.tduckFieldKey && item.tduckFieldKey !== "-");
  return {
    total: validRows.length,
    required: validRows.filter((item) => item.tduckRequired).length
  };
});

const fieldMappingSummary = computed(() => {
  if (!currentBoundFormKey.value) return "绑定表单后可同步 TDuck 字段";
  if (!fieldMappingStats.value.total) return "尚未同步字段";
  return `${fieldMappingStats.value.required} 个必填字段，${fieldMappingStats.value.total - fieldMappingStats.value.required} 个选填字段`;
});

const canSaveMappings = computed(() =>
  Boolean(currentMeeting.value && fieldMappings.value.some((item) => item.tduckFieldKey && item.tduckFieldKey !== "-" && item.attendeeField))
);

const currentSyncLogs = computed(() => store.syncLogs.filter((log) => log.meetingId === store.currentMeetingId));

const currentTduckSubmissionConflicts = computed(() => store.currentTduckSubmissionConflicts);
const pendingConflictCount = computed(() => currentTduckSubmissionConflicts.value.totalElements);
const canResolveSubmissionConflict = computed(() =>
  auth.user.roles.includes("SUPER_ADMIN") || auth.user.roles.includes("MEETING_ADMIN")
);

const webhookStatusLabel = computed(() => {
  if (currentBinding.value?.webhookStatus === "CONFIGURED") return "已配置";
  if (latestSyncLog.value?.status === "FAILED") return "有失败";
  if (latestSyncLog.value?.status === "PENDING") return "同步中";
  if (latestSyncLog.value?.status === "SUCCESS" || latestSyncLog.value?.status === "DUPLICATE_IGNORED") return "正常";
  return currentBinding.value?.webhookStatus === "NOT_CONFIGURED" ? "未配置" : "无日志";
});

const webhookStatusType = computed<"success" | "warning" | "error" | "default">(() => {
  if (latestSyncLog.value?.status === "FAILED") return "error";
  if (latestSyncLog.value?.status === "PENDING" || webhookStatusLabel.value === "未配置" || webhookStatusLabel.value === "无日志") return "warning";
  return "success";
});

const webhookStatusDescription = computed(() => {
  if (latestSyncLog.value) {
    return `${formatDateTimeLabel(latestSyncLog.value.createdAt)} · ${latestSyncLog.value.eventType || "Webhook"}`;
  }
  return "暂无同步记录";
});

const lastSyncLabel = computed(() => (latestSyncLog.value ? `最近同步：${webhookStatusDescription.value}` : "暂无 Webhook 同步记录"));

const syncLogStatusOptions = [
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILED" },
  { label: "重复忽略", value: "DUPLICATE_IGNORED" }
] satisfies Array<{ label: string; value: SyncLogStatusFilter }>;

const mappingColumns = [
  { title: "TDuck 字段", key: "tduckField" },
  { title: "字段 key", key: "tduckFieldKey" },
  { title: "TDuck 必填", key: "tduckRequired", render: (row: FieldMappingRow) => (row.tduckRequired ? "是" : "否") },
  {
    title: "映射到参会人字段",
    key: "attendeeField",
    render: (row: FieldMappingRow) =>
      h("div", { class: "mapping-select-cell" }, [
        h(NSelect, {
          value: row.attendeeField,
          options: attendeeFieldOptions,
          clearable: !row.requiredMapping,
          disabled: row.tduckFieldKey === "-",
          placeholder: "选择字段",
          "onUpdate:value": (value: string | null) => {
            row.attendeeField = value || null;
            if (!row.attendeeField) {
              row.requiredMapping = false;
            }
          }
        })
      ])
  },
  {
    title: "必需映射",
    key: "requiredMapping",
    render: (row: FieldMappingRow) =>
      h(NSwitch, {
        value: row.requiredMapping,
        disabled: row.tduckFieldKey === "-",
        "onUpdate:value": (value: boolean) => {
          row.requiredMapping = value;
          if (value && !row.attendeeField) {
            row.attendeeField = inferAttendeeField(row.tduckFieldKey, row.tduckField);
            if (!row.attendeeField) {
              row.requiredMapping = false;
              message.warning("请先选择要映射的参会人字段");
            }
          }
        }
      })
  }
];

const logColumns = [
  { title: "时间", key: "createdAt", render: (row: SyncLog) => formatDateTimeLabel(row.createdAt) },
  { title: "formKey", key: "formKey" },
  { title: "事件", key: "eventType" },
  {
    title: "数据 ID",
    key: "dataId",
    render: (row: SyncLog) => row.dataId
      ? h(NButton, { text: true, type: "primary", size: "small", onClick: () => openSyncLogDetail(row) }, { default: () => row.dataId })
      : "-"
  },
  { title: "状态", key: "status", render: (row: SyncLog) => h(StatusTag, { value: row.status }) },
  { title: "同步说明", key: "errorMessage" }
];

const conflictColumns = [
  { title: "新提交时间", key: "submittedAt", minWidth: 156, render: (row: TduckSubmissionConflictDto) => formatDateTimeLabel(row.submittedAt || row.createdAt) },
  {
    title: "当前生效报名",
    key: "existing",
    minWidth: 180,
    render: (row: TduckSubmissionConflictDto) => h("div", { class: "conflict-table-person" }, [
      h("strong", row.existing.name || "-"),
      h("span", row.existing.phone || "-")
    ])
  },
  { title: "当前数据 ID", key: "currentTduckDataId", minWidth: 142, render: (row: TduckSubmissionConflictDto) => row.currentTduckDataId || "-" },
  {
    title: "新的 TDuck 提交",
    key: "incoming",
    minWidth: 180,
    render: (row: TduckSubmissionConflictDto) => h("div", { class: "conflict-table-person" }, [
      h("strong", row.incoming.name || "-"),
      h("span", row.incoming.phone || "-")
    ])
  },
  { title: "新数据 ID", key: "incomingTduckDataId", minWidth: 132, render: (row: TduckSubmissionConflictDto) => row.incomingTduckDataId || "-" },
  { title: "状态", key: "status", width: 92, render: (row: TduckSubmissionConflictDto) => h(StatusTag, { value: row.status }) },
  {
    title: "操作",
    key: "actions",
    width: 108,
    render: (row: TduckSubmissionConflictDto) => h(
      NButton,
      { size: "small", type: "primary", secondary: true, onClick: () => openSubmissionConflict(row) },
      { default: () => canResolveSubmissionConflict.value ? "核对处理" : "查看资料" }
    )
  }
];

interface SubmissionFormField {
  key: string;
  label: string;
  attendeeField?: string | null;
}

const submissionFormFields = computed<SubmissionFormField[]>(() => {
  const fields: SubmissionFormField[] = [];
  const seen = new Set<string>();
  for (const mapping of fieldMappings.value) {
    const key = mapping.tduckFieldKey?.trim();
    if (!key || key === "-" || seen.has(key)) continue;
    seen.add(key);
    fields.push({ key, label: mapping.tduckField?.trim() || key, attendeeField: mapping.attendeeField });
  }
  if (fields.length) return fields;

  // 表单服务临时不可用时，仍用报名快照保留所有原始字段，避免核对页退化为固定字段集。
  const conflict = selectedSubmissionConflict.value;
  for (const participant of [conflict?.existing, conflict?.incoming]) {
    for (const key of Object.keys(participant?.formValues || {})) {
      const baseKey = key.endsWith("label") ? key.slice(0, -"label".length) : "";
      if (baseKey && Object.prototype.hasOwnProperty.call(participant?.formValues || {}, baseKey)) continue;
      if (seen.has(key)) continue;
      seen.add(key);
      fields.push({ key, label: key });
    }
  }
  return fields;
});

const syncLogDetailFields = computed<SubmissionFormField[]>(() => {
  const formValues = selectedSyncLogDetail.value?.formValues || {};
  // 同步快照会保留用户未填写字段的 null 值；这些字段仍应使用 TDuck 表单标题展示，
  // 不能因空值落入下面的原始 key 兜底逻辑而显示成 radio123 之类的内部字段名。
  const fields = submissionFormFields.value.filter((field) =>
    Object.prototype.hasOwnProperty.call(formValues, `${field.key}label`)
      || Object.prototype.hasOwnProperty.call(formValues, field.key)
  );
  const seen = new Set(fields.map((field) => field.key));
  for (const key of Object.keys(formValues)) {
    const baseKey = key.endsWith("label") ? key.slice(0, -"label".length) : "";
    if (baseKey && Object.prototype.hasOwnProperty.call(formValues, baseKey)) continue;
    if (seen.has(key)) continue;
    seen.add(key);
    fields.push({ key, label: key });
  }
  return fields;
});

watch(routeMeetingId, () => {
  pullAllSyncController?.abort();
  pullAllSyncController = null;
  pullingAllSync.value = false;
  submissionConflictModalVisible.value = false;
  selectedSubmissionConflict.value = null;
  submissionConflictNote.value = "";
  submissionConflictPage.value = 1;
  syncLogDetailModalVisible.value = false;
  selectedSyncLogDetail.value = null;
  syncLogPage.value = 1;
  syncLogTotal.value = 0;
  syncLogStatusFilter.value = null;
  latestSyncLog.value = null;
  submissionConflictLoadGeneration += 1;
  submissionConflictDetailGeneration += 1;
  syncLogLoadGeneration += 1;
  void syncRegistrationContext();
}, { immediate: true });

onMounted(() => {
  void searchTduckForms("");
});

onBeforeUnmount(() => {
  pullAllSyncController?.abort();
});

async function saveBindingAndGenerateQr() {
  if (!store.currentMeetingId) {
    message.warning("请先选择会议");
    return;
  }
  if (!selectedFormKey.value) {
    message.warning("请先选择 TDuck 表单");
    return;
  }
  if (!canEditBinding.value) {
    selectedFormKey.value = currentBoundFormKey.value;
    message.warning("当前会议已绑定 TDuck 表单，不能选择其他表单");
    return;
  }
  await persistBinding();
}

async function persistBinding() {
  if (!selectedFormKey.value) return false;
  savingBinding.value = true;
  try {
    const binding = await store.bindTduckForm(selectedFormKey.value);
    currentBinding.value = binding || null;
    bindingValidationStatus.value = binding?.validationStatus || "VALID";
    bindingValidationMessage.value = binding?.validationMessage || "";
    selectedFormKey.value = binding?.formKey || selectedFormKey.value;
    upsertCurrentBindingOption();
    await syncFields({ silent: true });
    await drawQr();
    message.success("绑定已保存，二维码已生成");
    return true;
  } catch (error) {
    message.error(extractErrorMessage(error, "保存 TDuck 绑定失败"));
    return false;
  } finally {
    savingBinding.value = false;
  }
}

async function toggleRegistrationEntry(enabled: boolean) {
  if (!store.currentMeetingId) {
    message.warning("请先选择会议");
    return;
  }
  if (!currentBoundFormKey.value) {
    message.warning("请先绑定 TDuck 表单");
    return;
  }
  togglingRegistration.value = true;
  try {
    const binding = await store.updateTduckRegistrationEnabled(store.currentMeetingId, enabled);
    currentBinding.value = binding || null;
    bindingValidationStatus.value = binding?.validationStatus || "VALID";
    bindingValidationMessage.value = binding?.validationMessage || "";
    selectedFormKey.value = binding?.formKey || selectedFormKey.value;
    upsertCurrentBindingOption();
    await drawQr();
    message.success(enabled ? "报名入口已开启，两个报名地址已同步开放" : "报名入口已关闭，两个报名地址已同步停止");
  } catch (error) {
    message.error(extractErrorMessage(error, enabled ? "开启报名入口失败" : "关闭报名入口失败"));
  } finally {
    togglingRegistration.value = false;
  }
}

async function syncFields(options: { silent?: boolean } = {}) {
  if (!store.currentMeetingId) {
    message.warning("请先选择会议");
    return false;
  }
  syncingFields.value = true;
  try {
    const result = await store.syncTduckFields(store.currentMeetingId);
    fieldMappings.value = result.fields.map(mapSyncedField);
    if (result.mappingStatus === "VALID") {
      if (!options.silent) {
        message.success(`已从 TDuck 同步 ${result.fieldCount} 个字段，自动映射 ${result.mappedCount} 个字段`);
      }
    } else {
      message.warning(`已同步 ${result.fieldCount} 个字段，请确认姓名和手机号映射`);
    }
    return true;
  } catch (error) {
    if (options.silent) {
      await loadSavedMappings();
      message.warning(extractErrorMessage(error, "绑定已保存，但自动同步字段失败，请点击同步字段重试"));
    } else {
      message.error(extractErrorMessage(error, "同步 TDuck 字段失败"));
    }
    return false;
  } finally {
    syncingFields.value = false;
  }
}

async function saveMappings() {
  if (!store.currentMeetingId) {
    message.warning("请先选择会议");
    return;
  }
  const editableRows = fieldMappings.value
    .filter((item) => item.tduckFieldKey && item.tduckFieldKey !== "-");
  const mappedRows = editableRows.filter((item) => item.attendeeField);
  const mappings = editableRows
    .map((item) => ({
      /*
       * 未选择 attendeeField 的行也要提交给后端，表示“管理员已确认该 TDuck 字段不需要映射”。
       * 否则保存或再次同步时，前后端的智能推断会把它重新自动映射回来。
       */
      attendeeField: item.attendeeField,
      tduckFieldKey: item.tduckFieldKey,
      tduckFieldLabel: item.tduckField,
      requiredMapping: Boolean(item.attendeeField && item.requiredMapping)
    }));
  if (!mappedRows.length) {
    message.warning("请至少配置一个字段映射");
    return;
  }
  const duplicateFields = findDuplicateAttendeeFields(mappedRows.map((item) => item.attendeeField as string));
  if (duplicateFields.length) {
    message.warning(`字段映射重复：${duplicateFields.join("、")}，每个 MeetingOS 字段只能绑定一个 TDuck 字段`);
    return;
  }
  savingMappings.value = true;
  try {
    await store.saveTduckMappings(store.currentMeetingId, mappings);
    /*
     * 保存接口只返回写入 tduck_field_mappings 的“已映射字段”，而页面需要展示 TDuck 表单完整字段清单。
     * 保存成功后必须重新读取 GET /mappings，它会把 TDuck 全量字段和已保存映射合并；否则未映射字段会在 UI
     * 上被误删，看起来像保存后字段数量从 12 个变成 5 个。
     */
    await loadSavedMappings();
    message.success("字段映射已保存");
  } catch (error) {
    message.error(extractErrorMessage(error, "保存字段映射失败"));
  } finally {
    savingMappings.value = false;
  }
}

async function refreshSyncLogs(options: { page?: number; pageSize?: number } = {}) {
  if (!store.currentMeetingId) return;
  const targetMeetingId = store.currentMeetingId;
  const generation = ++syncLogLoadGeneration;
  const page = Math.max(0, options.page ?? syncLogPage.value - 1);
  const pageSize = Math.max(1, options.pageSize ?? syncLogPageSize.value);
  const status = syncLogStatusFilter.value;
  loadingSyncLogs.value = true;
  try {
    const request = {
      page,
      size: pageSize,
      ...(status ? { status } : {})
    };
    const result = await store.loadSyncLogs(targetMeetingId, request);
    if (!result || store.currentMeetingId !== targetMeetingId || generation !== syncLogLoadGeneration) return;
    syncLogPage.value = result.page + 1;
    syncLogPageSize.value = result.size;
    syncLogTotal.value = result.totalElements;
    if (!status) latestSyncLog.value = result.content[0] || null;
  } catch (error) {
    if (generation === syncLogLoadGeneration) message.error(extractErrorMessage(error, "加载同步日志失败"));
  } finally {
    if (store.currentMeetingId === targetMeetingId && generation === syncLogLoadGeneration) loadingSyncLogs.value = false;
  }
}

function handleSyncLogPageChange(page: number) {
  syncLogPage.value = page;
  void refreshSyncLogs({ page: page - 1 });
}

function handleSyncLogPageSizeChange(pageSize: number) {
  syncLogPage.value = 1;
  syncLogPageSize.value = pageSize;
  void refreshSyncLogs({ page: 0, pageSize });
}

function handleSyncLogStatusChange(value: string | null) {
  const status = syncLogStatusOptions.some((item) => item.value === value) ? value as SyncLogStatusFilter : null;
  if (status === syncLogStatusFilter.value) return;
  syncLogStatusFilter.value = status;
  syncLogPage.value = 1;
  void refreshSyncLogs({ page: 0 });
}

async function openSyncLogDetail(syncLog: SyncLog) {
  if (!store.currentMeetingId) return;
  const targetMeetingId = store.currentMeetingId;
  selectedSyncLogDetail.value = null;
  syncLogDetailModalVisible.value = true;
  loadingSyncLogDetail.value = true;
  try {
    const detail = await store.loadTduckSyncLogDetail(syncLog.id, targetMeetingId);
    if (store.currentMeetingId === targetMeetingId && syncLogDetailModalVisible.value) {
      selectedSyncLogDetail.value = detail;
    }
  } catch (error) {
    message.error(extractErrorMessage(error, "加载同步内容失败"));
  } finally {
    if (store.currentMeetingId === targetMeetingId) loadingSyncLogDetail.value = false;
  }
}

function findDuplicateAttendeeFields(fields: string[]) {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const field of fields) {
    if (seen.has(field)) {
      duplicates.add(field);
      continue;
    }
    seen.add(field);
  }
  return Array.from(duplicates);
}


async function pullSyncSubmission() {
  if (!store.currentMeetingId) {
    message.warning("请先选择会议");
    return;
  }
  const dataId = pullSyncDataId.value.trim();
  if (!dataId) {
    message.warning("请输入 TDuck 数据 ID");
    return;
  }
  pullingSync.value = true;
  try {
    const result = await store.pullTduckSync(store.currentMeetingId, dataId);
    await Promise.allSettled([
      refreshTduckSubmissionConflicts({ silent: true, page: 0 }),
      refreshSyncLogs({ page: 0 })
    ]);
    if (result.status === "SUCCESS") {
      message.success(result.message || "补偿同步成功，参会人已刷新");
      pullSyncDataId.value = "";
    } else if (result.status === "DUPLICATE_IGNORED") {
      message.info(result.message || "这条 TDuck 数据已经同步过");
    } else {
      message.error(result.message || "补偿同步失败，请查看同步日志");
    }
  } catch (error) {
    message.error(extractErrorMessage(error, "补偿同步失败"));
  } finally {
    pullingSync.value = false;
  }
}

async function pullAllSyncSubmissions() {
  if (!store.currentMeetingId) {
    message.warning("请先选择会议");
    return;
  }
  pullAllSyncController?.abort();
  const controller = new AbortController();
  pullAllSyncController = controller;
  pullingAllSync.value = true;
  try {
    const result = await store.pullAllTduckSync(store.currentMeetingId, { signal: controller.signal });
    if (controller.signal.aborted) return;
    await Promise.allSettled([
      refreshTduckSubmissionConflicts({ silent: true, page: 0 }),
      refreshSyncLogs({ page: 0 })
    ]);
    if (controller.signal.aborted) return;
    const summary = result.message || `已扫描 ${result.scanned} 条，成功 ${result.success} 条，已同步跳过 ${result.duplicateIgnored} 条，失败 ${result.failed} 条`;
    if (result.failed > 0) {
      const failurePreview = result.failures
        ?.slice(0, 3)
        .map((item) => `${item.dataId}: ${item.message}`)
        .join("；");
      message.warning(failurePreview ? `${summary}。失败样例：${failurePreview}` : `${summary}，请查看同步日志`);
      return;
    }
    message.success(summary);
  } catch (error) {
    if (isRequestCanceled(error) || controller.signal.aborted) return;
    message.error(extractErrorMessage(error, "一键同步失败"));
  } finally {
    if (pullAllSyncController === controller) {
      pullAllSyncController = null;
      pullingAllSync.value = false;
    }
  }
}

async function refreshTduckSubmissionConflicts(options: { silent?: boolean; page?: number; pageSize?: number } = {}) {
  if (!store.currentMeetingId) return;
  const targetMeetingId = store.currentMeetingId;
  const generation = ++submissionConflictLoadGeneration;
  const page = Math.max(0, options.page ?? submissionConflictPage.value - 1);
  const pageSize = Math.max(1, options.pageSize ?? submissionConflictPageSize.value);
  loadingSubmissionConflicts.value = true;
  try {
    const result = await store.loadTduckSubmissionConflicts(targetMeetingId, { page, size: pageSize });
    if (store.currentMeetingId !== targetMeetingId || generation !== submissionConflictLoadGeneration) return;
    submissionConflictPage.value = result.page + 1;
    submissionConflictPageSize.value = result.size;
  } catch (error) {
    if (generation === submissionConflictLoadGeneration && !options.silent && !isRequestCanceled(error)) {
      message.error(extractErrorMessage(error, "加载重复 TDuck 报名失败"));
    }
  } finally {
    if (generation === submissionConflictLoadGeneration) loadingSubmissionConflicts.value = false;
  }
}

function handleSubmissionConflictPageChange(page: number) {
  submissionConflictPage.value = page;
  void refreshTduckSubmissionConflicts({ page: page - 1 });
}

function handleSubmissionConflictPageSizeChange(pageSize: number) {
  submissionConflictPage.value = 1;
  submissionConflictPageSize.value = pageSize;
  void refreshTduckSubmissionConflicts({ page: 0, pageSize });
}

async function openSubmissionConflict(conflict: TduckSubmissionConflictDto) {
  if (!store.currentMeetingId) return;
  const targetMeetingId = store.currentMeetingId;
  const generation = ++submissionConflictDetailGeneration;
  selectedSubmissionConflict.value = conflict;
  submissionConflictNote.value = "";
  submissionConflictModalVisible.value = true;
  loadingSubmissionConflictDetail.value = true;
  try {
    const detail = await store.loadTduckSubmissionConflict(conflict.id, targetMeetingId);
    if (store.currentMeetingId === targetMeetingId && submissionConflictModalVisible.value && generation === submissionConflictDetailGeneration) {
      selectedSubmissionConflict.value = detail;
    }
  } catch (error) {
    if (generation === submissionConflictDetailGeneration) {
      message.error(extractErrorMessage(error, "加载重复报名详情失败"));
    }
  } finally {
    if (generation === submissionConflictDetailGeneration) loadingSubmissionConflictDetail.value = false;
  }
}

function confirmSubmissionConflictResolution(resolution: TduckSubmissionResolution) {
  if (!canResolveSubmissionConflict.value) {
    message.warning("当前账号仅可查看重复报名资料");
    return;
  }
  const conflict = selectedSubmissionConflict.value;
  if (!conflict || resolvingSubmissionConflict.value) return;

  const adopting = resolution === "ADOPT_INCOMING";
  dialog.warning({
    title: adopting ? "确认采用新提交" : "确认保留现有报名",
    content: adopting
      ? "将更新参会人的报名资料和当前 TDuck 数据 ID；审核、签到、座位、住宿和晚宴安排会保留不变。"
      : "将忽略这条新的 TDuck 提交，当前参会人的资料和现场安排保持不变。",
    positiveText: adopting ? "采用新提交" : "保留现有",
    negativeText: "取消",
    onPositiveClick: () => {
      void resolveSubmissionConflict(resolution);
    }
  });
}

async function resolveSubmissionConflict(resolution: TduckSubmissionResolution) {
  const conflict = selectedSubmissionConflict.value;
  const meetingId = store.currentMeetingId;
  if (!conflict || !meetingId) return;

  resolvingSubmissionConflict.value = true;
  try {
    await store.resolveTduckSubmissionConflict(conflict.id, meetingId, resolution, submissionConflictNote.value);
    submissionConflictModalVisible.value = false;
    selectedSubmissionConflict.value = null;
    submissionConflictNote.value = "";
    submissionConflictPage.value = 1;
    message.success(resolution === "ADOPT_INCOMING" ? "已采用新的 TDuck 提交，现场业务安排已保留" : "已保留现有报名，新提交已忽略");
    await Promise.allSettled([
      refreshTduckSubmissionConflicts({ silent: true, page: 0 }),
      store.loadAttendees(meetingId),
      store.loadSyncLogs(meetingId),
      store.loadStatistics(meetingId)
    ]);
  } catch (error) {
    message.error(extractErrorMessage(error, "处理重复 TDuck 报名失败"));
  } finally {
    resolvingSubmissionConflict.value = false;
  }
}

function submissionFormValue(participant: TduckSubmissionParticipantDto, field: SubmissionFormField) {
  const snapshotValue = formSnapshotValue(participant.formValues, field.key);
  if (snapshotValue !== null) return snapshotValue;

  return mappedSubmissionValue(participant, field.attendeeField);
}

function syncLogDetailValue(field: SubmissionFormField) {
  return formSnapshotValue(selectedSyncLogDetail.value?.formValues, field.key) || "-";
}

function formSnapshotValue(formValues: Record<string, unknown> | undefined, fieldKey: string): string | null {
  // TDuck 选项组件会同时返回 value 和 valueLabel，详情优先展示填写页可见的 label。
  const displayValue = formValues?.[`${fieldKey}label`];
  if (hasTduckFormValue(displayValue)) return formatTduckFormValue(displayValue);

  const rawValue = formValues?.[fieldKey];
  if (hasTduckFormValue(rawValue)) return formatTduckFormValue(rawValue);

  return null;
}

function hasTduckFormValue(value: unknown) {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") return Boolean(value.trim());
  return !Array.isArray(value) || value.length > 0;
}

function mappedSubmissionValue(participant: TduckSubmissionParticipantDto, attendeeField?: string | null) {
  switch (attendeeField) {
    case "NAME": return participant.name?.trim() || "-";
    case "PHONE": return participant.phone?.trim() || "-";
    case "ORGANIZATION": return participant.organization?.trim() || "-";
    case "TITLE": return participant.title?.trim() || "-";
    case "IDENTITY_NO": return participant.identityNo?.trim() || "-";
    case "EMAIL": return participant.email?.trim() || "-";
    case "ATTENDEE_TYPE": return participant.attendeeType ? attendeeTypeLabel(participant.attendeeType) : "-";
    case "ACCOMMODATION_REQUIRED": return participant.accommodationRequired ? "需要" : "不需要";
    case "REMARK": return participant.remark?.trim() || "-";
    default: return "-";
  }
}

function formatTduckFormValue(value: unknown): string {
  if (value === null || value === undefined) return "-";
  if (typeof value === "string") return value.trim() || "-";
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (Array.isArray(value)) {
    const values = value.map(formatTduckFormValue).filter((item) => item !== "-");
    return values.length ? values.join("、") : "-";
  }
  try {
    const serialized = JSON.stringify(value);
    return serialized && serialized !== "{}" ? serialized : "-";
  } catch {
    return String(value);
  }
}

function openRegistrationEntry() {
  if (!currentMeeting.value) return;
  if (bindingValidationStatus.value === "INVALID") {
    message.warning("当前 TDuck 绑定已失效，请重新选择表单后再打开中转页");
    return;
  }
  void router.push(`/r/${currentMeeting.value.code}`);
}

function handleCreateTduckFormSelect(key: string | number) {
  if (!currentMeeting.value) {
    message.warning("请先选择会议");
    return;
  }
  const targetWindow = openTduckTargetWindow();
  if (targetWindow) void openTduckCreation(key === "ai" ? "ai" : "blank", targetWindow);
}

async function openTduckCreation(action: CreateTduckFormAction, targetWindow: Window) {
  if (!currentMeeting.value) {
    message.warning("请先选择会议");
    closeTduckLaunchTab(targetWindow);
    return;
  }
  const meeting = currentMeeting.value;
  openingTduck.value = true;
  try {
    await ensureTduckBrowserSession();
    if (action === "ai") {
      /*
       * TDuck 的 AI 建表能力目前挂在“我的项目”页的 ChatFormDialog 中，不是独立编辑器路由。
       * 部署侧 TDuck 前端会识别 aiCreate=1 并自动打开该抽屉；如果未来 TDuck 官方提供独立路由，
       * 这里只需要替换目标 URL，不影响 MeetingOS 绑定流程。
       */
      navigateTduckTarget(targetWindow, `${tduckFrontBaseUrl.value}/project/my?aiCreate=1`);
      return;
    }
    const form = await store.createTduckForm(meeting.id, `${meeting.name} 报名表`);
    formOptions.value = mergeFormOptions(formOptions.value, form);
    if (canEditBinding.value) {
      selectedFormKey.value = form.formKey;
      bindingValidationStatus.value = "";
      bindingValidationMessage.value = "";
    }
    /*
     * /project/form 是 TDuck 编辑器父路由，缺少 key 时会请求 formKey=undefined 并弹出
     * “must not be blank”。因此新建后必须用 TDuck 返回的 formKey 直达编辑器。
     */
    navigateTduckTarget(targetWindow, `${tduckFrontBaseUrl.value}/project/form/editor?key=${encodeURIComponent(form.formKey)}`);
  } catch (error) {
    closeTduckLaunchTab(targetWindow);
    message.error(extractErrorMessage(error, "创建 TDuck 表单失败"));
  } finally {
    openingTduck.value = false;
  }
}

async function openTduckEditor() {
  if (!tduckEditorUrl.value) {
    message.warning("请先选择或绑定 TDuck 表单");
    return;
  }
  const targetWindow = openTduckTargetWindow();
  if (targetWindow) await openTduckAfterSession(tduckEditorUrl.value, openingTduckEditor, targetWindow);
}

async function openTduckAfterSession(targetUrl: string, loading: typeof openingTduck, targetWindow: Window) {
  loading.value = true;
  try {
    await ensureTduckBrowserSession();
    navigateTduckTarget(targetWindow, targetUrl);
  } catch {
    closeTduckLaunchTab(targetWindow);
  } finally {
    loading.value = false;
  }
}

function openTduckTargetWindow() {
  const targetWindow = openTduckLaunchTab();
  if (!targetWindow) message.warning("浏览器阻止了新标签页，请允许本站打开新页面后重试");
  return targetWindow;
}

function navigateTduckTarget(targetWindow: Window, targetUrl: string) {
  if (navigateTduckLaunchTab(targetWindow, targetUrl)) return;
  message.warning("新标签页已关闭，请重新操作");
}

async function ensureTduckBrowserSession() {
  if (!currentMeeting.value) {
    message.warning("请先选择会议");
    throw new Error("当前会议为空");
  }
  try {
    const session = await store.loadTduckBrowserSession(currentMeeting.value.id);
    /*
     * TDuck 前端和 MeetingOS 通过 /tduck/ 部署在同一 origin 下，因此 localStorage 共享。
     * 这里写入 TDuck 原生登录成功后的两个 key，避免用户从 MeetingOS 跳转过去时又被
     * TDuck 路由守卫拦到登录页。不要把 TDuck 管理员密码放到前端，只写服务端换取到的 token。
     */
    persistTduckBrowserSession(session);
  } catch (error) {
    message.error(extractErrorMessage(error, "同步 TDuck 登录态失败，请检查 TDuck 管理员账号配置"));
    throw error;
  }
}

async function copyRedirectUrl() {
  if (!redirectUrl.value) return;
  try {
    await navigator.clipboard.writeText(redirectUrl.value);
    message.success("中转链接已复制");
  } catch {
    message.warning("复制失败，请手动复制中转地址");
  }
}

async function syncRegistrationContext() {
  await syncMeetingFromRoute();
  if (!store.currentMeetingId) return;
  const binding = await store.loadTduckBinding(store.currentMeetingId);
  currentBinding.value = binding || null;
  selectedFormKey.value = binding?.formKey || currentMeeting.value?.tduckFormKey || null;
  bindingValidationStatus.value = binding?.validationStatus || (selectedFormKey.value ? "UNKNOWN" : "");
  bindingValidationMessage.value = binding?.validationMessage || "";
  upsertCurrentBindingOption();
  await Promise.allSettled([
    loadSavedMappings(),
    refreshSyncLogs({ page: 0 }),
    refreshTduckSubmissionConflicts({ silent: true })
  ]);
  await drawQr();
}

async function loadSavedMappings() {
  if (!store.currentMeetingId || !currentMeeting.value) {
    fieldMappings.value = [];
    return;
  }
  try {
    const mappings = await store.loadTduckMappings(store.currentMeetingId);
    fieldMappings.value = mappings.length > 0
      ? mappings.map(mapSavedMapping)
      : [emptyMappingRow("尚未同步字段，请点击同步字段")];
  } catch (error) {
    fieldMappings.value = [emptyMappingRow("报名字段暂未加载，请点击同步字段重试")];
    if (!extractErrorMessage(error, "").includes("演示数据")) {
      message.warning(extractErrorMessage(error, "加载报名字段失败"));
    }
  }
}

async function searchTduckForms(keyword: string) {
  loadingForms.value = true;
  try {
    const forms = await store.loadTduckForms(keyword);
    formOptions.value = mergeFormOptions(forms, activeForm.value);
  } catch (error) {
    message.error(extractErrorMessage(error, "加载 TDuck 表单列表失败"));
  } finally {
    loadingForms.value = false;
  }
}

function handleFormSelect(value: string | null) {
  if (!canEditBinding.value) {
    selectedFormKey.value = currentBoundFormKey.value;
    message.warning("当前会议已绑定 TDuck 表单，不能选择其他表单");
    return;
  }
  selectedFormKey.value = value;
  if (value) {
    bindingValidationStatus.value = "";
    bindingValidationMessage.value = "";
  }
}

function upsertCurrentBindingOption() {
  const meeting = currentMeeting.value;
  const binding = currentBinding.value;
  const formKey = binding?.formKey || meeting?.tduckFormKey;
  if (!formKey) return;
  const option: TduckFormOptionDto = {
    formKey,
    formName: binding?.formName || meeting?.tduckFormName || "已绑定 TDuck 表单",
    status: bindingValidationStatus.value === "VALID" ? "2" : undefined,
    published: binding?.tduckPublished ?? bindingValidationStatus.value !== "INVALID",
    itemCount: 0,
    registrationUrl: binding?.registrationUrl || meeting?.registrationUrl || ""
  };
  formOptions.value = mergeFormOptions(formOptions.value, option);
}

function mergeFormOptions(forms: TduckFormOptionDto[], extra?: TduckFormOptionDto | null) {
  const merged = new Map<string, TduckFormOptionDto>();
  for (const item of forms) {
    if (item.formKey) merged.set(item.formKey, item);
  }
  if (extra?.formKey && !merged.has(extra.formKey)) {
    merged.set(extra.formKey, extra);
  }
  return Array.from(merged.values());
}

async function drawQr() {
  if (!qrCanvas.value) return;
  const context = qrCanvas.value.getContext("2d");
  if (bindingValidationStatus.value === "INVALID" || !redirectUrl.value) {
    context?.clearRect(0, 0, qrCanvas.value.width, qrCanvas.value.height);
    return;
  }
  // qrcode 只在当前页实际需要绘制二维码时加载，避免进入后台首屏就解析二维码库。
  const { default: QRCode } = await import("qrcode");
  await QRCode.toCanvas(qrCanvas.value, redirectUrl.value, {
    width: 220,
    margin: 1,
    color: { dark: "#111827", light: "#ffffff" }
  });
}

interface FieldMappingRow {
  tduckField: string;
  tduckFieldKey: string;
  tduckRequired: boolean;
  attendeeField: string | null;
  requiredMapping: boolean;
  status?: "NOT_CONFIGURED" | "VALID" | "INVALID";
}

function mapSyncedField(field: TduckFieldSyncItemDto): FieldMappingRow {
  return {
    tduckField: field.tduckFieldLabel || field.tduckFieldKey,
    tduckFieldKey: field.tduckFieldKey,
    tduckRequired: field.tduckRequired,
    attendeeField: field.attendeeField || null,
    requiredMapping: Boolean(field.attendeeField && field.requiredMapping),
    status: field.status
  };
}

function mapSavedMapping(mapping: TduckFieldMappingDto): FieldMappingRow {
  return {
    tduckField: mapping.tduckFieldLabel || mapping.tduckFieldKey,
    tduckFieldKey: mapping.tduckFieldKey,
    tduckRequired: mapping.tduckRequired ?? mapping.requiredMapping,
    attendeeField: mapping.attendeeField || null,
    requiredMapping: Boolean(mapping.attendeeField && mapping.requiredMapping),
    status: mapping.status
  };
}

function emptyMappingRow(label: string): FieldMappingRow {
  return { tduckField: label, tduckFieldKey: "-", tduckRequired: false, attendeeField: null, requiredMapping: false };
}
</script>

<style scoped>
.card-title-row span,
.mini-section-head span {
  color: var(--text-secondary);
}

.binding-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.registration-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.binding-card :deep(.n-card__content) {
  padding-top: 4px;
}

.card-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.card-title-row h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
  line-height: 26px;
  font-weight: 750;
}

.card-title-row span {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
}

.card-title-row.compact {
  align-items: center;
}

.binding-form {
  display: grid;
  gap: 14px;
}

.binding-form-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.9fr) minmax(280px, 1.1fr);
  gap: 14px;
}

.binding-alert {
  margin: 0;
}

.entry-toggle-panel {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--surface-bg);
}

.entry-toggle-panel span {
  display: block;
  margin-bottom: 4px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.entry-toggle-panel strong {
  color: var(--text-primary);
  font-size: 14px;
  line-height: 20px;
}

.entry-toggle-action {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.form-summary {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  background: var(--surface-muted);
}

.form-summary div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.form-summary span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.form-summary strong {
  color: var(--text-primary);
  font-size: 14px;
  line-height: 20px;
  word-break: break-all;
}

.summary-url {
  grid-column: 1 / -1;
}

.qr-card :deep(.n-card__content) {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding-top: 24px;
}

.qr-frame {
  width: 236px;
  height: 236px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #ffffff;
}

.qr-copy {
  width: 100%;
  display: grid;
  justify-items: center;
  gap: 4px;
}

.qr-copy strong {
  max-width: 280px;
  color: var(--text-primary);
  text-align: center;
  overflow-wrap: anywhere;
}

.qr-card span {
  max-width: 280px;
  word-break: break-all;
  color: var(--text-secondary);
  text-align: center;
}

.qr-actions {
  width: 100%;
}

.current-meeting-box {
  min-height: 40px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 12px;
  display: grid;
  gap: 4px;
  background: var(--surface-muted);
}

.current-meeting-box strong {
  color: var(--text-primary);
}

.current-meeting-box span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.section-card {
  margin-top: 16px;
}

.sync-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.field-panel,
.manual-sync-panel {
  min-width: 0;
}

.manual-sync-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.mini-section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.mini-section-head h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 22px;
}

.mini-section-head span {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
}

.hint-box {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--surface-bg);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.conflict-alert {
  margin-bottom: 14px;
}

.conflict-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.sync-log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.sync-log-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.sync-log-toolbar :deep(.n-select) {
  width: 156px;
}

.sync-log-detail-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.sync-log-detail-meta > div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.sync-log-detail-meta span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.sync-log-detail-meta strong {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.sync-log-data-grid {
  display: grid;
  grid-template-columns: minmax(140px, 0.42fr) minmax(0, 1fr);
}

.sync-log-data-grid > span,
.sync-log-data-grid > strong {
  min-width: 0;
  padding: 9px 12px;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.sync-log-data-grid > span {
  color: var(--text-secondary);
  background: var(--surface-muted);
}

.sync-log-data-grid > strong {
  color: var(--text-primary);
  font-weight: 600;
}

.sync-log-data-grid > :nth-last-child(-n + 2) {
  border-bottom: 0;
}

.conflict-table-person {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.conflict-table-person strong {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 20px;
}

.conflict-table-person span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.conflict-modal-alert {
  margin-bottom: 14px;
}

.conflict-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.conflict-meta-grid > div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.conflict-meta-grid span,
.submission-side-heading span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.conflict-meta-grid strong {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.submission-comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.submission-side {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.existing-submission {
  border-top: 3px solid var(--success-color, #18a058);
}

.incoming-submission {
  border-top: 3px solid var(--warning-color, #f0a020);
}

.submission-side-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
  background: var(--surface-bg);
}

.submission-side-heading > div {
  min-width: 0;
}

.submission-side-heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 22px;
}

.submission-side-heading span {
  display: block;
  margin-top: 2px;
  overflow-wrap: anywhere;
}

.submission-field-grid {
  display: grid;
  grid-template-columns: minmax(78px, 0.42fr) minmax(0, 1fr);
  gap: 0;
}

.submission-field-grid > span,
.submission-field-grid > strong {
  min-width: 0;
  padding: 9px 12px;
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.submission-field-grid > span {
  color: var(--text-secondary);
  background: var(--surface-bg);
}

.submission-field-grid > strong {
  color: var(--text-primary);
  font-weight: 600;
}

.submission-field-grid > :nth-last-child(-n + 2) {
  border-bottom: 0;
}

.conflict-note-field {
  margin-top: 16px;
  margin-bottom: 0;
}

@media (max-width: 1180px) {
  .registration-grid,
  .sync-workbench,
  .binding-form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .registration-grid {
    gap: 12px;
  }

  .qr-frame {
    width: min(236px, 100%);
    height: auto;
    aspect-ratio: 1;
  }

  .qr-frame canvas {
    max-width: 100%;
    height: auto;
  }

  .sync-workbench {
    gap: 12px;
  }

  .form-summary {
    grid-template-columns: 1fr;
  }

  .conflict-meta-grid,
  .submission-comparison,
  .sync-log-detail-meta {
    grid-template-columns: 1fr;
  }

  .conflict-pagination,
  .sync-log-pagination,
  .sync-log-toolbar {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .entry-toggle-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .binding-header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .mini-section-head,
  .mini-section-head :deep(.n-space) {
    align-items: stretch;
    flex-direction: column;
  }

  .mobile-scroll-table {
    width: 100%;
    overflow-x: auto;
  }

  .mobile-scroll-table :deep(.n-data-table) {
    min-width: 680px;
  }

  .conflict-table :deep(.n-data-table) {
    min-width: 940px;
  }
}
</style>
