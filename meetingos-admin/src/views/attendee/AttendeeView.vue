<template>
  <div>
    <page-header title="参会人管理">
      <n-button
        :loading="batchAuditingStatus === 'APPROVED'"
        :disabled="batchAuditingStatus !== null || !canBatchAuditTo('APPROVED')"
        @click="batchAudit('APPROVED')"
      >
        批量通过
      </n-button>
      <n-button
        :loading="batchAuditingStatus === 'REJECTED'"
        :disabled="batchAuditingStatus !== null || !canBatchAuditTo('REJECTED')"
        @click="batchAudit('REJECTED')"
      >
        批量拒绝
      </n-button>
      <n-button @click="exportAttendees">导出参会人</n-button>
      <n-upload
        :show-file-list="false"
        accept=".xlsx"
        :custom-request="importAttendees"
      >
        <n-button type="primary" :loading="importing">导入参会人</n-button>
      </n-upload>
    </page-header>

    <n-card class="surface" :bordered="false">
      <div class="filters">
        <div class="filter-main">
          <n-input v-model:value="keyword" placeholder="姓名/手机号/单位" clearable />
          <n-select v-model:value="auditStatus" :options="auditOptions" placeholder="审核状态" clearable />
          <n-select v-model:value="attendeeType" :options="typeOptions" placeholder="参会类型" clearable />
          <n-select v-model:value="hasSeat" :options="seatOptions" placeholder="是否已排座" clearable />
          <n-select
            v-model:value="selectedTags"
            :options="tagOptions"
            placeholder="标签"
            multiple
            filterable
            clearable
            :max-tag-count="1"
          />
        </div>
        <n-select
          v-model:value="visibleFieldKeys"
          class="field-visibility-select"
          :options="fieldVisibilityOptions"
          placeholder="显示字段"
          multiple
          filterable
          clearable
          :max-tag-count="2"
        />
      </div>
      <n-data-table
        v-if="!isMobile"
        v-model:checked-row-keys="checkedKeys"
        :row-key="rowKey"
        :columns="columns"
        :data="filteredAttendees"
        :pagination="{ pageSize: 10 }"
        :scroll-x="tableScrollX"
      />
      <div v-else class="mobile-card-list attendee-card-list">
        <article v-for="attendee in filteredAttendees" :key="attendee.id" class="mobile-data-card attendee-mobile-card">
          <div class="mobile-card-head">
            <button type="button" class="attendee-card-title" @click="openDetail(attendee)">
              <strong>{{ attendee.name || "-" }}</strong>
              <span>{{ attendee.phone || "-" }}</span>
            </button>
            <status-tag :value="attendee.auditStatus" />
          </div>

          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>单位</span>
              <strong>{{ attendee.company || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>类型</span>
              <strong>{{ attendeeTypeLabel(attendee.attendeeType) }}</strong>
            </div>
            <div class="mobile-field">
              <span>座位</span>
              <strong>{{ attendee.seatLabel || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>房间</span>
              <strong>{{ attendee.roomNo || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>签到</span>
              <status-tag :value="attendee.checkinStatus" />
            </div>
            <div class="mobile-field">
              <span>标签</span>
              <strong>{{ attendee.tags.length ? attendee.tags.join("、") : "-" }}</strong>
            </div>
          </div>

          <div class="mobile-card-actions">
            <n-button size="small" secondary type="primary" @click="openDetail(attendee)">详情</n-button>
            <n-button
              v-if="canAuditTo(attendee, 'APPROVED')"
              size="small"
              type="primary"
              secondary
              :loading="rowAuditLoading(attendee.id, 'APPROVED')"
              :disabled="isRowAuditing(attendee.id)"
              @click="auditOne(attendee, 'APPROVED')"
            >
              通过
            </n-button>
            <n-button
              v-if="canShowAuditTo(attendee, 'REJECTED')"
              size="small"
              type="error"
              secondary
              :loading="rowAuditLoading(attendee.id, 'REJECTED')"
              :disabled="isRowAuditing(attendee.id) || Boolean(rejectionBlockTitle(attendee))"
              :title="rejectionBlockTitle(attendee)"
              @click="auditOne(attendee, 'REJECTED')"
            >
              拒绝
            </n-button>
            <n-button size="small" secondary @click="openTagEditor(attendee)">标签</n-button>
          </div>
        </article>
        <n-empty v-if="!filteredAttendees.length" description="没有匹配的参会人" />
      </div>
    </n-card>

    <n-modal v-model:show="tagModalVisible" preset="card" title="编辑参会人标签" class="tag-modal">
      <n-form label-placement="top">
        <n-form-item :label="editingAttendee ? `${editingAttendee.name} 的标签` : '标签'">
          <n-dynamic-tags v-model:value="editingTags" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="tagModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="savingTags" @click="saveTags">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="detailVisible"
      preset="card"
      :title="detailAttendee ? `${detailAttendee.name} 的报名详情` : '报名详情'"
      class="attendee-detail-modal"
      style="width: min(960px, calc(100vw - 48px))"
      :mask-closable="!savingSeatAssignment && !savingRoomAssignment && !savingDinnerAssignment"
    >
      <div class="detail-modal-body">
        <n-divider title-placement="left">报名字段</n-divider>
        <!--
          TDuck 字段名称和值长度不可控，自定义两列表格能固定标签列并让长内容在值列内换行，
          避免弹窗中出现半屏空白、双滚动条或字段被挤压的错乱布局。
        -->
        <div class="detail-field-grid">
          <div
            v-for="field in detailFormFields"
            :key="field.key"
            class="detail-field-row"
          >
            <div class="detail-field-label">{{ field.label }}</div>
            <div class="detail-field-value">{{ field.value }}</div>
          </div>
        </div>

        <n-divider title-placement="left">系统状态</n-divider>
        <div v-if="detailAttendee" class="detail-field-grid">
          <div class="detail-field-row">
            <div class="detail-field-label">审核状态</div>
            <div class="detail-field-value">
              <status-tag :value="detailAttendee.auditStatus" />
            </div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">标签</div>
            <div class="detail-field-value">
              <n-space size="small">
                <n-tag v-for="tag in detailAttendee.tags" :key="tag" size="small">{{ tag }}</n-tag>
                <span v-if="!detailAttendee.tags.length">-</span>
              </n-space>
            </div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">座位号</div>
            <div class="detail-field-value">
              <n-select
                v-model:value="detailSeatId"
                filterable
                clearable
                placeholder="搜索/选择座位号"
                :options="detailSeatOptions"
                :loading="detailAssignmentLoading || savingSeatAssignment"
                :disabled="detailAttendee.auditStatus !== 'APPROVED'"
                @update:value="handleDetailSeatChange"
              />
            </div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">房间号</div>
            <div class="detail-field-value">
              <n-select
                v-model:value="detailRoomId"
                filterable
                clearable
                placeholder="搜索/选择房间号"
                :options="detailRoomOptions"
                :loading="detailAssignmentLoading || savingRoomAssignment"
                :disabled="detailAttendee.auditStatus !== 'APPROVED'"
                @update:value="handleDetailRoomChange"
              />
            </div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">晚宴桌号</div>
            <div class="detail-field-value">
              <n-select
                v-model:value="detailDinnerTableNo"
                filterable
                clearable
                placeholder="搜索/选择晚宴桌号"
                :options="detailDinnerTableOptions"
                :loading="detailAssignmentLoading || savingDinnerAssignment"
                :disabled="detailAttendee.auditStatus !== 'APPROVED'"
                @update:value="handleDetailDinnerChange"
              />
            </div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">签到</div>
            <div class="detail-field-value">
              <status-tag :value="detailAttendee.checkinStatus" />
            </div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">签到时间</div>
            <div class="detail-field-value">{{ formatDateTimeLabel(detailAttendee.checkinTime) }}</div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">报名时间</div>
            <div class="detail-field-value">{{ formatDateTimeLabel(detailAttendee.registeredAt) }}</div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">参会码</div>
            <div class="detail-field-value">{{ detailAttendee.attendeeCode || "-" }}</div>
          </div>
          <div class="detail-field-row">
            <div class="detail-field-label">TDuck 数据 ID</div>
            <div class="detail-field-value">{{ detailAttendee.tduckDataId || "-" }}</div>
          </div>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import type { SelectOption, UploadCustomRequestOptions } from "naive-ui";
import { NButton, NDataTable, NDynamicTags, NForm, NFormItem, NModal, NSpace, NTag, NUpload, useDialog, useMessage } from "naive-ui";
import { computed, h, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "@/components/PageHeader.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import type { ImportResultDto, TduckFieldMappingDto } from "@/stores/meeting";
import { useMeetingStore } from "@/stores/meeting";
import { attendeeFieldLabel, normalizedAttendeeFieldText } from "@/utils/attendeeFields";
import { attendeeTypeLabel, formatDateLikeValue, formatDateTimeLabel, seatTypeLabel } from "@/utils/meetingDisplay";
import type { Attendee, AuditStatus, DinnerTable } from "@/utils/types";

const store = useMeetingStore();
const route = useRoute();
const message = useMessage();
const dialog = useDialog();
const isMobile = useIsMobile();
const keyword = ref("");
const auditStatus = ref<AuditStatus | null>(null);
const attendeeType = ref<Attendee["attendeeType"] | null>(null);
const hasSeat = ref<boolean | null>(null);
const selectedTags = ref<string[]>([]);
const remoteFilteredAttendees = ref<Attendee[] | null>(null);
const checkedKeys = ref<string[]>([]);
const importing = ref(false);
const tagModalVisible = ref(false);
const editingAttendee = ref<Attendee | null>(null);
const editingTags = ref<string[]>([]);
const savingTags = ref(false);
const fieldMappings = ref<TduckFieldMappingDto[]>([]);
const visibleFieldKeys = ref<string[]>([]);
const detailVisible = ref(false);
const detailAttendeeId = ref<string | null>(null);
const detailSeatId = ref<string | null>(null);
const detailRoomId = ref<string | null>(null);
const detailDinnerTableNo = ref<string | null>(null);
const detailAssignmentLoading = ref(false);
const savingSeatAssignment = ref(false);
const savingRoomAssignment = ref(false);
const savingDinnerAssignment = ref(false);
const auditingById = ref<Record<string, AuditStatus>>({});
const batchAuditingStatus = ref<AuditStatus | null>(null);
const attendeePageLoading = ref(false);
const auditStatusValues = new Set<AuditStatus>(["PENDING", "APPROVED", "REJECTED", "CANCELLED"]);
let attendeePageLoadToken = 0;

const { routeMeetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });

const auditOptions = [
  { label: "待审核", value: "PENDING" },
  { label: "已通过", value: "APPROVED" },
  { label: "已拒绝", value: "REJECTED" }
];
const typeOptions = [
  { label: "VIP", value: "VIP" },
  { label: "嘉宾", value: "GUEST" },
  { label: "媒体", value: "MEDIA" },
  { label: "工作人员", value: "STAFF" },
  { label: "普通参会人", value: "NORMAL" }
];
const seatOptions = [
  { label: "已排座", value: true },
  { label: "未排座", value: false }
];

const tagOptions = computed(() =>
  Array.from(new Set(store.currentAttendees.flatMap((item) => item.tags)))
    .filter(Boolean)
    .map((tag) => ({ label: tag, value: tag }))
);

const detailAttendee = computed(() =>
  detailAttendeeId.value ? store.currentAttendees.find((attendee) => attendee.id === detailAttendeeId.value) || null : null
);

const localFilteredAttendees = computed(() =>
  store.currentAttendees.filter((item) => {
    const text = `${item.name}${item.phone}${item.company}${Object.values(item.formValues || {}).map(formatFieldValue).join("")}`;
    const tagMatched = !selectedTags.value.length || selectedTags.value.every((tag) => item.tags.includes(tag));
    const seatMatched = hasSeat.value == null || Boolean(item.seatLabel) === hasSeat.value;
    return (
      (!keyword.value || text.includes(keyword.value)) &&
      (!auditStatus.value || item.auditStatus === auditStatus.value) &&
      (!attendeeType.value || item.attendeeType === attendeeType.value) &&
      seatMatched &&
      tagMatched
    );
  })
);

const filteredAttendees = computed(() => remoteFilteredAttendees.value || localFilteredAttendees.value);

watch([keyword, auditStatus, attendeeType, hasSeat, selectedTags], () => {
  remoteFilteredAttendees.value = null;
}, { deep: true });

function rowKey(row: Attendee) {
  return row.id;
}

const fallbackFormMappings = computed<TduckFieldMappingDto[]>(() => [
  { attendeeField: "NAME", tduckFieldKey: "name", tduckFieldLabel: "姓名", requiredMapping: true },
  { attendeeField: "PHONE", tduckFieldKey: "phone", tduckFieldLabel: "手机号", requiredMapping: true },
  { attendeeField: "ORGANIZATION", tduckFieldKey: "organization", tduckFieldLabel: "单位", requiredMapping: false },
  { attendeeField: "TITLE", tduckFieldKey: "title", tduckFieldLabel: "职务", requiredMapping: false },
  { attendeeField: "ATTENDEE_TYPE", tduckFieldKey: "attendeeType", tduckFieldLabel: "类型", requiredMapping: false }
]);

const activeFormMappings = computed(() => (fieldMappings.value.length ? fieldMappings.value : fallbackFormMappings.value));
const visibleFormMappings = computed(() => {
  const selectedKeys = new Set(visibleFieldKeys.value);
  const selectedMappings = activeFormMappings.value.filter((mapping) => selectedKeys.has(fieldKeyOf(mapping)));
  return selectedMappings.length ? selectedMappings : defaultVisibleMappings(activeFormMappings.value);
});

const fieldVisibilityOptions = computed(() =>
  activeFormMappings.value.map((mapping) => ({
    label: fieldDisplayLabel(mapping),
    value: fieldKeyOf(mapping)
  }))
);

const formColumns = computed(() =>
  visibleFormMappings.value.map((mapping, index) => ({
    title: fieldDisplayLabel(mapping),
    key: `form-${fieldKeyOf(mapping)}`,
    minWidth: formColumnWidth(mapping),
    ellipsis: shouldEllipsisFormColumn(mapping) ? { tooltip: true } : false,
    render: (row: Attendee) => {
      const value = attendeeMappedValue(row, mapping);
      if (index === 0 || mapping.attendeeField === "NAME") {
        return h(NButton, { text: true, type: "primary", onClick: () => openDetail(row) }, { default: () => value || "-" });
      }
      return value || "-";
    }
  }))
);

const tableScrollX = computed(() => {
  const selectionWidth = 56;
  const formWidth = visibleFormMappings.value.reduce((total, mapping) => total + formColumnWidth(mapping), selectionWidth);
  const systemWidth = systemColumns.reduce((total, column) => total + column.minWidth, 0);
  return formWidth + systemWidth;
});

const systemColumns = [
  {
    title: "标签",
    key: "tags",
    minWidth: 120,
    render: (row: Attendee) =>
      row.tags.length
        ? h(NSpace, { size: 4 }, { default: () => row.tags.map((tag) => h(NTag, { size: "small" }, { default: () => tag })) })
        : "-"
  },
  { title: "审核", key: "auditStatus", minWidth: 96, render: (row: Attendee) => h(StatusTag, { value: row.auditStatus }) },
  { title: "座位", key: "seatLabel", minWidth: 160, render: (row: Attendee) => h("span", seatLabelCellProps(), row.seatLabel || "-") },
  { title: "房间", key: "roomNo", minWidth: 96, render: (row: Attendee) => row.roomNo || "-" },
  { title: "座位查询", key: "seatQueried", minWidth: 100, render: (row: Attendee) => renderQueryTag(row.seatQueried) },
  { title: "住宿查询", key: "roomQueried", minWidth: 100, render: (row: Attendee) => renderQueryTag(row.roomQueried) },
  { title: "签到", key: "checkinStatus", minWidth: 96, render: (row: Attendee) => h(StatusTag, { value: row.checkinStatus }) },
  {
    title: "操作",
    key: "actions",
    minWidth: 210,
    render: (row: Attendee) =>
      h("div", actionCellProps(), [
        ...renderAuditActions(row),
        h(NButton, { text: true, onClick: () => openTagEditor(row) }, { default: () => "标签" })
      ])
  }
];

const columns = computed(() => [
  { type: "selection" as const, width: 56 },
  ...formColumns.value,
  ...systemColumns
]);

function renderQueryTag(queried?: boolean) {
  return h(NTag, { size: "small", round: true, type: queried ? "success" : "default" }, { default: () => (queried ? "已查询" : "未查询") });
}

function seatLabelCellProps() {
  return {
    class: "seat-label-cell",
    style: {
      display: "inline-block",
      maxWidth: "100%",
      overflow: "hidden",
      textOverflow: "ellipsis",
      whiteSpace: "nowrap",
      verticalAlign: "middle"
    }
  };
}

function actionCellProps() {
  return {
    class: "action-buttons",
    style: {
      /*
       * 该列通过 render 函数生成，不会自动带上 SFC scoped style 的属性。
       * 关键布局放到 VNode 上，避免“已通过拒绝标签”在生产构建里挤在一起。
       */
      display: "inline-flex",
      alignItems: "center",
      gap: "12px",
      whiteSpace: "nowrap"
    }
  };
}

const detailFormFields = computed(() =>
  detailAttendee.value
    ? activeFormMappings.value.map((mapping) => ({
        key: `${mapping.attendeeField || "RAW"}-${mapping.tduckFieldKey}`,
        label: fieldDisplayLabel(mapping),
        value: attendeeMappedValue(detailAttendee.value as Attendee, mapping) || "-"
      }))
    : []
);

const detailSeatOptions = computed(() => {
  const currentAttendeeId = detailAttendee.value?.id;
  return store.currentSeats
    .filter((seat) => seat.status !== "DISABLED" && (!seat.locked || seat.attendeeId === currentAttendeeId) && (!seat.attendeeId || seat.attendeeId === currentAttendeeId))
    .sort((a, b) => a.areaName.localeCompare(b.areaName, "zh-Hans-CN") || a.rowNo - b.rowNo || a.seatNo - b.seatNo)
    .map((seat) => ({
      label: seatLabelText(seat),
      value: seat.id
    }));
});

const detailRoomOptions = computed(() => {
  const currentAttendeeId = detailAttendee.value?.id;
  return store.currentRooms
    .filter((room) => room.id === detailRoomId.value || roomAvailableForDetail(room, currentAttendeeId))
    .sort((a, b) => a.roomNo.localeCompare(b.roomNo, "zh-Hans-CN", { numeric: true }))
    .map((room) => {
      const occupancy = roomOccupancy(room);
      const hotel = room.hotelName ? `${room.hotelName} · ` : "";
      return {
        label: `${room.roomNo} · ${hotel}${room.roomType || "房间"} · ${occupancy.occupied}/${occupancy.capacity}人`,
        value: room.id
      };
    });
});

const detailDinnerTableOptions = computed<SelectOption[]>(() => {
  const attendee = detailAttendee.value;
  const currentTableNo = attendee ? currentDinnerTableNo(attendee) : detailDinnerTableNo.value;
  const tableByNo = new Map(store.currentDinnerTables.map((table) => [table.tableNo, table]));
  const tableNos = new Set<string>();

  /*
   * 桌号以下拉形式绑定，主要来自“晚宴管理”的桌号配置。
   * 同时把已有分配中的桌号纳入选项，兼容早期导入数据或后端自动补桌尚未刷新到前端的场景。
   */
  store.currentDinnerTables.forEach((table) => tableNos.add(table.tableNo));
  store.currentDinnerAssignments.forEach((assignment) => tableNos.add(assignment.tableNo));
  if (currentTableNo) tableNos.add(currentTableNo);

  return [...tableNos]
    .filter(Boolean)
    .filter((tableNo) => {
      const table = tableByNo.get(tableNo);
      if (tableNo === currentTableNo) return true;
      return table ? table.remainingCount > 0 : true;
    })
    .sort((a, b) => a.localeCompare(b, "zh-Hans-CN", { numeric: true }))
    .map((tableNo) => ({
      label: dinnerTableLabelText(tableNo, tableByNo.get(tableNo)),
      value: tableNo
    }));
});

onMounted(() => {
  void loadAttendeePageData();
});

watch(routeMeetingId, (meetingId, previousMeetingId) => {
  if (meetingId && meetingId !== previousMeetingId) {
    remoteFilteredAttendees.value = null;
    checkedKeys.value = [];
    void loadAttendeePageData();
  }
});

watch(activeFormMappings, (mappings) => {
  reconcileVisibleFields(mappings);
}, { immediate: true });

watch([keyword, auditStatus, attendeeType, hasSeat, selectedTags], () => {
  if (attendeePageLoading.value) return;
  void store.searchAttendees({
    keyword: keyword.value,
    auditStatus: auditStatus.value,
    attendeeType: attendeeType.value,
    hasSeat: hasSeat.value,
    tags: selectedTags.value
  }).then((items) => {
    remoteFilteredAttendees.value = items;
  });
}, { deep: true });

watch(
  () => route.query,
  (query) => {
    // 顶部全局搜索或外部链接可能带 query 进入本页，这里只同步已支持的筛选条件。
    keyword.value = normalizeQueryValue(query.keyword);
    auditStatus.value = parseAuditStatus(normalizeQueryValue(query.auditStatus));
    hasSeat.value = parseBooleanFilter(normalizeQueryValue(query.hasSeat));
  },
  { immediate: true }
);

async function loadAttendeePageData() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  const token = ++attendeePageLoadToken;
  if (!meetingId) {
    fieldMappings.value = [];
    visibleFieldKeys.value = [];
    return;
  }

  attendeePageLoading.value = true;
  try {
    /*
     * 参会人列表只需要会议详情、参会人和 TDuck 字段映射。
     * 不再通过 useRouteMeeting 默认加载完整工作区，避免进入/离开本页时顺带拉座位、住宿、签到等大列表。
     */
    const [, attendeesResult, mappingsResult] = await Promise.allSettled([
      syncMeetingFromRoute(),
      store.loadAttendees(meetingId),
      store.loadTduckMappings(meetingId)
    ]);

    if (token !== attendeePageLoadToken) return;
    if (attendeesResult.status === "rejected") {
      message.error(attendeesResult.reason instanceof Error ? attendeesResult.reason.message : "参会人数据加载失败");
    }
    fieldMappings.value = mappingsResult.status === "fulfilled" ? mappingsResult.value : [];
  } catch {
    // 没有绑定 TDuck 或接口暂不可用时，使用固定报名字段兜底，参会人列表仍保持可用。
    fieldMappings.value = [];
  } finally {
    if (token === attendeePageLoadToken) {
      attendeePageLoading.value = false;
    }
  }
}

function normalizeQueryValue(value: unknown) {
  if (Array.isArray(value)) return typeof value[0] === "string" ? value[0] : "";
  return typeof value === "string" ? value : "";
}

function parseAuditStatus(value: string): AuditStatus | null {
  return auditStatusValues.has(value as AuditStatus) ? (value as AuditStatus) : null;
}

function parseBooleanFilter(value: string): boolean | null {
  if (value === "true") return true;
  if (value === "false") return false;
  return null;
}

function auditOne(row: Attendee, status: AuditStatus) {
  if (!canAuditTo(row, status) || isRowAuditing(row.id)) return;
  if (status === "REJECTED") {
    confirmReject([row], () => submitAudit([row.id], status, "资料不完整", {
      successMessage: "已拒绝报名",
      failureMessage: "审核状态更新失败"
    }));
    return;
  }
  submitAudit([row.id], status, "", {
    successMessage: "已通过审核",
    failureMessage: "审核状态更新失败"
  });
}

function submitAudit(ids: string[], status: AuditStatus, reason: string, messages: { successMessage: string; failureMessage: string }) {
  ids.forEach((id) => setRowAuditing(id, status));
  void store.auditAttendees(ids, status, reason)
    .then(() => {
      invalidateRemoteAttendeeResults();
      message.success(messages.successMessage);
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : messages.failureMessage);
    })
    .finally(() => {
      ids.forEach(clearRowAuditing);
    });
}

function confirmReject(rows: Attendee[], onConfirm: () => void) {
  const names = rows.slice(0, 3).map((row) => row.name).join("、");
  const suffix = rows.length > 3 ? ` 等 ${rows.length} 人` : rows.length > 1 ? ` ${rows.length} 人` : "";
  dialog.warning({
    title: "确认拒绝报名",
    content: `拒绝后该参会人将不能签到、排座或查询座位/住宿。确认拒绝 ${names}${suffix}？`,
    positiveText: "确认拒绝",
    negativeText: "取消",
    onPositiveClick: onConfirm
  });
}

function auditRowsByIds(ids: string[]) {
  const selected = new Set(ids);
  return store.currentAttendees.filter((attendee) => selected.has(attendee.id));
}

function invalidateRemoteAttendeeResults() {
  /*
   * 有搜索/筛选条件时表格会优先展示 remoteFilteredAttendees。
   * 审核成功后 store.loadAttendees 会替换参会人对象，如果不清掉旧的远程结果，
   * 当前表格仍会拿着旧对象渲染，表现为后端已拒绝但页面还显示“已通过/拒绝”。
   */
  remoteFilteredAttendees.value = null;
}

function openDetail(row: Attendee) {
  detailAttendeeId.value = row.id;
  syncDetailAssignmentFields(row);
  detailVisible.value = true;
  void loadDetailAssignmentResources();
}

function syncDetailAssignmentFields(attendee: Attendee | null) {
  detailSeatId.value = attendee ? store.currentSeats.find((seat) => seat.attendeeId === attendee.id)?.id || null : null;
  detailRoomId.value = attendee?.roomId || (attendee?.roomNo ? store.currentRooms.find((room) => room.roomNo === attendee.roomNo)?.id || null : null);
  detailDinnerTableNo.value = attendee ? currentDinnerTableNo(attendee) : null;
}

async function loadDetailAssignmentResources() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;

  detailAssignmentLoading.value = true;
  try {
    /*
     * 参会人列表页本身不主动加载座位/房间/晚宴大数据。
     * 打开详情时再并发拉这些资源，既能让下拉可搜索，也避免拖慢列表首屏。
     */
    const [seatResult, roomResult, dinnerTableResult, dinnerResult] = await Promise.allSettled([
      store.loadSeats(meetingId),
      store.loadRooms(meetingId),
      store.loadDinnerTables(meetingId),
      store.loadDinnerAssignments(meetingId)
    ]);
    if (seatResult.status === "rejected") message.warning(errorMessageOf(seatResult.reason, "座位号加载失败"));
    if (roomResult.status === "rejected") message.warning(errorMessageOf(roomResult.reason, "房间号加载失败"));
    if (dinnerTableResult.status === "rejected") message.warning(errorMessageOf(dinnerTableResult.reason, "晚宴桌号加载失败"));
    if (dinnerResult.status === "rejected") message.warning(errorMessageOf(dinnerResult.reason, "晚宴分配加载失败"));
    syncDetailAssignmentFields(detailAttendee.value);
  } finally {
    detailAssignmentLoading.value = false;
  }
}

async function handleDetailSeatChange(value: string | null) {
  const attendee = detailAttendee.value;
  if (!attendee) return;
  if (value === currentSeatId(attendee)) return;

  savingSeatAssignment.value = true;
  try {
    if (value) {
      await store.assignSeat(attendee.id, value);
      message.success("座位号已更新");
    } else {
      await store.clearSeat(attendee.id);
      message.success("座位号已清空");
    }
    invalidateRemoteAttendeeResults();
    syncDetailAssignmentFields(detailAttendee.value);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "座位号更新失败");
    syncDetailAssignmentFields(detailAttendee.value);
  } finally {
    savingSeatAssignment.value = false;
  }
}

async function handleDetailRoomChange(value: string | null) {
  const attendee = detailAttendee.value;
  if (!attendee) return;
  if (value === currentRoomId(attendee)) return;

  savingRoomAssignment.value = true;
  try {
    if (value) {
      await store.assignRoom(attendee.id, value);
      message.success("房间号已更新");
    } else {
      await store.clearRoomAssignment(attendee.id);
      message.success("房间号已清空");
    }
    invalidateRemoteAttendeeResults();
    syncDetailAssignmentFields(detailAttendee.value);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "房间号更新失败");
    syncDetailAssignmentFields(detailAttendee.value);
  } finally {
    savingRoomAssignment.value = false;
  }
}

async function handleDetailDinnerChange(value: string | null) {
  const attendee = detailAttendee.value;
  if (!attendee) return;
  if (value === currentDinnerTableNo(attendee)) return;

  savingDinnerAssignment.value = true;
  try {
    if (value) {
      await store.assignDinner({ attendeeId: attendee.id, tableNo: value });
      message.success("晚宴桌号已更新");
    } else {
      await store.clearDinnerAssignment(attendee.id);
      message.success("晚宴桌号已清空");
    }
    invalidateRemoteAttendeeResults();
    syncDetailAssignmentFields(detailAttendee.value);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "晚宴桌号更新失败");
    syncDetailAssignmentFields(detailAttendee.value);
  } finally {
    savingDinnerAssignment.value = false;
  }
}

