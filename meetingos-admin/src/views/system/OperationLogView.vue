<template>
  <div>
    <page-header title="操作日志" />
    <n-card class="surface" :bordered="false">
      <n-alert v-if="loadError" class="log-load-error" type="warning" :show-icon="false">
        {{ loadError }}
      </n-alert>
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
import { NAlert, NDataTable } from "naive-ui";
import { extractErrorMessage, http, isForbidden, type PagePayload, unwrapApi } from "@/api/http";
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

interface OperationLogRow {
  time: string;
  operator: string;
  type: string;
  target: string;
  remark: string;
}

const logs = ref<OperationLogRow[]>([]);
const isMobile = useIsMobile();
const route = useRoute();
const routeMeetingId = () => String(route.params.meetingId || "");
const loadError = ref("");
const columns = [
  { title: "时间", key: "time", minWidth: 180, render: (row: { time?: string }) => formatDateTimeLabel(row.time) },
  { title: "操作人", key: "operator", minWidth: 120 },
  { title: "类型", key: "type", minWidth: 160 },
  { title: "对象", key: "target", minWidth: 160 },
  { title: "备注", key: "remark", minWidth: 160 }
];

onMounted(async () => {
  try {
    loadError.value = "";
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
  } catch (error) {
    logs.value = [];
    /*
     * 操作日志是系统级敏感数据。真实服务返回 403 时不能继续展示静态示例，
     * 否则会让权限问题看起来像“有数据但不准确”。
     */
    loadError.value = isForbidden(error) ? "当前账号无权查看操作日志" : extractErrorMessage(error, "操作日志加载失败");
  }
});
</script>

<style scoped>
.log-load-error {
  margin-bottom: 12px;
}
</style>
