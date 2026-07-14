<template>
  <div class="settings-view">
    <page-header title="系统设置" />

    <n-tabs v-model:value="activeSettingsTab" class="settings-tabs" type="segment" animated>
      <n-tab-pane name="branding" tab="品牌设置">
        <n-card class="surface section-card settings-card" :bordered="false">
          <div class="panel-heading">
            <div>
              <h2>品牌信息</h2>
              <span>设置侧边栏图标、浏览器标签标题和页面描述</span>
            </div>
          </div>

          <div class="brand-preview">
            <div class="preview-sidebar-brand">
              <div class="preview-brand-mark">
                <img :src="safeLogoUrl" :alt="settings.brandName" />
              </div>
              <strong>{{ settings.brandName || "会议管理系统" }}</strong>
            </div>
            <div class="preview-browser-tab">
              <img :src="safeFaviconUrl" alt="" />
              <span>{{ settings.browserTitle || settings.brandName || "会议管理系统" }}</span>
            </div>
          </div>

          <n-form class="settings-form" label-placement="left" label-width="150">
            <n-form-item label="侧边栏名称">
              <n-input v-model:value="settings.brandName" placeholder="例如：会议管理系统" />
            </n-form-item>
            <n-form-item label="浏览器标题">
              <n-input v-model:value="settings.browserTitle" placeholder="显示在浏览器标签页上的标题" />
            </n-form-item>
            <n-form-item label="标签描述">
              <n-input v-model:value="settings.metaDescription" type="textarea" placeholder="用于页面 meta description" />
            </n-form-item>
            <n-form-item label="系统图标">
              <div class="icon-setting-row">
                <n-input v-model:value="settings.logoUrl" placeholder="/uploads/... 或 https:// 图片地址" />
                <n-upload :show-file-list="false" accept=".png,.jpg,.jpeg,.webp,.gif" :custom-request="uploadLogo">
                  <n-button>选择图标</n-button>
                </n-upload>
              </div>
            </n-form-item>
            <n-form-item label="标签页图标">
              <div class="icon-setting-row">
                <n-input v-model:value="settings.faviconUrl" placeholder="默认跟随系统图标，也可单独设置" />
                <n-upload :show-file-list="false" accept=".png,.jpg,.jpeg,.webp,.gif" :custom-request="uploadFavicon">
                  <n-button>选择图标</n-button>
                </n-upload>
              </div>
            </n-form-item>
          </n-form>
          <n-space justify="end" class="settings-actions">
            <n-button :loading="loadingSettings" @click="loadSettings">刷新配置</n-button>
            <n-button @click="resetBrandingToDefault">恢复默认</n-button>
            <n-button type="primary" :loading="savingSettings" @click="saveSettings">保存配置</n-button>
          </n-space>
        </n-card>
      </n-tab-pane>

      <n-tab-pane name="integration" tab="接入配置">
        <n-card class="surface section-card settings-card" :bordered="false">
          <div class="panel-heading">
            <div>
              <h2>TDuck 接入</h2>
              <span>域名和 TDuck 前后端地址</span>
            </div>
          </div>
          <n-form class="settings-form" label-placement="left" label-width="150">
            <n-form-item label="会议系统域名"><n-input v-model:value="settings.baseUrl" /></n-form-item>
            <n-form-item label="TDuck 前端地址"><n-input v-model:value="settings.tduckFrontUrl" /></n-form-item>
            <n-form-item label="TDuck 后端地址"><n-input v-model:value="settings.tduckApiUrl" /></n-form-item>
          </n-form>
          <n-space justify="end" class="settings-actions">
            <n-button :loading="loadingSettings" @click="loadSettings">刷新配置</n-button>
            <n-button type="primary" :loading="savingSettings" @click="saveSettings">保存配置</n-button>
          </n-space>
        </n-card>
      </n-tab-pane>

      <n-tab-pane name="security" tab="安全策略">
        <n-card class="surface section-card settings-card" :bordered="false">
          <div class="panel-heading">
            <div>
              <h2>安全策略</h2>
              <span>数据展示和上线安全提醒</span>
            </div>
          </div>
          <div class="policy-list">
            <div class="policy-row">
              <div>
                <strong>敏感字段脱敏</strong>
                <span>手机号、邮箱等字段在普通列表中按策略处理</span>
              </div>
              <n-switch v-model:value="settings.maskSensitive" />
            </div>
          </div>
          <n-alert type="error" :bordered="false" class="security-alert">
            上线前必须替换所有默认密码和 CHANGE_ME 配置，并修改 TDuck 默认管理员密码。
          </n-alert>
          <n-space justify="end" class="settings-actions">
            <n-button :loading="loadingSettings" @click="loadSettings">刷新配置</n-button>
            <n-button type="primary" :loading="savingSettings" @click="saveSettings">保存配置</n-button>
          </n-space>
        </n-card>
      </n-tab-pane>

      <n-tab-pane name="access" tab="用户管理">
        <section class="access-shell">
          <div class="access-toolbar">
            <div>
              <h2>用户管理</h2>
              <span>集中维护后台账号、全局角色和各会议权限</span>
            </div>
            <n-space class="access-toolbar-actions">
              <n-button :loading="loadingSecurity" @click="loadSecurityData">刷新</n-button>
            </n-space>
          </div>

          <n-alert v-if="securityLoadError" type="error" :bordered="false" class="access-load-error">
            {{ securityLoadError }}。数据完整加载前不能新增或修改用户授权。
          </n-alert>

          <div class="access-overview-grid">
            <div class="access-overview-card">
              <span>后台用户</span>
              <strong>{{ users.length }}</strong>
              <small>{{ activeUserCount }} 个启用账号</small>
            </div>
            <div class="access-overview-card">
              <span>已授权用户</span>
              <strong>{{ authorizedUserCount }}</strong>
              <small>{{ permissions.length }} 项逐会议授权</small>
            </div>
            <div class="access-overview-card">
              <span>可授权会议</span>
              <strong>{{ authorizableMeetings.length }}</strong>
              <small>在用户编辑中逐项配置</small>
            </div>
          </div>

          <n-card class="surface section-card access-tabs-card" :bordered="false">
            <div class="access-tab-header">
              <div>
                <h3>后台用户</h3>
                <span>在新增或编辑用户时一并维护会议权限</span>
              </div>
              <n-button type="primary" :disabled="!securityDataReady" @click="openCreateUser">新增用户</n-button>
            </div>
            <div class="mobile-scroll-table">
              <n-data-table
                class="access-table"
                :columns="userColumns"
                :data="users"
                :loading="loadingSecurity"
                :pagination="{ pageSize: 8 }"
                :scroll-x="980"
                size="small"
              />
            </div>
          </n-card>
        </section>
      </n-tab-pane>
    </n-tabs>

    <n-modal
      v-model:show="showCreateUser"
      preset="card"
      title="新增后台用户"
      class="settings-modal"
      style="width: min(680px, calc(100vw - 32px))"
    >
      <n-form class="modal-form" label-placement="top">
        <n-form-item label="用户名"><n-input v-model:value="createUserForm.username" placeholder="请输入登录用户名" /></n-form-item>
        <n-form-item label="显示名"><n-input v-model:value="createUserForm.displayName" placeholder="请输入后台显示名称" /></n-form-item>
        <n-form-item label="初始密码">
          <n-input v-model:value="createUserForm.password" type="password" show-password-on="click" placeholder="请输入至少 6 位密码" />
        </n-form-item>
        <n-form-item label="全局角色"><n-select v-model:value="createUserForm.roles" :options="roleOptions" multiple placeholder="请选择全局角色" /></n-form-item>
        <n-form-item v-if="isCreatingSuperAdmin" label="会议授权">
          <n-alert type="info" :bordered="false">
            超级管理员自动拥有全部会议权限，无需单独选择。
          </n-alert>
        </n-form-item>
        <n-form-item v-else label="会议授权">
          <meeting-authorization-picker
            v-if="showCreateUser"
            v-model="createUserForm.meetingIds"
            :meetings="authorizableMeetings"
            :disabled="!securityDataReady"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateUser = false">取消</n-button>
          <n-button type="primary" :loading="savingUser" :disabled="!securityDataReady" @click="createUser">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showEditUser"
      preset="card"
      title="编辑后台用户"
      class="settings-modal"
      style="width: min(680px, calc(100vw - 32px))"
    >
      <n-form class="modal-form" label-placement="top">
        <n-form-item label="用户名">
          <n-input :value="editingUser?.username || ''" disabled />
        </n-form-item>
        <n-form-item label="显示名">
          <n-input v-model:value="editUserForm.displayName" placeholder="请输入后台显示名称" />
        </n-form-item>
        <n-form-item label="账号状态">
          <n-select v-model:value="editUserForm.status" :options="userStatusOptions" :disabled="isEditingLastActiveSuperAdmin" placeholder="请选择账号状态" />
        </n-form-item>
        <n-form-item label="全局角色">
          <n-select v-model:value="editUserForm.roles" :options="roleOptions" multiple :disabled="isEditingLastActiveSuperAdmin" placeholder="请选择全局角色" />
        </n-form-item>
        <n-form-item v-if="isEditingSuperAdmin" label="会议授权">
          <n-alert type="info" :bordered="false">
            超级管理员自动拥有全部会议权限，无需单独选择。
          </n-alert>
        </n-form-item>
        <n-form-item v-else label="会议授权">
          <meeting-authorization-picker
            v-if="showEditUser"
            v-model="editUserForm.meetingIds"
            :meetings="authorizableMeetings"
            :disabled="!securityDataReady"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditUser = false">取消</n-button>
          <n-button type="primary" :loading="savingUser" :disabled="!securityDataReady" @click="submitEditUser">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showResetPassword"
      preset="card"
      title="重置密码"
      class="settings-modal"
      style="width: min(480px, calc(100vw - 32px))"
    >
      <n-form class="modal-form" label-placement="top">
        <n-form-item label="用户">{{ resettingUser?.displayName || resettingUser?.username }}</n-form-item>
        <n-form-item label="新密码"><n-input v-model:value="resetPassword" type="password" show-password-on="click" placeholder="请输入至少 6 位新密码" /></n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showResetPassword = false">取消</n-button>
          <n-button type="primary" :loading="savingUser" @click="submitResetPassword">保存</n-button>
        </n-space>
      </template>
    </n-modal>

  </div>
