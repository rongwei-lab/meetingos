<template>
  <n-tag :type="type" size="small" round>{{ text }}</n-tag>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ value: string }>();

const dictionary: Record<string, { text: string; type: "default" | "success" | "warning" | "error" | "info" }> = {
  DRAFT: { text: "草稿", type: "default" },
  REGISTRATION_OPEN: { text: "报名中", type: "success" },
  REGISTRATION_CLOSED: { text: "报名截止", type: "warning" },
  READY: { text: "待开始", type: "info" },
  IN_PROGRESS: { text: "进行中", type: "success" },
  ENDED: { text: "已结束", type: "default" },
  ARCHIVED: { text: "已归档", type: "default" },
  PENDING: { text: "待审核", type: "warning" },
  APPROVED: { text: "已通过", type: "success" },
  REJECTED: { text: "已拒绝", type: "error" },
  CANCELLED: { text: "已取消", type: "default" },
  CHECKED_IN: { text: "已签到", type: "success" },
  NOT_CHECKED_IN: { text: "未签到", type: "warning" },
  SUCCESS: { text: "成功", type: "success" },
  FAILED: { text: "失败", type: "error" },
  DUPLICATE_IGNORED: { text: "重复已忽略", type: "info" },
  PENDING_CONFLICT: { text: "待处理", type: "warning" },
  ACTIVE: { text: "当前来源", type: "success" },
  SUPERSEDED: { text: "已被替代", type: "default" },
  IGNORED: { text: "已忽略", type: "default" }
};

const meta = computed(() => dictionary[props.value] || { text: props.value, type: "default" as const });
const text = computed(() => meta.value.text);
const type = computed(() => meta.value.type);
</script>
