<template>
  <div v-if="loading" class="designer-loading"><n-spin size="large" /><span>正在载入邀请设计…</span></div>
  <div v-else-if="loadError" class="designer-error surface">
    <n-result status="error" title="邀请方案无法打开" :description="loadError">
      <template #footer><n-button @click="goBack">返回方案列表</n-button></template>
    </n-result>
  </div>
  <div v-else-if="invitation && document" class="designer-shell" :class="{ 'preview-mode': readOnlyMode }">
    <header class="designer-toolbar">
      <div class="toolbar-group toolbar-left">
        <n-button quaternary size="small" aria-label="返回" @click="goBack"><template #icon><n-icon :component="ArrowBackOutline" /></template></n-button>
        <div class="designer-title">
          <strong>{{ invitation.name }}</strong>
          <span :class="saveStateClass">{{ saveStateText }}</span>
        </div>
      </div>

      <template v-if="!readOnlyMode">
        <div class="toolbar-group history-controls">
          <n-tooltip><template #trigger><n-button quaternary size="small" :disabled="!canUndo" aria-label="撤销" @click="undo"><template #icon><n-icon :component="ArrowUndoOutline" /></template></n-button></template>撤销</n-tooltip>
          <n-tooltip><template #trigger><n-button quaternary size="small" :disabled="!canRedo" aria-label="重做" @click="redo"><template #icon><n-icon :component="ArrowRedoOutline" /></template></n-button></template>重做</n-tooltip>
        </div>
        <div class="canvas-mode-switch">
          <button type="button" :class="{ active: canvasMode === 'h5' }" @click="canvasMode = 'h5'">H5</button>
          <button type="button" :class="{ active: canvasMode === 'poster' }" @click="canvasMode = 'poster'">海报</button>
        </div>
        <div class="toolbar-group zoom-controls">
          <n-button quaternary size="small" aria-label="缩小" @click="decreaseZoom"><template #icon><n-icon :component="RemoveOutline" /></template></n-button>
          <span>{{ Math.round(zoom * 100) }}%</span>
          <n-button quaternary size="small" aria-label="放大" @click="increaseZoom"><template #icon><n-icon :component="AddOutline" /></template></n-button>
        </div>
      </template>

      <div class="toolbar-group toolbar-right">
        <n-button secondary size="small" @click="showPreview = true"><template #icon><n-icon :component="EyeOutline" /></template>预览</n-button>
        <template v-if="!readOnlyMode">
          <n-button secondary size="small" :loading="saving" :disabled="invitation.status === 'ARCHIVED'" @click="saveDraft(true)">保存草稿</n-button>
          <n-dropdown :options="publishMenuOptions" trigger="click" @select="handlePublishMenu">
            <n-button type="primary" size="small" :loading="publishing" :disabled="invitation.status === 'ARCHIVED'">
              {{ invitation.status === "PUBLISHED" ? "重新发布" : "发布" }}
              <template #icon><n-icon :component="ChevronDownOutline" /></template>
            </n-button>
          </n-dropdown>
        </template>
        <n-button v-else type="primary" size="small" @click="copyFirstShareLink"><template #icon><n-icon :component="CopyOutline" /></template>复制链接</n-button>
      </div>
    </header>

    <main v-if="readOnlyMode" class="mobile-preview-area">
      <n-alert v-if="isMobile" type="info" :bordered="false">手机端后台仅支持方案预览和复制链接，请在桌面端编辑画布。</n-alert>
      <div class="phone-preview-frame">
        <InvitationDomRenderer :canvas="document.h5" :meeting="meetingSnapshot" :assets="assets" :share-url="firstEnabledShareLink?.shareUrl" :share-token="firstEnabledShareLink?.shareToken" :public-query-token="currentPublicQueryToken" registration-disabled actions-disabled />
      </div>
    </main>

    <main v-else class="designer-body">
      <aside class="left-panel">
        <div class="panel-tabs">
          <button v-for="tab in leftTabs" :key="tab.key" type="button" :class="{ active: leftTab === tab.key }" @click="leftTab = tab.key">{{ tab.label }}</button>
        </div>

        <div v-if="leftTab === 'elements'" class="palette-content">
          <p class="panel-section-title">基础元素</p>
          <div class="element-grid">
            <button v-for="item in visiblePaletteItems" :key="item.type" type="button" :disabled="item.requiresAsset && !assets.length" @click="addElement(item.type)">
              <n-icon :component="item.icon" />
              <span>{{ item.label }}</span>
            </button>
          </div>
          <n-alert v-if="!assets.length" type="default" :bordered="false" class="asset-hint">上传图片后可添加图片、视频和轮播组件。</n-alert>
        </div>

        <div v-else-if="leftTab === 'assets'" class="asset-content">
          <input ref="fileInput" class="hidden-file-input" type="file" accept="image/png,image/jpeg,image/webp,image/gif" multiple @change="handleFiles" />
          <n-button type="primary" block :loading="uploadingAsset" @click="fileInput?.click()"><template #icon><n-icon :component="CloudUploadOutline" /></template>上传图片素材</n-button>
          <div class="asset-grid">
            <button v-for="asset in assets" :key="asset.id" type="button" @click="addAssetImage(asset)">
              <img :src="asset.url" :alt="asset.originalName" />
              <span>{{ asset.originalName }}</span>
            </button>
          </div>
          <n-empty v-if="!assets.length" size="small" description="会议素材库为空" />
        </div>

        <div v-else class="layer-content">
          <p class="panel-section-title">图层 · {{ activeCanvas.elements.length }}/200</p>
          <div class="layer-list">
            <button v-for="element in reversedElements" :key="element.id" type="button" :class="{ active: selectedId === element.id }" @click="selectedId = element.id">
              <n-icon :component="elementIcon(element.type)" />
              <span>{{ elementLabel(element) }}</span>
              <n-icon class="lock-icon" :component="element.locked ? LockClosedOutline : LockOpenOutline" @click.stop="toggleLock(element)" />
            </button>
          </div>
        </div>
      </aside>

      <section class="canvas-workbench">
        <div class="canvas-scroll">
          <InvitationFabricCanvas
            ref="fabricCanvasRef"
            :canvas="activeCanvas"
            :meeting="meetingSnapshot"
            :assets="assets"
            :zoom="zoom"
            :selected-id="selectedId"
            :share-url="firstEnabledShareLink?.shareUrl"
            :share-token="firstEnabledShareLink?.shareToken"
            :public-query-token="currentPublicQueryToken"
            @select="selectedId = $event"
            @change="updateElement"
          />
        </div>
        <footer class="canvas-statusbar">
          <span>{{ canvasMode === "h5" ? `H5 · 375 × ${activeCanvas.height}` : "海报 · 1080 × 1920" }}</span>
          <span>{{ activeCanvas.elements.length }} 个元素</span>
          <span>领域文档 V1</span>
        </footer>
      </section>

      <aside class="right-panel">
        <div class="panel-tabs">
          <button v-for="tab in rightTabs" :key="tab.key" type="button" :class="{ active: rightTab === tab.key }" @click="rightTab = tab.key">{{ tab.label }}</button>
        </div>

        <div v-if="rightTab === 'design'" class="inspector-content">
          <template v-if="selectedElement">
            <div class="inspector-heading"><strong>{{ elementLabel(selectedElement) }}</strong><span>{{ selectedElement.type }}</span></div>
            <label v-if="selectedElement.type === 'IMAGE'">图片素材<n-select :value="Number(selectedElement.props.assetId) || null" :options="assetOptions" placeholder="选择会议素材" @update:value="updateProp('assetId', $event)" /></label>
            <label v-if="selectedElement.type === 'VIDEO'">视频封面<n-select :value="Number(selectedElement.props.coverAssetId) || null" :options="assetOptions" placeholder="选择上传的封面" @update:value="updateProp('coverAssetId', $event)" /></label>
            <label v-if="selectedElement.type === 'CAROUSEL'">轮播图片（最多 10 张）<n-select :value="carouselAssetIds" multiple :max-tag-count="3" :options="assetOptions" placeholder="选择会议素材" @update:value="updateCarouselAssets" /></label>
            <label v-if="supportsText(selectedElement)">内容<n-input :value="textValue(selectedElement)" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" @update:value="updateText" /></label>
            <label v-if="selectedElement.type === 'MEETING_FIELD'">会议字段<n-select :value="String(selectedElement.props.field || 'NAME')" :options="meetingFieldOptions" @update:value="updateMeetingField" /></label>
            <label v-if="selectedElement.type === 'BACKGROUND_MUSIC' || selectedElement.type === 'VIDEO'">HTTPS 直链<n-input :value="String(selectedElement.props.url || '')" placeholder="https://" @update:value="updateUrl" /></label>
            <label v-if="selectedElement.type === 'AMAP'">地址<n-input :value="String(selectedElement.props.address || '')" @update:value="updateProp('address', $event)" /></label>
            <div v-if="selectedElement.type === 'AMAP'" class="two-column-inputs">
              <label>经度<n-input-number :value="Number(selectedElement.props.longitude || 0)" :min="-180" :max="180" @update:value="updateProp('longitude', $event || 0)" /></label>
              <label>纬度<n-input-number :value="Number(selectedElement.props.latitude || 0)" :min="-90" :max="90" @update:value="updateProp('latitude', $event || 0)" /></label>
            </div>
            <n-alert v-if="selectedElement.type === 'AMAP' && !amapConfigured" type="warning" :bordered="false">当前构建未配置高德地图密钥，保存草稿不受影响，但发布会被阻止。</n-alert>
            <label v-if="selectedElement.type === 'QUERY_BUTTON'">查询功能<n-select :value="normalizeInvitationQueryTarget(selectedElement.props.queryTarget)" :options="INVITATION_QUERY_TARGET_OPTIONS" @update:value="updateQueryTarget" /></label>
            <n-alert v-if="selectedActionWarning" type="warning" :bordered="false">{{ selectedActionWarning }}</n-alert>
            <template v-if="selectedElement.type === 'CHANNEL_QR_CODE'">
              <label>二维码用途<n-select :value="normalizeInvitationQrPurpose(selectedElement.props.purpose)" :options="INVITATION_QR_PURPOSE_OPTIONS" @update:value="updateQrPurpose" /></label>
              <label>二维码说明<n-input :value="qrLabelValue" maxlength="60" show-count placeholder="清空后隐藏说明" @update:value="updateProp('label', $event)" /></label>
              <label>目标链接<n-input :value="selectedQrTargetText" readonly /></label>
            </template>
            <div v-if="selectedElement.type === 'CHANNEL_QR_CODE'" class="two-column-inputs">
              <label>二维码颜色<n-color-picker :value="String(selectedElement.props.foregroundColor || '#0b3425')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('foregroundColor', normalizeColor($event))" /></label>
              <label>二维码背景<n-color-picker :value="String(selectedElement.props.backgroundColor || '#ffffff')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('backgroundColor', normalizeColor($event))" /></label>
            </div>
            <label v-if="selectedElement.type === 'SHAPE'">形状类型<n-select :value="normalizeInvitationShape(selectedElement.props.shape)" :options="INVITATION_SHAPE_OPTIONS" @update:value="updateShapeKind" /></label>
            <div v-if="selectedElement.type === 'SHAPE'" class="two-column-inputs">
              <label>填充颜色<n-color-picker :value="String(selectedElement.props.fill || '#e7f3ee')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('fill', normalizeColor($event))" /></label>
              <label>边框颜色<n-color-picker :value="String(selectedElement.props.stroke || '#b9d3c7')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('stroke', normalizeColor($event))" /></label>
            </div>
            <label v-if="selectedElement.type === 'DIVIDER'">线条颜色<n-color-picker :value="String(selectedElement.props.color || '#cbded5')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('color', normalizeColor($event))" /></label>
            <div v-if="isActionButton(selectedElement)" class="two-column-inputs">
              <label>按钮颜色<n-color-picker :value="String(selectedElement.props.fill || '#08784f')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('fill', normalizeColor($event))" /></label>
              <label>文字颜色<n-color-picker :value="String(selectedElement.props.color || '#ffffff')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('color', normalizeColor($event))" /></label>
            </div>

            <div v-if="supportsTypography(selectedElement)" class="inspector-section">
              <p>文字样式</p>
              <label>字号<n-input-number :value="Number(selectedElement.props.fontSize || 16)" :min="8" :max="240" @update:value="updateProp('fontSize', $event || 16)" /></label>
              <label>字重<n-select :value="Number(selectedElement.props.fontWeight || 400)" :options="fontWeightOptions" @update:value="updateProp('fontWeight', $event)" /></label>
              <label v-if="!isActionButton(selectedElement)">颜色<n-color-picker :value="String(selectedElement.props.color || '#17261f')" :show-alpha="false" :modes="['hex']" @update:value="updateProp('color', normalizeColor($event))" /></label>
              <div v-if="!isActionButton(selectedElement)" class="inspector-field">
                <span>对齐</span>
                <n-radio-group
                  class="text-align-control"
                  aria-label="文字对齐"
                  :value="String(selectedElement.props.textAlign || 'left')"
                  size="small"
                  @update:value="updateProp('textAlign', $event)"
                >
                  <n-radio-button value="left">左</n-radio-button>
                  <n-radio-button value="center">中</n-radio-button>
                  <n-radio-button value="right">右</n-radio-button>
                </n-radio-group>
              </div>
            </div>

            <div class="inspector-section">
              <p>位置与大小</p>
              <div class="two-column-inputs">
                <label>X<n-input-number :value="selectedElement.x" :min="0" :max="activeCanvas.width - selectedElement.width" @update:value="updateGeometry('x', $event)" /></label>
                <label>Y<n-input-number :value="selectedElement.y" :min="0" :max="activeCanvas.height - selectedElement.height" @update:value="updateGeometry('y', $event)" /></label>
                <label>宽度<n-input-number :value="selectedElement.width" :min="1" :max="activeCanvas.width - selectedElement.x" @update:value="updateGeometry('width', $event)" /></label>
                <label>高度<n-input-number :value="selectedElement.height" :min="1" :max="activeCanvas.height - selectedElement.y" @update:value="updateGeometry('height', $event)" /></label>
                <label>旋转<n-input-number :value="selectedElement.rotation" :min="-360" :max="360" @update:value="updateGeometry('rotation', $event)" /></label>
                <label>透明度<n-input-number :value="Number(selectedElement.props.opacity ?? 1)" :min="0" :max="1" :step="0.1" @update:value="updateProp('opacity', $event ?? 1)" /></label>
              </div>
            </div>

            <div class="inspector-section layer-actions">
              <p>层级与操作</p>
              <n-button-group>
                <n-button size="small" aria-label="上移一层" @click="moveLayer(selectedElement, 1)"><template #icon><n-icon :component="ArrowUpOutline" /></template></n-button>
                <n-button size="small" aria-label="下移一层" @click="moveLayer(selectedElement, -1)"><template #icon><n-icon :component="ArrowDownOutline" /></template></n-button>
                <n-button size="small" aria-label="置顶" @click="moveLayer(selectedElement, 999)"><template #icon><n-icon :component="ArrowUpOutline" /></template></n-button>
                <n-button size="small" aria-label="置底" @click="moveLayer(selectedElement, -999)"><template #icon><n-icon :component="ArrowDownOutline" /></template></n-button>
              </n-button-group>
              <div class="action-row"><n-button size="small" secondary @click="duplicateElement(selectedElement)"><template #icon><n-icon :component="CopyOutline" /></template>复制</n-button><n-button size="small" secondary @click="toggleLock(selectedElement)"><template #icon><n-icon :component="selectedElement.locked ? LockOpenOutline : LockClosedOutline" /></template>{{ selectedElement.locked ? "解锁" : "锁定" }}</n-button><n-button size="small" type="error" secondary @click="deleteElement(selectedElement.id)"><template #icon><n-icon :component="TrashOutline" /></template>删除</n-button></div>
            </div>
          </template>
          <template v-else>
            <div class="inspector-heading"><strong>画布</strong><span>{{ canvasMode === "h5" ? "移动 H5" : "1080 × 1920 海报" }}</span></div>
            <label>背景颜色<n-color-picker :value="activeCanvas.backgroundColor" :show-alpha="false" :modes="['hex']" @update:value="updateCanvasColor" /></label>
            <label>背景素材<n-select :value="activeCanvas.backgroundImageAssetId" clearable :options="assetOptions" placeholder="不使用背景图片" @update:value="updateCanvasBackground" /></label>
            <label v-if="canvasMode === 'h5'">画布高度<n-input-number :value="activeCanvas.height" :min="667" :max="6000" :step="100" @update:value="updateCanvasHeight" /></label>
          </template>
        </div>

        <div v-else-if="rightTab === 'channels'" class="channel-panel-content">
          <div class="inspector-heading"><strong>发布渠道</strong><span>{{ shareLinks.length }} 个链接</span></div>
          <article v-for="link in shareLinks" :key="link.id" class="channel-mini-row">
            <span :class="{ enabled: link.enabled }" />
            <div><strong>{{ link.channelName }}</strong><small>{{ link.enabled ? `指向 V${link.currentVersionNo || '-'}` : "已停用" }}</small></div>
            <n-button quaternary size="tiny" aria-label="复制链接" @click="copyShareLink(link)"><template #icon><n-icon :component="CopyOutline" /></template></n-button>
          </article>
          <n-empty v-if="!shareLinks.length" size="small" description="发布方案后创建渠道链接" />
          <n-button block secondary @click="openChannelWorkspace">管理全部渠道</n-button>
        </div>

        <div v-else class="effect-content">
          <div class="inspector-heading"><strong>画布设置</strong><span>公开 H5 与海报</span></div>
          <label>H5 高度<n-input-number :value="document.h5.height" :min="667" :max="6000" :step="100" @update:value="updateDocumentH5Height" /></label>
          <div class="document-summary">
            <span><strong>{{ document.h5.elements.length }}</strong>H5 元素</span>
            <span><strong>{{ document.poster.elements.length }}</strong>海报元素</span>
            <span><strong>{{ documentSize }}</strong>文档大小</span>
          </div>
          <n-button block secondary @click="openExportDialog"><template #icon><n-icon :component="DownloadOutline" /></template>导出海报 PNG</n-button>
        </div>
      </aside>
    </main>

    <n-modal v-model:show="showPreview" preset="card" title="移动 H5 预览" class="preview-modal" style="width: 430px; max-width: calc(100vw - 20px)">
      <div class="preview-phone"><InvitationDomRenderer :canvas="document.h5" :meeting="meetingSnapshot" :assets="assets" :share-url="firstEnabledShareLink?.shareUrl" :share-token="firstEnabledShareLink?.shareToken" :public-query-token="currentPublicQueryToken" registration-disabled actions-disabled /></div>
    </n-modal>

    <n-modal v-model:show="showExport" preset="card" title="导出渠道海报" style="width: 520px">
      <n-alert v-if="invitation.status !== 'PUBLISHED'" type="warning" :bordered="false">当前方案未发布，将导出带“预览”标识的草稿样张。</n-alert>
      <n-form-item label="选择已启用渠道"><n-select v-model:value="exportLinkId" :options="exportableLinkOptions" placeholder="请选择渠道" /></n-form-item>
      <div class="export-preview">
        <InvitationFabricCanvas v-if="exportDocument" ref="exportCanvasRef" :canvas="exportDocument.poster" :meeting="exportMeetingSnapshot" :assets="assets" :share-url="exportShareLink?.shareUrl" :share-token="exportShareLink?.shareToken" :public-query-token="currentPublicQueryToken" :zoom="0.34" @ready="exportCanvasReady = true" />
      </div>
      <template #footer><n-space justify="end"><n-button @click="showExport = false">取消</n-button><n-button type="primary" :disabled="!exportCanvasReady || !exportLinkId" @click="downloadExportPoster">导出 1080×1920 PNG</n-button></n-space></template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch, type Component } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from "vue-router";
