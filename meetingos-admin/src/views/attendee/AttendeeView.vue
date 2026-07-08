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
      <n-button secondary @click="openNameplatePrint">打印席签</n-button>
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
      v-model:show="nameplateModalVisible"
      preset="card"
      title="打印席签"
      class="nameplate-modal"
      style="width: min(1080px, calc(100vw - 48px))"
    >
      <div class="nameplate-modal-body">
        <section class="nameplate-settings">
          <div class="nameplate-section">
            <div class="nameplate-section-head">
              <strong>折后单面尺寸</strong>
              <span>{{ selectedNameplateSize.label }}，展开高 {{ selectedNameplateSize.heightMm * 2 }}mm</span>
            </div>
            <div class="nameplate-size-grid">
              <button
                v-for="size in nameplateSizePresets"
                :key="size.value"
                type="button"
                class="nameplate-size-option"
                :class="{ active: nameplateSizeValue === size.value }"
                @click="nameplateSizeValue = size.value"
              >
                {{ size.label }}
              </button>
            </div>
          </div>

          <div class="nameplate-section">
            <div class="nameplate-section-head">
              <strong>字体</strong>
              <span>颜色和字号在展示字段中单独设置</span>
            </div>
            <div class="nameplate-font-grid">
              <n-select v-model:value="nameplateFontValue" :options="nameplateFontOptions" placeholder="选择字体" />
            </div>
          </div>

          <div class="nameplate-section">
            <div class="nameplate-section-head">
              <strong>模板背景</strong>
              <span>{{ safeNameplateTemplateUrl ? "当前会议已配置" : "未上传时为白底" }}</span>
            </div>
            <div class="nameplate-template-control">
              <n-input :value="safeNameplateTemplateUrl || '白底默认模板'" readonly />
              <n-upload
                :show-file-list="false"
                accept=".png,.jpg,.jpeg,.webp,.gif"
                :custom-request="uploadNameplateTemplate"
              >
                <n-button secondary :loading="uploadingNameplateTemplate">上传模板</n-button>
              </n-upload>
              <n-button secondary :disabled="!safeNameplateTemplateUrl || savingNameplateTemplate" @click="clearNameplateTemplate">清空</n-button>
            </div>
          </div>

          <div class="nameplate-section">
            <div class="nameplate-section-head">
              <strong>展示字段</strong>
              <span>按当前顺序打印</span>
            </div>
            <n-select
              v-model:value="pendingNameplateField"
              :options="availableNameplateFieldOptions"
              placeholder="添加字段"
              clearable
              @update:value="handleNameplateFieldSelect"
            />
            <div class="nameplate-field-list">
              <div
                v-for="(field, index) in selectedNameplateFieldSettings"
                :key="field.fieldKey"
                class="nameplate-field-row"
              >
                <span>{{ nameplateFieldLabel(field.fieldKey) }}</span>
                <div class="nameplate-field-style-controls">
                  <div class="nameplate-field-style-control">
                    <span>字号</span>
                    <n-input-number
                      class="nameplate-font-size-input"
                      :value="displayNameplateFieldFontSize(field, index)"
                      :min="10"
                      :max="120"
                      :precision="0"
                      :show-button="false"
                      clearable
                      placeholder="自动"
                      size="small"
                      @update:value="updateNameplateFieldFontSize(index, $event)"
                    >
                      <template #suffix>pt</template>
                    </n-input-number>
                  </div>
                  <div class="nameplate-field-style-control">
                    <span>颜色</span>
                    <n-color-picker
                      class="nameplate-field-color-picker"
                      :value="field.color || '#000000'"
                      :show-alpha="false"
                      :modes="['hex']"
                      size="small"
                      @update:value="updateNameplateFieldColor(index, $event)"
                    />
                  </div>
                  <div class="nameplate-field-style-control">
                    <span>字距</span>
                    <n-input-number
                      class="nameplate-letter-spacing-input"
                      :value="displayNameplateFieldLetterSpacing(field, index)"
                      :min="0"
                      :max="1"
                      :step="0.02"
                      :precision="2"
                      :show-button="false"
                      clearable
                      placeholder="自动"
                      size="small"
                      @update:value="updateNameplateFieldLetterSpacing(index, $event)"
                    >
                      <template #suffix>em</template>
                    </n-input-number>
                  </div>
                  <div class="nameplate-field-style-control is-switch-control">
                    <span>加粗</span>
                    <n-switch
                      :value="displayNameplateFieldBold(field, index)"
                      size="small"
                      @update:value="updateNameplateFieldBold(index, $event)"
                    />
                  </div>
                </div>
                <div class="nameplate-field-actions">
                  <n-button text size="tiny" :disabled="index === 0" @click="moveNameplateField(index, -1)">上移</n-button>
                  <n-button
                    text
                    size="tiny"
                    :disabled="index === selectedNameplateFieldSettings.length - 1"
                    @click="moveNameplateField(index, 1)"
                  >
                    下移
                  </n-button>
                  <n-button text size="tiny" type="error" @click="removeNameplateField(index)">移除</n-button>
                </div>
              </div>
            </div>
            <n-button size="small" quaternary @click="resetNameplateFields">恢复默认字段</n-button>
          </div>
        </section>

        <section class="nameplate-preview-panel">
          <div class="nameplate-section-head">
            <strong>打印预览</strong>
            <span>已选择 {{ selectedNameplateAttendees.length }} 人</span>
          </div>
          <div class="nameplate-preview-shell">
            <div ref="nameplatePreviewViewportRef" class="nameplate-preview-viewport" :style="nameplatePreviewViewportStyle">
              <div
                class="nameplate-preview-a4"
                :style="nameplatePreviewPaperStyle"
              >
                <div class="nameplate-preview-page-layout" :class="nameplatePreviewLayoutClass">
                  <div
                    v-for="(previewAttendee, sheetIndex) in nameplatePreviewSheetAttendees"
                    :key="`${previewAttendee?.id || 'placeholder'}-${sheetIndex}`"
                    class="nameplate-preview-sheet"
                    :style="nameplatePreviewSheetStyle"
                  >
                    <div class="nameplate-preview-face is-top" :style="nameplatePreviewFaceStyle">
                      <div class="nameplate-preview-content" :style="nameplatePreviewContentStyle">
                        <div
                          v-for="(field, index) in selectedNameplateFieldSettings"
                          :key="field.fieldKey"
                          class="nameplate-preview-line"
                          :class="nameplateFieldClass(field.fieldKey, index)"
                          :style="nameplatePreviewLineStyle(field, index)"
                        >
                          {{ previewNameplateFieldValue(field.fieldKey, previewAttendee) }}
                        </div>
                      </div>
                    </div>
                    <div class="nameplate-preview-fold-line" />
                    <div class="nameplate-preview-face" :style="nameplatePreviewFaceStyle">
                      <div class="nameplate-preview-content" :style="nameplatePreviewContentStyle">
                        <div
                          v-for="(field, index) in selectedNameplateFieldSettings"
                          :key="field.fieldKey"
                          class="nameplate-preview-line"
                          :class="nameplateFieldClass(field.fieldKey, index)"
                          :style="nameplatePreviewLineStyle(field, index)"
                        >
                          {{ previewNameplateFieldValue(field.fieldKey, previewAttendee) }}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <p class="nameplate-print-hint">
            预览按 {{ nameplatePreviewPaperHint }} 显示，席签居中放置。沿中线对折成 V 型席签，打印时边距设为无或最小。
          </p>
        </section>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="nameplateModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="printingNameplates" @click="printNameplates">打开打印</n-button>
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
import { computed, defineAsyncComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "@/components/PageHeader.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import type { ImportResultDto, MeetingPrintFieldSettingsDto, TduckFieldMappingDto } from "@/stores/meeting";
import { useMeetingStore } from "@/stores/meeting";
import { attendeeFieldLabel, normalizedAttendeeFieldText } from "@/utils/attendeeFields";
import { attendeeTypeLabel, formatDateLikeValue, formatDateTimeLabel, seatTypeLabel } from "@/utils/meetingDisplay";
import { isAllowedUploadImage, sanitizeImageUrl } from "@/utils/safeUrl";
import type { Attendee, AuditStatus, DinnerTable } from "@/utils/types";

const store = useMeetingStore();
const route = useRoute();
const message = useMessage();
const dialog = useDialog();
const isMobile = useIsMobile();
const NColorPicker = defineAsyncComponent(() => import("naive-ui/es/color-picker").then((module) => module.NColorPicker));
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
const nameplateModalVisible = ref(false);
const printingNameplates = ref(false);
const nameplateSizeValue = ref("100x220");
const nameplateFontValue = ref<NameplateFontKey>("hei");
const nameplateTemplateUrl = ref("");
const loadingNameplateTemplate = ref(false);
const savingNameplateTemplate = ref(false);
const uploadingNameplateTemplate = ref(false);
const pendingNameplateField = ref<NameplateFieldKey | null>(null);
const selectedNameplateFieldSettings = ref<NameplateFieldSetting[]>([defaultNameplateFieldSetting("name")]);
const nameplatePreviewViewportRef = ref<HTMLElement | null>(null);
const nameplatePreviewViewportWidth = ref(420);
const auditStatusValues = new Set<AuditStatus>(["PENDING", "APPROVED", "REJECTED", "CANCELLED"]);
let attendeePageLoadToken = 0;
let nameplateSettingsSaveTimer: number | null = null;
let nameplatePreviewResizeObserver: ResizeObserver | null = null;

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

type NameplateFieldKey =
  | "name"
  | "company"
  | "title"
  | "phone"
  | "attendeeType"
  | "seatLabel"
  | "roomNo"
  | "dinnerTableNo"
  | "attendeeCode"
  | "tags";

type NameplateFontKey = "hei" | "sourceHan" | "fzLantingHei" | "yaheiBold" | "song" | "kai" | "fangsong" | "system";

interface NameplateFieldSetting {
  fieldKey: NameplateFieldKey;
  fontSizePt: number | null;
  color: string | null;
  letterSpacingEm: number | null;
  bold: boolean | null;
}

const nameplateSizePresets = [
  { label: "100x200mm", value: "100x200", heightMm: 100, widthMm: 200 },
  { label: "100x210mm", value: "100x210", heightMm: 100, widthMm: 210 },
  { label: "60x90mm", value: "60x90", heightMm: 60, widthMm: 90 },
  { label: "80x120mm", value: "80x120", heightMm: 80, widthMm: 120 },
  { label: "80x150mm", value: "80x150", heightMm: 80, widthMm: 150 },
  { label: "80x180mm", value: "80x180", heightMm: 80, widthMm: 180 },
  { label: "80x200mm", value: "80x200", heightMm: 80, widthMm: 200 },
  { label: "100x150mm", value: "100x150", heightMm: 100, widthMm: 150 },
  { label: "100x180mm", value: "100x180", heightMm: 100, widthMm: 180 },
  { label: "100x220mm", value: "100x220", heightMm: 100, widthMm: 220 }
] as const;

const nameplateFieldOptions: Array<{ label: string; value: NameplateFieldKey }> = [
  { label: "姓名", value: "name" },
  { label: "单位", value: "company" },
  { label: "职务", value: "title" },
  { label: "手机号", value: "phone" },
  { label: "参会类型", value: "attendeeType" },
  { label: "座位号", value: "seatLabel" },
  { label: "房间号", value: "roomNo" },
  { label: "晚宴桌号", value: "dinnerTableNo" },
  { label: "参会码", value: "attendeeCode" },
  { label: "标签", value: "tags" }
];

const nameplateFontOptions: Array<{ label: string; value: NameplateFontKey; css: string; defaultBold?: boolean }> = [
  { label: "黑体", value: "hei", css: '"SimHei", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial, sans-serif' },
  { label: "思源黑体", value: "sourceHan", css: '"Source Han Sans SC", "Source Han Sans CN", "Noto Sans CJK SC", "Noto Sans SC", "Microsoft YaHei", sans-serif' },
  { label: "方正兰亭黑", value: "fzLantingHei", css: '"FZLanTingHeiS", "FZLanTingHei-R-GBK", "方正兰亭黑", "Microsoft YaHei", "SimHei", sans-serif' },
  { label: "微软雅黑粗体", value: "yaheiBold", css: '"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif', defaultBold: true },
  { label: "宋体", value: "song", css: '"SimSun", "Songti SC", "Noto Serif CJK SC", serif' },
  { label: "楷体", value: "kai", css: '"KaiTi", "Kaiti SC", "STKaiti", serif' },
  { label: "仿宋", value: "fangsong", css: '"FangSong", "FangSong_GB2312", "STFangsong", serif' },
  { label: "苹方/系统默认", value: "system", css: '"PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", Arial, sans-serif' }
];

const a4PortraitPaper = { label: "A4 纵向", widthMm: 210, heightMm: 297, orientation: "portrait" as const };
const a4LandscapePaper = { label: "A4 横向", widthMm: 297, heightMm: 210, orientation: "landscape" as const };
const cssPxPerMm = 96 / 25.4;
const cssPxPerPt = 96 / 72;

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

const defaultNameplateSizePreset = nameplateSizePresets.find((size) => size.value === "100x220") || nameplateSizePresets[0];

const selectedNameplateSize = computed(() =>
  nameplateSizePresets.find((size) => size.value === nameplateSizeValue.value) || defaultNameplateSizePreset
);

const selectedNameplateFont = computed(() =>
  nameplateFontOptions.find((font) => font.value === nameplateFontValue.value) || nameplateFontOptions[0]
);

const safeNameplateTemplateUrl = computed(() => sanitizeImageUrl(nameplateTemplateUrl.value));

const nameplateFaceBackgroundStyle = computed(() => {
  const url = safeNameplateTemplateUrl.value;
  if (!url) return {};
  return {
    backgroundImage: `url("${cssUrlEscape(url)}")`
  };
});

const selectedNameplateFieldKeys = computed(() => selectedNameplateFieldSettings.value.map((field) => field.fieldKey));

const availableNameplateFieldOptions = computed(() => {
  const selected = new Set(selectedNameplateFieldKeys.value);
  return nameplateFieldOptions.filter((option) => !selected.has(option.value));
});

const selectedNameplateAttendees = computed(() => {
  const selected = checkedKeys.value.map(String);
  const attendeeById = new Map([...store.currentAttendees, ...filteredAttendees.value].map((attendee) => [attendee.id, attendee]));
  return selected.map((id) => attendeeById.get(id)).filter(isAttendee);
});

const nameplatePreviewAttendee = computed(() => selectedNameplateAttendees.value[0] || filteredAttendees.value[0] || null);

const nameplatePreviewLayout = computed(() => resolveNameplateA4Layout(
  selectedNameplateSize.value.widthMm,
  selectedNameplateSize.value.heightMm * 2
));

const nameplatePreviewPaper = computed(() => nameplatePreviewLayout.value.paper);

const nameplatePreviewPaperHint = computed(() =>
  `${nameplatePreviewPaper.value.label}，每页最多 ${nameplatePreviewLayout.value.capacity} 个`
);

const nameplatePreviewViewportStyle = computed(() => {
  const paper = nameplatePreviewPaper.value;
  /*
   * 预览必须和实际打印窗口同方向，否则管理员会误判折线和文字朝向。
   * 纸张方向由 resolveNameplateA4Layout 决定，A4 横向就直接横向展示。
   */
  return {
    aspectRatio: `${paper.widthMm} / ${paper.heightMm}`
  };
});

const nameplatePreviewPaperStyle = computed(() => ({
  aspectRatio: `${nameplatePreviewPaper.value.widthMm} / ${nameplatePreviewPaper.value.heightMm}`,
  width: "100%"
}));

const nameplatePreviewScale = computed(() => {
  const paperWidthPx = nameplatePreviewPaper.value.widthMm * cssPxPerMm;
  return Math.max(0.1, nameplatePreviewViewportWidth.value / paperWidthPx);
});

const nameplatePreviewLayoutClass = computed(() => `is-${nameplatePreviewLayout.value.arrangement}`);

const nameplatePreviewSheetAttendees = computed<Array<Attendee | null>>(() => {
  const previewAttendees = selectedNameplateAttendees.value.length
    ? selectedNameplateAttendees.value
    : nameplatePreviewAttendee.value
      ? [nameplatePreviewAttendee.value]
      : [null];
  return previewAttendees.slice(0, nameplatePreviewLayout.value.capacity);
});

const nameplatePreviewSheetStyle = computed(() => {
  const size = selectedNameplateSize.value;
  const paper = nameplatePreviewPaper.value;
  const sheetHeightMm = size.heightMm * 2;
  return {
    width: `${(size.widthMm / paper.widthMm) * 100}%`,
    height: `${(sheetHeightMm / paper.heightMm) * 100}%`,
    color: "#000000",
    fontFamily: selectedNameplateFont.value.css
  };
});

const nameplatePreviewContentStyle = computed(() => ({
  gap: `${nameplatePreviewMmToPx(nameplateLineGapMm(selectedNameplateSize.value.heightMm, selectedNameplateFieldSettings.value.length))}px`
}));

const nameplatePreviewFaceStyle = computed(() => {
  const size = selectedNameplateSize.value;
  const paddingInlineMm = Math.max(8, Math.round(size.widthMm * 0.08));
  const paddingBlockMm = Math.max(6, Math.round(size.heightMm * 0.12));
  return {
    ...nameplateFaceBackgroundStyle.value,
    padding: `${nameplatePreviewMmToPx(paddingBlockMm)}px ${nameplatePreviewMmToPx(paddingInlineMm)}px`
  };
});

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

onBeforeUnmount(() => {
  if (nameplateSettingsSaveTimer) {
    window.clearTimeout(nameplateSettingsSaveTimer);
    nameplateSettingsSaveTimer = null;
  }
  disconnectNameplatePreviewResizeObserver();
});

watch(nameplateModalVisible, (visible) => {
  if (!visible) return;
  void nextTick(() => {
    setupNameplatePreviewResizeObserver();
  });
});

watch(routeMeetingId, (meetingId, previousMeetingId) => {
  if (meetingId && meetingId !== previousMeetingId) {
    remoteFilteredAttendees.value = null;
    checkedKeys.value = [];
    nameplateTemplateUrl.value = "";
    selectedNameplateFieldSettings.value = [defaultNameplateFieldSetting("name")];
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
  return store.currentSeatByAttendeeId.get(attendee.id)?.id || null;
}

function currentRoomId(attendee: Attendee) {
  return attendee.roomId || (attendee.roomNo ? store.currentRoomByNo.get(attendee.roomNo)?.id || null : null);
}

function currentDinnerTableNo(attendee: Attendee) {
  return store.currentDinnerAssignmentByAttendeeId.get(attendee.id)?.tableNo || null;
}

function seatLabelText(seat: { areaName: string; rowNo: number; seatNo: number; seatType?: string }) {
  return `${seat.areaName} ${seat.rowNo}排${seat.seatNo}座${seat.seatType ? ` · ${seatTypeLabel(seat.seatType)}` : ""}`;
}

function roomOccupancy(room: { id: string; roomNo: string; capacity?: number }, currentAttendeeId?: string) {
  const occupied = currentAttendeesForRoom(room).filter((attendee) => attendee.id !== currentAttendeeId).length;
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
  const occupied = table?.occupiedCount ?? (store.currentDinnerAssignmentsByTableNo.get(tableNo)?.length || 0);
  return {
    occupied,
    capacity: Math.max(1, table?.capacity || occupied || 10)
  };
}

function errorMessageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function currentAttendeesForRoom(room: { id: string; roomNo: string }) {
  const byRoomId = store.currentAttendeesByRoomId.get(room.id) || [];
  const byRoomNo = store.currentAttendeesByRoomNo.get(room.roomNo) || [];
  if (!byRoomId.length) return byRoomNo;
  if (!byRoomNo.length) return byRoomId;
  return Array.from(new Map([...byRoomId, ...byRoomNo].map((attendee) => [attendee.id, attendee])).values());
}

function roomAvailableForDetail(room: { id: string; roomNo: string; capacity?: number; status?: string }, currentAttendeeId?: string) {
  if (room.status === "DISABLED") return false;
  const occupants = currentAttendeesForRoom(room);
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

function openNameplatePrint() {
  if (!selectedNameplateAttendees.value.length) {
    message.warning("请先勾选要打印席签的参会人");
    return;
  }
  nameplateModalVisible.value = true;
  void loadNameplatePrintSettings();
}

async function loadNameplatePrintSettings() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId || loadingNameplateTemplate.value) return;
  loadingNameplateTemplate.value = true;
  try {
    const settings = await store.loadMeetingPrintSettings(meetingId);
    nameplateTemplateUrl.value = settings.nameplateTemplateUrl || "";
    selectedNameplateFieldSettings.value = normalizeNameplateFieldSettings(settings.nameplateFields);
  } catch (error) {
    message.warning(error instanceof Error ? error.message : "席签模板设置加载失败");
  } finally {
    loadingNameplateTemplate.value = false;
  }
}

function uploadNameplateTemplate(options: UploadCustomRequestOptions) {
  const file = options.file.file;
  if (!file) {
    options.onError();
    message.error("请选择要上传的模板图片");
    return;
  }
  if (!isAllowedUploadImage(file)) {
    options.onError();
    message.warning("仅支持 JPG、PNG、WebP 或 GIF 图片");
    return;
  }

  uploadingNameplateTemplate.value = true;
  void store.uploadImageAsset(file)
    .then(async (result) => {
      await saveNameplateTemplateUrl(result.url);
      options.onFinish();
      message.success("席签模板已上传并保存");
    })
    .catch((error) => {
      options.onError();
      message.error(error instanceof Error ? error.message : "席签模板上传失败");
    })
    .finally(() => {
      uploadingNameplateTemplate.value = false;
    });
}

function clearNameplateTemplate() {
  void saveNameplateTemplateUrl("").then(() => {
    message.success("席签模板已清空，打印将使用白底");
  }).catch((error) => {
    message.error(error instanceof Error ? error.message : "席签模板清空失败");
  });
}

async function saveNameplateTemplateUrl(url: string) {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) throw new Error("请先选择会议");
  savingNameplateTemplate.value = true;
  try {
    const saved = await store.saveMeetingPrintSettings(meetingId, {
      nameplateTemplateUrl: url || null,
      nameplateFields: serializeNameplateFieldSettings()
    });
    nameplateTemplateUrl.value = saved.nameplateTemplateUrl || "";
    selectedNameplateFieldSettings.value = normalizeNameplateFieldSettings(saved.nameplateFields);
  } finally {
    savingNameplateTemplate.value = false;
  }
}

function handleNameplateFieldSelect(value: string | null) {
  if (isNameplateFieldKey(value) && !selectedNameplateFieldKeys.value.includes(value)) {
    selectedNameplateFieldSettings.value = [
      ...selectedNameplateFieldSettings.value,
      defaultNameplateFieldSetting(value)
    ];
    scheduleSaveNameplatePrintSettings();
  }
  pendingNameplateField.value = null;
}

function moveNameplateField(index: number, direction: -1 | 1) {
  const targetIndex = index + direction;
  if (targetIndex < 0 || targetIndex >= selectedNameplateFieldSettings.value.length) return;
  const next = [...selectedNameplateFieldSettings.value];
  const [field] = next.splice(index, 1);
  next.splice(targetIndex, 0, field);
  selectedNameplateFieldSettings.value = next;
  scheduleSaveNameplatePrintSettings();
}

function removeNameplateField(index: number) {
  if (selectedNameplateFieldSettings.value.length <= 1) {
    message.warning("席签至少保留一个展示字段");
    return;
  }
  selectedNameplateFieldSettings.value = selectedNameplateFieldSettings.value.filter((_, currentIndex) => currentIndex !== index);
  scheduleSaveNameplatePrintSettings();
}

function resetNameplateFields() {
  selectedNameplateFieldSettings.value = [defaultNameplateFieldSetting("name")];
  scheduleSaveNameplatePrintSettings();
}

function updateNameplateFieldFontSize(index: number, value: number | null) {
  const nextValue = Number.isFinite(value) && value != null ? clampNumber(Math.round(value), 10, 120) : null;
  selectedNameplateFieldSettings.value = selectedNameplateFieldSettings.value.map((field, currentIndex) =>
    currentIndex === index ? { ...field, fontSizePt: nextValue } : field
  );
  scheduleSaveNameplatePrintSettings();
}

function updateNameplateFieldColor(index: number, value: string | null) {
  selectedNameplateFieldSettings.value = selectedNameplateFieldSettings.value.map((field, currentIndex) =>
    currentIndex === index ? { ...field, color: safeNameplateColorOrNull(value) } : field
  );
  scheduleSaveNameplatePrintSettings();
}

function updateNameplateFieldLetterSpacing(index: number, value: number | null) {
  const nextValue = Number.isFinite(value) && value != null
    ? Math.round(clampNumber(value, 0, 1) * 100) / 100
    : null;
  selectedNameplateFieldSettings.value = selectedNameplateFieldSettings.value.map((field, currentIndex) =>
    currentIndex === index ? { ...field, letterSpacingEm: nextValue } : field
  );
  scheduleSaveNameplatePrintSettings();
}

function updateNameplateFieldBold(index: number, value: boolean) {
  selectedNameplateFieldSettings.value = selectedNameplateFieldSettings.value.map((field, currentIndex) =>
    currentIndex === index ? { ...field, bold: value } : field
  );
  scheduleSaveNameplatePrintSettings();
}

function scheduleSaveNameplatePrintSettings() {
  if (nameplateSettingsSaveTimer) {
    window.clearTimeout(nameplateSettingsSaveTimer);
  }
  nameplateSettingsSaveTimer = window.setTimeout(() => {
    nameplateSettingsSaveTimer = null;
    void saveNameplatePrintSettingsSilently();
  }, 500);
}

async function saveNameplatePrintSettingsSilently() {
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;
  try {
    const saved = await store.saveMeetingPrintSettings(meetingId, {
      nameplateTemplateUrl: nameplateTemplateUrl.value || null,
      nameplateFields: serializeNameplateFieldSettings()
    });
    nameplateTemplateUrl.value = saved.nameplateTemplateUrl || "";
    selectedNameplateFieldSettings.value = normalizeNameplateFieldSettings(saved.nameplateFields);
  } catch (error) {
    message.warning(error instanceof Error ? error.message : "席签字段设置保存失败");
  }
}

function nameplateFieldLabel(fieldKey: NameplateFieldKey) {
  return nameplateFieldOptions.find((option) => option.value === fieldKey)?.label || fieldKey;
}

function previewNameplateFieldValue(fieldKey: NameplateFieldKey, attendee: Attendee | null) {
  if (!attendee) return nameplateFieldLabel(fieldKey);
  return nameplateFieldValue(attendee, fieldKey) || "-";
}

function nameplateFieldClass(fieldKey: NameplateFieldKey, index: number) {
  return isPrimaryNameplateField(fieldKey, index) ? "is-primary" : "is-secondary";
}

function displayNameplateFieldFontSize(field: NameplateFieldSetting, index: number) {
  return effectiveNameplateFontPt(field, index);
}

function displayNameplateFieldLetterSpacing(field: NameplateFieldSetting, index: number) {
  return effectiveNameplateLetterSpacingEm(field, index);
}

function displayNameplateFieldBold(field: NameplateFieldSetting, index: number) {
  return effectiveNameplateBold(field, index);
}

function nameplatePreviewLineStyle(field: NameplateFieldSetting, index: number) {
  const boldStroke = nameplateFieldBoldStroke(field, index);
  return {
    color: nameplateFieldColor(field),
    fontSize: `${nameplatePreviewPtToPx(effectiveNameplateFontPt(field, index))}px`,
    letterSpacing: `${effectiveNameplateLetterSpacingEm(field, index)}em`,
    fontWeight: String(nameplateFieldFontWeight(field, index)),
    WebkitTextStroke: boldStroke ? `${boldStroke}px currentColor` : undefined,
    textShadow: boldStroke ? nameplateFieldBoldTextShadow(boldStroke) : undefined
  };
}

function setupNameplatePreviewResizeObserver() {
  disconnectNameplatePreviewResizeObserver();
  const element = nameplatePreviewViewportRef.value;
  if (!element) return;

  const updateWidth = () => {
    const width = element.getBoundingClientRect().width;
    if (width > 0) {
      nameplatePreviewViewportWidth.value = width;
    }
  };
  updateWidth();

  if (typeof ResizeObserver === "undefined") return;
  nameplatePreviewResizeObserver = new ResizeObserver(updateWidth);
  nameplatePreviewResizeObserver.observe(element);
}

function disconnectNameplatePreviewResizeObserver() {
  nameplatePreviewResizeObserver?.disconnect();
  nameplatePreviewResizeObserver = null;
}

function nameplatePreviewPtToPx(pt: number) {
  return pt * cssPxPerPt * nameplatePreviewScale.value;
}

function nameplatePreviewMmToPx(mm: number) {
  return mm * cssPxPerMm * nameplatePreviewScale.value;
}

async function printNameplates() {
  const attendees = selectedNameplateAttendees.value;
  if (!attendees.length) {
    message.warning("请先勾选要打印席签的参会人");
    return;
  }
  if (!selectedNameplateFieldKeys.value.length) {
    message.warning("请至少选择一个展示字段");
    return;
  }

  printingNameplates.value = true;
  try {
    await ensureNameplateAuxiliaryData();
    const printWindow = window.open("", "_blank", "width=1024,height=768");
    if (!printWindow) {
      message.error("打印窗口被浏览器拦截，请允许弹出窗口后重试");
      return;
    }

    printWindow.opener = null;
    printWindow.document.open("text/html", "replace");
    printWindow.document.write(buildNameplatePrintHtml(attendees));
    printWindow.document.close();
    window.setTimeout(() => {
      printWindow.focus();
      printWindow.print();
    }, 300);
    message.success("已打开席签打印窗口");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "席签打印失败");
  } finally {
    printingNameplates.value = false;
  }
}