</template>

<script setup lang="ts">
import type { UploadCustomRequestOptions } from "naive-ui";
import { computed, h, onMounted, reactive, ref } from "vue";
import { NButton, NDataTable, NSpace, NTag, NUpload, useMessage, type DataTableColumns } from "naive-ui";
import MeetingAuthorizationPicker from "@/components/MeetingAuthorizationPicker.vue";
import PageHeader from "@/components/PageHeader.vue";
import { useMeetingStore, type AdminUserDto, type MeetingUserPermissionDto } from "@/stores/meeting";
import { hasGlobalMeetingAccess, planMeetingAuthorizationChanges } from "@/utils/meetingPermissions";
import { assertSafeImageUrl, isAllowedUploadImage, sanitizeImageUrl } from "@/utils/safeUrl";

const store = useMeetingStore();
const message = useMessage();
const users = ref<AdminUserDto[]>([]);
const permissions = ref<MeetingUserPermissionDto[]>([]);
const authorizableMeetings = ref<Array<{ id: string; name: string; code: string }>>([]);
const loadingSecurity = ref(false);
const securityDataReady = ref(false);
const securityLoadError = ref("");
const loadingSettings = ref(false);
const savingSettings = ref(false);
const savingUser = ref(false);
const activeSettingsTab = ref<"branding" | "integration" | "security" | "access">("branding");
const showCreateUser = ref(false);
const showEditUser = ref(false);
const showResetPassword = ref(false);
const editingUser = ref<AdminUserDto | null>(null);
const resettingUser = ref<AdminUserDto | null>(null);
const resetPassword = ref("");
const defaultBrandIcon = "/favicon.svg";
type TagType = "success" | "info" | "warning" | "error" | "default" | "primary";