function currentSeatId(attendee: Attendee) {
  return store.currentSeats.find((seat) => seat.attendeeId === attendee.id)?.id || null;
}

function currentRoomId(attendee: Attendee) {
  return attendee.roomId || (attendee.roomNo ? store.currentRooms.find((room) => room.roomNo === attendee.roomNo)?.id || null : null);
}

function currentDinnerTableNo(attendee: Attendee) {
  return store.currentDinnerAssignments.find((assignment) => assignment.attendeeId === attendee.id)?.tableNo || null;
}

function seatLabelText(seat: { areaName: string; rowNo: number; seatNo: number; seatType?: string }) {
  return `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座${seat.seatType ? ` · ${seatTypeLabel(seat.seatType)}` : ""}`;
}

function roomOccupancy(room: { id: string; roomNo: string; capacity?: number }, currentAttendeeId?: string) {
  const occupied = store.currentAttendees.filter((attendee) => (attendee.roomId === room.id || attendee.roomNo === room.roomNo) && attendee.id !== currentAttendeeId).length;
  return {
    occupied,
    capacity: Math.max(1, room.capacity || 1)
  };
}

function dinnerTableLabelText(tableNo: string, table?: DinnerTable) {
  const occupancy = dinnerTableOccupancy(tableNo, table);
  const remark = table?.remark?.trim() ? ` · ${table.remark.trim()}` : "";
  return `${tableNo} · ${occupancy.occupied}/${occupancy.capacity}人${remark}`;
}