import axios from "axios";
import {
  AddOutline,
  AlbumsOutline,
  ArrowBackOutline,
  ArrowDownOutline,
  ArrowRedoOutline,
  ArrowUndoOutline,
  ArrowUpOutline,
  BrushOutline,
  ChevronDownOutline,
  CheckmarkCircleOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  CopyOutline,
  CropOutline,
  DownloadOutline,
  EaselOutline,
  EyeOutline,
  ImageOutline,
  LayersOutline,
  LinkOutline,
  LockClosedOutline,
  LockOpenOutline,
  MapOutline,
  MusicalNotesOutline,
  PlayCircleOutline,
  QrCodeOutline,
  RemoveOutline,
  ReorderTwoOutline,
  SearchOutline,
  ShapesOutline,
  TextOutline,
  TimeOutline,
  TrashOutline
} from "@vicons/ionicons5";
import {
  NButton,
  NButtonGroup,
  NColorPicker,
  NDropdown,
  NIcon,
  NInputNumber,
  NRadioButton,
  NRadioGroup,
  NResult,
  NSelect,
  useMessage,
  type DropdownOption
} from "naive-ui";
import InvitationFabricCanvas from "@/components/invitation/InvitationFabricCanvas.vue";
import InvitationDomRenderer from "@/components/invitation/InvitationDomRenderer.vue";
import {
  changeInvitationStatus,
  getInvitation,
  listInvitationAssets,
  listInvitationShareLinks,
  saveInvitationDraft,
  uploadInvitationAsset
} from "@/api/invitations";
import { extractErrorMessage } from "@/api/http";
import { useRouteMeeting } from "@/composables/useRouteMeeting";
import { useIsMobile } from "@/composables/useResponsive";
import { useMeetingStore } from "@/stores/meeting";
import type {
  InvitationAsset,
  InvitationCanvasV1,
  InvitationDesignDocumentV1,
  InvitationDetail,
  InvitationElementType,
  InvitationElementV1,
  InvitationMeetingSnapshot,
  InvitationQrPurpose,
  InvitationQueryTarget,
  InvitationShareLink
} from "@/types/invitation";
import { cloneInvitationDocument, createElementId, validateInvitationDocument } from "@/utils/invitationDocument";
import { INVITATION_SHAPE_OPTIONS, normalizeInvitationShape, type InvitationShapeKind } from "@/utils/invitationShape";
import { isInvitationShareLinkAvailable } from "@/utils/invitationShareLink";
import {
  INVITATION_QR_PURPOSE_OPTIONS,
  INVITATION_QUERY_TARGET_OPTIONS,
  invitationDocumentRequiresPublicQueryToken,
  invitationQrDefaultLabel,
  invitationQrLabel,
  invitationQueryButtonDefaultText,
  isInvitationQueryTarget,
  normalizeInvitationQrPurpose,
  normalizeInvitationQueryTarget,
  resolveInvitationQrTarget
} from "@/utils/invitationActions";