const createUserForm = reactive({
  username: "",
  displayName: "",
  password: "",
  status: "ACTIVE",
  roles: ["MEETING_ADMIN"] as string[],
  meetingIds: [] as string[]
});

const editUserForm = reactive({
  displayName: "",
  status: "ACTIVE",
  roles: ["MEETING_ADMIN"] as string[],
  meetingIds: [] as string[]
});

const settings = reactive({
  baseUrl: "https://meeting.example.com",
  tduckFrontUrl: "https://meeting.example.com/tduck/",
  tduckApiUrl: "https://meeting.example.com/tduck-api/",
  maskSensitive: true,
  brandName: "会议管理系统",
  browserTitle: "会议管理系统",
  metaDescription: "会议管理系统",
  logoUrl: defaultBrandIcon,
  faviconUrl: defaultBrandIcon
});
const safeLogoUrl = computed(() => sanitizeImageUrl(settings.logoUrl) || defaultBrandIcon);
const safeFaviconUrl = computed(() => sanitizeImageUrl(settings.faviconUrl) || safeLogoUrl.value);

const roleOptions = [
  { label: "超级管理员", value: "SUPER_ADMIN" },
  { label: "会议管理员", value: "MEETING_ADMIN" },
  { label: "签到人员", value: "CHECKIN_STAFF" },
  { label: "住宿人员", value: "ROOM_STAFF" },
  { label: "只读查看", value: "VIEWER" }
];

