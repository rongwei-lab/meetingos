<template>
  <section v-if="visible" class="import-task-feedback" aria-live="polite" aria-atomic="true">
    <n-alert :type="alertType" :show-icon="false">
      <div class="task-summary">
        <div class="task-copy">
          <strong :title="filename">{{ filename || task?.originalFilename || "导入文件" }}</strong>
          <span>{{ statusText }}</span>
          <small v-if="task?.message">{{ task.message }}</small>
          <small v-if="refreshWarning" class="warning-text">导入已完成，但页面刷新失败：{{ refreshWarning }}</small>
        </div>
        <n-button v-if="canOpenResult" size="small" secondary @click="$emit('open-result')">查看结果</n-button>
      </div>
      <n-progress
        v-if="busy || isProcessing"
        class="task-progress"
        type="line"
        :percentage="progress"
        :status="progressStatus"
        :height="8"
        processing
      />
    </n-alert>
  </section>

  <n-modal
    :show="show"
    preset="card"
    class="import-result-modal"
    style="width: min(680px, calc(100vw - 32px))"
    :title="`${label}结果`"
    :mask-closable="!busy"
    @update:show="$emit('update:show', $event)"
  >
    <div class="result-heading">
      <div>
        <strong>{{ filename || task?.originalFilename || "导入文件" }}</strong>
        <span>{{ statusText }}</span>
      </div>
      <n-tag :type="resultTagType" :bordered="false">{{ resultTagText }}</n-tag>
    </div>

    <div v-if="task" class="result-metrics" aria-label="导入结果统计">
      <div><span>总行数</span><strong>{{ task.totalRows }}</strong></div>
      <div><span>已处理</span><strong>{{ task.processedRows }}</strong></div>
      <div><span>成功</span><strong class="success-text">{{ task.importedRows }}</strong></div>
      <div><span>失败</span><strong :class="{ 'error-text': task.failedRows > 0 }">{{ task.failedRows }}</strong></div>
    </div>

    <n-alert v-if="flowError" type="error" :show-icon="false">{{ flowError }}</n-alert>
    <n-alert v-if="refreshWarning" type="warning" :show-icon="false">
      数据已经完成导入，但当前页面刷新失败。请手动刷新页面后查看最新结果。
    </n-alert>
    <p v-if="task?.message" class="result-message">{{ task.message }}</p>

    <template #footer>
      <div class="result-actions">
        <n-button
          v-if="task?.failureFileAvailable"
          secondary
          :loading="downloadingFailures"
          :disabled="busy"
          @click="$emit('download-failures')"
        >
          下载失败明细 XLSX
        </n-button>
        <n-button type="primary" :disabled="busy" @click="$emit('update:show', false)">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { NAlert, NButton, NModal, NProgress, NTag } from "naive-ui";
import type { ImportTaskDto } from "@/stores/meeting";
import { importTaskStatusText } from "@/utils/fileTasks";

const props = defineProps<{
  show: boolean;
  label: string;
  filename: string;
  task: ImportTaskDto | null;
  busy: boolean;
  uploadingFile: boolean;
  progress: number;
  flowError: string;
  refreshWarning: string;
  downloadingFailures: boolean;
}>();

defineEmits<{
  "update:show": [show: boolean];
  "open-result": [];
  "download-failures": [];
}>();

const visible = computed(() => props.busy || Boolean(props.task || props.flowError || props.filename));
const isProcessing = computed(() => ["PENDING", "PREFLIGHTING", "PREVIEW_READY", "RUNNING"].includes(props.task?.status || ""));
const canOpenResult = computed(() => Boolean(props.flowError || (props.task && !isProcessing.value)));
const statusText = computed(() => {
  if (props.uploadingFile) return `正在上传文件 ${Math.round(props.progress)}%`;
  if (props.task) return importTaskStatusText(props.task.status);
  return props.flowError || "等待处理";
});
const alertType = computed(() => {
  if (props.flowError || ["FAILED", "EXPIRED"].includes(props.task?.status || "")) return "error" as const;
  if (props.refreshWarning || (props.task?.failedRows || 0) > 0) return "warning" as const;
  if (props.task?.status === "COMPLETED") return "success" as const;
  return "info" as const;
});
const progressStatus = computed(() => {
  if (props.flowError || props.task?.status === "FAILED") return "error" as const;
  if (props.task?.status === "COMPLETED") return "success" as const;
  return "default" as const;
});
const resultTagType = computed(() => {
  if (props.flowError || ["FAILED", "EXPIRED"].includes(props.task?.status || "")) return "error" as const;
  if ((props.task?.failedRows || 0) > 0 || props.refreshWarning) return "warning" as const;
  return props.task?.status === "COMPLETED" ? "success" as const : "info" as const;
});
const resultTagText = computed(() => {
  if (props.flowError) return "失败";
  return props.task ? importTaskStatusText(props.task.status) : "未完成";
});
</script>

<style scoped>
.import-task-feedback {
  margin: -4px 0 16px;
}

.task-summary,
.result-heading,
.result-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.task-copy,
.result-heading > div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.task-copy strong,
.result-heading strong {
  overflow-wrap: anywhere;
}

.task-copy span,
.task-copy small,
.result-heading span,
.result-message {
  color: var(--text-secondary);
}

.warning-text {
  color: #b45309 !important;
}

.task-progress {
  margin-top: 10px;
}

.result-heading {
  align-items: flex-start;
  margin-bottom: 18px;
}

.result-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.result-metrics > div {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  display: grid;
  gap: 4px;
}

.result-metrics span {
  color: var(--text-secondary);
  font-size: 12px;
}

.result-metrics strong {
  font-size: 22px;
}

.success-text {
  color: #16a34a;
}

.error-text {
  color: #dc2626;
}

.result-message {
  margin: 14px 0 0;
  line-height: 1.6;
}

.result-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

@media (max-width: 640px) {
  .task-summary,
  .result-heading {
    align-items: flex-start;
  }

  .result-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .result-actions > :deep(.n-button) {
    flex: 1 1 auto;
  }
}
</style>