function dinnerTableOccupancy(tableNo: string, table?: DinnerTable) {
  const occupied = table?.occupiedCount ?? store.currentDinnerAssignments.filter((assignment) => assignment.tableNo === tableNo).length;
  return {
    occupied,
    capacity: Math.max(1, table?.capacity || occupied || 10)
  };
}

function errorMessageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function roomAvailableForDetail(room: { id: string; roomNo: string; capacity?: number; status?: string }, currentAttendeeId?: string) {
  if (room.status === "DISABLED") return false;
  const occupants = store.currentAttendees.filter((attendee) => attendee.roomId === room.id || attendee.roomNo === room.roomNo);
  if (currentAttendeeId && occupants.some((attendee) => attendee.id === currentAttendeeId)) return true;
  return occupants.length < Math.max(1, room.capacity || 1);
}

function openTagEditor(row: Attendee) {
  editingAttendee.value = row;
  editingTags.value = [...row.tags];
  tagModalVisible.value = true;
}

function saveTags() {
  if (!editingAttendee.value) return;
  savingTags.value = true;
  void store.updateAttendeeTags(editingAttendee.value.id, editingTags.value)
    .then(() => {
      tagModalVisible.value = false;
      message.success("标签已更新");
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "标签更新失败");
    })
    .finally(() => {
      savingTags.value = false;
    });
}