const userStatusOptions = [
  { label: "启用", value: "ACTIVE" },
  { label: "停用", value: "DISABLED" }
];

const roleLabelMap = new Map(roleOptions.map((item) => [item.value, item.label]));

const activeUserCount = computed(() => users.value.filter((user) => isActiveStatus(user.status)).length);
const isEditingLastActiveSuperAdmin = computed(() => isLastActiveSuperAdmin(editingUser.value));
const isCreatingSuperAdmin = computed(() => hasGlobalMeetingAccess(createUserForm.roles));
const isEditingSuperAdmin = computed(() => hasGlobalMeetingAccess(editUserForm.roles));

const authorizedUserCount = computed(() => {
  const userIds = new Set(
    permissions.value
      .map((item) => String(item.userId || item.username || item.displayName || item.id || ""))
      .filter(Boolean)
  );
  users.value
    .filter((user) => hasGlobalMeetingAccess(user.roles))
    .forEach((user) => userIds.add(String(user.id)));
  return userIds.size;
});

const userColumns: DataTableColumns<AdminUserDto> = [
  { title: "用户名", key: "username", minWidth: 140 },
  { title: "显示名", key: "displayName", minWidth: 160, render: (row: AdminUserDto) => row.displayName || row.name || "-" },
  {
    title: "角色",
    key: "roles",
    minWidth: 240,
    render: (row: AdminUserDto) => renderTagList(row.roles || [], (role) => roleLabelMap.get(role) || role, roleTagType)
  },
  {
    title: "授权会议",
    key: "meetingPermissions",
    minWidth: 300,
    render: (row: AdminUserDto) => renderUserMeetingPermissions(row)
  },
  {
    title: "状态",
    key: "status",
    minWidth: 110,
    render: (row: AdminUserDto) => renderStatusTag(row.status)
  },
  {
    title: "操作",
    key: "actions",
    width: 180,
    align: "right",
    render: (row: AdminUserDto) =>
      h(NSpace, { justify: "end", size: 12 }, {
        default: () => [
          h(NButton, { text: true, type: "primary", disabled: !securityDataReady.value, onClick: () => openEditUser(row) }, { default: () => "编辑" }),
          h(NButton, { text: true, type: "primary", onClick: () => openResetPassword(row) }, { default: () => "重置密码" })
        ]
      })
  }
];

onMounted(() => {
  void loadSettings();
  void loadSecurityData();
});

async function loadSecurityData() {
  loadingSecurity.value = true;
  securityDataReady.value = false;
  securityLoadError.value = "";
  try {
    const [loadedUsers, loadedPermissions, loadedMeetings] = await Promise.all([
      store.loadAdminUsers(),
      store.loadMeetingUserPermissions(),
      store.loadAuthorizableMeetings()
    ]);
    users.value = loadedUsers;
    permissions.value = loadedPermissions;
    authorizableMeetings.value = loadedMeetings.map((meeting) => ({
      id: meeting.id,
      name: meeting.name,
      code: meeting.code
    }));
    securityDataReady.value = true;
  } catch (error) {
    securityLoadError.value = error instanceof Error ? error.message : "用户与授权信息加载失败";
    message.error(securityLoadError.value);
  } finally {
    loadingSecurity.value = false;
  }
}

function roleTagType(role?: string): TagType {
  if (role === "SUPER_ADMIN") return "error";
  if (role === "MEETING_ADMIN") return "info";
  if (role === "CHECKIN_STAFF") return "success";
  if (role === "ROOM_STAFF") return "warning";
  return "default";
}

