<template>
  <div>
    <page-header title="晚宴管理">
      <n-button secondary size="large" @click="downloadImportTemplate">
        <template #icon><n-icon :component="DownloadOutline" /></template>
        导入模板
      </n-button>
      <n-upload :show-file-list="false" accept=".xlsx" :disabled="importing" :custom-request="importAssignments">
        <n-button secondary size="large" :loading="importing">
          <template #icon><n-icon :component="CloudUploadOutline" /></template>
          导入分配
        </n-button>
      </n-upload>
      <n-button secondary size="large" @click="importHistoryVisible = true">
        <template #icon><n-icon :component="TimeOutline" /></template>
        导入记录
      </n-button>
      <n-button secondary size="large" :loading="exporting" @click="exportDinner">
        <template #icon><n-icon :component="DownloadOutline" /></template>
        导出晚宴安排
      </n-button>
      <n-button type="primary" size="large" @click="openAssignModal()">
        <template #icon><n-icon :component="AddOutline" /></template>
        手动安排
      </n-button>
    </page-header>

    <import-task-feedback
      v-if="importFeedbackVisible"
      v-model:show="importResultVisible"
      label="晚宴分配导入"
      :filename="importFileName"
      :task="importTask"
      :busy="importing"
      :uploading-file="uploadingImportFile"
      :progress="importProgress"
      :flow-error="importFlowError"
      :refresh-warning="importRefreshWarning"
      :downloading-failures="downloadingImportFailures"
      @open-result="openImportResult"
      @download-failures="downloadImportFailures"
    />

    <import-task-history-modal
      v-model:show="importHistoryVisible"
      :meeting-id="routeMeetingId || store.currentMeetingId || ''"
      :type-options="dinnerImportHistoryTypes"
    />

    <n-card class="surface dinner-settings-card" :bordered="false" title="晚宴信息">
      <div class="dinner-settings-form">
        <n-form-item label="晚宴地点">
          <n-input v-model:value="dinnerSettingsForm.venue" placeholder="例如 三层宴会厅" />
        </n-form-item>
        <n-form-item label="晚宴时间">
          <n-input v-model:value="dinnerSettingsForm.dinnerTime" placeholder="例如 18:30" />
        </n-form-item>
        <div class="settings-actions">
          <n-button type="primary" :loading="savingSettings" @click="saveDinnerSettings">保存</n-button>
        </div>
      </div>
    </n-card>

    <section class="dinner-stats" aria-label="晚宴统计">
      <div v-for="item in dinnerStats" :key="item.label" class="dinner-stat-card surface">
        <div class="dinner-stat-value" :class="item.tone">{{ item.value }}</div>
        <div class="dinner-stat-label">{{ item.label }}</div>
      </div>
    </section>

    <n-card class="surface section-card" :bordered="false">
      <template #header>
        <div class="card-header-row">
          <span>桌号管理</span>
          <n-button type="primary" secondary @click="openTableConfigModal">
            <template #icon><n-icon :component="GridOutline" /></template>
            桌号配置
          </n-button>
        </div>
      </template>

      <n-data-table
        v-if="!isMobile"
        :columns="tableColumns"
        :data="sortedDinnerTables"
        :pagination="{ pageSize: 8 }"
        :row-key="tableRowKey"
      >
        <template #empty>
          <n-empty description="暂无桌号，请先新增或导入晚宴分配" />
        </template>
      </n-data-table>
      <div v-else class="mobile-paged-list">
        <div class="mobile-card-list dinner-card-list">
        <article v-for="table in mobileDinnerTables" :key="table.id" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <button type="button" class="table-no-mobile-button" @click="openTableMembersModal(table)">
                <strong>{{ table.tableNo }}</strong>
              </button>
            </div>
            <n-tag round size="small" :type="table.overCapacityCount > 0 ? 'error' : table.remainingCount === 0 ? 'warning' : 'success'">
              {{ table.occupiedCount }}/{{ table.capacity }}
            </n-tag>
          </div>
          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>还缺人数</span>
              <strong>{{ table.remainingCount }}</strong>
            </div>
            <div class="mobile-field">
              <span>备注</span>
              <strong>{{ table.remark || "-" }}</strong>
            </div>
          </div>
          <div class="mobile-card-actions">
            <n-button size="small" type="primary" secondary @click="openTableModal(table)">编辑</n-button>
            <n-button size="small" type="error" secondary :disabled="table.occupiedCount > 0" :loading="deletingTableId === table.id" @click="deleteDinnerTable(table)">删除</n-button>
          </div>
        </article>
        <n-empty v-if="!sortedDinnerTables.length" description="暂无桌号，请先新增或导入晚宴分配" />
        </div>
        <n-pagination
          v-if="mobileDinnerTableItemCount > mobileDinnerTablePageSize"
          v-model:page="mobileDinnerTablePage"
          class="mobile-pagination"
          :page-count="mobileDinnerTablePageCount"
          :page-size="mobileDinnerTablePageSize"
          :item-count="mobileDinnerTableItemCount"
          :page-slot="5"
          aria-label="晚宴桌号列表分页"
        />
      </div>
    </n-card>

    <n-card class="surface section-card" :bordered="false">
      <template #header>
        <div class="card-header-row">
          <span>晚宴参会人列表</span>
          <n-button secondary :disabled="!filteredDinnerAttendeeRows.length" @click="exportDinnerAttendeeList">
            <template #icon><n-icon :component="DownloadOutline" /></template>
            导出当前列表
          </n-button>
        </div>
      </template>

      <div class="dinner-filter-bar dinner-filter-bar--unified">
        <n-input v-model:value="filters.keyword" clearable placeholder="搜索姓名 / 手机号 / 单位 / 桌号">
          <template #prefix><n-icon :component="SearchOutline" /></template>
        </n-input>
        <n-select v-model:value="filters.status" placeholder="状态" :options="statusFilterOptions" />
        <n-select v-model:value="filters.tableNo" clearable placeholder="桌号" :disabled="filters.status === 'UNASSIGNED'" :options="tableFilterOptions" />
      </div>

      <n-data-table
        v-if="!isMobile"
        :columns="dinnerAttendeeColumns"
        :data="filteredDinnerAttendeeRows"
        :pagination="{ pageSize: 10 }"
        :row-key="dinnerAttendeeRowKey"
      >
        <template #empty>
          <n-empty description="暂无符合条件的参会人" />
        </template>
      </n-data-table>
      <div v-else class="mobile-paged-list">
        <div class="mobile-card-list dinner-card-list">
        <article v-for="row in mobileDinnerAttendeeRows" :key="row.attendeeId" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <strong>{{ row.attendeeName }}</strong>
              <span>{{ row.organization || "-" }} · {{ row.attendeePhone || "-" }}</span>
            </div>
            <n-tag round size="small" :type="row.assigned ? 'success' : 'warning'">
              {{ row.assigned ? row.tableNo : "未安排" }}
            </n-tag>
          </div>
          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>职务</span>
              <strong>{{ row.title || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>备注</span>
              <strong>{{ row.remark || "-" }}</strong>
            </div>
          </div>
          <div class="mobile-card-actions">
            <template v-if="row.assigned && row.assignment">
              <n-button size="small" type="primary" secondary @click="openAssignModal(row.assignment)">编辑</n-button>
              <n-button size="small" type="error" secondary :loading="clearingId === row.attendeeId" @click="clearAssignment(row.assignment)">清空</n-button>
            </template>
            <n-button v-else size="small" type="primary" secondary @click="openAssignModal(null, row.attendeeId)">安排桌号</n-button>
          </div>
        </article>
        <n-empty v-if="!filteredDinnerAttendeeRows.length" description="暂无符合条件的参会人" />
        </div>
        <n-pagination
          v-if="mobileDinnerAttendeeItemCount > mobileDinnerAttendeePageSize"
          v-model:page="mobileDinnerAttendeePage"
          class="mobile-pagination"
          :page-count="mobileDinnerAttendeePageCount"
          :page-size="mobileDinnerAttendeePageSize"
          :item-count="mobileDinnerAttendeeItemCount"
          :page-slot="5"
          aria-label="晚宴参会人列表分页"
        />
      </div>
    </n-card>

    <n-modal
      v-model:show="assignModalVisible"
      preset="card"
      title="晚宴桌号"
      class="dinner-modal"
      style="width: min(560px, calc(100vw - 32px))"
      :mask-closable="!saving"
    >
      <n-form label-placement="top" :show-feedback="false">
        <n-form-item label="参会人">
          <n-select
            v-model:value="form.attendeeId"
            filterable
            clearable
            :disabled="Boolean(editingAssignment)"
            placeholder="搜索姓名 / 手机号 / 单位"
            :options="attendeeOptions"
          />
        </n-form-item>
        <n-form-item label="桌号">
          <n-select
            v-model:value="form.tableNo"
            filterable
            tag
            clearable
            placeholder="选择或输入桌号"
            :options="assignmentTableOptions"
          />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="可填写主桌、清真、临时调整等说明" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="assignModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveAssignment">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="tableMembersModalVisible"
      preset="card"
      :title="tableMembersTitle"
      class="dinner-members-modal"
      style="width: min(860px, calc(100vw - 32px))"
      :mask-closable="!assigningTableMember"
    >
      <div v-if="activeSelectedTable" class="table-members-panel">
        <section class="table-members-summary" aria-label="本桌统计">
          <div>
            <span>可就餐人数</span>
            <strong>{{ activeSelectedTable.capacity }}</strong>
          </div>
          <div>
            <span>已安排人数</span>
            <strong>{{ activeSelectedTable.occupiedCount }}</strong>
          </div>
          <div>
            <span>{{ activeSelectedTable.overCapacityCount > 0 ? "超员人数" : "还缺人数" }}</span>
            <strong :class="activeSelectedTable.overCapacityCount > 0 ? 'danger' : ''">
              {{ activeSelectedTable.overCapacityCount > 0 ? activeSelectedTable.overCapacityCount : activeSelectedTable.remainingCount }}
            </strong>
          </div>
        </section>

        <div class="member-add-row">
          <n-select
            v-model:value="tableMemberForm.attendeeId"
            filterable
            clearable
            :disabled="!canAddMemberToSelectedTable"
            :placeholder="tableMemberSelectPlaceholder"
            :options="tableMemberAttendeeOptions"
          />
          <n-button type="primary" :disabled="!tableMemberForm.attendeeId || !canAddMemberToSelectedTable" :loading="assigningTableMember" @click="addMemberToSelectedTable">
            加入本桌
          </n-button>
        </div>

        <n-data-table
          v-if="!isMobile"
          :columns="tableMemberColumns"
          :data="selectedTableAssignments"
          :pagination="{ pageSize: 10 }"
          :row-key="rowKey"
        >
          <template #empty>
            <n-empty description="本桌暂无就餐人员" />
          </template>
        </n-data-table>
        <div v-else class="mobile-paged-list">
          <div class="mobile-card-list dinner-card-list">
          <article v-for="assignment in mobileSelectedTableAssignments" :key="assignment.id" class="mobile-data-card">
            <div class="mobile-card-head">
              <div class="mobile-card-title">
                <strong>{{ assignment.attendeeName }}</strong>
                <span>{{ assignment.organization || "-" }} · {{ assignment.attendeePhone || "-" }}</span>
              </div>
            </div>
            <div class="mobile-card-fields">
              <div class="mobile-field">
                <span>备注</span>
                <strong>{{ assignment.remark || "-" }}</strong>
              </div>
            </div>
            <div class="mobile-card-actions">
              <n-button size="small" type="primary" secondary @click="editTableMember(assignment)">编辑</n-button>
              <n-button size="small" type="error" secondary :loading="clearingId === assignment.attendeeId" @click="clearAssignment(assignment)">移出本桌</n-button>
            </div>
          </article>
          <n-empty v-if="!selectedTableAssignments.length" description="本桌暂无就餐人员" />
          </div>
          <n-pagination
            v-if="mobileTableMemberItemCount > mobileTableMemberPageSize"
            v-model:page="mobileTableMemberPage"
            class="mobile-pagination"
            :page-count="mobileTableMemberPageCount"
            :page-size="mobileTableMemberPageSize"
            :item-count="mobileTableMemberItemCount"
            :page-slot="5"
            aria-label="本桌人员列表分页"
          />
        </div>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="tableMembersModalVisible = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="tableConfigModalVisible"
      preset="card"
      class="dinner-table-layout-modal"
      style="width: min(920px, calc(100vw - 32px))"
      :mask-closable="!savingTableConfig"
    >
      <template #header>
        <div class="table-layout-modal-title">晚宴桌号配置</div>
      </template>

      <div class="table-layout-config">
        <div class="table-layout-summary-row">
          <span>共 {{ tableDrafts.length }} 个桌号 · {{ tableDraftTotalCapacity }} 个可就餐位</span>
          <n-button class="add-table-button" round secondary type="primary" @click="addTableDraft">
            <template #icon><n-icon :component="AddOutline" /></template>
            添加桌号
          </n-button>
        </div>

        <section class="table-batch-panel">
          <div class="table-batch-grid">
            <n-form-item label="桌号前缀" :show-feedback="false">
              <n-input v-model:value="tableBatchForm.prefix" placeholder="例如 A / 贵宾" />
            </n-form-item>
            <n-form-item label="起始编号" :show-feedback="false">
              <n-input-number v-model:value="tableBatchForm.startNo" :min="1" :precision="0" class="full-width-control" />
            </n-form-item>
            <n-form-item label="桌数" :show-feedback="false">
              <n-input-number v-model:value="tableBatchForm.count" :min="1" :max="300" :precision="0" class="full-width-control" />
            </n-form-item>
            <n-form-item label="后缀" :show-feedback="false">
              <n-input v-model:value="tableBatchForm.suffix" placeholder="例如 号桌 / 桌" />
            </n-form-item>
            <n-form-item label="每桌人数" :show-feedback="false">
              <n-input-number v-model:value="tableBatchForm.capacity" :min="1" :max="200" :precision="0" class="full-width-control" />
            </n-form-item>
            <n-form-item label="备注" :show-feedback="false">
              <n-input v-model:value="tableBatchForm.remark" placeholder="可选" />
            </n-form-item>
          </div>
          <div class="table-batch-actions">
            <span class="table-batch-preview">预览：{{ tableBatchPreview }}</span>
            <n-button type="primary" secondary @click="appendBatchTableDrafts">
              <template #icon><n-icon :component="AddOutline" /></template>
              加入配置
            </n-button>
          </div>
        </section>

        <div class="table-draft-list">
          <section v-for="draft in tableDrafts" :key="draft.uid" class="table-draft-card">
            <header class="table-draft-card-header">
              <div class="table-draft-main">
                <div class="table-draft-name">
                  <span class="table-draft-dot" :style="{ background: draft.color }"></span>
                  <n-input v-model:value="draft.tableNo" class="table-draft-name-input" placeholder="桌号" />
                </div>
                <div class="table-draft-meta">
                  <span>{{ normalizedTableCapacity(draft.capacity) }} 人</span>
                  <span>{{ tableDraftOccupancySummary(draft) }}</span>
                </div>
              </div>
              <n-button
                quaternary
                circle
                size="small"
                class="table-draft-delete-button"
                :loading="deletingTableUid === draft.uid"
                title="移除桌号"
                @click="requestRemoveTableDraft(draft)"
              >
                <template #icon><n-icon :component="TrashOutline" /></template>
              </n-button>
            </header>

            <div class="table-draft-fields">
              <n-form-item label="可就餐人数" :show-feedback="false">
                <n-input-number v-model:value="draft.capacity" :min="1" :max="200" :precision="0" class="full-width-control" />
              </n-form-item>
              <n-form-item label="备注" :show-feedback="false">
                <n-input v-model:value="draft.remark" placeholder="可填写区域、餐标、特殊说明等" />
              </n-form-item>
            </div>
          </section>
        </div>

        <n-button block size="large" type="primary" class="generate-table-button" :loading="savingTableConfig" @click="saveTableConfig">
          <template #icon><n-icon :component="GridOutline" /></template>
          生成桌号
        </n-button>
      </div>
    </n-modal>

    <n-modal
      v-model:show="tableModalVisible"
      preset="card"
      title="桌号设置"
      class="dinner-modal"
      style="width: min(520px, calc(100vw - 32px))"
      :mask-closable="!savingTable"
    >
      <n-form label-placement="top" :show-feedback="false">
        <n-form-item label="桌号">
          <n-input v-model:value="tableForm.tableNo" placeholder="例如 1号桌 / 主桌 / A12" />
        </n-form-item>
        <n-form-item label="可就餐人数">
          <n-input-number v-model:value="tableForm.capacity" :min="1" :precision="0" placeholder="每桌可坐人数" class="full-width-control" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="tableForm.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="可填写区域、餐标、特殊说明等" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="savingTable" @click="tableModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="savingTable" @click="saveDinnerTable">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import type { DataTableColumns, SelectOption } from "naive-ui";
import { NButton, NDataTable, NPagination, NPopconfirm, NTag, NUpload, useDialog, useMessage } from "naive-ui";
import { AddOutline, CloudUploadOutline, DownloadOutline, GridOutline, SearchOutline, TimeOutline, TrashOutline } from "@vicons/ionicons5";
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import ImportTaskFeedback from "@/components/ImportTaskFeedback.vue";
import ImportTaskHistoryModal from "@/components/ImportTaskHistoryModal.vue";
import PageHeader from "@/components/PageHeader.vue";
import { useExportTaskDownload } from "@/composables/useExportTaskDownload";
import { useImportTaskFlow } from "@/composables/useImportTaskFlow";
import { useClientPagination } from "@/composables/useClientPagination";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import type { DinnerAssignment, DinnerTable } from "@/utils/types";
import { useMeetingStore } from "@/stores/meeting";
import { attendeeTypeLabel } from "@/utils/meetingDisplay";
import { downloadXlsxTemplate } from "@/utils/xlsxTemplate";

type DinnerStatusFilter = "ALL" | "ASSIGNED" | "UNASSIGNED";

interface DinnerAttendeeRow {
  attendeeId: string;
  attendeeCode?: string;
  attendeeName: string;
  attendeePhone?: string;
  organization?: string;
  title?: string;
  attendeeType?: string;
  tableNo?: string;
  remark?: string;
  assigned: boolean;
  assignment?: DinnerAssignment;
}

interface TableDraft {
  uid: string;
  id?: string;
  tableNo: string;
  originalTableNo?: string;
  capacity: number;
  remark: string;
  persisted: boolean;
  occupiedCount: number;
  color: string;
}

const store = useMeetingStore();
const message = useMessage();
const dialog = useDialog();
const isMobile = useIsMobile();
const { routeMeetingId } = useRouteMeeting({ loadWorkspace: false });
const {
  importing,
  uploadingFile: uploadingImportFile,
  selectedFileName: importFileName,
  task: importTask,
  flowError: importFlowError,
  refreshWarning: importRefreshWarning,
  resultVisible: importResultVisible,
  downloadingFailures: downloadingImportFailures,
  hasFeedback: importFeedbackVisible,
  progress: importProgress,
  handleUpload: importAssignments,
  downloadFailures: downloadImportFailures,
  openResult: openImportResult,
  cancelAndReset: cancelDinnerImport
} = useImportTaskFlow({
  type: "dinner",
  label: "晚宴分配导入",
  refresh: async (meetingId) => {
    await Promise.all([
      store.loadDinnerAssignments(meetingId),
      store.loadDinnerTables(meetingId),
      store.loadDinnerSettings(meetingId)
    ]);
  }
});
const {
  exporting,
  runExport: runDinnerExport,
  cancelAndReset: cancelDinnerExport
} = useExportTaskDownload();
const importHistoryVisible = ref(false);
const dinnerImportHistoryTypes = [{ label: "晚宴分配", value: "dinner" }] as const;
const saving = ref(false);
const savingTable = ref(false);
const savingSettings = ref(false);
const assigningTableMember = ref(false);
const assignModalVisible = ref(false);
const tableModalVisible = ref(false);
const tableConfigModalVisible = ref(false);
const tableMembersModalVisible = ref(false);
const savingTableConfig = ref(false);
const editingAssignment = ref<DinnerAssignment | null>(null);
const editingTable = ref<DinnerTable | null>(null);
const selectedTable = ref<DinnerTable | null>(null);
const clearingId = ref("");
const deletingTableId = ref("");
const deletingTableUid = ref<string | null>(null);
const filters = reactive({
  keyword: "",
  tableNo: null as string | null,
  status: "ALL" as DinnerStatusFilter
});
const dinnerSettingsForm = reactive({
  venue: "",
  dinnerTime: ""
});
const form = reactive({
  attendeeId: null as string | null,
  tableNo: null as string | null,
  remark: ""
});
const tableForm = reactive({
  id: null as string | null,
  tableNo: "",
  capacity: 10,
  remark: ""
});
const tableMemberForm = reactive({
  attendeeId: null as string | null
});
const tableBatchForm = reactive({
  prefix: "",
  startNo: 1,
  count: 10,
  suffix: "号桌",
  capacity: 10,
  remark: ""
});
const tableDrafts = ref<TableDraft[]>([]);
const tablePalette = ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#06b6d4"];

onMounted(loadDinnerPageData);

watch(routeMeetingId, (meetingId, previousMeetingId) => {
  if (meetingId === previousMeetingId) return;
  cancelDinnerImport();
  cancelDinnerExport();
  importHistoryVisible.value = false;
  if (meetingId) void loadDinnerPageData();
});

watch(
  () => [store.currentMeeting?.dinnerVenue, store.currentMeeting?.dinnerTime],
  syncDinnerSettingsForm,
  { immediate: true }
);

const assignmentByAttendeeId = computed(() => new Map(store.currentDinnerAssignments.map((item) => [item.attendeeId, item])));
const approvedAttendees = computed(() => store.currentAttendees.filter((attendee) => attendee.auditStatus === "APPROVED"));
const assignedCount = computed(() => store.currentDinnerAssignments.length);
const unassignedAttendees = computed(() => approvedAttendees.value.filter((attendee) => !assignmentByAttendeeId.value.has(attendee.id)));
const tableFilterOptions = computed(() => distinctOptions(store.currentDinnerAssignments.map((item) => item.tableNo).filter(Boolean)));
const statusFilterOptions = [
  { label: "全部", value: "ALL" },
  { label: "已安排", value: "ASSIGNED" },
  { label: "未安排", value: "UNASSIGNED" }
];
const sortedDinnerTables = computed(() => [...store.currentDinnerTables].sort((a, b) => a.tableNo.localeCompare(b.tableNo, "zh-Hans-CN")));
const {
  page: mobileDinnerTablePage,
  pageCount: mobileDinnerTablePageCount,
  pageSize: mobileDinnerTablePageSize,
  itemCount: mobileDinnerTableItemCount,
  visibleItems: mobileDinnerTables
} = useClientPagination(sortedDinnerTables, 12, routeMeetingId);
const totalDinnerCapacity = computed(() => store.currentDinnerTables.reduce((sum, table) => sum + table.capacity, 0));
const remainingDinnerSeats = computed(() => store.currentDinnerTables.reduce((sum, table) => sum + table.remainingCount, 0));
const tableDraftTotalCapacity = computed(() => tableDrafts.value.reduce((sum, draft) => sum + normalizedTableCapacity(draft.capacity), 0));
const tableBatchPreview = computed(() => {
  const names = buildBatchTableNos().slice(0, 3);
  if (!names.length) return "-";
  const suffix = tableBatchForm.count > names.length ? " ..." : "";
  return `${names.join("、")}${suffix}`;
});
const activeSelectedTable = computed(() => {
  if (!selectedTable.value) return null;
  /*
   * 分配/移出人员后 store 会重新加载桌号统计，不能继续依赖打开弹窗时的旧对象。
   * 这里优先按 id 找最新桌号，找不到时再按桌号兜底，保证弹窗内人数统计实时刷新。
   */
  return (
    store.currentDinnerTables.find((table) => table.id === selectedTable.value?.id) ||
    store.currentDinnerTables.find((table) => table.tableNo === selectedTable.value?.tableNo) ||
    selectedTable.value
  );
});
const tableMembersTitle = computed(() => activeSelectedTable.value ? `${activeSelectedTable.value.tableNo} 人员` : "桌号人员");
const selectedTableAssignments = computed(() => {
  const tableNo = activeSelectedTable.value?.tableNo;
  return tableNo ? store.currentDinnerAssignments.filter((assignment) => assignment.tableNo === tableNo) : [];
});
const {
  page: mobileTableMemberPage,
  pageCount: mobileTableMemberPageCount,
  pageSize: mobileTableMemberPageSize,
  itemCount: mobileTableMemberItemCount,
  visibleItems: mobileSelectedTableAssignments
} = useClientPagination(
  selectedTableAssignments,
  10,
  computed(() => activeSelectedTable.value?.id || activeSelectedTable.value?.tableNo || "")
);
const tableMemberAttendeeOptions = computed<SelectOption[]>(() =>
  unassignedAttendees.value.map((attendee) => ({
    label: [attendee.name, attendee.phone, attendee.company].filter(Boolean).join(" · "),
    value: attendee.id
  }))
);
const canAddMemberToSelectedTable = computed(() => {
  const table = activeSelectedTable.value;
  return Boolean(table && table.remainingCount > 0 && table.overCapacityCount === 0 && tableMemberAttendeeOptions.value.length > 0);
});
const tableMemberSelectPlaceholder = computed(() => {
  const table = activeSelectedTable.value;
  if (!table) return "选择未安排参会人";
  if (table.overCapacityCount > 0) return "该桌已超员，请先移出人员或调整容量";
  if (table.remainingCount <= 0) return "该桌已坐满，请先移出人员或调整容量";
  if (!tableMemberAttendeeOptions.value.length) return "暂无未安排参会人";
  return "选择未安排参会人";
});

const dinnerStats = computed(() => [
  { label: "已审核参会人", value: approvedAttendees.value.length, tone: "neutral" },
  { label: "已安排桌号", value: assignedCount.value, tone: "success" },
  { label: "未安排", value: unassignedAttendees.value.length, tone: "warning" },
  { label: "桌号数量", value: store.currentDinnerTables.length || new Set(store.currentDinnerAssignments.map((item) => item.tableNo)).size, tone: "primary" },
  { label: "可就餐人数", value: totalDinnerCapacity.value, tone: "neutral" },
  { label: "还缺人数", value: remainingDinnerSeats.value, tone: "warning" }
]);

const dinnerAttendeeRows = computed<DinnerAttendeeRow[]>(() => approvedAttendees.value.map((attendee) => {
  const assignment = assignmentByAttendeeId.value.get(attendee.id);
  if (assignment) {
    return {
      attendeeId: attendee.id,
      attendeeCode: attendee.attendeeCode,
      attendeeName: assignment.attendeeName,
      attendeePhone: assignment.attendeePhone,
      organization: assignment.organization,
      title: assignment.title,
      attendeeType: assignment.attendeeType,
      tableNo: assignment.tableNo,
      remark: assignment.remark,
      assigned: true,
      assignment
    };
  }
  return {
    attendeeId: attendee.id,
    attendeeCode: attendee.attendeeCode,
    attendeeName: attendee.name,
    attendeePhone: attendee.phone,
    organization: attendee.company,
    title: attendee.title,
    attendeeType: attendee.attendeeType,
    assigned: false
  };
}));

const filteredDinnerAttendeeRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return dinnerAttendeeRows.value.filter((row) => {
    const searchable = [
      row.attendeeName,
      row.attendeePhone,
      row.organization,
      row.title,
      row.tableNo,
      row.remark
    ].filter(Boolean).join(" ").toLowerCase();
    return (
      (!keyword || searchable.includes(keyword)) &&
      (filters.status === "ALL" || (filters.status === "ASSIGNED" ? row.assigned : !row.assigned)) &&
      (!filters.tableNo || row.tableNo === filters.tableNo)
    );
  });
});
const {
  page: mobileDinnerAttendeePage,
  pageCount: mobileDinnerAttendeePageCount,
  pageSize: mobileDinnerAttendeePageSize,
  itemCount: mobileDinnerAttendeeItemCount,
  visibleItems: mobileDinnerAttendeeRows
} = useClientPagination(
  filteredDinnerAttendeeRows,
  20,
  computed(() => [routeMeetingId.value, filters.keyword, filters.status, filters.tableNo])
);