function batchAudit(status: AuditStatus) {
  if (!checkedKeys.value.length) {
    message.warning("请先选择参会人");
    return;
  }
  const lockedRejectCount = status === "REJECTED" ? rejectionBlockedRows(checkedKeys.value).length : 0;
  const targetIds = auditTargetIds(checkedKeys.value, status);
  if (!targetIds.length) {
    if (lockedRejectCount > 0) {
      message.warning("所选参会人已有查询或分配记录，不能拒绝");
      return;
    }
    message.info("所选参会人没有可审核项");
    return;
  }

  const submit = () => {
    /*
     * 批量操作只提交真正需要变更的行，并给这些行加本地锁。
     * 这样既避免重复点击刷出多条 toast，也避免把已是目标状态的行再次写入审核日志。
     */
    batchAuditingStatus.value = status;
    targetIds.forEach((id) => setRowAuditing(id, status));
    void store.auditAttendees(targetIds, status, status === "REJECTED" ? "批量拒绝" : "")
      .then(() => {
        invalidateRemoteAttendeeResults();
        checkedKeys.value = checkedKeys.value.filter((id) => !targetIds.includes(String(id)));
        message.success(lockedRejectCount > 0 ? `批量审核完成，已跳过 ${lockedRejectCount} 名已有查询或分配记录的参会人` : "批量审核完成");
      })
      .catch((error) => {
        message.error(error instanceof Error ? error.message : "批量审核失败");
      })
      .finally(() => {
        targetIds.forEach(clearRowAuditing);
        batchAuditingStatus.value = null;
      });
  };

  if (status === "REJECTED") {
    confirmReject(auditRowsByIds(targetIds), submit);
    return;
  }
  submit();
}