function isActiveStatus(status?: string) {
  return !status || status === "ACTIVE" || status === "ENABLED";
}

function statusLabel(status?: string) {
  if (isActiveStatus(status)) return "启用";
  if (status === "DISABLED" || status === "INACTIVE") return "停用";
  if (status === "LOCKED") return "锁定";
  return status || "启用";
}

function statusTagType(status?: string): TagType {
  if (isActiveStatus(status)) return "success";
  if (status === "LOCKED") return "warning";
  return "default";
}

function renderStatusTag(status?: string) {
  return h(NTag, { size: "small", type: statusTagType(status) }, { default: () => statusLabel(status) });
}

function renderTagList(items: string[], label: (value: string) => string, type: (value: string) => TagType) {
  if (!items.length) return "-";
  return h(
    "div",
    { class: "tag-list" },
    items.map((item) => h(NTag, { size: "small", type: type(item) }, { default: () => label(item) }))
  );
}

function permissionMeetingName(row: MeetingUserPermissionDto) {
  const meetingId = row.meetingId == null ? "" : String(row.meetingId);
  const meeting = authorizableMeetings.value.find((item) => item.id === meetingId)
    || store.meetings.find((item) => item.id === meetingId);
  return meeting?.name || row.meetingName || row.meetingCode || (meetingId ? `会议 ${meetingId}` : "未知会议");
}

function renderUserMeetingPermissions(user: AdminUserDto) {
  if (hasGlobalMeetingAccess(user.roles)) {
    return h(NTag, { size: "small", type: "success" }, { default: () => "全部会议" });
  }
  const userPermissions = permissions.value.filter((item) => String(item.userId) === String(user.id));
  if (!userPermissions.length) return "-";
  return h(
    "div",
    { class: "tag-list" },
    userPermissions.map((item) => h(
      NTag,
      { key: String(item.id || item.meetingId), size: "small", type: "info" },
      { default: () => permissionMeetingName(item) }
    ))
  );
}

function loadSettings() {
  loadingSettings.value = true;
  void store.loadSystemSettings()
    .then((loaded) => {
      settings.baseUrl = loaded.baseUrl;
      settings.tduckFrontUrl = loaded.tduckFrontUrl;
      settings.tduckApiUrl = loaded.tduckApiUrl;
      settings.maskSensitive = loaded.maskSensitive;
      settings.brandName = loaded.brandName || "会议管理系统";
      settings.browserTitle = loaded.browserTitle || settings.brandName;
      settings.metaDescription = loaded.metaDescription || "会议管理系统";
      settings.logoUrl = loaded.logoUrl || defaultBrandIcon;
      settings.faviconUrl = loaded.faviconUrl || settings.logoUrl || defaultBrandIcon;
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "系统配置加载失败");
    })
    .finally(() => {
      loadingSettings.value = false;
    });
}

function saveSettings() {
  try {
    assertSafeImageUrl(settings.logoUrl, "系统图标");
    assertSafeImageUrl(settings.faviconUrl || settings.logoUrl, "标签页图标");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "图片地址不安全");
    return;
  }
  savingSettings.value = true;
  void store.saveSystemSettings({
    baseUrl: settings.baseUrl,
    tduckFrontUrl: settings.tduckFrontUrl,
    tduckApiUrl: settings.tduckApiUrl,
    maskSensitive: settings.maskSensitive,
    brandName: settings.brandName.trim(),
    browserTitle: settings.browserTitle.trim(),
    metaDescription: settings.metaDescription.trim(),
    logoUrl: settings.logoUrl.trim(),
    faviconUrl: settings.faviconUrl.trim() || settings.logoUrl.trim()
  })
    .then((saved) => {
      settings.brandName = saved.brandName;
      settings.browserTitle = saved.browserTitle;
      settings.metaDescription = saved.metaDescription;
      settings.logoUrl = saved.logoUrl;
      settings.faviconUrl = saved.faviconUrl;
      message.success("系统配置已保存");
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "系统配置保存失败");
    })
    .finally(() => {
      savingSettings.value = false;
    });
}

function resetBrandingToDefault() {
  settings.brandName = "会议管理系统";
  settings.browserTitle = "会议管理系统";
  settings.metaDescription = "会议管理系统";
  settings.logoUrl = defaultBrandIcon;
  settings.faviconUrl = defaultBrandIcon;
}

