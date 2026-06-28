<template>
  <div>
    <page-header title="操作日志" subtitle="记录创建会议、审核报名、导入导出、排座、住宿变更、签到补签等关键行为" />
    <n-card class="surface" :bordered="false">
      <n-data-table v-if="!isMobile" :columns="columns" :data="logs" :pagination="{ pageSize: 8 }" />
      <div v-else class="mobile-card-list log-card-list">
        <article v-for="log in logs" :key="`${log.time}-${log.operator}-${log.target}`" class="mobile-data-card">
          <div class="mobile-card-head">
            <div class="mobile-card-title">
              <strong>{{ log.type }}</strong>
              <span>{{ formatDateTimeLabel(log.time) }}</span>
            </div>
            <n-tag size="small" round>{{ log.operator || "-" }}</n-tag>
          </div>
          <div class="mobile-card-fields">
            <div class="mobile-field">
              <span>对象</span>
              <strong>{{ log.target || "-" }}</strong>
            </div>
            <div class="mobile-field">
              <span>备注</span>
              <strong>{{ log.remark || "-" }}</strong>
            </div>
          </div>
        </article>
        <n-empty v-if="!logs.length" description="暂无操作日志" />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { NDataTable } from "naive-ui";
import { http, type PagePayload, unwrapApi } from "@/api/http";
import PageHeader from "@/components/PageHeader.vue";
import { useIsMobile } from "@/composables/useResponsive";
import { formatDateTimeLabel, operationActionLabel, operationMessageLabel, operationTargetLabel } from "@/utils/meetingDisplay";

interface OperationLogDto {
  id: number;
  actor: string;
  action: string;
  targetType?: string;
  targetId?: string;
  message?: string;
  createdAt?: string;
}

const logs = ref([
  { time: "2026-06-24 09:10", operator: "超级管理员", type: "审核报名", target: "陈嘉宁", remark: "审核通过" },
  { time: "2026-06-24 09:12", operator: "超级管理员", type: "自动排座", target: "全国数字经济论坛", remark: "新增 4 个座位分配" },
  { time: "2026-06-24 09:16", operator: "住宿工作人员", type: "修改房间", target: "1801", remark: "分配给嘉宾" },
  { time: "2026-06-24 09:20", operator: "签到工作人员", type: "扫码签到", target: "王子墨", remark: "A入口" }
]);
const isMobile = useIsMobile();
const route = useRoute();
const routeMeetingId = () => String(route.params.meetingId || "");
const columns = [
  { title: "时间", key: "time", minWidth: 180, render: (row: { time?: string }) => formatDateTimeLabel(row.time) },
  { title: "操作人", key: "operator", minWidth: 120 },
  { title: "类型", key: "type", minWidth: 160 },
  { title: "对象", key: "target", minWidth: 160 },
  { title: "备注", key: "remark", minWidth: 160 }
];

onMounted(async () => {
  try {
    const page = unwrapApi(
      await http.get<PagePayload<OperationLogDto>>("/admin/operation-logs", {
        params: { meetingId: routeMeetingId() || undefined, page: 0, size: 50 }
      })
    );
    logs.value = page.content.map((item) => ({
      time: item.createdAt || "",
      operator: item.actor,
      type: operationActionLabel(item.action),
      target: operationTargetLabel(item.targetType, item.targetId),
      remark: operationMessageLabel(item.message)
    }));
  } catch {
    // 后端未启动时保留静态演示日志。
  }
});
</script>