type CanvasMode = "h5" | "poster";
type LeftTab = "elements" | "assets" | "layers";
type RightTab = "design" | "channels" | "effects";
type FabricCanvasExpose = { exportPng: () => string; selectAll: () => void; discardSelection: () => void };

const route = useRoute();
const router = useRouter();
const message = useMessage();
const meetingStore = useMeetingStore();
const isMobile = useIsMobile();
const { routeMeetingId: meetingId, syncMeetingFromRoute } = useRouteMeeting({ autoSync: false, loadWorkspace: false });
const invitationId = computed(() => String(route.params.invitationId || ""));
const invitation = ref<InvitationDetail | null>(null);
const document = ref<InvitationDesignDocumentV1 | null>(null);
const assets = ref<InvitationAsset[]>([]);
const shareLinks = ref<InvitationShareLink[]>([]);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const publishing = ref(false);
const dirty = ref(false);
const conflict = ref(false);
const lastSavedJson = ref("");
const canvasMode = ref<CanvasMode>("h5");
const leftTab = ref<LeftTab>("elements");
const rightTab = ref<RightTab>("design");
const selectedId = ref("");
const zoomByMode = reactive<Record<CanvasMode, number>>({ h5: 0.82, poster: 0.34 });
const showPreview = ref(route.query.preview === "1");
const showExport = ref(false);
const exportLinkId = ref<number | null>(null);
const exportDocument = ref<InvitationDesignDocumentV1 | null>(null);
const exportMeetingSnapshot = ref<Partial<InvitationMeetingSnapshot>>({});
const uploadingAsset = ref(false);
const fileInput = ref<HTMLInputElement>();
const fabricCanvasRef = ref<FabricCanvasExpose>();
const exportCanvasRef = ref<FabricCanvasExpose>();
const exportCanvasReady = ref(false);
const history = ref<string[]>([]);
const historyIndex = ref(-1);
let historyTimer: number | undefined;
let autosaveTimer: number | undefined;
let savePromise: Promise<void> | null = null;
let hydrating = true;
let restoringHistory = false;
let designerMounted = false;
let loadGeneration = 0;

const NColorPickerAsync = NColorPicker || defineAsyncComponent(() => import("naive-ui/es/color-picker").then((module) => module.NColorPicker));
void NColorPickerAsync;