function uploadLogo(options: UploadCustomRequestOptions) {
  void uploadBrandImage(options, (url) => {
    settings.logoUrl = url;
    if (!settings.faviconUrl || settings.faviconUrl === defaultBrandIcon) {
      settings.faviconUrl = url;
    }
  });
}

function uploadFavicon(options: UploadCustomRequestOptions) {
  void uploadBrandImage(options, (url) => {
    settings.faviconUrl = url;
  });
}

async function uploadBrandImage(options: UploadCustomRequestOptions, apply: (url: string) => void) {
  const file = options.file.file;
  if (!file) {
    options.onError();
    return;
  }
  if (!isAllowedUploadImage(file)) {
    message.warning("仅支持 JPG、PNG、WebP 或 GIF 图片");
    options.onError();
    return;
  }
  try {
    const result = await store.uploadImageAsset(file);
    apply(result.url);
    options.onFinish();
    message.success("图片已上传");
  } catch {
    message.error("图标上传失败");
    options.onError();
  }
}

function openCreateUser() {
  if (!securityDataReady.value) {
    message.warning("请先刷新并完整加载用户与授权数据");
    return;
  }
  createUserForm.username = "";
  createUserForm.displayName = "";
  createUserForm.password = "";
  createUserForm.status = "ACTIVE";
  createUserForm.roles = ["MEETING_ADMIN"];
  createUserForm.meetingIds = [];
  showCreateUser.value = true;
}

async function createUser() {
  if (!securityDataReady.value) {
    message.warning("授权数据尚未完整加载，暂不能创建用户");
    return;
  }
  if (!createUserForm.username.trim() || !createUserForm.displayName.trim() || createUserForm.password.length < 6) {
    message.warning("请填写用户名、显示名和至少 6 位初始密码");
    return;
  }
  savingUser.value = true;
  try {
    const createdUser = await store.createAdminUser({
      username: createUserForm.username.trim(),
      displayName: createUserForm.displayName.trim(),
      password: createUserForm.password,
      status: createUserForm.status,
      roles: createUserForm.roles
    });
    const permissionResult = await syncMeetingPermissions(createdUser.id, createUserForm.meetingIds, createUserForm.roles);
    showCreateUser.value = false;
    await loadSecurityData();
    if (permissionResult.failureCount > 0) {
      message.warning(`用户已创建，但有 ${permissionResult.failureCount} 项会议授权保存失败，请编辑用户后重试`);
    } else {
      message.success(isCreatingSuperAdmin.value ? "用户已创建，超级管理员自动拥有全部会议权限" : "用户及会议授权已保存");
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : "用户创建失败");
  } finally {
    savingUser.value = false;
  }
}

function openEditUser(user: AdminUserDto) {
  if (!securityDataReady.value) {
    message.warning("请先刷新并完整加载用户与授权数据");
    return;
  }
  editingUser.value = user;
  editUserForm.displayName = user.displayName || user.name || user.username;
  editUserForm.status = normalizeUserStatus(user.status);
  editUserForm.roles = user.roles?.length ? [...user.roles] : ["MEETING_ADMIN"];
  editUserForm.meetingIds = meetingIdsForUser(user.id);
  showEditUser.value = true;
}

async function submitEditUser() {
  if (!securityDataReady.value) {
    message.warning("授权数据尚未完整加载，暂不能保存");
    return;
  }
  if (!editingUser.value) {
    return;
  }
  if (!editUserForm.displayName.trim()) {
    message.warning("请填写显示名");
    return;
  }
  if (!editUserForm.roles.length) {
    message.warning("请至少选择一个全局角色");
    return;
  }
  if (isLastActiveSuperAdmin(editingUser.value)) {
    /*
     * 初始管理员用户名可以自定义。前端与后端都按角色保护最后一个启用的超级管理员，
     * 允许修改显示名，但禁止停用或移除角色，避免把全局后台入口锁死。
     */
    editUserForm.status = "ACTIVE";
    editUserForm.roles = ensureRole(editUserForm.roles, "SUPER_ADMIN");
  }
  savingUser.value = true;
  try {
    const userId = editingUser.value.id;
    await store.updateAdminUser(userId, {
      /*
       * 后端当前只允许更新显示名、状态和角色；用户名作为登录标识保持只读，
       * 避免改名后已有授权、审计日志和当前会话出现难以追踪的身份变更。
       */
      displayName: editUserForm.displayName.trim(),
      status: editUserForm.status,
      roles: editUserForm.roles
    });
    const permissionResult = await syncMeetingPermissions(userId, editUserForm.meetingIds, editUserForm.roles);
    showEditUser.value = false;
    editingUser.value = null;
    await loadSecurityData();
    if (permissionResult.failureCount > 0) {
      message.warning(`用户信息已保存，但有 ${permissionResult.failureCount} 项会议授权保存失败，请重新编辑后重试`);
    } else {
      message.success(isEditingSuperAdmin.value ? "用户信息已保存，超级管理员自动拥有全部会议权限" : "用户及会议授权已保存");
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : "用户信息保存失败");
  } finally {
    savingUser.value = false;
  }
}