function canAuditTo(row: Attendee, status: AuditStatus) {
  /*
   * TDuck 报名默认通过，因此允许管理员把“已通过”改为“已拒绝”。
   * 已拒绝/已取消不在普通入口里恢复，避免误操作重新放行无效报名。
   */
  if (status === "REJECTED" && rejectionBlockReasons(row).length) return false;
  if (row.auditStatus === "PENDING") return status === "APPROVED" || status === "REJECTED";
  return row.auditStatus === "APPROVED" && status === "REJECTED";
}

function canShowAuditTo(row: Attendee, status: AuditStatus) {
  if (status !== "REJECTED") return canAuditTo(row, status);
  if (row.auditStatus === "PENDING") return true;
  return row.auditStatus === "APPROVED";
}

function hasPublicQueryRecord(row: Attendee) {
  return Boolean(row.seatQueried || row.roomQueried || row.dinnerQueried);
}

function hasAssignedResource(row: Attendee) {
  return Boolean(row.seatAssigned || row.seatLabel || row.roomAssigned || row.roomNo || row.dinnerAssigned);
}

function rejectionBlockReasons(row: Attendee) {
  const reasons: string[] = [];
  if (row.seatAssigned || row.seatLabel) reasons.push("已分配座位");
  if (row.roomAssigned || row.roomNo) reasons.push("已分配住宿");
  if (row.dinnerAssigned) reasons.push("已分配晚宴");
  if (hasPublicQueryRecord(row)) reasons.push("已有查询记录");
  return reasons;
}