const readOnlyMode = computed(() => isMobile.value || route.query.preview === "1" || invitation.value?.status === "ARCHIVED");
const activeCanvas = computed<InvitationCanvasV1>(() => document.value![canvasMode.value]);
const zoom = computed(() => zoomByMode[canvasMode.value]);
const selectedElement = computed(() => activeCanvas.value.elements.find((item) => item.id === selectedId.value) || null);
const reversedElements = computed(() => [...activeCanvas.value.elements].sort((a, b) => b.zIndex - a.zIndex));
const invitationPublicationState = computed(() => invitation.value ? [{ id: invitation.value.id, status: invitation.value.status }] : []);
const firstEnabledShareLink = computed(() => shareLinks.value.find((item) => isInvitationShareLinkAvailable(item, invitationPublicationState.value)));
const exportableShareLinks = computed(() => shareLinks.value.filter((item) => isExportChannelAvailable(item)));
const exportableLinkOptions = computed(() => exportableShareLinks.value.map((item) => ({
  label: item.currentVersionNo ? `${item.channelName} · V${item.currentVersionNo}` : `${item.channelName} · 尚未发布`,
  value: item.id
})));
const assetOptions = computed(() => assets.value.map((asset) => ({ label: asset.originalName, value: asset.id })));
const carouselAssetIds = computed(() => {
  const value = selectedElement.value?.props.assetIds;
  return Array.isArray(value) ? value.map(Number).filter(Number.isFinite).slice(0, 10) : [];
});
const exportShareLink = computed(() => exportableShareLinks.value.find((item) => item.id === exportLinkId.value));
const currentPublicQueryToken = computed(() => meetingStore.currentMeeting?.publicQueryToken || "");
const qrLabelValue = computed(() => selectedElement.value?.type === "CHANNEL_QR_CODE" ? invitationQrLabel(selectedElement.value) : "");
const selectedQrTarget = computed(() => selectedElement.value?.type === "CHANNEL_QR_CODE"
  ? resolveInvitationQrTarget(selectedElement.value, {
      meetingCode: meetingSnapshot.value.code,
      publicQueryToken: currentPublicQueryToken.value,
      shareToken: firstEnabledShareLink.value?.shareToken,
      shareUrl: firstEnabledShareLink.value?.shareUrl
    })
  : { url: "", unavailableReason: "" });
const selectedQrTargetText = computed(() => selectedQrTarget.value.url || selectedQrTarget.value.unavailableReason);
const selectedActionWarning = computed(() => {
  const element = selectedElement.value;
  const meeting = meetingStore.currentMeeting;
  if (!element || !meeting) return "";
  if ((element.type === "CHECKIN_BUTTON" || element.type === "QUERY_BUTTON") && !currentPublicQueryToken.value) {
    return "会议未配置公开查询令牌，草稿可保存，但不能发布。";
  }
  if (element.type === "CHECKIN_BUTTON" && !meeting.selfCheckinEnabled) return "当前会议尚未开放独立自助签到。";
  if (element.type !== "QUERY_BUTTON") return "";
  const target = normalizeInvitationQueryTarget(element.props.queryTarget);
  if (target === "SEAT_QUERY" && !meeting.seatQueryEnabled) return "当前会议尚未开放座位查询。";
  if (target === "ROOM_QUERY" && !meeting.roomQueryEnabled) return "当前会议尚未开放住宿查询。";
  if (target === "DINNER_QUERY" && !meeting.dinnerQueryEnabled) return "当前会议尚未开放晚宴查询。";
  return "";
});
const amapConfigured = computed(() => Boolean(import.meta.env.VITE_AMAP_KEY));
const canUndo = computed(() => historyIndex.value > 0);
const canRedo = computed(() => historyIndex.value >= 0 && historyIndex.value < history.value.length - 1);
const documentSize = computed(() => document.value ? `${Math.ceil(new TextEncoder().encode(JSON.stringify(document.value)).byteLength / 1024)} KiB` : "0 KiB");
const saveStateText = computed(() => conflict.value ? "版本冲突，请刷新" : saving.value ? "保存中…" : dirty.value ? "有未保存更改" : "已自动保存");
const saveStateClass = computed(() => ({ dirty: dirty.value, conflict: conflict.value }));

const leftTabs = [{ key: "elements" as const, label: "元素" }, { key: "assets" as const, label: "素材" }, { key: "layers" as const, label: "图层" }];
const rightTabs = [{ key: "design" as const, label: "设计" }, { key: "channels" as const, label: "渠道" }, { key: "effects" as const, label: "画布" }];
const paletteItems: Array<{ type: InvitationElementType; label: string; icon: Component; poster: boolean; requiresAsset?: boolean }> = [
  { type: "TEXT", label: "文本", icon: TextOutline, poster: true },
  { type: "IMAGE", label: "图片", icon: ImageOutline, poster: true, requiresAsset: true },
  { type: "SHAPE", label: "形状", icon: ShapesOutline, poster: true },
  { type: "DIVIDER", label: "分隔线", icon: ReorderTwoOutline, poster: true },
  { type: "MEETING_FIELD", label: "会议字段", icon: CodeSlashOutline, poster: true },
  { type: "REGISTRATION_BUTTON", label: "报名按钮", icon: CropOutline, poster: true },
  { type: "CHECKIN_BUTTON", label: "签到按钮", icon: CheckmarkCircleOutline, poster: false },
  { type: "QUERY_BUTTON", label: "查询按钮", icon: SearchOutline, poster: false },
  { type: "CHANNEL_QR_CODE", label: "二维码", icon: QrCodeOutline, poster: true },
  { type: "COUNTDOWN", label: "倒计时", icon: TimeOutline, poster: true },
  { type: "BACKGROUND_MUSIC", label: "背景音乐", icon: MusicalNotesOutline, poster: false },
  { type: "VIDEO", label: "视频", icon: PlayCircleOutline, poster: false, requiresAsset: true },
  { type: "CAROUSEL", label: "轮播", icon: AlbumsOutline, poster: false, requiresAsset: true },
  { type: "AMAP", label: "地图", icon: MapOutline, poster: false }
];
const visiblePaletteItems = computed(() => paletteItems.filter((item) => canvasMode.value === "h5" || item.poster));
const meetingFieldOptions = [
  ["会议名称", "NAME"], ["主办方", "ORGANIZER"], ["协办方", "CO_ORGANIZER"], ["开始时间", "START_TIME"], ["结束时间", "END_TIME"], ["详细地址", "LOCATION"], ["场馆名称", "VENUE_NAME"], ["会议简介", "DESCRIPTION"], ["会议议程", "AGENDA"], ["会议封面", "COVER_IMAGE"], ["联系人", "CONTACT_NAME"], ["联系电话", "CONTACT_PHONE"]
].map(([label, value]) => ({ label, value }));
const fontWeightOptions = [{ label: "常规 400", value: 400 }, { label: "中等 500", value: 500 }, { label: "半粗 600", value: 600 }, { label: "粗体 700", value: 700 }];
const publishMenuOptions = computed<DropdownOption[]>(() => {
  const options: DropdownOption[] = [{ label: invitation.value?.status === "PUBLISHED" ? "重新发布当前草稿" : "发布当前方案", key: "publish" }, { label: "导出海报 PNG", key: "export" }];
  if (invitation.value?.status === "PUBLISHED") options.push({ type: "divider", key: "divider" }, { label: "下线公开页面", key: "unpublish" });
  if (invitation.value?.status !== "ARCHIVED") options.push({ label: "归档方案", key: "archive" });
  return options;
});

watch(canvasMode, () => { selectedId.value = ""; });

watch(document, () => {
  if (hydrating || restoringHistory || !document.value) return;
  const json = JSON.stringify(document.value);
  dirty.value = json !== lastSavedJson.value;
  scheduleHistory(json);
  scheduleAutosave();
}, { deep: true });

watch([meetingId, invitationId], async ([nextMeetingId, nextInvitationId], [previousMeetingId, previousInvitationId]) => {
  if (!designerMounted || !nextMeetingId || !nextInvitationId || (nextMeetingId === previousMeetingId && nextInvitationId === previousInvitationId)) return;
  if (nextMeetingId !== previousMeetingId && !await syncMeetingFromRoute()) return;
  if (!isCurrentRouteIdentity(nextMeetingId, nextInvitationId)) return;
  await loadDesigner(nextMeetingId, nextInvitationId);
});

watch(exportLinkId, () => {
  if (showExport.value) exportCanvasReady.value = false;
});

onMounted(async () => {
  const meeting = await syncMeetingFromRoute();
  if (meeting) await loadDesigner();
  else loading.value = false;
  designerMounted = true;
  window.addEventListener("keydown", handleShortcut);
  window.addEventListener("beforeunload", handleBeforeUnload);
});