const attendeeOptions = computed<SelectOption[]>(() => {
  const selectedId = editingAssignment.value?.attendeeId;
  return approvedAttendees.value
    .filter((attendee) => attendee.id === selectedId || !assignmentByAttendeeId.value.has(attendee.id))
    .map((attendee) => ({
      label: [attendee.name, attendee.phone, attendee.company].filter(Boolean).join(" · "),
      value: attendee.id
    }));
});

const assignmentTableOptions = computed<SelectOption[]>(() => {
  const values = new Set<string>();
  store.currentDinnerTables.forEach((table) => values.add(table.tableNo));
  store.currentDinnerAssignments.forEach((assignment) => values.add(assignment.tableNo));
  return [...values]
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b, "zh-Hans-CN"))
    .map((value) => ({ label: value, value }));
});

const tableColumns: DataTableColumns<DinnerTable> = [
  {
    title: "桌号",
    key: "tableNo",
    minWidth: 110,
    render(row) {
      return h(NButton, {
        text: true,
        type: "primary",
        class: "table-no-link",
        onClick: () => openTableMembersModal(row)
      }, { default: () => row.tableNo });
    }
  },
  { title: "可就餐人数", key: "capacity", width: 120 },
  { title: "已安排人数", key: "occupiedCount", width: 120 },
  {
    title: "还缺人数",
    key: "remainingCount",
    width: 120,
    render(row) {
      return h(NTag, { type: row.overCapacityCount > 0 ? "error" : row.remainingCount === 0 ? "warning" : "success", size: "small" }, {
        default: () => row.overCapacityCount > 0 ? `超员 ${row.overCapacityCount}` : row.remainingCount
      });
    }
  },
  { title: "备注", key: "remark", minWidth: 150 },
  {
    title: "操作",
    key: "actions",
    width: 150,
    render(row) {
      return h("div", actionCellProps(), [
        h(NButton, { text: true, type: "primary", size: "small", onClick: () => openTableModal(row) }, { default: () => "编辑" }),
        h(NPopconfirm, {
          disabled: row.occupiedCount > 0,
          positiveText: "删除",
          negativeText: "取消",
          onPositiveClick: () => deleteDinnerTable(row)
        }, {
          trigger: () => h(NButton, {
            text: true,
            type: "error",
            size: "small",
            disabled: row.occupiedCount > 0,
            loading: deletingTableId.value === row.id
          }, { default: () => "删除" }),
          default: () => "确认删除该桌号？"
        })
      ]);
    }
  }
];

