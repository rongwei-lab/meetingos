<template>
  <n-modal
    :show="show"
    preset="card"
    title="新增参会人"
    class="create-attendee-modal"
    style="width: min(720px, calc(100vw - 32px))"
    :mask-closable="!saving"
    :closable="!saving"
    :close-on-esc="!saving"
    @update:show="updateShow"
  >
    <n-alert v-if="error" class="create-attendee-error" type="error" :show-icon="true">{{ error }}</n-alert>
    <n-form ref="formRef" class="create-attendee-form" :model="form" :rules="rules" :disabled="saving" label-placement="top" @submit.prevent="submit">
      <div class="create-attendee-fields">
        <n-form-item label="姓名" path="name">
          <n-input v-model:value="form.name" placeholder="请输入参会人姓名" :maxlength="120" />
        </n-form-item>
        <n-form-item label="手机号" path="phone">
          <n-input v-model:value="form.phone" placeholder="请输入手机号" :maxlength="40" />
        </n-form-item>
        <n-form-item label="单位" path="organization">
          <n-input v-model:value="form.organization" placeholder="请输入单位名称" :maxlength="160" />
        </n-form-item>
        <n-form-item label="职务" path="title">
          <n-input v-model:value="form.title" placeholder="请输入职务" :maxlength="120" />
        </n-form-item>
        <n-form-item label="参会类型" path="attendeeType">
          <n-select v-model:value="form.attendeeType" :options="attendeeTypeOptions" />
        </n-form-item>
        <n-form-item label="是否需要住宿" path="accommodationRequired">
          <n-switch v-model:value="form.accommodationRequired">
            <template #checked>需要</template>
            <template #unchecked>不需要</template>
          </n-switch>
        </n-form-item>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="form.email" placeholder="请输入邮箱（选填）" :maxlength="160" />
        </n-form-item>
        <n-form-item label="证件号" path="identityNo">
          <n-input v-model:value="form.identityNo" placeholder="请输入证件号（选填）" :maxlength="80" />
        </n-form-item>
        <n-form-item class="create-attendee-field-wide" label="标签" path="tags">
          <n-dynamic-tags v-model:value="form.tags" />
        </n-form-item>
        <n-form-item class="create-attendee-field-wide" label="备注" path="remark">
          <n-input v-model:value="form.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" :maxlength="500" show-count placeholder="填写参会人备注（选填）" />
        </n-form-item>
      </div>
      <p class="create-attendee-hint">新增后默认待审核，可在参会人列表中通过审核。</p>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button :disabled="saving" @click="updateShow(false)">取消</n-button>
        <n-button type="primary" :loading="saving" :disabled="saving" @click="submit">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { NAlert, NButton, NDynamicTags, NForm, NFormItem, NInput, NModal, NSelect, NSpace, NSwitch, useMessage, type FormInst, type FormRules } from "naive-ui";
import { useMeetingStore, type CreateAttendeePayload } from "@/stores/meeting";
import { attendeeTypeLabel } from "@/utils/meetingDisplay";
import type { Attendee, AttendeeType } from "@/utils/types";

const props = defineProps<{ show: boolean; meetingId: string }>();
const emit = defineEmits<{
  "update:show": [show: boolean];
  created: [attendee: Attendee];
}>();
const store = useMeetingStore();
const message = useMessage();
const formRef = ref<FormInst | null>(null);
const form = ref(emptyForm());
const saving = ref(false);
const error = ref("");
let formGeneration = 0;

const attendeeTypeOptions = (["NORMAL", "VIP", "GUEST", "MEDIA", "STAFF"] as AttendeeType[])
  .map((value) => ({ value, label: attendeeTypeLabel(value) }));
const rules: FormRules = {
  name: {
    required: true,
    trigger: ["input", "blur"],
    validator: (_rule, value: string) => Boolean(value.trim()) || new Error("请填写姓名")
  },
  phone: {
    required: true,
    trigger: ["input", "blur"],
    validator: (_rule, value: string) => {
      const normalized = value.trim().replace(/[\s-]/g, "");
      if (!normalized) return new Error("请填写手机号");
      return /^\+?\d{6,20}$/.test(normalized) || new Error("手机号格式不正确");
    }
  },
  email: {
    trigger: ["input", "blur"],
    validator: (_rule, value: string) => !value.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()) || new Error("邮箱格式不正确")
  },
  tags: {
    trigger: "change",
    validator: (_rule, value: string[]) => Array.from(new Set(value.map((tag) => tag.trim()).filter(Boolean))).join(",").length <= 300 || new Error("标签总长度不能超过 300 个字符")
  }
};

function emptyForm(): Required<CreateAttendeePayload> {
  return { name: "", phone: "", organization: "", title: "", identityNo: "", email: "", attendeeType: "NORMAL", accommodationRequired: false, tags: [], remark: "" };
}

watch(() => props.show, (visible) => {
  formGeneration += 1;
  if (!visible) return;
  form.value = emptyForm();
  error.value = "";
  formRef.value?.restoreValidation();
});

watch(() => props.meetingId, () => {
  formGeneration += 1;
  emit("update:show", false);
});

onBeforeUnmount(() => { formGeneration += 1; });

function updateShow(visible: boolean) {
  if (!saving.value) emit("update:show", visible);
}

async function submit() {
  if (saving.value || !props.show || !formRef.value) return;
  if (!props.meetingId) {
    error.value = "请先选择会议";
    return;
  }
  const generation = formGeneration;
  const meetingId = props.meetingId;
  saving.value = true;
  error.value = "";
  try {
    try {
      await formRef.value.validate();
    } catch {
      return;
    }
    if (generation !== formGeneration || props.meetingId !== meetingId) return;
    const created = await store.createAttendee({ ...form.value, tags: [...form.value.tags] }, meetingId);
    // 切换会议或离开页面后，旧请求只负责完成创建，不再关闭新弹窗或发出成功提示。
    if (generation !== formGeneration || props.meetingId !== meetingId || !props.show) return;
    emit("update:show", false);
    emit("created", created);
    message.success("参会人已新增，审核状态为待审核");
  } catch (cause) {
    if (generation === formGeneration && props.show) error.value = cause instanceof Error ? cause.message : "新增参会人失败";
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.create-attendee-error {
  margin-bottom: 16px;
}

.create-attendee-form {
  max-height: calc(100dvh - 240px);
  overflow-y: auto;
  padding: 2px 4px;
}

.create-attendee-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.create-attendee-field-wide {
  grid-column: 1 / -1;
}

.create-attendee-hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}

@media (max-width: 640px) {
  .create-attendee-fields {
    grid-template-columns: 1fr;
  }
}
</style>