onBeforeUnmount(() => {
  loadGeneration += 1;
  if (historyTimer) window.clearTimeout(historyTimer);
  clearAutosaveTimer();
  window.removeEventListener("keydown", handleShortcut);
  window.removeEventListener("beforeunload", handleBeforeUnload);
});

onBeforeRouteLeave(async () => {
  if (await flushPendingDraft()) return true;
  showUnsavedNavigationError();
  return false;
});

onBeforeRouteUpdate(async (to, from) => {
  const nextMeetingId = String(to.params.meetingId || "");
  const nextInvitationId = String(to.params.invitationId || "");
  const previousMeetingId = String(from.params.meetingId || "");
  const previousInvitationId = String(from.params.invitationId || "");
  if (nextMeetingId === previousMeetingId && nextInvitationId === previousInvitationId) return true;
  if (await flushPendingDraft()) return true;
  showUnsavedNavigationError();
  return false;
});

async function loadDesigner(targetMeetingId = meetingId.value, targetInvitationId = invitationId.value) {
  if (!isCurrentRouteIdentity(targetMeetingId, targetInvitationId)) return;
  const generation = ++loadGeneration;
  loading.value = true;
  loadError.value = "";
  conflict.value = false;
  hydrating = true;
  try {
    const [detail, assetList, links] = await Promise.all([
      getInvitation(targetMeetingId, targetInvitationId),
      listInvitationAssets(targetMeetingId),
      listInvitationShareLinks(targetMeetingId, targetInvitationId)
    ]);
    if (!isCurrentLoad(generation, targetMeetingId, targetInvitationId)) return;
    invitation.value = detail;
    document.value = cloneInvitationDocument(detail.draftDocument);
    assets.value = assetList;
    shareLinks.value = links;
    conflict.value = false;
    selectedId.value = "";
    exportLinkId.value = null;
    showExport.value = false;
    lastSavedJson.value = JSON.stringify(document.value);
    history.value = [lastSavedJson.value];
    historyIndex.value = 0;
    dirty.value = false;
  } catch (error) {
    if (isCurrentLoad(generation, targetMeetingId, targetInvitationId)) loadError.value = extractErrorMessage(error, "邀请方案读取失败");
  } finally {
    if (isCurrentLoad(generation, targetMeetingId, targetInvitationId)) {
      hydrating = false;
      loading.value = false;
    }
  }
}

function scheduleAutosave() {
  if (invitation.value?.status === "ARCHIVED" || conflict.value) return;
  clearAutosaveTimer();
  autosaveTimer = window.setTimeout(() => void saveDraft(false), 1400);
}

function clearAutosaveTimer() {
  if (autosaveTimer) window.clearTimeout(autosaveTimer);
  autosaveTimer = undefined;
}

async function flushPendingDraft() {
  let previousSavedJson: string | null = null;
  do {
    clearAutosaveTimer();
    previousSavedJson = lastSavedJson.value;
    await saveDraft(false);
    clearAutosaveTimer();
    // 保存失败时 lastSavedJson 不会前进，避免在离页守卫中无限重试。
    if (dirty.value && lastSavedJson.value === previousSavedJson) break;
  } while (dirty.value && !conflict.value);
  return !dirty.value && !conflict.value;
}

function showUnsavedNavigationError() {
  message.error(conflict.value ? "草稿存在版本冲突，请刷新后再离开" : "草稿尚未保存，请稍后重试");
}

function isCurrentRouteIdentity(targetMeetingId: string, targetInvitationId: string) {
  return meetingId.value === targetMeetingId && invitationId.value === targetInvitationId;
}

function isCurrentLoad(generation: number, targetMeetingId: string, targetInvitationId: string) {
  return generation === loadGeneration && isCurrentRouteIdentity(targetMeetingId, targetInvitationId);
}

async function saveDraft(notify: boolean) {
  if (!invitation.value || !document.value || conflict.value) return;
  while (savePromise) await savePromise;
  if (!invitation.value || !document.value || conflict.value) return;
  const json = JSON.stringify(document.value);
  if (json === lastSavedJson.value) { if (notify) message.success("草稿已是最新"); return; }
  const currentInvitationId = invitation.value.id;
  const currentMeetingId = meetingId.value;
  const currentRouteInvitationId = invitationId.value;
  const currentRevision = invitation.value.draftRevision;
  const currentName = invitation.value.name;
  const currentDocument = cloneInvitationDocument(document.value);
  const run = (async () => {
    saving.value = true;
    try {
      const updated = await saveInvitationDraft(currentMeetingId, currentInvitationId, { expectedRevision: currentRevision, name: currentName, document: currentDocument });
      if (!isCurrentRouteIdentity(currentMeetingId, currentRouteInvitationId) || invitation.value?.id !== currentInvitationId) return;
      invitation.value = updated;
      lastSavedJson.value = json;
      dirty.value = JSON.stringify(document.value) !== json;
      if (notify) message.success("草稿已保存");
    } catch (error) {
      if (!isCurrentRouteIdentity(currentMeetingId, currentRouteInvitationId) || invitation.value?.id !== currentInvitationId) return;
      if (axios.isAxiosError(error) && error.response?.status === 409) { conflict.value = true; message.error("草稿已在其他窗口更新，请刷新后再编辑"); }
      else if (notify) message.error(extractErrorMessage(error, "草稿保存失败"));
    } finally {
      saving.value = false;
    }
  })();
  savePromise = run;
  try {
    await run;
  } finally {
    if (savePromise === run) savePromise = null;
    if (dirty.value && !conflict.value) scheduleAutosave();
  }
}

async function publish() {
  if (!invitation.value || !document.value) return;
  const errors = validateInvitationDocument(document.value, amapConfigured.value);
  if (invitationDocumentRequiresPublicQueryToken(document.value) && !currentPublicQueryToken.value) {
    errors.unshift("会议未配置公开查询令牌，不能发布查询或签到入口");
  }
  if (errors.length) { message.error(errors[0]!); return; }
  publishing.value = true;
  try {
    if (!await flushPendingDraft()) throw new Error("草稿尚未成功保存，无法发布");
    const currentMeetingId = meetingId.value;
    const currentRouteInvitationId = invitationId.value;
    const currentInvitationId = invitation.value.id;
    const updated = await changeInvitationStatus(currentMeetingId, currentInvitationId, "publish");
    if (!isCurrentRouteIdentity(currentMeetingId, currentRouteInvitationId) || invitation.value?.id !== currentInvitationId) return;
    invitation.value = updated;
    const links = await listInvitationShareLinks(currentMeetingId, currentInvitationId);
    if (!isCurrentRouteIdentity(currentMeetingId, currentRouteInvitationId) || invitation.value?.id !== currentInvitationId) return;
    shareLinks.value = links;
    message.success("方案已发布，现有渠道链接已指向最新版本");
  } catch (error) { message.error(extractErrorMessage(error, "发布失败")); }
  finally { publishing.value = false; }
}

async function handlePublishMenu(key: string | number) {
  if (key === "publish") { await publish(); return; }
  if (key === "export") { openExportDialog(); return; }
  if ((key === "unpublish" || key === "archive") && invitation.value) {
    publishing.value = true;
    try {
      if (!await flushPendingDraft()) throw new Error("草稿尚未成功保存，无法更新方案状态");
      const currentMeetingId = meetingId.value;
      const currentRouteInvitationId = invitationId.value;
      const currentInvitationId = invitation.value.id;
      const updated = await changeInvitationStatus(currentMeetingId, currentInvitationId, key);
      if (!isCurrentRouteIdentity(currentMeetingId, currentRouteInvitationId) || invitation.value?.id !== currentInvitationId) return;
      invitation.value = updated;
      message.success(key === "archive" ? "方案已归档" : "公开页面已下线");
    }
    catch (error) { message.error(extractErrorMessage(error, "状态更新失败")); }
    finally { publishing.value = false; }
  }
}