const dinnerAttendeeColumns: DataTableColumns<DinnerAttendeeRow> = [
  { title: "姓名", key: "attendeeName", minWidth: 100 },
  { title: "手机号", key: "attendeePhone", minWidth: 130 },
  { title: "单位", key: "organization", minWidth: 150 },
  { title: "职务", key: "title", minWidth: 120 },
  {
    title: "状态",
    key: "assigned",
    width: 100,
    render(row) {
      return h(NTag, { type: row.assigned ? "success" : "warning", size: "small" }, {
        default: () => row.assigned ? "已安排" : "未安排"
      });
    }
  },
  { title: "桌号", key: "tableNo", width: 110 },
  { title: "备注", key: "remark", minWidth: 140 },
  {
    title: "操作",
    key: "actions",
    width: 150,
    render(row) {
      if (!row.assigned || !row.assignment) {
        return h(NButton, { size: "small", type: "primary", secondary: true, onClick: () => openAssignModal(null, row.attendeeId) }, { default: () => "安排桌号" });
      }
      return h("div", actionCellProps(), [
        h(NButton, { text: true, type: "primary", size: "small", onClick: () => openAssignModal(row.assignment) }, { default: () => "编辑" }),
        h(NButton, { text: true, type: "error", size: "small", loading: clearingId.value === row.attendeeId, onClick: () => clearAssignment(row.assignment!) }, { default: () => "清空" })
      ]);
    }
  }
];