function rejectionBlockTitle(row: Attendee) {
  const reasons = rejectionBlockReasons(row);
  return reasons.length ? `${reasons.join("、")}，不能拒绝` : undefined;
}

function renderAuditActions(row: Attendee) {
  if (row.auditStatus === "PENDING") {
    return [
      h(
        NButton,
        {
          text: true,
          type: "primary",
          loading: rowAuditLoading(row.id, "APPROVED"),
          disabled: isRowAuditing(row.id),
          onClick: () => auditOne(row, "APPROVED")
        },
        { default: () => "通过" }
      ),
      h(
        NButton,
        {
          text: true,
          type: "error",
          loading: rowAuditLoading(row.id, "REJECTED"),
          disabled: isRowAuditing(row.id) || Boolean(rejectionBlockTitle(row)),
          title: rejectionBlockTitle(row),
          onClick: () => auditOne(row, "REJECTED")
        },
        { default: () => "拒绝" }
      )
    ];
  }

  if (row.auditStatus === "APPROVED") {
    return [
      h(
        NButton,
        { text: true, disabled: true, type: "primary" },
        { default: () => "已通过" }
      ),
      h(
        NButton,
        {
          text: true,
          type: "error",
          loading: rowAuditLoading(row.id, "REJECTED"),
          disabled: isRowAuditing(row.id) || Boolean(rejectionBlockTitle(row)),
          title: rejectionBlockTitle(row),
          onClick: () => auditOne(row, "REJECTED")
        },
        { default: () => "拒绝" }
      )
    ];
  }

  {
    const labelMap: Record<AuditStatus, string> = {
      PENDING: "待审核",
      APPROVED: "已通过",
      REJECTED: "已拒绝",
      CANCELLED: "已取消"
    };
    const typeMap: Partial<Record<AuditStatus, "primary" | "error" | "default">> = {
      APPROVED: "primary",
      REJECTED: "error",
      CANCELLED: "default"
    };
    return [
      h(
        NButton,
        { text: true, disabled: true, type: typeMap[row.auditStatus] || "default" },
        { default: () => labelMap[row.auditStatus] || "已审核" }
      )
    ];
  }
}