function addElement(type: InvitationElementType) {
  if (activeCanvas.value.elements.length >= 200) { message.warning("每个画布最多 200 个元素"); return; }
  const maxZ = Math.max(0, ...activeCanvas.value.elements.map((item) => item.zIndex));
  const position = nextElementPosition();
  const asset = assets.value[0];
  const definitions: Record<InvitationElementType, Pick<InvitationElementV1, "width" | "height" | "props">> = {
    TEXT: { width: 260, height: 54, props: { text: "双击编辑文字", fontSize: canvasMode.value === "poster" ? 48 : 24, fontWeight: 600, color: "#17261f", textAlign: "left", lineHeight: 1.25 } },
    IMAGE: { width: canvasMode.value === "poster" ? 720 : 300, height: canvasMode.value === "poster" ? 440 : 180, props: { assetId: asset?.id || 0, objectFit: "cover", borderRadius: 4, alt: asset?.originalName || "会议图片" } },
    SHAPE: { width: canvasMode.value === "poster" ? 320 : 120, height: canvasMode.value === "poster" ? 160 : 80, props: { shape: "rounded-rectangle", fill: "#e7f3ee", stroke: "#b9d3c7", strokeWidth: 1, borderRadius: 12 } },
    DIVIDER: { width: canvasMode.value === "poster" ? 760 : 300, height: 1, props: { color: "#cbded5", thickness: 1, style: "solid" } },
    MEETING_FIELD: { width: canvasMode.value === "poster" ? 760 : 300, height: canvasMode.value === "poster" ? 80 : 42, props: { field: "NAME", labelPrefix: "", fontSize: canvasMode.value === "poster" ? 48 : 22, fontWeight: 600, color: "#173b2d", textAlign: "left", lineHeight: 1.3 } },
    REGISTRATION_BUTTON: { width: canvasMode.value === "poster" ? 760 : 300, height: canvasMode.value === "poster" ? 120 : 52, props: { text: "立即报名", fill: "#08784f", color: "#ffffff", borderRadius: 6, fontSize: canvasMode.value === "poster" ? 42 : 19, fontWeight: 700 } },
    CHECKIN_BUTTON: { width: 300, height: 52, props: { text: "立即签到", fill: "#166b8f", color: "#ffffff", borderRadius: 6, fontSize: 19, fontWeight: 700 } },
    QUERY_BUTTON: { width: 300, height: 52, props: { text: "立即查询", queryTarget: "COMBINED_QUERY", fill: "#405f52", color: "#ffffff", borderRadius: 6, fontSize: 19, fontWeight: 700 } },
    CHANNEL_QR_CODE: { width: canvasMode.value === "poster" ? 280 : 180, height: canvasMode.value === "poster" ? 320 : 205, props: { purpose: "REGISTRATION", foregroundColor: "#0b3425", backgroundColor: "#ffffff", label: "扫码报名" } },
    COUNTDOWN: { width: canvasMode.value === "poster" ? 760 : 335, height: canvasMode.value === "poster" ? 120 : 72, props: { target: meetingSnapshot.value.startTime || new Date(Date.now() + 86400000).toISOString(), format: "DD天 HH时 mm分 ss秒", fontSize: canvasMode.value === "poster" ? 36 : 20, fontFamily: "Inter", fontWeight: 700, color: "#08784f" } },
    BACKGROUND_MUSIC: { width: 145, height: 42, props: { url: "https://example.com/meeting-music.mp3", title: "大会背景音乐" } },
    VIDEO: { width: 335, height: 200, props: { url: "https://example.com/meeting-video.mp4", coverAssetId: asset?.id || 0, controls: true } },
    CAROUSEL: { width: 335, height: 210, props: { assetIds: asset ? [asset.id] : [], intervalMs: 4000, autoplay: true, objectFit: "cover" } },
    AMAP: { width: 335, height: 220, props: { longitude: 121.49, latitude: 31.24, address: meetingSnapshot.value.venueName || meetingSnapshot.value.location || "会议地点", zoom: 15, navigationUrl: "https://uri.amap.com/marker?position=121.49,31.24" } }
  };
  const definition = definitions[type];
  const width = Math.min(definition.width, activeCanvas.value.width);
  const height = Math.min(definition.height, activeCanvas.value.height);
  const next: InvitationElementV1 = { id: createElementId(type.toLowerCase()), type, x: Math.min(position.x, activeCanvas.value.width - width), y: Math.min(position.y, activeCanvas.value.height - height), width, height, rotation: 0, zIndex: maxZ + 1, locked: false, props: { ...definition.props } };
  activeCanvas.value.elements.push(next);
  selectedId.value = next.id;
  rightTab.value = "design";
}

function addAssetImage(asset: InvitationAsset) {
  const previousCount = activeCanvas.value.elements.length;
  addElement("IMAGE");
  const added = activeCanvas.value.elements[previousCount];
  if (added) { added.props.assetId = asset.id; added.props.alt = asset.originalName; }
}

async function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  if (!files.length) return;
  uploadingAsset.value = true;
  try {
    for (const file of files) assets.value.push(await uploadInvitationAsset(meetingId.value, file));
    message.success(`已上传 ${files.length} 个素材`);
  } catch (error) { message.error(extractErrorMessage(error, "素材上传失败")); }
  finally { uploadingAsset.value = false; input.value = ""; }
}

function updateElement(next: InvitationElementV1) {
  const index = activeCanvas.value.elements.findIndex((item) => item.id === next.id);
  if (index >= 0) activeCanvas.value.elements[index] = next;
}

function updateText(value: string) {
  if (!selectedElement.value) return;
  updateProp("text", value);
}

function updateQueryTarget(value: InvitationQueryTarget) {
  const element = selectedElement.value;
  if (!element || element.type !== "QUERY_BUTTON" || !isInvitationQueryTarget(value)) return;
  const previous = normalizeInvitationQueryTarget(element.props.queryTarget);
  const currentText = String(element.props.text || "");
  if (!currentText || currentText === invitationQueryButtonDefaultText(previous)) {
    element.props.text = invitationQueryButtonDefaultText(value);
  }
  element.props.queryTarget = value;
}

function updateQrPurpose(value: InvitationQrPurpose) {
  const element = selectedElement.value;
  if (!element || element.type !== "CHANNEL_QR_CODE") return;
  const previous = normalizeInvitationQrPurpose(element.props.purpose);
  const currentLabel = invitationQrLabel(element);
  if (!currentLabel || currentLabel === invitationQrDefaultLabel(previous)
      || (element.props.purpose == null && currentLabel === "扫码报名")) {
    element.props.label = invitationQrDefaultLabel(value);
  }
  element.props.purpose = value;
}

function updateMeetingField(value: string) { updateProp("field", value); }
function updateUrl(value: string) { updateProp("url", value); }
function updateCarouselAssets(value: number[]) { if (selectedElement.value) selectedElement.value.props.assetIds = value.slice(0, 10); }
function updateProp(key: string, value: unknown) { if (selectedElement.value && ["string", "number", "boolean"].includes(typeof value)) selectedElement.value.props[key] = value as string | number | boolean; }
function updateShapeKind(value: InvitationShapeKind) {
  if (!selectedElement.value || selectedElement.value.type !== "SHAPE") return;
  const shape = normalizeInvitationShape(value);
  selectedElement.value.props.shape = shape;
  if (shape === "rounded-rectangle") {
    selectedElement.value.props.borderRadius = Math.max(8, Math.round(Math.min(selectedElement.value.width, selectedElement.value.height) * 0.18));
  } else if (shape === "rectangle") {
    selectedElement.value.props.borderRadius = 0;
  }
}
function updateGeometry(key: "x" | "y" | "width" | "height" | "rotation", value: number | null) { if (selectedElement.value && value != null) selectedElement.value[key] = value; }
function updateCanvasColor(value: string) { activeCanvas.value.backgroundColor = normalizeColor(value); }
function updateCanvasBackground(value: number | null) { activeCanvas.value.backgroundImageAssetId = value || null; }
function updateCanvasHeight(value: number | null) { if (value != null && canvasMode.value === "h5") activeCanvas.value.height = Math.max(667, Math.min(6000, value)); }
function updateDocumentH5Height(value: number | null) { if (value != null && document.value) document.value.h5.height = Math.max(667, Math.min(6000, value)); }

function duplicateElement(element: InvitationElementV1) {
  const copy = cloneElement(element);
  copy.id = createElementId(element.type.toLowerCase());
  copy.x = Math.min(activeCanvas.value.width - copy.width, copy.x + 12);
  copy.y = Math.min(activeCanvas.value.height - copy.height, copy.y + 12);
  copy.zIndex = Math.max(0, ...activeCanvas.value.elements.map((item) => item.zIndex)) + 1;
  activeCanvas.value.elements.push(copy);
  selectedId.value = copy.id;
}

function deleteElement(id: string) {
  const index = activeCanvas.value.elements.findIndex((item) => item.id === id);
  if (index >= 0) activeCanvas.value.elements.splice(index, 1);
  selectedId.value = "";
}

function toggleLock(element: InvitationElementV1) { element.locked = !element.locked; }