const tableMemberColumns: DataTableColumns<DinnerAssignment> = [
  { title: "姓名", key: "attendeeName", minWidth: 100 },
  { title: "手机号", key: "attendeePhone", minWidth: 130 },
  { title: "单位", key: "organization", minWidth: 150 },
  { title: "备注", key: "remark", minWidth: 150 },
  {
    title: "操作",
    key: "actions",
    width: 150,
    render(row) {
      return h("div", actionCellProps(), [
        h(NButton, { text: true, type: "primary", size: "small", onClick: () => editTableMember(row) }, { default: () => "编辑" }),
        h(NPopconfirm, {
          positiveText: "移出",
          negativeText: "取消",
          onPositiveClick: () => clearAssignment(row)
        }, {
          trigger: () => h(NButton, {
            text: true,
            type: "error",
            size: "small",
            loading: clearingId.value === row.attendeeId
          }, { default: () => "移出" }),
          default: () => "确认将该参会人移出本桌？"
        })
      ]);
    }
  }
];

async function loadDinnerPageData() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;
  const [dinnerResult, tableResult, settingsResult, attendeeResult] = await Promise.allSettled([
    store.loadDinnerAssignments(meetingId),
    store.loadDinnerTables(meetingId),
    store.loadDinnerSettings(meetingId),
    store.loadAttendees(meetingId)
  ]);
  syncDinnerSettingsForm();
  if (dinnerResult.status === "rejected") {
    message.error(errorMessageOf(dinnerResult.reason, "晚宴安排加载失败"));
  }
  if (settingsResult.status === "rejected") {
    message.warning(errorMessageOf(settingsResult.reason, "晚宴信息加载失败"));
  }
  if (attendeeResult.status === "rejected") {
    message.warning(errorMessageOf(attendeeResult.reason, "参会人数据加载失败，未安排列表可能不完整"));
  }
  if (tableResult.status === "rejected") {
    message.warning(errorMessageOf(tableResult.reason, "桌号数据加载失败，容量统计可能不完整"));
  }
}

