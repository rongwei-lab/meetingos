<template>
  <div class="meeting-authorization-picker">
    <n-input
      v-model:value="keyword"
      clearable
      placeholder="搜索会议名称或编号"
    />

    <div class="meeting-selection-meta">
      <span>已选择 {{ modelValue.length }} 个会议</span>
      <span>共 {{ meetings.length }} 个会议</span>
    </div>

    <n-checkbox-group
      :value="modelValue"
      :disabled="disabled"
      @update:value="updateValue"
    >
      <div v-if="filteredMeetings.length" class="meeting-checkbox-list">
        <div
          v-for="meeting in filteredMeetings"
          :key="meeting.id"
          class="meeting-checkbox-item"
        >
          <n-checkbox :value="meeting.id">
            <span class="meeting-checkbox-copy">
              <strong>{{ meeting.name }}</strong>
              <small>{{ meeting.code }}</small>
            </span>
          </n-checkbox>
        </div>
      </div>
      <n-empty v-else size="small" description="未找到匹配的会议" />
    </n-checkbox-group>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { NCheckbox, NCheckboxGroup, NEmpty, NInput } from "naive-ui";

interface AuthorizableMeeting {
  id: string;
  name: string;
  code: string;
}

const props = withDefaults(defineProps<{
  modelValue: string[];
  meetings: AuthorizableMeeting[];
  disabled?: boolean;
}>(), {
  disabled: false
});

const emit = defineEmits<{
  "update:modelValue": [value: string[]];
}>();

const keyword = ref("");
const filteredMeetings = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase();
  if (!query) return props.meetings;
  return props.meetings.filter((meeting) =>
    `${meeting.name} ${meeting.code}`.toLocaleLowerCase().includes(query)
  );
});

function updateValue(value: Array<string | number>) {
  emit("update:modelValue", [...new Set(value.map(String))]);
}
</script>

<style scoped>
.meeting-authorization-picker {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.meeting-selection-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.meeting-checkbox-list {
  max-height: 280px;
  overflow-y: auto;
  display: grid;
  gap: 8px;
  padding-right: 4px;
}

.meeting-checkbox-item {
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--surface-bg);
}

.meeting-checkbox-item:has(.n-checkbox--checked) {
  border-color: rgba(37, 99, 235, 0.45);
  background: rgba(37, 99, 235, 0.04);
}

.meeting-checkbox-item :deep(.n-checkbox) {
  width: 100%;
  align-items: flex-start;
}

.meeting-checkbox-item :deep(.n-checkbox__label) {
  min-width: 0;
  flex: 1;
  padding-left: 10px;
}

.meeting-checkbox-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.meeting-checkbox-copy strong,
.meeting-checkbox-copy small {
  min-width: 0;
  overflow-wrap: anywhere;
}

.meeting-checkbox-copy strong {
  color: var(--text-primary);
  font-size: 14px;
  line-height: 20px;
}

.meeting-checkbox-copy small {
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 520px) {
  .meeting-checkbox-list {
    max-height: 220px;
  }
}
</style>