function meetingIdsForUser(userId: string | number) {
  return permissions.value
    .filter((permission) => String(permission.userId) === String(userId) && permission.meetingId != null)
    .map((permission) => String(permission.meetingId));
}

async function syncMeetingPermissions(userId: string | number, meetingIds: string[], roles: string[]) {
  if (!securityDataReady.value) {
    throw new Error("授权数据尚未完整加载，请刷新后重试");
  }
  const changes = planMeetingAuthorizationChanges(
    userId,
    authorizableMeetings.value.map((meeting) => meeting.id),
    meetingIds,
    permissions.value,
    roles
  );
  const operations = changes.map((change) =>
    change.type === "grant"
      ? store.grantMeetingUserPermission({ meetingId: change.meetingId, userId, role: change.role })
      : store.deleteMeetingUserPermission(change.permissionId)
  );

  const results = await Promise.allSettled(operations);
  return {
    successCount: results.filter((result) => result.status === "fulfilled").length,
    failureCount: results.filter((result) => result.status === "rejected").length
  };
}

function normalizeUserStatus(status?: string) {
  return isActiveStatus(status) ? "ACTIVE" : "DISABLED";
}

function isLastActiveSuperAdmin(user?: AdminUserDto | null) {
  if (!user || !isActiveStatus(user.status) || !user.roles?.includes("SUPER_ADMIN")) {
    return false;
  }
  return users.value.filter((candidate) =>
    isActiveStatus(candidate.status) && candidate.roles?.includes("SUPER_ADMIN")
  ).length === 1;
}

function ensureRole(roles: string[], role: string) {
  return roles.includes(role) ? roles : [role, ...roles];
}

function openResetPassword(user: AdminUserDto) {
  resettingUser.value = user;
  resetPassword.value = "";
  showResetPassword.value = true;
}

function submitResetPassword() {
  if (!resettingUser.value || resetPassword.value.length < 6) {
    message.warning("新密码至少 6 位");
    return;
  }
  savingUser.value = true;
  void store.resetAdminUserPassword(resettingUser.value.id, resetPassword.value)
    .then(() => {
      message.success("密码已重置");
      showResetPassword.value = false;
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "密码重置失败");
    })
    .finally(() => {
      savingUser.value = false;
    });
}

</script>

<style scoped>
.settings-view {
  max-width: 1360px;
}

.settings-tabs {
  margin-top: 8px;
}

.settings-tabs > :deep(.n-tabs-nav) {
  width: min(100%, 980px);
  max-width: 980px;
  margin: 0 0 16px;
}

.settings-tabs > :deep(.n-tabs-nav .n-tabs-rail) {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.settings-tabs > :deep(.n-tabs-nav .n-tabs-tab-wrapper),
.settings-tabs > :deep(.n-tabs-nav .n-tabs-tab) {
  min-width: 0;
  width: 100%;
}

.settings-tabs > :deep(.n-tabs-nav .n-tabs-tab) {
  display: flex;
  justify-content: center;
}

.settings-tabs > :deep(.n-tabs-nav .n-tabs-tab__label) {
  display: block;
  width: 100%;
  text-align: center;
}

.settings-card {
  max-width: 820px;
}

.panel-heading,
.access-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-heading h2,
.access-toolbar h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.panel-heading span,
.access-toolbar span,
.policy-row span {
  color: #64748b;
}

.settings-form {
  max-width: 720px;
}

.settings-actions {
  margin: 12px 0 16px;
}

.brand-preview {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.preview-sidebar-brand,
.preview-browser-tab {
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  display: flex;
  align-items: center;
  color: var(--text-primary);
  background: var(--surface-bg);
}

.preview-sidebar-brand {
  gap: 12px;
  padding: 16px;
}

.preview-brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
}

.preview-brand-mark img,
.preview-browser-tab img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.preview-sidebar-brand strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
}