function rowKey(row: DinnerAssignment) {
  return row.id;
}

function dinnerAttendeeRowKey(row: DinnerAttendeeRow) {
  return row.attendeeId;
}

function tableRowKey(row: DinnerTable) {
  return row.id;
}

function actionCellProps() {
  /*
   * Naive UI 的 DataTable 会在内部渲染单元格内容，scoped CSS 在某些窄屏/弹窗场景下
   * 可能不能稳定约束运行时创建的操作按钮。这里加一层内联布局，保证“编辑/删除”等动作
   * 始终有间距且不折成粘连文本。
   */
  return {
    class: "table-action-cell",
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: "10px",
      whiteSpace: "nowrap"
    }
  };
}

function openAssignModal(assignment?: DinnerAssignment | null, attendeeId?: string) {
  editingAssignment.value = assignment || null;
  form.attendeeId = assignment?.attendeeId || attendeeId || null;
  form.tableNo = assignment?.tableNo || null;
  form.remark = assignment?.remark || "";
  assignModalVisible.value = true;
}

function openTableModal(table?: DinnerTable | null) {
  editingTable.value = table || null;
  tableForm.id = table?.id || null;
  tableForm.tableNo = table?.tableNo || "";
  tableForm.capacity = table?.capacity || 10;
  tableForm.remark = table?.remark || "";
  tableModalVisible.value = true;
}