function moveLayer(element: InvitationElementV1, direction: number) {
  const ordered = [...activeCanvas.value.elements].sort((a, b) => a.zIndex - b.zIndex);
  const index = ordered.findIndex((item) => item.id === element.id);
  const targetIndex = direction > 100 ? ordered.length - 1 : direction < -100 ? 0 : Math.max(0, Math.min(ordered.length - 1, index + direction));
  ordered.splice(index, 1);
  ordered.splice(targetIndex, 0, element);
  ordered.forEach((item, position) => { item.zIndex = position + 1; });
}

function scheduleHistory(json: string) {
  if (historyTimer) window.clearTimeout(historyTimer);
  historyTimer = window.setTimeout(() => {
    if (history.value[historyIndex.value] === json) return;
    history.value = history.value.slice(0, historyIndex.value + 1);
    history.value.push(json);
    if (history.value.length > 60) history.value.shift();
    historyIndex.value = history.value.length - 1;
  }, 280);
}

function undo() { if (canUndo.value) restoreHistory(historyIndex.value - 1); }
function redo() { if (canRedo.value) restoreHistory(historyIndex.value + 1); }
function restoreHistory(index: number) { if (!history.value[index]) return; restoringHistory = true; document.value = JSON.parse(history.value[index]!) as InvitationDesignDocumentV1; historyIndex.value = index; selectedId.value = ""; restoringHistory = false; dirty.value = JSON.stringify(document.value) !== lastSavedJson.value; scheduleAutosave(); }

