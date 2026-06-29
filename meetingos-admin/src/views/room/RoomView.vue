<template>
  <div>
    <page-header title="住宿管理">
      <n-button secondary size="large" @click="openCreateRoomModal">
        <template #icon><n-icon :component="AddOutline" /></template>
        新增房间
      </n-button>
      <n-button secondary size="large" @click="openBatchRoomModal">
        <template #icon><n-icon :component="AlbumsOutline" /></template>
        批量生成
      </n-button>
      <n-dropdown trigger="click" :options="importTemplateOptions" @select="downloadImportTemplate">
        <n-button secondary size="large">
          <template #icon><n-icon :component="DownloadOutline" /></template>
          导入模板
        </n-button>
      </n-dropdown>
      <n-upload :show-file-list="false" accept=".xlsx" :custom-request="importRooms">
        <n-button secondary size="large" :loading="importingRooms">
          <template #icon><n-icon :component="CloudUploadOutline" /></template>
          导入房间号
        </n-button>
      </n-upload>
      <n-upload :show-file-list="false" accept=".xlsx" :custom-request="importAssignments">
        <n-button secondary size="large" :loading="importingAssignments">
          <template #icon><n-icon :component="CloudUploadOutline" /></template>
          导入分配
        </n-button>
      </n-upload>
      <n-button secondary size="large" @click="exportRooms">
        <template #icon><n-icon :component="DownloadOutline" /></template>
        导出住宿安排
      </n-button>
    </page-header>

    <section class="room-stats" aria-label="住宿统计">
      <div v-for="item in roomStats" :key="item.label" class="room-stat-card surface">
        <div class="room-stat-value" :class="item.tone">{{ item.value }}</div>
        <div class="room-stat-label">{{ item.label }}</div>
      </div>
    </section>

    <n-card class="surface" :bordered="false">
      <template #header>
        <div class="card-header-row">
          <span>房源配置</span>
        </div>
      </template>

      <div class="room-filter-bar">
        <n-input v-model:value="filters.keyword" clearable placeholder="搜索房号 / 酒店 / 房型">
          <template #prefix><n-icon :component="SearchOutline" /></template>
        </n-input>
        <n-select v-model:value="filters.hotelName" clearable placeholder="酒店" :options="hotelFilterOptions" />
        <n-select v-model:value="filters.roomType" clearable placeholder="房型" :options="roomTypeFilterOptions" />
        <n-select v-model:value="filters.status" clearable placeholder="状态" :options="roomStatusFilterOptions" />
      </div>

      <n-data-table
        v-if="!isMobile"
        :columns="roomColumns"
        :data="filteredRooms"
        :pagination="{ pageSize: 8 }"
        :row-key="roomRowKey"
      >
        <template #empty>
          <n-empty description="还没有可分配的房源" />
        </template>
      </n-data-table>
      <div v-else class="mobile-card-list room-card-list">
        <article v-for="room in filteredRooms" :key="room.id" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <strong>{{ room.roomNo }}</strong>
              <span>{{ room.hotelName || "会议酒店" }} · {{ room.roomType || "房间" }}</span>
            </div>
            <n-tag :type="roomStatusMeta[room.status]?.type || 'warning'" round size="small">
              {{ roomStatusMeta[room.status]?.label || "未知" }}
            </n-tag>
          </div>

          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>入住</span>
              <strong>{{ roomOccupancy(room).occupied }}/{{ roomOccupancy(room).capacity }}</strong>
            </div>
            <div class="mobile-field">
              <span>楼层</span>
              <strong>{{ room.floorNo || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>入住提示</span>
              <strong>{{ room.checkinTips || room.remark || "-" }}</strong>
            </div>
          </div>

          <div class="mobile-card-actions">
            <n-button size="small" type="primary" secondary @click="openOccupancyModal(room)">配置入住</n-button>
          </div>
        </article>
        <n-empty v-if="!filteredRooms.length" description="还没有可分配的房源" />
      </div>
    </n-card>

    <n-card class="surface section-card" :bordered="false">
      <template #header>
        <div class="card-header-row">
          <span>住宿参会人列表</span>
          <n-button secondary :disabled="!filteredRoomAttendeeRows.length" @click="exportRoomAttendeeList">
            <template #icon><n-icon :component="DownloadOutline" /></template>
            导出当前列表
          </n-button>
        </div>
      </template>

      <div class="room-attendee-filter-bar">
        <n-input v-model:value="attendeeFilters.keyword" clearable placeholder="搜索姓名 / 手机号 / 单位 / 房间号">
          <template #prefix><n-icon :component="SearchOutline" /></template>
        </n-input>
        <n-select v-model:value="attendeeFilters.status" placeholder="状态" :options="assignmentStatusFilterOptions" />
        <n-select
          v-model:value="attendeeFilters.roomNo"
          clearable
          placeholder="房间号"
          :disabled="attendeeFilters.status === 'UNASSIGNED'"
          :options="assignedRoomFilterOptions"
        />
      </div>

      <n-data-table
        v-if="!isMobile"
        :columns="roomAttendeeColumns"
        :data="filteredRoomAttendeeRows"
        :pagination="{ pageSize: 10 }"
        :row-key="roomAttendeeRowKey"
      >
        <template #empty>
          <n-empty description="暂无符合条件的参会人" />
        </template>
      </n-data-table>
      <div v-else class="mobile-card-list assignment-card-list">
        <article v-for="row in filteredRoomAttendeeRows" :key="row.attendeeId" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <strong>{{ row.attendeeName }}</strong>
              <span>{{ row.organization || "-" }} · {{ row.attendeePhone || "-" }}</span>
            </div>
            <n-tag round size="small" :type="row.assigned ? 'success' : 'warning'">
              {{ row.assigned ? row.roomNo : "未安排" }}
            </n-tag>
          </div>
          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>职务</span>
              <strong>{{ row.title || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>备注</span>
              <strong>{{ row.roomRemark || "-" }}</strong>
            </div>
          </div>
          <div class="mobile-card-actions">
            <template v-if="row.assigned">
              <n-button size="small" type="primary" secondary @click="openAssignRoomForAttendee(row)">编辑</n-button>
              <n-button size="small" type="error" secondary :loading="clearingAttendeeId === row.attendeeId" @click="clearRoomAssignmentByRow(row)">清空</n-button>
            </template>
            <n-button v-else size="small" type="primary" secondary @click="openAssignRoomForAttendee(row)">安排房间</n-button>
          </div>
        </article>
        <n-empty v-if="!filteredRoomAttendeeRows.length" description="暂无符合条件的参会人" />
      </div>
    </n-card>

    <n-modal v-model:show="createRoomModalVisible" preset="card" title="新增房间" class="room-modal" style="width: min(560px, calc(100vw - 32px))" :mask-closable="!savingRoom">
        <n-form label-placement="top" :show-feedback="false">
          <n-form-item label="房间号">
            <n-input v-model:value="roomForm.roomNo" placeholder="例如 1801" />
          </n-form-item>
          <n-form-item label="可入住人数">
            <n-input-number v-model:value="roomForm.capacity" :min="1" :max="20" style="width: 100%" />
          </n-form-item>
          <n-form-item label="酒店">
            <n-input v-model:value="roomForm.hotelName" placeholder="例如 上海会议中心酒店" />
          </n-form-item>
        <n-form-item label="房型">
          <n-select v-model:value="roomForm.roomType" tag filterable placeholder="选择或输入房型" :options="roomTypePresetOptions" />
        </n-form-item>
        <n-form-item label="状态">
          <n-select v-model:value="roomForm.status" :options="roomStatusEditOptions" />
        </n-form-item>
        <n-form-item label="入住提示">
          <n-input
            v-model:value="roomForm.remark"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="例如 14:00 后可入住，凭身份证到前台办理"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="savingRoom" @click="createRoomModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="savingRoom" @click="saveRoom">保存房间</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="batchRoomModalVisible" preset="card" title="批量生成房间" class="room-modal" style="width: min(560px, calc(100vw - 32px))" :mask-closable="!batchSavingRooms">
      <n-form label-placement="top" :show-feedback="false">
        <n-form-item label="酒店">
          <n-input v-model:value="batchForm.hotelName" placeholder="例如 上海会议中心酒店" />
        </n-form-item>
        <div class="batch-grid">
          <n-form-item label="起始楼层">
            <n-input-number v-model:value="batchForm.floorStart" :min="1" :max="99" />
          </n-form-item>
          <n-form-item label="结束楼层">
            <n-input-number v-model:value="batchForm.floorEnd" :min="1" :max="99" />
          </n-form-item>
          <n-form-item label="起始房号">
            <n-input-number v-model:value="batchForm.roomStart" :min="1" :max="99" />
          </n-form-item>
          <n-form-item label="结束房号">
            <n-input-number v-model:value="batchForm.roomEnd" :min="1" :max="99" />
          </n-form-item>
        </div>
        <n-form-item label="房型">
          <n-select v-model:value="batchForm.roomType" tag filterable placeholder="选择或输入房型" :options="roomTypePresetOptions" />
        </n-form-item>
        <n-form-item label="每间可入住人数">
          <n-input-number v-model:value="batchForm.capacity" :min="1" :max="20" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态">
          <n-select v-model:value="batchForm.status" :options="roomStatusEditOptions" />
        </n-form-item>
        <n-form-item label="统一入住提示">
          <n-input
            v-model:value="batchForm.remark"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="批量生成的房间会共用这段提示"
          />
        </n-form-item>

        <div class="batch-preview">
          <strong>将生成 {{ batchPreview.total }} 间</strong>
          <span v-if="batchPreview.examples.length">示例：{{ batchPreview.examples.join("、") }}</span>
          <span v-else>请设置有效的楼层和房号范围</span>
        </div>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="batchSavingRooms" @click="batchRoomModalVisible = false">取消</n-button>
          <n-button type="primary" :disabled="batchPreview.total === 0" :loading="batchSavingRooms" @click="saveBatchRooms">
            生成房间
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="assignModalVisible"
      preset="card"
      title="住宿安排"
      class="room-modal"
      style="width: min(560px, calc(100vw - 32px))"
      :mask-closable="!assigningRoom"
    >
      <n-form label-placement="top" :show-feedback="false">
        <n-form-item label="参会人">
          <n-input :value="assignForm.attendeeLabel" readonly placeholder="请选择参会人" />
        </n-form-item>
        <n-form-item label="房间号">
          <n-select
            v-model:value="assignForm.roomId"
            filterable
            clearable
            placeholder="选择房间号"
            :options="assignRoomOptions"
          />
        </n-form-item>
        <n-form-item label="住宿备注">
          <n-input
            v-model:value="assignForm.roomRemark"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="可填写入住说明、同住人或前台备注；留空则使用房间入住提示"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="assigningRoom" @click="assignModalVisible = false">取消</n-button>
          <n-button type="primary" :disabled="!assignForm.attendeeId || !assignForm.roomId" :loading="assigningRoom" @click="saveRoomAssignment">
            保存
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="occupancyModalVisible" preset="card" class="room-modal" style="width: min(560px, calc(100vw - 32px))" :mask-closable="!assigningRoom && !clearingRoom">
      <template #header>
        <div class="occupancy-modal-title">
          <span>{{ activeRoom ? `${activeRoom.roomNo} 入住人员` : "配置入住人员" }}</span>
          <n-tag v-if="activeRoom" round size="small" :type="roomOccupancy(activeRoom).occupied >= roomOccupancy(activeRoom).capacity ? 'warning' : 'success'">
            {{ roomOccupancy(activeRoom).occupied }}/{{ roomOccupancy(activeRoom).capacity }} 人
          </n-tag>
        </div>
      </template>

      <template v-if="activeRoom">
        <div class="occupancy-summary">
          <strong>{{ activeRoom.hotelName || "会议酒店" }} · {{ activeRoom.roomType }}</strong>
          <span>{{ activeRoom.checkinTips || "暂无入住提示" }}</span>
        </div>

        <n-data-table
          :columns="roomOccupantColumns"
          :data="activeRoomOccupants"
          :pagination="false"
          size="small"
          class="occupant-table"
        >
          <template #empty>
            <n-empty description="当前房间暂无入住人员" />
          </template>
        </n-data-table>

        <n-form label-placement="top" :show-feedback="false" class="occupancy-form">
          <n-form-item label="添加入住人员">
            <n-select
              v-model:value="selectedAttendeeId"
              filterable
              clearable
              placeholder="搜索姓名 / 手机号 / 单位"
              :options="availableAttendeeOptionsForActiveRoom"
              :disabled="roomOccupancy(activeRoom).occupied >= roomOccupancy(activeRoom).capacity"
            />
          </n-form-item>
          <n-form-item label="住宿备注">
            <n-input
              v-model:value="assignmentRemark"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="可填写入住说明、同住人或前台备注；留空则使用房间入住提示"
            />
          </n-form-item>
          <n-button
            block
            type="primary"
            size="large"
            :disabled="!selectedAttendeeId || roomOccupancy(activeRoom).occupied >= roomOccupancy(activeRoom).capacity"
            :loading="assigningRoom"
            @click="assignRoom"
          >
            <template #icon><n-icon :component="CheckmarkCircleOutline" /></template>
            添加到该房间
          </n-button>
        </n-form>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import type { DataTableColumns, DropdownOption, SelectOption, UploadCustomRequestOptions } from "naive-ui";
import { NButton, NDataTable, NTag, NUpload, useMessage } from "naive-ui";
import {
  AddOutline,
  AlbumsOutline,
  CheckmarkCircleOutline,
  CloudUploadOutline,
  DownloadOutline,
  SearchOutline
} from "@vicons/ionicons5";
import { computed, h, onMounted, reactive, ref, watch } from "vue";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import type { CreateRoomPayload, ImportResultDto } from "@/stores/meeting";
import { useMeetingStore } from "@/stores/meeting";
import { downloadXlsxTemplate } from "@/utils/xlsxTemplate";
import type { Attendee, Room, RoomStatus } from "@/utils/types";

type RoomFormState = {
  roomNo: string;
  hotelName: string;
  roomType: string;
  remark: string;
  capacity: number | null;
  status: RoomStatus;
};

type BatchRoomFormState = Omit<RoomFormState, "roomNo"> & {
  floorStart: number;
  floorEnd: number;
  roomStart: number;
  roomEnd: number;
};

type ImportTemplateKey = "rooms" | "assignments";
type AssignmentStatusFilter = "ALL" | "ASSIGNED" | "UNASSIGNED";

interface RoomAttendeeRow {
  attendeeId: string;
  attendeeName: string;
  attendeePhone?: string;
  organization?: string;
  title?: string;
  attendeeType?: string;
  roomNo?: string;
  roomRemark?: string;
  assigned: boolean;
  attendee: Attendee;
}

const store = useMeetingStore();
const message = useMessage();
const isMobile = useIsMobile();
const selectedAttendeeId = ref<string | null>(null);
const selectedRoomId = ref<string | null>(null);
const assignmentRemark = ref("");
const importingRooms = ref(false);
const importingAssignments = ref(false);
const assigningRoom = ref(false);
const createRoomModalVisible = ref(false);
const batchRoomModalVisible = ref(false);
const assignModalVisible = ref(false);
const occupancyModalVisible = ref(false);
const savingRoom = ref(false);
const batchSavingRooms = ref(false);
const clearingRoom = ref(false);
const clearingAttendeeId = ref("");
const activeRoomId = ref<string | null>(null);

const filters = reactive({
  keyword: "",
  hotelName: null as string | null,
  roomType: null as string | null,
  status: null as RoomStatus | null
});

const attendeeFilters = reactive({
  keyword: "",
  roomNo: null as string | null,
  status: "ALL" as AssignmentStatusFilter
});

const roomForm = reactive<RoomFormState>({
  roomNo: "",
  hotelName: "",
  roomType: "标准间",
  remark: "",
  capacity: 2,
  status: "AVAILABLE"
});

const batchForm = reactive<BatchRoomFormState>({
  hotelName: "",
  floorStart: 18,
  floorEnd: 18,
  roomStart: 1,
  roomEnd: 10,
  roomType: "标准间",
  remark: "",
  capacity: 2,
  status: "AVAILABLE"
});

const assignForm = reactive({
  attendeeId: null as string | null,
  attendeeLabel: "",
  roomId: null as string | null,
  roomRemark: ""
});

const { routeMeetingId } = useRouteMeeting({ loadWorkspace: false });

onMounted(() => {
  void loadRoomPageData();
});

watch(routeMeetingId, (meetingId, previousMeetingId) => {
  if (meetingId && meetingId !== previousMeetingId) {
    void loadRoomPageData();
  }
});

const roomStatusMeta: Record<RoomStatus, { label: string; type: "success" | "warning" | "error" }> = {
  AVAILABLE: { label: "可分配", type: "success" },
  ASSIGNED: { label: "已分配", type: "warning" },
  DISABLED: { label: "停用", type: "error" }
};

const roomStatusFilterOptions = [
  { label: "可分配", value: "AVAILABLE" },
  { label: "已分配", value: "ASSIGNED" },
  { label: "停用", value: "DISABLED" }
] satisfies SelectOption[];

const roomStatusEditOptions = roomStatusFilterOptions;

const roomTypePresetOptions = [
  { label: "标准间", value: "标准间" },
  { label: "大床房", value: "大床房" },
  { label: "双床房", value: "双床房" },
  { label: "套房", value: "套房" }
] satisfies SelectOption[];

const importTemplateOptions: DropdownOption[] = [
  { label: "房间号模板", key: "rooms" },
  { label: "分配模板", key: "assignments" }
];
const assignmentStatusFilterOptions = [
  { label: "全部", value: "ALL" },
  { label: "已安排", value: "ASSIGNED" },
  { label: "未安排", value: "UNASSIGNED" }
] satisfies SelectOption[];

const approvedAttendees = computed(() => store.currentAttendees.filter((attendee) => attendee.auditStatus === "APPROVED"));

const activeRoom = computed(() => store.currentRooms.find((room) => room.id === activeRoomId.value) || null);

const assignedAttendees = computed(() => store.currentAttendees.filter((attendee) => attendee.roomNo));
const unassignedAttendees = computed(() => approvedAttendees.value.filter((attendee) => !attendee.roomNo));

const activeRoomOccupants = computed(() => (activeRoom.value ? occupantsForRoom(activeRoom.value) : []));

const availableAttendeeOptionsForActiveRoom = computed(() => {
  return approvedAttendees.value
    // 入住配置弹窗只用于“追加新人”；当前房间已有人员已在上方表格中展示并可移出。
    .filter((attendee) => !attendee.roomNo)
    .map((attendee) => ({
      label: formatAttendeeLabel(attendee),
      value: attendee.id
    }));
});

const roomStats = computed(() => {
  const rooms = store.currentRooms;
  const assignedRooms = rooms.filter((room) => room.status === "ASSIGNED").length;
  const availableRooms = rooms.filter((room) => room.status === "AVAILABLE").length;
  const disabledRooms = rooms.filter((room) => room.status === "DISABLED").length;
  return [
    { label: "房源总数", value: rooms.length, tone: "neutral" },
    { label: "可分配房间", value: availableRooms, tone: "success" },
    { label: "已分配房间", value: assignedRooms, tone: "warning" },
    { label: "停用房间", value: disabledRooms, tone: "muted" },
    { label: "已安排嘉宾", value: assignedAttendees.value.length, tone: "primary" },
    { label: "未安排嘉宾", value: unassignedAttendees.value.length, tone: "warning" }
  ];
});

const hotelFilterOptions = computed(() => distinctOptions(store.currentRooms.map((room) => room.hotelName).filter(Boolean)));
const roomTypeFilterOptions = computed(() => distinctOptions(store.currentRooms.map((room) => room.roomType).filter(Boolean)));
const assignedRoomFilterOptions = computed(() => distinctOptions(assignedAttendees.value.map((attendee) => attendee.roomNo).filter(Boolean) as string[]));
const assignRoomOptions = computed<SelectOption[]>(() => store.currentRooms
  .filter((room) => room.status !== "DISABLED")
  .map((room) => {
    const occupancy = roomOccupancy(room);
    return {
      label: `${room.roomNo} · ${room.hotelName || "会议酒店"} · ${room.roomType || "房间"} · ${occupancy.occupied}/${occupancy.capacity}`,
      value: room.id,
      disabled: room.id !== assignForm.roomId && occupancy.occupied >= occupancy.capacity
    };
  }));

const filteredRooms = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return store.currentRooms.filter((room) => {
    const occupancy = roomOccupancy(room);
    const searchable = `${room.roomNo} ${room.hotelName} ${room.roomType} ${room.checkinTips} ${occupancy.occupied}/${occupancy.capacity}`.toLowerCase();
    return (
      (!keyword || searchable.includes(keyword)) &&
      (!filters.hotelName || room.hotelName === filters.hotelName) &&
      (!filters.roomType || room.roomType === filters.roomType) &&
      (!filters.status || room.status === filters.status)
    );
  });
});

const batchPreview = computed(() => {
  const roomNos = buildBatchRoomNos();
  return {
    total: roomNos.length,
    examples: [...roomNos.slice(0, 4), ...(roomNos.length > 4 ? ["..."] : [])]
  };
});

const roomAttendeeRows = computed<RoomAttendeeRow[]>(() => approvedAttendees.value.map((attendee) => ({
  attendeeId: attendee.id,
  attendeeName: attendee.name,
  attendeePhone: attendee.phone,
  organization: attendee.company,
  title: attendee.title,
  attendeeType: attendee.attendeeType,
  roomNo: attendee.roomNo,
  roomRemark: attendee.roomRemark,
  assigned: Boolean(attendee.roomNo),
  attendee
})));

const filteredRoomAttendeeRows = computed(() => {
  const keyword = attendeeFilters.keyword.trim().toLowerCase();
  return roomAttendeeRows.value.filter((row) => {
    const searchable = [
      row.attendeeName,
      row.attendeePhone,
      row.organization,
      row.title,
      row.attendeeType,
      row.roomNo,
      row.roomRemark
    ].filter(Boolean).join(" ").toLowerCase();
    return (
      (!keyword || searchable.includes(keyword)) &&
      (attendeeFilters.status === "ALL" || (attendeeFilters.status === "ASSIGNED" ? row.assigned : !row.assigned)) &&
      (!attendeeFilters.roomNo || row.roomNo === attendeeFilters.roomNo)
    );
  });
});

const roomColumns: DataTableColumns<Room> = [
  {
    title: "房间号",
    key: "roomNo",
    width: 110,
    render(row) {
      return h(
        NButton,
        {
          text: true,
          type: "primary",
          size: "small",
          onClick: () => openOccupancyModal(row)
        },
        { default: () => row.roomNo }
      );
    }
  },
  { title: "酒店", key: "hotelName", minWidth: 150 },
  { title: "楼层", key: "floorNo", width: 90 },
  { title: "房型", key: "roomType", width: 110 },
  {
    title: "入住",
    key: "occupancy",
    width: 100,
    render(row) {
      const occupancy = roomOccupancy(row);
      return `${occupancy.occupied}/${occupancy.capacity}`;
    }
  },
  {
    title: "状态",
    key: "status",
    width: 100,
    render(row) {
      const meta = roomStatusMeta[row.status] || { label: "未知", type: "warning" as const };
      return h(NTag, { type: meta.type, round: true, size: "small" }, { default: () => meta.label });
    }
  },
  { title: "入住提示", key: "checkinTips", minWidth: 200 }
];

const roomAttendeeColumns: DataTableColumns<RoomAttendeeRow> = [
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
  { title: "房间号", key: "roomNo", width: 110 },
  { title: "房间备注", key: "roomRemark", minWidth: 140 },
  {
    title: "操作",
    key: "actions",
    width: 150,
    render(row) {
      if (!row.assigned) {
        return h(NButton, { size: "small", type: "primary", secondary: true, onClick: () => openAssignRoomForAttendee(row) }, { default: () => "安排房间" });
      }
      return h("div", actionCellProps(), [
        h(NButton, { text: true, type: "primary", size: "small", onClick: () => openAssignRoomForAttendee(row) }, { default: () => "编辑" }),
        h(NButton, { text: true, type: "error", size: "small", loading: clearingAttendeeId.value === row.attendeeId, onClick: () => clearRoomAssignmentByRow(row) }, { default: () => "清空" })
      ]);
    }
  }
];

const roomOccupantColumns: DataTableColumns<Attendee> = [
  { title: "姓名", key: "name" },
  { title: "手机号", key: "phone", minWidth: 120 },
  { title: "单位", key: "company", minWidth: 130 },
  {
    title: "操作",
    key: "actions",
    width: 72,
    align: "center",
    render(row) {
      return h(
        "div",
        { class: "occupant-action-cell" },
        h(
          NButton,
          {
            text: true,
            type: "error",
            size: "small",
            disabled: clearingRoom.value,
            onClick: () => clearRoomAssignment(row)
          },
          { default: () => "移出" }
        )
      );
    }
  }
];

async function loadRoomPageData() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;
  const [roomsResult, attendeesResult] = await Promise.allSettled([
    store.loadRooms(meetingId),
    store.loadAttendees(meetingId)
  ]);
  if (roomsResult.status === "rejected") {
    message.error(errorMessageOf(roomsResult.reason, "房源数据加载失败"));
  }
  if (attendeesResult.status === "rejected") {
    message.warning(errorMessageOf(attendeesResult.reason, "参会人数据加载失败，住宿人员列表可能不完整"));
  }
  store.reconcileAssignments();
  store.refreshMeetingCounters();
}

function roomRowKey(row: Room) {
  return row.id;
}

function roomAttendeeRowKey(row: RoomAttendeeRow) {
  return row.attendeeId;
}

function actionCellProps() {
  /*
   * DataTable 的操作列由运行时渲染，给按钮包一层稳定的 inline-flex，
   * 避免“编辑/清空”在窄列或弹窗中挤成粘连文本。
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

function openCreateRoomModal() {
  resetRoomForm();
  createRoomModalVisible.value = true;
}

function openBatchRoomModal() {
  batchRoomModalVisible.value = true;
}

function openOccupancyModal(room: Room) {
  activeRoomId.value = room.id;
  selectedRoomId.value = room.id;
  selectedAttendeeId.value = null;
  assignmentRemark.value = room.checkinTips || room.remark || "";
  occupancyModalVisible.value = true;
}

function openAssignRoomForAttendee(row: RoomAttendeeRow) {
  assignForm.attendeeId = row.attendeeId;
  assignForm.attendeeLabel = [row.attendeeName, row.attendeePhone, row.organization].filter(Boolean).join(" · ");
  assignForm.roomId = row.attendee.roomId || roomByNo(row.roomNo)?.id || null;
  assignForm.roomRemark = row.roomRemark || roomByNo(row.roomNo)?.checkinTips || roomByNo(row.roomNo)?.remark || "";
  assignModalVisible.value = true;
}

async function saveRoom() {
  const payload = roomPayloadFromForm(roomForm);
  if (!payload.roomNo) {
    message.warning("请填写房间号");
    return;
  }
  if (!roomForm.capacity || roomForm.capacity < 1) {
    message.warning("请填写可入住人数");
    return;
  }
  savingRoom.value = true;
  try {
    await store.createRoom(payload);
    createRoomModalVisible.value = false;
    message.success("房间已新增");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "新增房间失败");
  } finally {
    savingRoom.value = false;
  }
}

async function saveBatchRooms() {
  const roomNos = buildBatchRoomNos();
  if (!roomNos.length) {
    message.warning("请设置有效的楼层和房号范围");
    return;
  }

  batchSavingRooms.value = true;
  const failures: string[] = [];
  let created = 0;
  try {
    for (const roomNo of roomNos) {
      try {
        await store.createRoom({
          roomNo,
          hotelName: batchForm.hotelName,
          roomType: batchForm.roomType,
          remark: batchForm.remark,
          capacity: Math.max(1, Math.floor(batchForm.capacity || 1)),
          status: batchForm.status
        });
        created++;
      } catch (error) {
        const reason = error instanceof Error ? error.message : "创建失败";
        failures.push(`${roomNo}: ${reason}`);
      }
    }

    if (failures.length) {
      message.warning(`已生成 ${created} 间，失败 ${failures.length} 间；${failures.slice(0, 3).join("；")}`, { duration: 7000 });
    } else {
      batchRoomModalVisible.value = false;
      message.success(`已生成 ${created} 间房`);
    }
  } finally {
    batchSavingRooms.value = false;
  }
}

async function assignRoom() {
  if (!selectedAttendeeId.value || !selectedRoomId.value) {
    message.warning("请选择入住人员");
    return;
  }
  assigningRoom.value = true;
  try {
    await store.assignRoom(selectedAttendeeId.value, selectedRoomId.value, assignmentRemark.value);
    message.success("已添加入住人员");
    selectedAttendeeId.value = null;
  } catch (error) {
    message.error(error instanceof Error ? error.message : "分配房间失败");
  } finally {
    assigningRoom.value = false;
  }
}

async function saveRoomAssignment() {
  if (!assignForm.attendeeId || !assignForm.roomId) {
    message.warning("请选择参会人和房间号");
    return;
  }
  assigningRoom.value = true;
  try {
    await store.assignRoom(assignForm.attendeeId, assignForm.roomId, assignForm.roomRemark);
    assignModalVisible.value = false;
    message.success("住宿安排已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存住宿安排失败");
  } finally {
    assigningRoom.value = false;
  }
}

async function clearRoomAssignment(attendee: Attendee) {
  clearingRoom.value = true;
  try {
    await store.clearRoomAssignment(attendee.id);
    message.success("已移出入住人员");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "移出入住人员失败");
  } finally {
    clearingRoom.value = false;
  }
}

async function clearRoomAssignmentByRow(row: RoomAttendeeRow) {
  clearingAttendeeId.value = row.attendeeId;
  try {
    await store.clearRoomAssignment(row.attendeeId);
    message.success("住宿安排已清空");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "清空住宿安排失败");
  } finally {
    clearingAttendeeId.value = "";
  }
}

function importRooms(options: UploadCustomRequestOptions) {
  const file = options.file.file;
  if (!file) {
    options.onError();
    return;
  }
  importingRooms.value = true;
  void store.importRooms(file)
    .then((result) => {
      options.onFinish();
      showImportResult(result);
    })
    .catch((error) => {
      options.onError();
      message.error(error instanceof Error ? error.message : "房间号导入失败");
    })
    .finally(() => {
      importingRooms.value = false;
    });
}

function importAssignments(options: UploadCustomRequestOptions) {
  const file = options.file.file;
  if (!file) {
    options.onError();
    return;
  }
  importingAssignments.value = true;
  void store.importRoomAssignments(file)
    .then((result) => {
      options.onFinish();
      showImportResult(result);
    })
    .catch((error) => {
      options.onError();
      message.error(error instanceof Error ? error.message : "房间分配导入失败");
    })
    .finally(() => {
      importingAssignments.value = false;
    });
}

function exportRooms() {
  void store.downloadExport("rooms").then(() => {
    message.success("住宿安排 CSV 已开始下载");
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "导出失败，请确认后端服务已启动");
  });
}

function exportRoomAttendeeList() {
  /*
   * 顶部“导出住宿安排”保留原后端完整导出；这里导出当前统一参会人列表，
   * 会保留搜索、状态和房间号筛选结果，便于按页面视图核对或现场临时分发。
   */
  downloadXlsxTemplate(
    `${currentMeetingName()}-住宿参会人列表`,
    ["姓名", "手机号", "单位", "职务", "参会类型", "状态", "房间号", "房间备注"],
    filteredRoomAttendeeRows.value.map((row) => [
      row.attendeeName || "",
      row.attendeePhone || "",
      row.organization || "",
      row.title || "",
      row.attendeeType || "",
      row.assigned ? "已安排" : "未安排",
      row.roomNo || "",
      row.roomRemark || ""
    ]),
    "住宿参会人"
  );
  message.success("住宿参会人列表 XLSX 已开始下载");
}

function downloadImportTemplate(key: string | number) {
  const templateKey = key as ImportTemplateKey;
  const template = importTemplates[templateKey];
  if (!template) {
    message.warning("未找到导入模板");
    return;
  }
  downloadXlsxTemplate(template.filename, template.headers, template.rows);
  message.success(`${template.filename} 已开始下载`);
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

function resetRoomForm() {
  roomForm.roomNo = "";
  roomForm.hotelName = store.currentRooms[0]?.hotelName || "";
  roomForm.roomType = store.currentRooms[0]?.roomType || "标准间";
  roomForm.remark = store.currentRooms[0]?.checkinTips || "";
  roomForm.capacity = store.currentRooms[0]?.capacity || 2;
  roomForm.status = "AVAILABLE";
}

function roomPayloadFromForm(form: RoomFormState): CreateRoomPayload {
  return {
    roomNo: form.roomNo.trim(),
    hotelName: form.hotelName.trim(),
    roomType: form.roomType.trim(),
    remark: form.remark.trim(),
    capacity: Math.max(1, Math.floor(form.capacity || 1)),
    status: form.status
  };
}

function buildBatchRoomNos() {
  const floorStart = Math.floor(batchForm.floorStart || 0);
  const floorEnd = Math.floor(batchForm.floorEnd || 0);
  const roomStart = Math.floor(batchForm.roomStart || 0);
  const roomEnd = Math.floor(batchForm.roomEnd || 0);
  if (floorStart <= 0 || floorEnd < floorStart || roomStart <= 0 || roomEnd < roomStart) return [];
  const roomNos: string[] = [];
  for (let floor = floorStart; floor <= floorEnd; floor++) {
    for (let room = roomStart; room <= roomEnd; room++) {
      roomNos.push(`${floor}${String(room).padStart(2, "0")}`);
    }
  }
  return roomNos;
}

function distinctOptions(values: string[]) {
  return [...new Set(values)]
    .sort((a, b) => a.localeCompare(b, "zh-Hans-CN"))
    .map((value) => ({ label: value, value }));
}

function formatAttendeeLabel(attendee: Attendee) {
  return [attendee.name, attendee.phone, attendee.company].filter(Boolean).join(" · ");
}

function currentMeetingName() {
  return (store.currentMeeting?.name || "住宿安排").replace(/[\\/:*?"<>|]/g, "_");
}

function roomByNo(roomNo?: string) {
  return roomNo ? store.currentRooms.find((room) => room.roomNo === roomNo) : undefined;
}

function occupantsForRoom(room: Room) {
  return store.currentAttendees.filter((attendee) => attendee.roomId === room.id || attendee.roomNo === room.roomNo);
}

function roomOccupancy(room: Room) {
  return {
    occupied: occupantsForRoom(room).length,
    capacity: Math.max(1, room.capacity || 1)
  };
}

function errorMessageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

const importTemplates: Record<ImportTemplateKey, { filename: string; headers: string[]; rows: string[][] }> = {
  rooms: {
    filename: "房间号导入模板",
    headers: ["房间号", "酒店", "房型", "可入住人数", "入住提示"],
    rows: [
      ["1801", "上海会议中心酒店", "标准间", "2", "14:00 后可入住，凭身份证办理入住"],
      ["1802", "上海会议中心酒店", "大床房", "1", "VIP 嘉宾优先安排"]
    ]
  },
  assignments: {
    filename: "房间分配导入模板",
    headers: ["姓名", "手机号", "证件号", "单位", "房间号", "房间备注"],
    rows: [
      ["张三", "13800000000", "", "示例公司", "1801", "同住人：李四"],
      ["李四", "", "110101199001010000", "示例公司", "1801", "与张三同住"]
    ]
  }
};

</script>

<style scoped>
.room-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.room-stat-card {
  padding: 18px 20px;
}

.room-stat-value {
  font-size: 28px;
  line-height: 36px;
  font-weight: 750;
}

.room-stat-value.success {
  color: #16a34a;
}

.room-stat-value.warning {
  color: #d97706;
}

.room-stat-value.primary {
  color: var(--accent-color);
}

.room-stat-value.muted {
  color: var(--text-secondary);
}

.room-stat-label {
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

.room-filter-bar {
  display: grid;
  grid-template-columns: minmax(180px, 1.4fr) minmax(120px, 0.9fr) minmax(120px, 0.8fr) minmax(120px, 0.7fr);
  gap: 10px;
  margin-bottom: 14px;
}

.room-attendee-filter-bar {
  display: grid;
  grid-template-columns: minmax(280px, 1.5fr) minmax(140px, 0.45fr) minmax(140px, 0.7fr);
  gap: 10px;
  margin-bottom: 14px;
}

.table-action-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.occupant-action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
}

.section-card {
  margin-top: 16px;
}

.room-modal {
  width: min(560px, calc(100vw - 32px));
}

.occupancy-modal-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.occupancy-summary {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  margin-bottom: 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.occupancy-summary span {
  color: var(--text-secondary);
  font-size: 13px;
}

.occupant-table {
  margin-bottom: 14px;
}

.occupancy-form {
  padding-top: 2px;
}

.batch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.batch-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
  color: var(--text-secondary);
  font-size: 14px;
}

.batch-preview strong {
  color: var(--text-primary);
}

@media (max-width: 1180px) {
  .room-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .room-filter-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .room-stats,
  .room-filter-bar,
  .room-attendee-filter-bar,
  .batch-grid {
    grid-template-columns: 1fr;
  }
}
</style>