function openTableConfigModal() {
  syncTableDraftsFromStore();
  tableConfigModalVisible.value = true;
}

function openTableMembersModal(table: DinnerTable) {
  selectedTable.value = table;
  tableMemberForm.attendeeId = null;
  tableMembersModalVisible.value = true;
}

function editTableMember(assignment: DinnerAssignment) {
  /*
   * 复用单人晚宴桌号弹窗，允许会务人员修改备注或把该人员移动到其他桌。
   * 先关闭本桌人员弹窗，避免双层弹窗在移动端占据过多空间。
   */
  tableMembersModalVisible.value = false;
  openAssignModal(assignment);
}

async function saveAssignment() {
  if (!form.attendeeId) {
    message.warning("请选择参会人");
    return;
  }
  if (!form.tableNo?.trim()) {
    message.warning("请填写桌号");
    return;
  }
  const tableNo = form.tableNo.trim();
  saving.value = true;
  try {
    await store.assignDinner({
      attendeeId: form.attendeeId,
      tableNo,
      remark: form.remark
    });
    assignModalVisible.value = false;
    message.success("晚宴桌号已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function addMemberToSelectedTable() {
  const table = activeSelectedTable.value;
  if (!table) return;
  if (!tableMemberForm.attendeeId) {
    message.warning("请选择参会人");
    return;
  }
  if (!canAddMemberToSelectedTable.value) {
    message.warning("该桌容量已满，请先移出人员或调整容量");
    return;
  }
  assigningTableMember.value = true;
  try {
    await store.assignDinner({
      attendeeId: tableMemberForm.attendeeId,
      tableNo: table.tableNo
    });
    tableMemberForm.attendeeId = null;
    message.success("已加入本桌");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "加入本桌失败");
  } finally {
    assigningTableMember.value = false;
  }
}

async function saveDinnerTable() {
  if (!tableForm.tableNo.trim()) {
    message.warning("请填写桌号");
    return;
  }
  if (!tableForm.capacity || tableForm.capacity < 1) {
    message.warning("请填写可就餐人数");
    return;
  }
  savingTable.value = true;
  try {
    await store.saveDinnerTable({
      id: tableForm.id || undefined,
      tableNo: tableForm.tableNo,
      capacity: tableForm.capacity,
      remark: tableForm.remark
    });
    tableModalVisible.value = false;
    message.success("桌号已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "桌号保存失败");
  } finally {
    savingTable.value = false;
  }
}

function addTableDraft() {
  const index = tableDrafts.value.length;
  tableDrafts.value.push({
    uid: `new-${Date.now()}-${index}`,
    tableNo: nextTableNo(),
    capacity: 10,
    remark: "",
    persisted: false,
    occupiedCount: 0,
    color: tablePalette[index % tablePalette.length]
  });
}

function appendBatchTableDrafts() {
  const names = buildBatchTableNos();
  if (!names.length) {
    message.warning("请填写有效的桌号范围");
    return;
  }

  const existing = new Set(tableDrafts.value.map((draft) => draft.tableNo.trim()).filter(Boolean));
  const created: TableDraft[] = [];
  names.forEach((tableNo) => {
    if (existing.has(tableNo)) return;
    const index = tableDrafts.value.length + created.length;
    created.push({
      uid: `batch-${Date.now()}-${index}`,
      tableNo,
      capacity: normalizedTableCapacity(tableBatchForm.capacity),
      remark: tableBatchForm.remark.trim(),
      persisted: false,
      occupiedCount: 0,
      color: tablePalette[index % tablePalette.length]
    });
    existing.add(tableNo);
  });

  if (!created.length) {
    message.warning("该批桌号已在配置中");
    return;
  }
  tableDrafts.value.push(...created);
  message.success(`已加入 ${created.length} 个桌号`);
}

function requestRemoveTableDraft(draft: TableDraft) {
  if (!draft.persisted) {
    tableDrafts.value = tableDrafts.value.filter((item) => item.uid !== draft.uid);
    return;
  }

  if (draft.occupiedCount > 0) {
    dialog.warning({
      title: "该桌已有人员",
      content: `${draft.originalTableNo || draft.tableNo} 当前已有 ${draft.occupiedCount} 人就餐。请先点击桌号移出人员，再删除该桌。`,
      positiveText: "知道了"
    });
    return;
  }

  dialog.warning({
    title: `删除 ${draft.originalTableNo || draft.tableNo}`,
    content: "该桌暂无人员，删除后会移除当前会议中的桌号配置。",
    positiveText: "删除桌号",
    negativeText: "取消",
    onPositiveClick: () => deleteTableDraft(draft)
  });
}

async function deleteTableDraft(draft: TableDraft) {
  if (!draft.id) return;
  deletingTableUid.value = draft.uid;
  try {
    await store.deleteDinnerTable(draft.id);
    tableDrafts.value = tableDrafts.value.filter((item) => item.uid !== draft.uid);
    message.success("桌号已删除");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "桌号删除失败");
  } finally {
    deletingTableUid.value = null;
  }
}

async function saveTableConfig() {
  let drafts: TableDraft[];
  try {
    drafts = normalizeTableDrafts();
  } catch (error) {
    message.warning(error instanceof Error ? error.message : "请检查桌号配置");
    return;
  }

  const downsized = drafts.filter((draft) => draft.persisted && draft.occupiedCount > normalizedTableCapacity(draft.capacity));
  if (downsized.length) {
    message.warning(`有桌号容量小于已安排人数：${downsized.map((draft) => draft.tableNo).join("、")}`);
    return;
  }

  savingTableConfig.value = true;
  try {
    /*
     * 后端目前提供单桌新增/更新接口。这里保持与现有接口兼容，把批量配置拆成多次保存；
     * 已存在桌号走更新，新增桌号走创建，避免批量生成时影响已经安排好的就餐人员。
     */
    for (const draft of drafts) {
      await store.saveDinnerTable({
        id: draft.id,
        tableNo: draft.tableNo,
        capacity: normalizedTableCapacity(draft.capacity),
        remark: draft.remark
      });
    }
    const meetingId = routeMeetingId.value || store.currentMeetingId;
    if (meetingId) {
      await store.loadDinnerTables(meetingId);
    }
    syncTableDraftsFromStore();
    tableConfigModalVisible.value = false;
    message.success("桌号已生成");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "桌号配置保存失败");
  } finally {
    savingTableConfig.value = false;
  }
}

