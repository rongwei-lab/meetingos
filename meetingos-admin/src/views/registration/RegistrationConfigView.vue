<template>
  <div>
    <page-header title="TDuck 报名配置" subtitle="绑定 TDuck 表单、同步报名字段、生成中转报名二维码，并跟踪 Webhook 同步日志">
      <n-button type="primary" :loading="savingBinding" :disabled="!canSaveBinding" @click="saveBindingAndGenerateQr">
        {{ isBindingLocked ? "已绑定" : "保存绑定并生成二维码" }}
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
            <n-tag round :type="bindingStatusType">{{ bindingStatusLabel }}</n-tag>
          </div>
        </template>

        <n-form class="binding-form" label-placement="top" :show-feedback="false">
          <div class="binding-form-grid">
            <n-form-item label="当前会议">
              <div class="current-meeting-box">
                <strong>{{ currentMeeting?.name || "未选择会议" }}</strong>
                <span v-if="currentMeeting">{{ meetingSummaryLabel(currentMeeting, { includeCode: true }) }}</span>
                <span v-else>请先在左侧选择会议</span>
              </div>
            </n-form-item>

            <n-form-item label="选择 TDuck 表单">
              <n-select
                v-model:value="selectedFormKey"
                filterable
                remote
                :clearable="!isBindingLocked"
                :disabled="isBindingLocked"
                :loading="loadingForms"
                :options="formSelectOptions"
                :placeholder="isBindingLocked ? '当前会议已绑定 TDuck 表单' : '搜索并选择已发布 TDuck 表单'"
                @search="searchTduckForms"
                @update:value="handleFormSelect"
              />
            </n-form-item>
          </div>

          <n-alert v-if="currentBoundFormKey" type="success" :bordered="false" class="binding-alert">
            已绑定 TDuck 表单。为避免误操作，当前会议不允许在此处改选其他表单。
          </n-alert>

          <n-alert v-if="bindingValidationStatus === 'INVALID'" type="error" :bordered="false" class="binding-alert">
            当前绑定失效：{{ bindingValidationMessage || "请重新选择 TDuck 表单后保存" }}
          </n-alert>
          <n-alert v-else-if="bindingValidationStatus === 'UNKNOWN'" type="warning" :bordered="false" class="binding-alert">
            暂无法校验当前 TDuck 绑定：{{ bindingValidationMessage || "请确认 TDuck 管理员账号和服务状态" }}
          </n-alert>

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
        <template #header>
          <div class="card-title-row compact">
            <div>
              <h2>报名二维码</h2>
              <span>对外分发这个固定入口</span>
            </div>
          </div>
        </template>
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
          <n-button size="small" :loading="syncingFields" :disabled="!currentMeeting" @click="syncFields">同步字段</n-button>
        </div>
      </template>

      <div class="sync-workbench">
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
            <n-data-table :columns="mappingColumns" :data="fieldMappings" :pagination="{ pageSize: 8 }" />
          </div>
        </div>

        <div class="manual-sync-panel">
          <div class="mini-section-head">
            <div>
              <h3>补偿同步</h3>
              <span>用于处理 Webhook 漏投或失败重试</span>
            </div>
          </div>
          <n-input v-model:value="pullSyncDataId" placeholder="输入 TDuck 数据 ID，例如 211748" clearable />
          <n-button block type="primary" :loading="pullingSync" :disabled="!currentMeeting || !pullSyncDataId.trim()" @click="pullSyncSubmission">
            同步这条报名
          </n-button>
          <div class="hint-box">
            从 TDuck 后台复制数据 ID 后在这里补拉，成功后会刷新参会人和同步日志。
          </div>
        </div>
      </div>
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
      <div class="mobile-scroll-table">
        <n-data-table :columns="logColumns" :data="store.syncLogs" :pagination="{ pageSize: 5 }" />
      </div>
    </n-card>

  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from "vue";