.preview-browser-tab {
  gap: 10px;
  padding: 14px 16px;
}

.preview-browser-tab img {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
}

.preview-browser-tab span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-setting-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.section-card {
  margin-bottom: 16px;
}

.access-shell {
  min-width: 0;
  display: grid;
  gap: 14px;
}

.access-toolbar {
  margin: 2px 0 0;
}

.access-toolbar-actions {
  flex: 0 0 auto;
}

.access-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.access-overview-card {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-bg);
  display: grid;
  gap: 5px;
}

.access-overview-card span {
  color: #64748b;
  font-size: 13px;
}

.access-overview-card strong {
  min-width: 0;
  color: var(--text-primary);
  font-size: 24px;
  line-height: 30px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-overview-card small {
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-tabs-card :deep(.n-card__content) {
  min-width: 0;
  padding: 16px 20px 20px;
}

.access-tabs-card {
  min-width: 0;
  max-width: 100%;
}

.access-tab-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.access-tab-header h3 {
  margin: 0 0 4px;
  color: var(--text-primary);
  font-size: 17px;
  line-height: 24px;
}

.access-tab-header span {
  color: #64748b;
  font-size: 13px;
}

.access-table :deep(.n-data-table-th),
.access-table :deep(.n-data-table-td) {
  padding: 10px 12px;
}

.access-table :deep(.n-data-table-pagination) {
  margin: 12px 0 0;
}

:deep(.identity-cell) {
  min-width: 0;
  display: grid;
  gap: 2px;
}

:deep(.identity-cell strong) {
  min-width: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.identity-cell span) {
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.policy-list {
  display: grid;
  gap: 12px;
}

.policy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px;
  border: 1px solid rgba(15, 148, 136, 0.12);
  border-radius: 8px;
  background: rgba(15, 148, 136, 0.04);
}

.policy-row strong {
  display: block;
  margin-bottom: 4px;
  font-size: 15px;
}

.security-alert {
  margin-top: 16px;
}

:global(.settings-modal .n-card-header) {
  flex: 0 0 auto;
  padding: 22px 24px 10px;
}

:global(.settings-modal) {
  max-height: calc(100vh - 32px);
}

:global(.settings-modal .n-card__content) {
  min-height: 0;
  padding: 8px 24px 4px;
  overflow-y: auto;
}

:global(.settings-modal .n-card__footer) {
  flex: 0 0 auto;
  padding: 12px 24px 22px;
}

.modal-form {
  display: grid;
  gap: 2px;
}

:deep(.tag-list) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 980px) {
  .access-overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .settings-card {
    max-width: none;
  }

  .settings-tabs > :deep(.n-tabs-nav) {
    width: 100%;
    max-width: none;
    overflow-x: auto;
  }

  .settings-tabs > :deep(.n-tabs-nav .n-tabs-rail) {
    width: max-content;
    min-width: 100%;
    grid-template-columns: repeat(4, minmax(92px, 1fr));
  }

  .panel-heading,
  .access-toolbar,
  .policy-row {
    flex-direction: column;
    align-items: stretch;
  }

  .settings-form :deep(.n-form-item) {
    grid-template-columns: 1fr;
  }

  .brand-preview {
    grid-template-columns: 1fr;
  }

  .icon-setting-row {
    grid-template-columns: 1fr;
  }

  .access-tab-header {
    flex-direction: column;
    align-items: stretch;
  }

  .settings-actions :deep(.n-button),
  .access-toolbar-actions :deep(.n-button),
  .access-tab-header > .n-button {
    width: 100%;
  }

  .mobile-scroll-table {
    min-width: 0;
    max-width: 100%;
    width: 100%;
    overflow-x: auto;
  }

  .mobile-scroll-table :deep(.n-data-table) {
    min-width: 720px;
  }
}
</style>