function buildBatchTableNos() {
  const count = Math.max(0, Math.floor(tableBatchForm.count || 0));
  const start = Math.max(1, Math.floor(tableBatchForm.startNo || 1));
  const prefix = tableBatchForm.prefix.trim();
  const suffix = tableBatchForm.suffix.trim();
  return Array.from({ length: count }, (_, index) => `${prefix}${start + index}${suffix}`.trim()).filter(Boolean);
}

function normalizeTableDrafts() {
  const normalized = tableDrafts.value.map((draft, index) => ({
    ...draft,
    tableNo: draft.tableNo.trim(),
    remark: draft.remark.trim(),
    capacity: normalizedTableCapacity(draft.capacity),
    color: tablePalette[index % tablePalette.length]
  }));
  if (normalized.some((draft) => !draft.tableNo)) {
    throw new Error("请填写所有桌号");
  }
  const names = normalized.map((draft) => draft.tableNo);
  const duplicate = names.find((name, index) => names.indexOf(name) !== index);
  if (duplicate) {
    throw new Error(`桌号重复：${duplicate}`);
  }
  tableDrafts.value = normalized;
  return normalized;
}

function normalizedTableCapacity(value: number | null | undefined) {
  return Math.max(1, Math.floor(value || 1));
}

function tableDraftOccupancySummary(draft: TableDraft) {
  if (!draft.persisted) return "新桌号";
  if (!draft.occupiedCount) return "未安排";
  return `${draft.occupiedCount} 已安排`;
}

function nextTableNo() {
  const existing = new Set(tableDrafts.value.map((draft) => draft.tableNo.trim()).filter(Boolean));
  for (let index = 1; index <= 500; index += 1) {
    const name = `${index}号桌`;
    if (!existing.has(name)) return name;
  }
  return `桌号${tableDrafts.value.length + 1}`;
}

function syncTableDraftsFromStore() {
  if (!store.currentDinnerTables.length) {
    tableDrafts.value = [];
    addTableDraft();
    return;
  }
  tableDrafts.value = sortedDinnerTables.value.map((table, index) => ({
    uid: `table-${table.id}`,
    id: table.id,
    tableNo: table.tableNo,
    originalTableNo: table.tableNo,
    capacity: table.capacity,
    remark: table.remark || "",
    persisted: true,
    occupiedCount: table.occupiedCount,
    color: tablePalette[index % tablePalette.length]
  }));
}

async function saveDinnerSettings() {
  savingSettings.value = true;
  try {
    await store.saveDinnerSettings({
      venue: dinnerSettingsForm.venue,
      dinnerTime: dinnerSettingsForm.dinnerTime
    });
    message.success("晚宴信息已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "晚宴信息保存失败");
  } finally {
    savingSettings.value = false;
  }
}

async function deleteDinnerTable(table: DinnerTable) {
  if (table.occupiedCount > 0) {
    message.warning("该桌已有就餐人员，不能删除");
    return;
  }
  deletingTableId.value = table.id;
  try {
    await store.deleteDinnerTable(table.id);
    message.success("桌号已删除");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "桌号删除失败");
  } finally {
    deletingTableId.value = "";
  }
}

async function clearAssignment(assignment: DinnerAssignment) {
  clearingId.value = assignment.attendeeId;
  try {
    await store.clearDinnerAssignment(assignment.attendeeId);
    message.success("晚宴安排已清空");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "清空失败");
  } finally {
    clearingId.value = "";
  }
}

function exportDinner() {
  if (exporting.value) return;
  void runDinnerExport("dinner")
    .then(() => {
      message.success("晚宴安排 XLSX 已生成并开始下载");
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "导出失败");
    });
}