function handleShortcut(event: KeyboardEvent) {
  if (readOnlyMode.value || event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
  const command = event.metaKey || event.ctrlKey;
  if (command && event.key.toLowerCase() === "z") { event.preventDefault(); event.shiftKey ? redo() : undo(); }
  else if (command && event.key.toLowerCase() === "a") { event.preventDefault(); fabricCanvasRef.value?.selectAll(); }
  else if (command && event.key.toLowerCase() === "d" && selectedElement.value) { event.preventDefault(); duplicateElement(selectedElement.value); }
  else if ((event.key === "Delete" || event.key === "Backspace") && selectedElement.value) { event.preventDefault(); deleteElement(selectedElement.value.id); }
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return;
  event.preventDefault();
  event.returnValue = "";
}

function openExportDialog() {
  if (!document.value || !invitation.value) return;
  exportCanvasReady.value = false;
  exportLinkId.value = shareLinks.value.find((item) => isExportChannelAvailable(item))?.id ?? null;
  if (invitation.value.status === "PUBLISHED" && invitation.value.latestPublishedVersion?.designDocument) {
    exportDocument.value = cloneInvitationDocument(invitation.value.latestPublishedVersion.designDocument);
    exportMeetingSnapshot.value = invitation.value.latestPublishedVersion.meetingSnapshot || meetingSnapshot.value;
  } else {
    exportDocument.value = cloneInvitationDocument(document.value);
    exportMeetingSnapshot.value = meetingSnapshot.value;
  }
  showExport.value = true;
}

async function downloadExportPoster() {
  if (!exportCanvasRef.value || !invitation.value) return;
  if (!exportCanvasReady.value) { message.warning("海报仍在生成，请稍后再试"); return; }
  const selectedLink = shareLinks.value.find((item) => item.id === exportLinkId.value && isExportChannelAvailable(item));
  if (!selectedLink) { message.warning("请选择一个已启用且未过期的渠道"); return; }
  const dataUrl = exportCanvasRef.value.exportPng();
  const finalUrl = invitation.value.status === "PUBLISHED" ? dataUrl : await addPreviewWatermark(dataUrl);
  const suffix = invitation.value.status === "PUBLISHED" ? selectedLink.channelName : `${selectedLink.channelName}-预览`;
  downloadDataUrl(finalUrl, `${invitation.value.name}-${suffix}.png`);
}

function isExportChannelAvailable(link: InvitationShareLink) {
  if (!invitation.value || link.invitationId !== invitation.value.id) return false;
  if (!link.enabled) return false;
  if (!link.expiresAt) return true;
  const expiresAt = new Date(link.expiresAt).getTime();
  return Number.isFinite(expiresAt) && expiresAt > Date.now();
}

async function addPreviewWatermark(dataUrl: string) {
  const image = new Image();
  image.src = dataUrl;
  await image.decode();
  const canvas = globalThis.document.createElement("canvas");
  canvas.width = 1080; canvas.height = 1920;
  const context = canvas.getContext("2d");
  if (!context) return dataUrl;
  context.drawImage(image, 0, 0);
  context.save();
  context.translate(540, 960); context.rotate(-Math.PI / 5);
  context.font = "700 150px Inter, sans-serif"; context.textAlign = "center"; context.fillStyle = "rgba(205, 41, 41, .28)";
  context.fillText("预览", 0, 0); context.restore();
  return canvas.toDataURL("image/png");
}

async function copyFirstShareLink() { if (firstEnabledShareLink.value) await copyShareLink(firstEnabledShareLink.value); else message.warning("当前方案没有已启用渠道链接"); }
async function copyShareLink(link: InvitationShareLink) { await navigator.clipboard.writeText(link.shareUrl); message.success("渠道链接已复制"); }
function openChannelWorkspace() { void router.push({ name: "meeting-invitations", params: { meetingId: meetingId.value }, query: { tab: "channels" } }); }
function goBack() { void router.push({ name: "meeting-invitations", params: { meetingId: meetingId.value } }); }
function increaseZoom() { zoomByMode[canvasMode.value] = Math.min(canvasMode.value === "h5" ? 1.5 : 0.7, zoomByMode[canvasMode.value] + (canvasMode.value === "h5" ? .1 : .05)); }
function decreaseZoom() { zoomByMode[canvasMode.value] = Math.max(canvasMode.value === "h5" ? .4 : .15, zoomByMode[canvasMode.value] - (canvasMode.value === "h5" ? .1 : .05)); }
function isActionButton(element: InvitationElementV1) { return ["REGISTRATION_BUTTON", "CHECKIN_BUTTON", "QUERY_BUTTON"].includes(element.type); }
function supportsText(element: InvitationElementV1) { return element.type === "TEXT" || isActionButton(element); }
function supportsTypography(element: InvitationElementV1) { return ["TEXT", "MEETING_FIELD", "REGISTRATION_BUTTON", "CHECKIN_BUTTON", "QUERY_BUTTON", "COUNTDOWN"].includes(element.type); }
function textValue(element: InvitationElementV1) { return String(element.props.text || ""); }
function normalizeColor(value: string) { return /^#[0-9a-f]{6}$/i.test(value) ? value : value.slice(0, 7); }
function cloneElement(element: InvitationElementV1) { return JSON.parse(JSON.stringify(element)) as InvitationElementV1; }
function nextElementPosition() { const offset = activeCanvas.value.elements.length % 8 * 12; return { x: canvasMode.value === "poster" ? 120 + offset : 20 + offset, y: canvasMode.value === "poster" ? 120 + offset : 40 + offset }; }
function downloadDataUrl(url: string, filename: string) { const anchor = globalThis.document.createElement("a"); anchor.href = url; anchor.download = filename; anchor.click(); }

const meetingSnapshot = computed<Partial<InvitationMeetingSnapshot>>(() => {
  const meeting = meetingStore.currentMeeting;
  if (!meeting || String(meeting.id) !== meetingId.value) return invitation.value?.latestPublishedVersion?.meetingSnapshot || {};
  return { id: Number(meeting.id), code: meeting.code, name: meeting.name, organizer: meeting.organizer, coOrganizer: meeting.coOrganizer, startTime: meeting.startTime, endTime: meeting.endTime, registrationStartTime: meeting.registrationStartTime, registrationEndTime: meeting.registrationEndTime, location: meeting.venue, venueName: meeting.venue, description: meeting.description, agenda: meeting.agenda, coverImageUrl: meeting.coverImageUrl, contactName: meeting.contactName, contactPhone: meeting.contactPhone };
});

function elementIcon(type: InvitationElementType): Component { return paletteItems.find((item) => item.type === type)?.icon || LayersOutline; }
function elementLabel(element: InvitationElementV1) {
  if (element.type === "TEXT") return String(element.props.text || "文本").slice(0, 18);
  if (element.type === "MEETING_FIELD") return meetingFieldOptions.find((item) => item.value === element.props.field)?.label || "会议字段";
  if (element.type === "SHAPE") return INVITATION_SHAPE_OPTIONS.find((item) => item.value === normalizeInvitationShape(element.props.shape))?.label || "形状";
  if (element.type === "QUERY_BUTTON") return `${INVITATION_QUERY_TARGET_OPTIONS.find((item) => item.value === normalizeInvitationQueryTarget(element.props.queryTarget))?.label || "查询"}按钮`;
  if (element.type === "CHANNEL_QR_CODE") return `${INVITATION_QR_PURPOSE_OPTIONS.find((item) => item.value === normalizeInvitationQrPurpose(element.props.purpose))?.label || "渠道"}二维码`;
  return paletteItems.find((item) => item.type === element.type)?.label || element.type;
}
</script>

<style scoped>
.designer-loading, .designer-error { min-height: 480px; display: grid; place-content: center; justify-items: center; gap: 14px; }
.designer-shell { height: calc(100vh - 104px); min-height: 620px; margin: -24px; display: grid; grid-template-rows: 58px minmax(0, 1fr); overflow: hidden; color: #192a22; background: #f2f5f3; }
.designer-toolbar { min-width: 0; padding: 0 14px; display: grid; grid-template-columns: minmax(260px, 1fr) auto auto auto minmax(310px, 1fr); align-items: center; gap: 12px; background: #fff; border-bottom: 1px solid #dde7e2; }
.toolbar-group { display: flex; align-items: center; gap: 5px; }
.toolbar-left { min-width: 0; }
.toolbar-right { justify-content: flex-end; }
.designer-title { min-width: 0; display: grid; line-height: 1.25; }
.designer-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.designer-title span { color: #718279; font-size: 11px; }
.designer-title span.dirty { color: #9b6b1e; }
.designer-title span.conflict { color: #c83a31; }
.canvas-mode-switch { padding: 3px; display: flex; gap: 2px; background: #edf2ef; border-radius: 6px; }
.canvas-mode-switch button { min-width: 70px; height: 30px; border: 0; border-radius: 4px; color: #62736b; background: transparent; font-size: 12px; cursor: pointer; }
.canvas-mode-switch button.active { color: #08784f; background: #fff; box-shadow: 0 1px 3px rgba(18, 56, 40, .12); font-weight: 700; }
.zoom-controls { min-width: 118px; justify-content: center; }
.zoom-controls span { width: 42px; text-align: center; font-size: 12px; }
.designer-body { min-height: 0; display: grid; grid-template-columns: 250px minmax(390px, 1fr) 286px; }
.left-panel, .right-panel { min-width: 0; display: flex; flex-direction: column; background: #fff; overflow: hidden; }
.left-panel { border-right: 1px solid #dde7e2; }
.right-panel { border-left: 1px solid #dde7e2; }
.panel-tabs { height: 48px; flex: 0 0 auto; padding: 0 10px; display: flex; border-bottom: 1px solid #e4ece8; }
.panel-tabs button { flex: 1; border: 0; border-bottom: 2px solid transparent; color: #66776f; background: transparent; font-size: 12px; cursor: pointer; }
.panel-tabs button.active { border-bottom-color: #08784f; color: #08784f; font-weight: 700; }
.palette-content, .asset-content, .layer-content, .inspector-content, .channel-panel-content, .effect-content { min-height: 0; padding: 14px; overflow-y: auto; }
.panel-section-title { margin: 0 0 10px; color: #53675e; font-size: 12px; font-weight: 700; }
.element-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }
.element-grid button { aspect-ratio: 1; min-width: 0; border: 1px solid #dfe9e4; border-radius: 6px; display: grid; place-content: center; justify-items: center; gap: 6px; color: #344a40; background: #fff; cursor: pointer; font-size: 11px; }
.element-grid button:hover:not(:disabled) { border-color: #79ad97; color: #08784f; background: #f4faf7; }
.element-grid button:disabled { opacity: .4; cursor: not-allowed; }
.element-grid .n-icon { font-size: 22px; }
.asset-hint { margin-top: 12px; font-size: 11px; }
.hidden-file-input { display: none; }
.asset-grid { margin-top: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.asset-grid button { min-width: 0; padding: 0; border: 1px solid #dfe9e4; border-radius: 6px; overflow: hidden; color: #465b51; background: #fff; cursor: pointer; text-align: left; }
.asset-grid img { width: 100%; aspect-ratio: 1.25; display: block; object-fit: cover; }
.asset-grid span { padding: 6px 7px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 10px; }
.layer-list { display: grid; gap: 3px; }
.layer-list button { width: 100%; min-height: 34px; padding: 0 8px; border: 1px solid transparent; border-radius: 5px; display: grid; grid-template-columns: 18px minmax(0, 1fr) 18px; align-items: center; gap: 7px; color: #5a6e64; background: transparent; font-size: 11px; text-align: left; cursor: pointer; }
.layer-list button.active { border-color: #b8d5c8; color: #08784f; background: #edf8f3; }
.layer-list button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lock-icon { opacity: .68; }
.canvas-workbench { min-width: 0; min-height: 0; display: grid; grid-template-rows: minmax(0, 1fr) 32px; overflow: hidden; }
.canvas-scroll { min-width: 0; min-height: 0; padding: 20px 44px 60px; display: flex; align-items: flex-start; justify-content: flex-start; overflow: auto; background-color: #edf1ef; background-image: linear-gradient(#dfe6e2 1px, transparent 1px), linear-gradient(90deg, #dfe6e2 1px, transparent 1px); background-size: 20px 20px; }
.canvas-scroll > :deep(.fabric-viewport) { margin-inline: auto; }
.canvas-statusbar { padding: 0 14px; display: flex; align-items: center; justify-content: flex-end; gap: 18px; color: #72837b; background: #fff; border-top: 1px solid #dde7e2; font-size: 10px; }
.inspector-content, .channel-panel-content, .effect-content { display: flex; flex-direction: column; gap: 13px; }
.inspector-heading { padding-bottom: 11px; display: grid; gap: 2px; border-bottom: 1px solid #e8efec; }
.inspector-heading strong { font-size: 14px; }
.inspector-heading span { color: #7c8d85; font-size: 10px; }
.inspector-content > label,
.inspector-section > label,
.two-column-inputs > label,
.effect-content > label,
.inspector-field { display: grid; gap: 6px; color: #52665c; font-size: 11px; }
.text-align-control { width: 100%; display: flex; }
.text-align-control :deep(.n-radio-button) { min-width: 0; flex: 1; }
.text-align-control :deep(.n-radio-button__content) { width: 100%; text-align: center; }
.inspector-section { padding-top: 12px; display: grid; gap: 9px; border-top: 1px solid #e8efec; }
.inspector-section p { margin: 0; color: #344b40; font-size: 12px; font-weight: 700; }
.two-column-inputs { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.action-row { display: flex; gap: 6px; flex-wrap: wrap; }
.channel-mini-row { padding: 9px 0; display: grid; grid-template-columns: 8px minmax(0, 1fr) 28px; align-items: center; gap: 8px; border-bottom: 1px solid #e8efec; }
.channel-mini-row > span { width: 7px; height: 7px; border-radius: 50%; background: #a6b2ac; }
.channel-mini-row > span.enabled { background: #0c9463; }
.channel-mini-row div { min-width: 0; display: grid; }
.channel-mini-row strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.channel-mini-row small { color: #819088; font-size: 9px; }
.document-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.document-summary span { padding: 8px 4px; display: grid; justify-items: center; color: #73837b; background: #f3f7f5; border-radius: 5px; font-size: 9px; }
.document-summary strong { color: #164a35; font-size: 15px; }
.preview-phone { max-height: 76vh; overflow-y: auto; background: #eef2ef; box-shadow: inset 0 0 0 1px #dce7e2; }
.export-preview { min-height: 460px; margin-top: 12px; display: grid; place-items: center; overflow: auto; background: #edf2ef; border: 1px solid #dce8e2; }
.mobile-preview-area { min-height: 0; padding: 16px; overflow-y: auto; }
.phone-preview-frame { width: min(100%, 430px); margin: 14px auto 40px; overflow: hidden; background: #fff; box-shadow: 0 14px 34px rgba(15, 54, 37, .14); }
.preview-mode .designer-toolbar { grid-template-columns: minmax(0, 1fr) auto; }

@media (max-width: 1260px) {
  .designer-body { grid-template-columns: 220px minmax(370px, 1fr) 260px; }
  .designer-toolbar { grid-template-columns: minmax(220px, 1fr) auto auto minmax(290px, 1fr); }
  .history-controls { display: none; }
}
@media (max-width: 800px) {
  .designer-shell { height: calc(100vh - 90px); min-height: 560px; margin: -16px; grid-template-rows: auto minmax(0, 1fr); }
  .designer-toolbar { min-height: 58px; padding: 8px; display: flex; flex-wrap: wrap; }
  .toolbar-left { flex: 1; }
  .toolbar-right { width: 100%; justify-content: flex-end; }
}
</style>