function auditTargetIds(ids: Array<string | number>, status: AuditStatus) {
  const selected = new Set(ids.map(String));
  return store.currentAttendees
    .filter((attendee) => selected.has(attendee.id) && canAuditTo(attendee, status) && !isRowAuditing(attendee.id))
    .map((attendee) => attendee.id);
}

function rejectionBlockedRows(ids: Array<string | number>) {
  const selected = new Set(ids.map(String));
  return store.currentAttendees.filter((attendee) => selected.has(attendee.id) && (hasPublicQueryRecord(attendee) || hasAssignedResource(attendee)));
}

function canBatchAuditTo(status: AuditStatus) {
  if (status === "REJECTED" && rejectionBlockedRows(checkedKeys.value).length > 0) return true;
  return auditTargetIds(checkedKeys.value, status).length > 0;
}

function rowAuditLoading(id: string, status: AuditStatus) {
  return auditingById.value[id] === status;
}

function isRowAuditing(id: string) {
  return Boolean(auditingById.value[id]);
}

function setRowAuditing(id: string, status: AuditStatus) {
  auditingById.value = { ...auditingById.value, [id]: status };
}

function clearRowAuditing(id: string) {
  const next = { ...auditingById.value };
  delete next[id];
  auditingById.value = next;
}

function importAttendees(options: UploadCustomRequestOptions) {
  const file = options.file.file;
  if (!file) {
    options.onError();
    return;
  }
  importing.value = true;
  void store.importAttendees(file)
    .then((result) => {
      options.onFinish();
      showImportResult(result);
    })
    .catch((error) => {
      options.onError();
      message.error(error instanceof Error ? error.message : "参会人导入失败");
    })
    .finally(() => {
      importing.value = false;
    });
}

function exportAttendees() {
  void store.downloadExport("attendees", { format: "xlsx" }).then(() => {
    message.success("参会人 XLSX 已开始下载");
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "导出失败，请确认后端服务已启动");
  });
}

function showImportResult(result: ImportResultDto) {
  const failures = result.failedRows?.slice(0, 3).map((row) => `${row.rowNumber}行：${row.reason}`).join("；");
  const summary = failures ? `${result.message}；${failures}` : result.message;
  if (result.skippedRows > 0) {
    message.warning(summary, { duration: 6000 });
  } else {
    message.success(summary);
  }
}

function attendeeMappedValue(row: Attendee, mapping: TduckFieldMappingDto) {
  const labelValue = row.formValues?.[`${mapping.tduckFieldKey}label`];
  if (labelValue != null && labelValue !== "") {
    return formatFieldValue(labelValue);
  }
  const rawValue = row.formValues?.[mapping.tduckFieldKey];
  if (rawValue != null && rawValue !== "") {
    return formatFieldValue(rawValue);
  }
  // 只有能对应到 MeetingOS 固定字段时才回退；TDuck 报名字段缺失时应显示为空，
  // 避免把固定字段误填到数字组件、评分组件等动态报名字段里。
  return mapping.attendeeField ? fallbackAttendeeFieldValue(row, mapping.attendeeField) : "";
}

function fallbackAttendeeFieldValue(row: Attendee, field: string) {
  const values: Record<string, string | undefined> = {
    NAME: row.name,
    PHONE: row.phone,
    ORGANIZATION: row.company,
    TITLE: row.title,
    IDENTITY_NO: row.identityNo,
    EMAIL: row.email,
    ATTENDEE_TYPE: attendeeTypeLabel(row.attendeeType),
    ACCOMMODATION_REQUIRED: row.accommodationRequired == null ? undefined : (row.accommodationRequired ? "是" : "否"),
    ROOM_NO: row.roomNo,
    ROOM_REMARK: row.roomRemark,
    REMARK: row.remark
  };
  return values[field] || "";
}