async function ensureNameplateAuxiliaryData() {
  /*
   * 参会人列表接口只返回“是否分配晚宴”，不包含具体桌号。
   * 只有管理员把晚宴桌号加入席签字段时才额外拉取分配表，避免默认打印姓名时增加页面负担。
   */
  if (!selectedNameplateFieldKeys.value.includes("dinnerTableNo")) return;
  const meetingId = routeMeetingId.value || store.currentMeetingId;
  if (!meetingId) return;
  try {
    await store.loadDinnerAssignments(meetingId);
  } catch {
    message.warning("晚宴桌号加载失败，将按当前已缓存数据打印");
  }
}

function buildNameplatePrintHtml(attendees: Attendee[]) {
  const size = selectedNameplateSize.value;
  const meetingName = store.currentMeeting?.name || "会议";
  const title = `${meetingName}席签`;
  const fields = selectedNameplateFieldSettings.value;
  const layout = resolveNameplateA4Layout(size.widthMm, size.heightMm * 2);
  const pages = chunkNameplateAttendees(attendees, layout.capacity)
    .map((pageAttendees) => {
      const sheets = pageAttendees
        .map((attendee) => {
          const lines = fields
            .map((field, index) => buildNameplateFieldHtml(attendee, field, index))
            .filter(Boolean)
            .join("");
          return buildNameplateSheetHtml(lines);
        })
        .join("");
      return `<section class="nameplate-page"><div class="nameplate-page-layout nameplate-page-layout--${layout.arrangement}">${sheets}</div></section>`;
    })
    .join("");

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <style>${buildNameplatePrintCss(size.widthMm, size.heightMm)}</style>
</head>
<body>
  ${pages}
</body>
</html>`;
}

function buildNameplateSheetHtml(lines: string) {
  return `<div class="nameplate-sheet">${buildNameplateFaceHtml(lines, true)}<div class="nameplate-fold-line"></div>${buildNameplateFaceHtml(lines, false)}</div>`;
}

function buildNameplateFaceHtml(lines: string, rotated: boolean) {
  const templateUrl = safeNameplateTemplateUrl.value;
  const style = templateUrl ? ` style="background-image: url('${cssUrlEscape(templateUrl)}')"` : "";
  return `<div class="nameplate-face${rotated ? " nameplate-face--top" : ""}"${style}><div class="nameplate__content">${lines}</div></div>`;
}

function buildNameplateFieldHtml(attendee: Attendee, field: NameplateFieldSetting, index: number) {
  const value = nameplateFieldValue(attendee, field.fieldKey).trim();
  if (!value) return "";
  const className = isPrimaryNameplateField(field.fieldKey, index) ? "nameplate__line--primary" : "nameplate__line--secondary";
  const boldStroke = nameplateFieldBoldStroke(field, index);
  const strongBoldStyle = boldStroke
    ? ` -webkit-text-stroke: ${boldStroke}px currentColor; text-shadow: ${nameplateFieldBoldTextShadow(boldStroke)};`
    : "";
  const style = `font-size: ${effectiveNameplateFontPt(field, index)}pt; color: ${nameplateFieldColor(field)}; letter-spacing: ${effectiveNameplateLetterSpacingEm(field, index)}em; font-weight: ${nameplateFieldFontWeight(field, index)};${strongBoldStyle}`;
  return `<div class="nameplate__line ${className}" style="${style}">${escapeHtml(value)}</div>`;
}

function buildNameplatePrintCss(widthMm: number, heightMm: number) {
  const fieldCount = selectedNameplateFieldKeys.value.length;
  const gapMm = nameplateLineGapMm(heightMm, fieldCount);
  const paddingInlineMm = Math.max(8, Math.round(widthMm * 0.08));
  const paddingBlockMm = Math.max(6, Math.round(heightMm * 0.12));
  const fontFamily = selectedNameplateFont.value.css;
  const sheetHeightMm = heightMm * 2;
  const layout = resolveNameplateA4Layout(widthMm, sheetHeightMm);
  const paper = layout.paper;

  return `
@page {
  size: ${paper.widthMm}mm ${paper.heightMm}mm;
  margin: 0;
}
* {
  box-sizing: border-box;
}
html,
body {
  margin: 0;
  padding: 0;
  color: #000000;
  background: #ffffff;
  font-family: ${fontFamily};
}
.nameplate-page {
  width: ${paper.widthMm}mm;
  height: ${paper.heightMm}mm;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #ffffff;
  break-after: page;
  page-break-after: always;
}
.nameplate-page:last-child {
  break-after: auto;
  page-break-after: auto;
}
.nameplate-page-layout {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}
.nameplate-page-layout--row,
.nameplate-page-layout--single {
  flex-direction: row;
}
.nameplate-page-layout--column {
  flex-direction: column;
}
.nameplate-sheet {
  width: ${widthMm}mm;
  height: ${sheetHeightMm}mm;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  color: #000000;
  background: #ffffff;
  border: 0.35mm dashed rgba(0, 0, 0, 0.35);
}
.nameplate-face {
  width: ${widthMm}mm;
  height: ${heightMm}mm;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: ${paddingBlockMm}mm ${paddingInlineMm}mm;
  color: #000000;
  background: #ffffff;
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
}
.nameplate-face--top .nameplate__content {
  transform: rotate(180deg);
}
.nameplate-fold-line {
  position: absolute;
  left: 0;
  top: ${heightMm}mm;
  width: 100%;
  border-top: 0.35mm dashed rgba(0, 0, 0, 0.32);
}
.nameplate__content {
  width: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${gapMm}mm;
  text-align: center;
}
.nameplate__line {
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: pre-wrap;
  letter-spacing: 0;
}
.nameplate__line--primary {
  line-height: 1.05;
  font-weight: 800;
  overflow-wrap: normal;
  word-break: keep-all;
  white-space: nowrap;
}
.nameplate__line--secondary {
  line-height: 1.18;
  font-weight: 520;
}
@media screen {
  body {
    padding: 24px;
    background: #f3f4f6;
    display: grid;
    gap: 18px;
    justify-items: center;
  }
  .nameplate-page {
    border: 1px solid #d9d9d9;
    box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  }
}
@media print {
  body {
    background: #ffffff;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .nameplate-page {
    margin: 0;
    border: 0;
    box-shadow: none;
  }
}`;
}

function nameplatePrimaryFontPt(heightMm: number, fieldCount: number) {
  if (fieldCount <= 1) return clampNumber(Math.round(heightMm * 0.68), 44, 82);
  if (fieldCount === 2) return clampNumber(Math.round(heightMm * 0.48), 34, 66);
  return clampNumber(Math.round(heightMm * 0.36), 28, 52);
}

function nameplateSecondaryFontPt(heightMm: number, fieldCount: number) {
  if (fieldCount <= 2) return clampNumber(Math.round(heightMm * 0.22), 15, 32);
  return clampNumber(Math.round(heightMm * 0.17), 13, 26);
}

function nameplateLineGapMm(heightMm: number, fieldCount: number) {
  if (fieldCount <= 1) return 0;
  return clampNumber(Math.round(heightMm * 0.045), 3, 8);
}

function clampNumber(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

type NameplateA4Paper = typeof a4PortraitPaper | typeof a4LandscapePaper;
type NameplatePageArrangement = "single" | "row" | "column";

interface NameplateA4Layout {
  paper: NameplateA4Paper;
  capacity: 1 | 2;
  arrangement: NameplatePageArrangement;
}

function resolveNameplateA4Layout(widthMm: number, sheetHeightMm: number): NameplateA4Layout {
  /*
   * 席签尺寸必须按原尺寸放入 A4，不再自动缩小。
   * 先判断同一张 A4 是否能容纳两个席签；能放两个就两两分页，不能则单个居中。
   */
  const twoPerPageCandidates: NameplateA4Layout[] = [
    { paper: a4PortraitPaper, capacity: 2, arrangement: "row" },
    { paper: a4PortraitPaper, capacity: 2, arrangement: "column" },
    { paper: a4LandscapePaper, capacity: 2, arrangement: "row" },
    { paper: a4LandscapePaper, capacity: 2, arrangement: "column" }
  ];
  const twoPerPageLayout = twoPerPageCandidates.find((layout) => nameplateLayoutFits(widthMm, sheetHeightMm, layout));
  if (twoPerPageLayout) return twoPerPageLayout;

  const onePerPageCandidates: NameplateA4Layout[] = [
    { paper: a4PortraitPaper, capacity: 1, arrangement: "single" },
    { paper: a4LandscapePaper, capacity: 1, arrangement: "single" }
  ];
  return onePerPageCandidates.find((layout) => nameplateLayoutFits(widthMm, sheetHeightMm, layout)) || onePerPageCandidates[0];
}

function nameplateLayoutFits(widthMm: number, sheetHeightMm: number, layout: NameplateA4Layout) {
  const { paper, arrangement } = layout;
  if (arrangement === "row") {
    return widthMm * layout.capacity <= paper.widthMm && sheetHeightMm <= paper.heightMm;
  }
  if (arrangement === "column") {
    return widthMm <= paper.widthMm && sheetHeightMm * layout.capacity <= paper.heightMm;
  }
  return widthMm <= paper.widthMm && sheetHeightMm <= paper.heightMm;
}

function chunkNameplateAttendees(attendees: Attendee[], capacity: 1 | 2) {
  const pages: Attendee[][] = [];
  for (let index = 0; index < attendees.length; index += capacity) {
    pages.push(attendees.slice(index, index + capacity));
  }
  return pages;
}

function isPrimaryNameplateField(fieldKey: NameplateFieldKey, index: number) {
  return fieldKey === "name" || index === 0;
}

function defaultNameplateFieldSetting(fieldKey: NameplateFieldKey): NameplateFieldSetting {
  return { fieldKey, fontSizePt: null, color: null, letterSpacingEm: null, bold: null };
}

function normalizeNameplateFieldSettings(fields?: MeetingPrintFieldSettingsDto[] | null): NameplateFieldSetting[] {
  const seen = new Set<NameplateFieldKey>();
  const normalized: NameplateFieldSetting[] = [];
  for (const item of fields || []) {
    if (!isNameplateFieldKey(item.fieldKey) || seen.has(item.fieldKey)) continue;
    seen.add(item.fieldKey);
    const numericSize = typeof item.fontSizePt === "number" && Number.isFinite(item.fontSizePt)
      ? Math.round(item.fontSizePt)
      : null;
    normalized.push({
      fieldKey: item.fieldKey,
      fontSizePt: numericSize == null ? null : clampNumber(numericSize, 10, 120),
      color: safeNameplateColorOrNull(item.color),
      letterSpacingEm: normalizeNameplateLetterSpacing(item.letterSpacingEm),
      bold: typeof item.bold === "boolean" ? item.bold : null
    });
  }
  return normalized.length ? normalized : [defaultNameplateFieldSetting("name")];
}

function serializeNameplateFieldSettings(): MeetingPrintFieldSettingsDto[] {
  return normalizeNameplateFieldSettings(selectedNameplateFieldSettings.value).map((field) => ({
    fieldKey: field.fieldKey,
    fontSizePt: field.fontSizePt,
    color: safeNameplateColorOrNull(field.color),
    letterSpacingEm: field.letterSpacingEm,
    bold: field.bold
  }));
}

function effectiveNameplateFontPt(field: NameplateFieldSetting, index: number) {
  if (field.fontSizePt != null) {
    return clampNumber(Math.round(field.fontSizePt), 10, 120);
  }
  const size = selectedNameplateSize.value;
  const fieldCount = selectedNameplateFieldSettings.value.length;
  return isPrimaryNameplateField(field.fieldKey, index)
    ? nameplatePrimaryFontPt(size.heightMm, fieldCount)
    : nameplateSecondaryFontPt(size.heightMm, fieldCount);
}

function nameplateFieldColor(field: NameplateFieldSetting) {
  return safeNameplateColorOrNull(field.color) || "#000000";
}

function effectiveNameplateLetterSpacingEm(field: NameplateFieldSetting, index: number) {
  if (field.letterSpacingEm != null) {
    return normalizeNameplateLetterSpacing(field.letterSpacingEm) ?? 0;
  }
  return isPrimaryNameplateField(field.fieldKey, index) ? 0.08 : 0.03;
}

function effectiveNameplateBold(field: NameplateFieldSetting, index: number) {
  if (typeof field.bold === "boolean") return field.bold;
  if (selectedNameplateFont.value.defaultBold) return true;
  return isPrimaryNameplateField(field.fieldKey, index);
}

function nameplateFieldFontWeight(field: NameplateFieldSetting, index: number) {
  return effectiveNameplateBold(field, index) ? 900 : 500;
}

function nameplateFieldBoldStroke(field: NameplateFieldSetting, index: number) {
  if (!effectiveNameplateBold(field, index)) return 0;
  /*
   * 很多中文字体在浏览器里没有完整的 800/900 字重文件，打印时只靠
   * font-weight 会显得“没加粗”。这里用极细同色描边补强视觉重量，
   * 字号越大描边略增，避免 100pt 以上大字仍然显瘦。
   */
  const fontSizePt = effectiveNameplateFontPt(field, index);
  return Math.round(clampNumber(fontSizePt * 0.006, 0.35, 0.85) * 100) / 100;
}

function nameplateFieldBoldTextShadow(strokePx: number) {
  const offset = Math.round(Math.max(0.2, strokePx * 0.45) * 100) / 100;
  return `${offset}px 0 0 currentColor, -${offset}px 0 0 currentColor`;
}

function normalizeNameplateLetterSpacing(value?: number | null) {
  if (typeof value !== "number" || !Number.isFinite(value)) return null;
  return Math.round(clampNumber(value, 0, 1) * 100) / 100;
}

function nameplateFieldValue(attendee: Attendee, fieldKey: NameplateFieldKey) {
  const values: Record<NameplateFieldKey, string> = {
    name: attendee.name || "",
    company: attendee.company || "",
    title: attendee.title || "",
    phone: attendee.phone || "",
    attendeeType: attendeeTypeLabel(attendee.attendeeType),
    seatLabel: attendee.seatLabel || "",
    roomNo: attendee.roomNo || "",
    dinnerTableNo: currentDinnerTableNo(attendee) || "",
    attendeeCode: attendee.attendeeCode || "",
    tags: attendee.tags.join("、")
  };
  return values[fieldKey] || "";
}

function isAttendee(value: Attendee | undefined): value is Attendee {
  return Boolean(value);
}

function isNameplateFieldKey(value: string | null): value is NameplateFieldKey {
  return Boolean(value && nameplateFieldOptions.some((option) => option.value === value));
}

function safeNameplateColorOrNull(value?: string | null) {
  const text = value?.trim() || "";
  return /^#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?$/.test(text) ? text : null;
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function cssUrlEscape(value: string) {
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/'/g, "\\'");
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

.nameplate-modal :deep(.n-card__content) {
  padding-top: 8px;
}

.nameplate-modal-body {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(300px, 0.92fr);
  gap: 24px;
}

.nameplate-settings,
.nameplate-preview-panel {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 20px;
}

.nameplate-section {
  min-width: 0;
  display: grid;
  gap: 12px;
}

.nameplate-section-head {
  min-width: 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.nameplate-section-head strong {
  color: var(--text-primary);
  font-size: 14px;
}

.nameplate-section-head span {
  color: var(--text-secondary);
  font-size: 12px;
}

.nameplate-size-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.nameplate-size-option {
  min-width: 0;
  height: 44px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  background: var(--surface-bg);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.nameplate-size-option:hover {
  border-color: var(--accent-color);
  color: var(--accent-color);
}

.nameplate-size-option.active {
  border-color: var(--accent-color);
  color: var(--accent-color);
  background: color-mix(in srgb, var(--accent-color) 8%, transparent);
}

.nameplate-font-grid {
  display: grid;
  grid-template-columns: minmax(180px, 1fr);
  gap: 12px;
}

.nameplate-template-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: center;
}

.nameplate-field-list {
  display: grid;
  gap: 8px;
}

.nameplate-field-row {
  min-width: 0;
  min-height: 66px;
  display: grid;
  grid-template-columns: minmax(72px, auto) minmax(0, 1fr);
  grid-template-areas:
    "label controls"
    "label actions";
  align-items: center;
  gap: 8px 12px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-muted);
}

.nameplate-field-row > span {
  grid-area: label;
  min-width: 0;
  color: var(--text-primary);
  font-weight: 650;
}

.nameplate-field-style-controls {
  grid-area: controls;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(104px, 0.9fr) minmax(116px, 1fr) minmax(104px, 0.9fr) minmax(72px, 0.52fr);
  gap: 10px;
  align-items: center;
}

.nameplate-field-style-control {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.nameplate-field-style-control.is-switch-control {
  grid-template-columns: auto auto;
  justify-content: start;
}

.nameplate-font-size-input,
.nameplate-field-color-picker,
.nameplate-letter-spacing-input {
  min-width: 0;
  width: 100%;
}

.nameplate-field-actions {
  grid-area: actions;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
  flex: 0 0 auto;
}

.nameplate-preview-shell {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22px;
  border-radius: 8px;
  background: var(--surface-muted);
  overflow: hidden;
}

.nameplate-preview-viewport {
  width: min(100%, 420px);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.nameplate-preview-a4 {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 1px solid #d7e3f1;
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.14);
}

.nameplate-preview-page-layout {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.nameplate-preview-page-layout.is-row,
.nameplate-preview-page-layout.is-single {
  flex-direction: row;
}

.nameplate-preview-page-layout.is-column {
  flex-direction: column;
}

.nameplate-preview-sheet {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 1px dashed rgba(0, 0, 0, 0.35);
  border-radius: 4px;
  background: #ffffff;
  overflow: hidden;
}

.nameplate-preview-face {
  width: 100%;
  height: 50%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22px;
  background-color: #ffffff;
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
}

.nameplate-preview-face.is-top .nameplate-preview-content {
  transform: rotate(180deg);
}

.nameplate-preview-fold-line {
  position: absolute;
  top: 50%;
  left: 0;
  width: 100%;
  border-top: 1px dashed rgba(0, 0, 0, 0.35);
  z-index: 1;
}

.nameplate-preview-content {
  min-width: 0;
  display: grid;
  gap: 10px;
  text-align: center;
}

.nameplate-preview-line {
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: pre-wrap;
  letter-spacing: 0;
}

.nameplate-preview-line.is-primary {
  font-size: 42px;
  line-height: 1.08;
  font-weight: 800;
  overflow-wrap: normal;
  word-break: keep-all;
  white-space: nowrap;
}

.nameplate-preview-line.is-secondary {
  font-size: 18px;
  line-height: 1.2;
  font-weight: 520;
}

.nameplate-print-hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
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

  .nameplate-modal-body {
    grid-template-columns: 1fr;
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

  .nameplate-size-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .nameplate-font-grid,
  .nameplate-template-control,
  .nameplate-field-row,
  .nameplate-field-style-controls {
    grid-template-columns: 1fr;
  }

  .nameplate-font-grid,
  .nameplate-template-control,
  .nameplate-field-style-controls,
  .nameplate-field-row,
  .nameplate-section-head {
    align-items: stretch;
  }

  .nameplate-field-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .nameplate-field-row {
    grid-template-areas:
      "label"
      "controls"
      "actions";
  }

  .nameplate-preview-face {
    padding: 18px;
  }

  .nameplate-preview-line.is-primary {
    font-size: 32px;
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
