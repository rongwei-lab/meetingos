<template>
  <div>
    <page-header title="签到工作台">
      <n-select v-model:value="entrance" :options="entranceOptions" style="width: 140px" />
      <n-button type="primary" @click="submitCheckin">{{ isSupplementMode ? "提交补签" : "签到" }}</n-button>
    </page-header>

    <div class="checkin-grid">
      <n-card class="surface checkin-card" :bordered="false">
        <h2>扫码或搜索签到</h2>
        <p>兼容紧凑签到码：MEETINGOS-CHECKIN:{meetingId}:{attendeeId}</p>
        <n-radio-group v-model:value="checkinMode" class="mode-switch">
          <n-radio-button value="NORMAL">现场签到</n-radio-button>
          <n-radio-button value="SUPPLEMENT">补签</n-radio-button>
        </n-radio-group>
        <n-input
          v-model:value="checkinInput"
          size="large"
          :placeholder="isSupplementMode ? '输入需补签参会人的手机号/姓名/签到码' : '扫描二维码，或输入手机号/姓名'"
          @keyup.enter="submitCheckin"
        />
        <n-input
          v-if="isSupplementMode"
          v-model:value="supplementRemark"
          class="supplement-remark"
          type="textarea"
          placeholder="补签原因（必填）"
          :autosize="{ minRows: 2, maxRows: 4 }"
        />
        <n-space>
          <n-button @click="fillDemo">填入演示签到码</n-button>
          <n-button @click="manualSupplement">手动补签</n-button>
        </n-space>
      </n-card>

      <n-card class="surface result-card" :bordered="false" title="最近签到结果">
        <n-result v-if="lastResult" status="success" title="签到成功" :description="`${lastResult.name} · ${lastResult.company}`">
          <template #footer>
            <n-space justify="center">
              <n-tag type="success">{{ formatDateTimeLabel(lastResult.checkinTime) }}</n-tag>
              <n-tag>{{ lastResult.seatLabel || "未排座" }}</n-tag>
            </n-space>
          </template>
        </n-result>
        <n-empty v-else description="等待扫码" />
      </n-card>
    </div>

    <n-card class="surface section-card" :bordered="false" title="签到记录">
      <n-data-table
        v-if="!isMobile"
        :columns="recordColumns"
        :data="store.currentCheckinRecords"
        :pagination="{ defaultPageSize: 10, showSizePicker: true, pageSizes: PAGINATION_PAGE_SIZES, prefix: paginationSummary }"
      />
      <div v-else class="mobile-card-list checkin-record-cards">
        <article v-for="record in store.currentCheckinRecords" :key="`${record.checkinTime}-${record.attendeeName}`" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <strong>{{ record.attendeeName || "-" }}</strong>
              <span>{{ formatDateTimeLabel(record.checkinTime) }}</span>
            </div>
            <n-tag size="small" round>{{ record.entrance || "-" }}</n-tag>
          </div>
          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>方式</span>
              <strong>{{ record.method || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>操作人</span>
              <strong>{{ record.operator || "-" }}</strong>
            </div>
          </div>
        </article>
        <n-empty v-if="!store.currentCheckinRecords.length" description="暂无签到记录" />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { NDataTable, NRadioButton, NRadioGroup, NResult, useMessage } from "naive-ui";
import PageHeader from "@/components/PageHeader.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import { useMeetingStore } from "@/stores/meeting";
import { formatDateTimeLabel } from "@/utils/meetingDisplay";
import { PAGINATION_PAGE_SIZES, paginationSummary } from "@/utils/pagination";
import type { Attendee } from "@/utils/types";

const store = useMeetingStore();
const message = useMessage();
const isMobile = useIsMobile();
const checkinInput = ref("");
const entrance = ref("A入口");
const checkinMode = ref<"NORMAL" | "SUPPLEMENT">("NORMAL");
const supplementRemark = ref("");
const lastResult = ref<Attendee | null>(null);
const isSupplementMode = computed(() => checkinMode.value === "SUPPLEMENT");
let checkinPageLoadToken = 0;

const { routeMeetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });

const entranceOptions = [
  { label: "A入口", value: "A入口" },
  { label: "B入口", value: "B入口" },
  { label: "贵宾入口", value: "贵宾入口" }
];

const recordColumns = [
  { title: "时间", key: "checkinTime", render: (row: { checkinTime?: string }) => formatDateTimeLabel(row.checkinTime) },
  { title: "姓名", key: "attendeeName" },
  { title: "方式", key: "method" },
  { title: "入口", key: "entrance" },
  { title: "操作人", key: "operator" }
];

onMounted(() => {
  void loadCheckinPageData();
});

watch(routeMeetingId, (meetingId, previousMeetingId) => {
  if (meetingId && meetingId !== previousMeetingId) {
    lastResult.value = null;
    void loadCheckinPageData();
  }
});

async function loadCheckinPageData() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  const token = ++checkinPageLoadToken;
  if (!meetingId) return;
  const results = await Promise.allSettled([
    syncMeetingFromRoute(),
    store.loadAttendees(meetingId),
    store.loadCheckinRecords(meetingId)
  ]);
  if (token !== checkinPageLoadToken) return;
  const failed = results.find((result) => result.status === "rejected") as PromiseRejectedResult | undefined;
  if (failed) {
    message.error(failed.reason instanceof Error ? failed.reason.message : "签到数据加载失败");
  }
}

function fillDemo() {
  const attendee = store.currentAttendees.find((item) => item.auditStatus === "APPROVED" && item.checkinStatus !== "CHECKED_IN");
  if (attendee) checkinInput.value = `MEETINGOS-CHECKIN:${attendee.meetingId}:${attendee.attendeeCode || attendee.id}`;
}

function submitCheckin() {
  const input = checkinInput.value.trim();
  const remark = supplementRemark.value.trim();
  if (!input) {
    message.warning("请输入签到码、手机号或姓名");
    return;
  }
  if (isSupplementMode.value && !remark) {
    message.warning("补签必须填写原因");
    return;
  }

  void store.checkinByCode(input, entrance.value, {
    checkinType: isSupplementMode.value ? "SUPPLEMENT" : undefined,
    remark: isSupplementMode.value ? remark : undefined
  }).then((attendee) => {
    lastResult.value = attendee;
    checkinInput.value = "";
    if (isSupplementMode.value) supplementRemark.value = "";
    message.success(isSupplementMode.value ? "补签成功" : "签到成功");
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "签到失败");
  });
}

function manualSupplement() {
  checkinMode.value = "SUPPLEMENT";
  checkinInput.value = store.currentAttendees.find((item) => item.auditStatus === "APPROVED")?.phone || "";
}
</script>

<style scoped>
.checkin-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 16px;
}

.checkin-card {
  padding: 18px;
}

.checkin-card h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.checkin-card p {
  margin: 0 0 20px;
  color: #64748b;
}

.mode-switch {
  margin-bottom: 16px;
}

.supplement-remark {
  margin-top: 12px;
}

.checkin-card :deep(.n-space) {
  margin-top: 16px;
}

.result-card {
  min-height: 260px;
}

.section-card {
  margin-top: 16px;
}

@media (max-width: 880px) {
  .checkin-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .checkin-card {
    padding: 14px;
  }

  .checkin-card h2 {
    font-size: 22px;
    line-height: 30px;
  }

  .checkin-card :deep(.n-space) {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