function formatFieldValue(value: unknown): string {
  if (value == null) return "";
  if (Array.isArray(value)) return value.map(formatFieldValue).filter(Boolean).join("、");
  if (typeof value === "object") return JSON.stringify(value);
  if (typeof value === "boolean") return value ? "是" : "否";
  return formatDateLikeValue(String(value));
}

function fieldDisplayLabel(mapping: TduckFieldMappingDto) {
  return mapping.tduckFieldLabel || (mapping.attendeeField ? attendeeFieldLabel(mapping.attendeeField) : mapping.tduckFieldKey);
}

function fieldKeyOf(mapping: TduckFieldMappingDto) {
  return `${mapping.attendeeField || "RAW"}:${mapping.tduckFieldKey}`;
}

function formColumnWidth(mapping: TduckFieldMappingDto) {
  const label = fieldDisplayLabel(mapping);
  const normalized = normalizedAttendeeFieldText({ ...mapping, tduckFieldLabel: label });
  if (
    ["PHONE", "EMERGENCY_PHONE"].includes(mapping.attendeeField || "") ||
    normalized.includes("phone") ||
    normalized.includes("mobile") ||
    normalized.includes("电话") ||
    normalized.includes("手机")
  ) {
    return 160;
  }
  if (
    ["EMAIL", "INVOICE_EMAIL"].includes(mapping.attendeeField || "") ||
    normalized.includes("email") ||
    normalized.includes("mail") ||
    normalized.includes("邮箱")
  ) {
    return 220;
  }
  if (["ADDRESS", "DIETARY_REQUIREMENT", "ARRIVAL_FLIGHT_TRAIN", "DEPARTURE_FLIGHT_TRAIN", "INVOICE_TITLE"].includes(mapping.attendeeField || "")) {
    return 220;
  }
  if (["ARRIVAL_DATE", "DEPARTURE_DATE"].includes(mapping.attendeeField || "") || normalized.includes("date") || normalized.includes("time") || normalized.includes("时间") || normalized.includes("日期")) {
    return 180;
  }
  if (mapping.attendeeField === "NAME") return 120;
  if (normalized.includes("备注") || normalized.includes("需求") || normalized.includes("地址")) return 180;
  return 150;
}

function shouldEllipsisFormColumn(mapping: TduckFieldMappingDto) {
  const normalized = normalizedAttendeeFieldText({ ...mapping, tduckFieldLabel: fieldDisplayLabel(mapping) });
  return !(
    mapping.attendeeField === "PHONE" ||
    mapping.attendeeField === "EMAIL" ||
    mapping.attendeeField === "EMERGENCY_PHONE" ||
    mapping.attendeeField === "INVOICE_EMAIL" ||
    normalized.includes("phone") ||
    normalized.includes("mobile") ||
    normalized.includes("电话") ||
    normalized.includes("手机") ||
    normalized.includes("email") ||
    normalized.includes("mail") ||
    normalized.includes("邮箱")
  );
}

function defaultVisibleMappings(mappings: TduckFieldMappingDto[]) {
  const standard = mappings.filter((mapping) => Boolean(mapping.attendeeField));
  const source = standard.length ? standard : mappings;
  return source.slice(0, 5);
}

function reconcileVisibleFields(mappings: TduckFieldMappingDto[]) {
  const availableKeys = new Set(mappings.map(fieldKeyOf));
  const kept = visibleFieldKeys.value.filter((key) => availableKeys.has(key));
  if (kept.length) {
    visibleFieldKeys.value = kept;
    return;
  }
  /*
   * TDuck 表单常常有十几个字段；默认全展示会把列表撑得很宽。
   * 这里默认只展示标准映射字段（最多 5 个），管理员仍可通过“显示字段”
   * 多选把任意 TDuck 报名字段加到列表里，详情抽屉始终展示完整字段。
   */
  visibleFieldKeys.value = defaultVisibleMappings(mappings).map(fieldKeyOf);
}
</script>

<style scoped>
.filters {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-main {
  min-width: 0;
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(220px, 1.2fr) repeat(3, minmax(140px, 0.58fr)) minmax(180px, 0.8fr);
  gap: 12px;
}

.field-visibility-select {
  flex: 0 0 260px;
}

.tag-modal {
  max-width: 520px;
}

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.action-buttons :deep(.n-button) {
  flex: 0 0 auto;
}

.attendee-card-title {
  min-width: 0;
  border: 0;
  padding: 0;
  display: grid;
  gap: 2px;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.attendee-card-title strong {
  color: var(--accent-pressed);
}

.detail-modal-body {
  max-height: min(72vh, 760px);
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 8px;
}

.detail-field-grid {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-bg);
}

.detail-field-row {
  display: grid;
  grid-template-columns: minmax(180px, 32%) minmax(0, 1fr);
  min-height: 46px;
  border-bottom: 1px solid var(--border-color);
}

.detail-field-row:last-child {
  border-bottom: 0;
}

.detail-field-label,
.detail-field-value {
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 12px 16px;
  line-height: 22px;
  word-break: break-word;
}

.detail-field-label {
  color: var(--text-primary);
  background: var(--surface-muted);
  border-right: 1px solid var(--border-color);
  font-weight: 650;
}

.detail-field-value {
  color: var(--text-primary);
}

.detail-field-value :deep(.n-select) {
  width: min(420px, 100%);
}

@media (max-width: 1440px) {
  .filters {
    flex-direction: column;
  }

  .filter-main {
    width: 100%;
    grid-template-columns: minmax(220px, 1.2fr) repeat(4, minmax(140px, 0.7fr));
  }

  .field-visibility-select {
    width: min(320px, 100%);
    flex-basis: auto;
    align-self: flex-end;
  }
}

@media (max-width: 640px) {
  .filters {
    flex-direction: column;
  }

  .filter-main {
    width: 100%;
    grid-template-columns: 1fr;
  }

  .field-visibility-select {
    width: 100%;
    flex-basis: auto;
  }

  .detail-field-row {
    grid-template-columns: 1fr;
  }

  .detail-field-label {
    border-right: 0;
    border-bottom: 1px solid var(--border-color);
  }
}
</style>