import { extractErrorMessage } from "@/api/http";
import { NDataTable, NSelect, NSwitch, useMessage } from "naive-ui";
import { useRouter } from "vue-router";
import PageHeader from "@/components/PageHeader.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useMeetingStore, type TduckBindingDto, type TduckFieldMappingDto, type TduckFieldSyncItemDto, type TduckFormOptionDto } from "@/stores/meeting";
import { formatDateTimeLabel, meetingSummaryLabel } from "@/utils/meetingDisplay";
import type { SyncLog } from "@/utils/types";

type BindingValidationStatus = "VALID" | "INVALID" | "UNKNOWN" | "";

const store = useMeetingStore();
const message = useMessage();
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
const pullSyncDataId = ref("");
const bindingValidationStatus = ref<BindingValidationStatus>("");
const bindingValidationMessage = ref("");
const currentBinding = ref<TduckBindingDto | null>(null);

const redirectUrl = computed(() => (currentMeeting.value ? `${window.location.origin}/r/${currentMeeting.value.code}` : ""));

const activeForm = computed<TduckFormOptionDto | null>(() => {
  const selected = formOptions.value.find((item) => item.formKey === selectedFormKey.value);
  if (selected) return selected;
  const meeting = currentMeeting.value;
  if (!meeting?.tduckFormKey || selectedFormKey.value !== meeting.tduckFormKey) return null;
  return {
    formKey: meeting.tduckFormKey,
    formName: meeting.tduckFormName || "已绑定 TDuck 表单",
    status: bindingValidationStatus.value === "VALID" ? "2" : undefined,
    published: bindingValidationStatus.value !== "INVALID",
    itemCount: 0,
    registrationUrl: meeting.registrationUrl || ""
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
const isBindingLocked = computed(() => Boolean(currentBoundFormKey.value));
const canSaveBinding = computed(() => Boolean(currentMeeting.value && selectedFormKey.value && !savingBinding.value && !isBindingLocked.value));
const canOpenRegistrationEntry = computed(() => Boolean(currentMeeting.value && bindingValidationStatus.value !== "INVALID"));

const fieldMappings = ref<FieldMappingRow[]>([]);
const attendeeFieldOptions = [
  { label: "姓名", value: "NAME" },
  { label: "手机号", value: "PHONE" },
  { label: "单位", value: "ORGANIZATION" },
  { label: "职务", value: "TITLE" },
  { label: "参会类型", value: "ATTENDEE_TYPE" },
  { label: "证件号", value: "IDENTITY_NO" },
  { label: "邮箱", value: "EMAIL" },
  { label: "是否住宿", value: "ACCOMMODATION_REQUIRED" },
  { label: "房间号", value: "ROOM_NO" },
  { label: "房间备注", value: "ROOM_REMARK" },
  { label: "备注", value: "REMARK" }
];

const bindingStatusLabel = computed(() => {
  if (!currentMeeting.value) return "未选择会议";
  if (bindingValidationStatus.value === "INVALID") return "绑定失效";
  if (bindingValidationStatus.value === "UNKNOWN") return currentBoundFormKey.value ? "待校验" : "未绑定";
  return currentBoundFormKey.value ? "已绑定" : "未绑定";
});

const bindingStatusType = computed<"success" | "warning" | "error" | "default">(() => {
  if (bindingValidationStatus.value === "INVALID") return "error";
  if (bindingValidationStatus.value === "UNKNOWN" || !currentBoundFormKey.value) return "warning";
  return "success";
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

const latestSyncLog = computed(() => store.syncLogs[0] || null);

const webhookStatusLabel = computed(() => {
  if (currentBinding.value?.webhookStatus === "CONFIGURED") return "已配置";
  if (latestSyncLog.value?.status === "FAILED") return "有失败";
  if (latestSyncLog.value?.status === "PENDING") return "同步中";
  if (latestSyncLog.value?.status === "SUCCESS") return "正常";
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
          }
        }
      })
  }
];

const logColumns = [
  { title: "时间", key: "createdAt", render: (row: SyncLog) => formatDateTimeLabel(row.createdAt) },
  { title: "formKey", key: "formKey" },
  { title: "事件", key: "eventType" },
  { title: "数据 ID", key: "dataId" },
  { title: "状态", key: "status", render: (row: SyncLog) => h(StatusTag, { value: row.status }) },
  { title: "错误原因", key: "errorMessage" }
];

watch(routeMeetingId, () => {
  void syncRegistrationContext();
}, { immediate: true });

onMounted(() => {
  void searchTduckForms("");
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
  if (isBindingLocked.value) {
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
  const mappings = fieldMappings.value
    .filter((item) => item.tduckFieldKey && item.tduckFieldKey !== "-" && item.attendeeField)
    .map((item) => ({
      attendeeField: item.attendeeField,
      tduckFieldKey: item.tduckFieldKey,
      tduckFieldLabel: item.tduckField,
      requiredMapping: item.requiredMapping
    }));
  if (!mappings.length) {
    message.warning("请至少配置一个字段映射");
    return;
  }
  savingMappings.value = true;
  try {
    const saved = await store.saveTduckMappings(store.currentMeetingId, mappings);
    fieldMappings.value = saved.map(mapSavedMapping);
    message.success("字段映射已保存");
  } catch (error) {
    message.error(extractErrorMessage(error, "保存字段映射失败"));
  } finally {
    savingMappings.value = false;
  }
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

function openRegistrationEntry() {
  if (!currentMeeting.value) return;
  if (bindingValidationStatus.value === "INVALID") {
    message.warning("当前 TDuck 绑定已失效，请重新选择表单后再打开中转页");
    return;
  }
  void router.push(`/r/${currentMeeting.value.code}`);
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
  await loadSavedMappings();
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
  if (isBindingLocked.value) {
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
    published: bindingValidationStatus.value !== "INVALID",
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
    attendeeField: field.attendeeField || inferAttendeeField(field.tduckFieldKey, field.tduckFieldLabel),
    requiredMapping: field.requiredMapping,
    status: field.status
  };
}

function mapSavedMapping(mapping: TduckFieldMappingDto): FieldMappingRow {
  return {
    tduckField: mapping.tduckFieldLabel || mapping.tduckFieldKey,
    tduckFieldKey: mapping.tduckFieldKey,
    tduckRequired: mapping.tduckRequired ?? mapping.requiredMapping,
    attendeeField: mapping.attendeeField || inferAttendeeField(mapping.tduckFieldKey, mapping.tduckFieldLabel || ""),
    requiredMapping: mapping.requiredMapping,
    status: mapping.status
  };
}

function emptyMappingRow(label: string): FieldMappingRow {
  return { tduckField: label, tduckFieldKey: "-", tduckRequired: false, attendeeField: null, requiredMapping: false };
}

function inferAttendeeField(key: string, label = "") {
  const text = `${key} ${label}`.toLowerCase();
  if (text.includes("phone") || text.includes("mobile") || text.includes("手机") || text.includes("电话")) return "PHONE";
  if (text.includes("name") || text.includes("姓名")) return "NAME";
  if (text.includes("organization") || text.includes("company") || text.includes("单位") || text.includes("公司")) return "ORGANIZATION";
  if (text.includes("title") || text.includes("position") || text.includes("职务") || text.includes("职位")) return "TITLE";
  if (text.includes("email") || text.includes("邮箱") || text.includes("mail")) return "EMAIL";
  if (text.includes("identity") || text.includes("idcard") || text.includes("证件") || text.includes("身份证")) return "IDENTITY_NO";
  if (text.includes("attendeetype") || text.includes("参会类型")) return "ATTENDEE_TYPE";
  if (text.includes("roomremark") || text.includes("房间备注") || text.includes("住宿备注")) return "ROOM_REMARK";
  if (text.includes("room") || text.includes("房间号") || text.includes("房号")) return "ROOM_NO";
  if (text.includes("remark") || text.includes("备注")) return "REMARK";
  return null;
}
</script>

<style scoped>
.card-title-row span,
.mini-section-head span {
  color: var(--text-secondary);
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

  .card-title-row {
    align-items: flex-start;
    flex-direction: column;
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
}
</style>