function downloadImportTemplate() {
  downloadXlsxTemplate("晚宴分配导入模板", ["参会码", "姓名", "手机号", "证件号", "单位", "桌号", "备注"], [], {
    instructions: [
      "请在“导入模板”工作表填写数据，不要修改第一行表头名称。",
      "桌号为必填；如果桌号不存在，系统会自动创建该桌，默认可就餐人数为 10 人。",
      "参会人匹配优先使用参会码，其次使用未脱敏手机号、证件号；这些字段都为空时，使用姓名+单位匹配。",
      "从 MeetingOS 导出的文件回导时请保留参会码；脱敏手机号不会用于精确匹配。",
      "如果姓名+单位匹配到多人，请改填手机号或证件号，避免误分配。",
      "系统会校验桌号容量，超过可就餐人数的行会导入失败。",
      "备注可填写主桌、清真、素食等现场用餐提示。",
      "第二个“填写示例”工作表仅供参考，不会参与导入。"
    ],
    exampleRows: [
      ["DIGITAL2026-AB12CD34", "张三", "13800000000", "", "示例公司", "1号桌", "主桌"],
      ["DIGITAL2026-EF56GH78", "李四", "", "110101199001010000", "示例公司", "2号桌", "清真"]
    ],
    textColumnCount: 7
  });
  message.success("晚宴分配导入模板已开始下载");
}

function exportDinnerAttendeeList() {
  /*
   * 顶部“导出晚宴安排”生成服务端固定版本；这里导出的是统一参会人列表的当前视图，
   * 会保留搜索、状态和桌号筛选结果，便于现场按当前页面结果临时分发。
   */
  downloadXlsxTemplate(
    `${currentMeetingName()}-晚宴参会人列表`,
    ["参会码", "姓名", "手机号", "单位", "职务", "参会类型", "状态", "桌号", "备注"],
    filteredDinnerAttendeeRows.value.map((row) => [
      row.attendeeCode || "",
      row.attendeeName || "",
      row.attendeePhone || "",
      row.organization || "",
      row.title || "",
      attendeeTypeLabel(row.attendeeType),
      row.assigned ? "已安排" : "未安排",
      row.tableNo || "",
      row.remark || ""
    ]),
    "晚宴参会人"
  );
  message.success("晚宴参会人列表 XLSX 已开始下载");
}

function currentMeetingName() {
  return (store.currentMeeting?.name || "晚宴安排").replace(/[\\/:*?"<>|]/g, "_");
}

function syncDinnerSettingsForm() {
  dinnerSettingsForm.venue = store.currentMeeting?.dinnerVenue || "";
  dinnerSettingsForm.dinnerTime = store.currentMeeting?.dinnerTime || "";
}

function distinctOptions(values: string[]) {
  return [...new Set(values)]
    .sort((a, b) => a.localeCompare(b, "zh-Hans-CN"))
    .map((value) => ({ label: value, value }));
}

function errorMessageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
</script>

<style scoped>
.dinner-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.dinner-settings-card {
  margin-bottom: 16px;
}

.dinner-settings-form {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(180px, 0.8fr) auto;
  gap: 12px;
  align-items: end;
}

.settings-actions {
  padding-bottom: 2px;
}

.dinner-stat-card {
  padding: 18px 20px;
}

.dinner-stat-value {
  font-size: 28px;
  line-height: 36px;
  font-weight: 750;
}

.dinner-stat-value.success {
  color: #16a34a;
}

.dinner-stat-value.warning {
  color: #d97706;
}

.dinner-stat-value.primary {
  color: var(--accent-color);
}

.dinner-stat-label {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 14px;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dinner-filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1.5fr) minmax(120px, 0.7fr);
  gap: 10px;
  margin-bottom: 14px;
}

.dinner-filter-bar--unified {
  grid-template-columns: minmax(280px, 1.5fr) minmax(140px, 0.45fr) minmax(140px, 0.7fr);
}

.section-card {
  margin-top: 16px;
}

.unassigned-toolbar {
  margin-bottom: 14px;
}

.table-action-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dinner-modal {
  width: min(560px, calc(100vw - 32px));
}

.dinner-members-modal {
  width: min(760px, calc(100vw - 32px));
}

.dinner-table-layout-modal {
  color: #0f172a;
}

.table-layout-modal-title {
  font-size: 30px;
  line-height: 38px;
  font-weight: 800;
  color: #24364f;
}

.table-layout-config {
  display: grid;
  gap: 18px;
}

.table-layout-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
  font-size: 20px;
  font-weight: 750;
}

.add-table-button {
  min-width: 132px;
  font-weight: 750;
}

.table-batch-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #f8fafc;
}

.table-batch-grid {
  display: grid;
  grid-template-columns: 1fr 120px 120px 1fr 140px 1fr;
  gap: 10px;
  align-items: end;
}

.table-batch-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-batch-preview {
  min-width: 0;
  color: #64748b;
  font-size: 14px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-draft-list {
  display: grid;
  gap: 14px;
  max-height: min(460px, 52vh);
  overflow: auto;
  padding-right: 4px;
}

.table-draft-card {
  padding: 18px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #fff;
}

.table-draft-card-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.table-draft-main {
  min-width: 0;
}

.table-draft-name {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.table-draft-dot {
  width: 14px;
  height: 14px;
  border-radius: 999px;
}

.table-draft-name-input :deep(.n-input__input-el) {
  font-size: 18px;
  font-weight: 800;
}

.table-draft-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding-left: 26px;
  color: #8ba0bb;
  font-size: 13px;
  font-weight: 750;
}

.table-draft-delete-button {
  color: #94a3b8;
}

.table-draft-fields {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 14px;
}

.generate-table-button {
  height: 52px;
  font-size: 17px;
  font-weight: 800;
}

.table-members-panel {
  display: grid;
  gap: 14px;
}

.table-members-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.table-members-summary > div {
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.table-members-summary span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.table-members-summary strong {
  display: block;
  margin-top: 2px;
  font-size: 22px;
  line-height: 28px;
}

.table-members-summary strong.danger {
  color: #dc2626;
}

.member-add-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 10px;
}

.table-no-link {
  font-weight: 650;
}

.table-no-mobile-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-color);
  font: inherit;
  cursor: pointer;
}

.full-width-control {
  width: 100%;
}

@media (max-width: 980px) {
  .dinner-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dinner-filter-bar {
    grid-template-columns: 1fr;
  }

  .dinner-filter-bar--unified {
    grid-template-columns: 1fr;
  }

  .dinner-settings-form {
    grid-template-columns: 1fr;
  }

  .member-add-row,
  .table-members-summary,
  .table-draft-fields {
    grid-template-columns: 1fr;
  }

  .table-batch-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .dinner-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-layout-modal-title {
    font-size: 24px;
    line-height: 32px;
  }

  .table-layout-summary-row,
  .table-batch-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .table-batch-grid {
    grid-template-columns: 1fr;
  }
}
</style>
