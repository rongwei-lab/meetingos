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
              <span>域名、前后端地址和 Webhook Token</span>
            </div>
          </div>
          <n-form class="settings-form" label-placement="left" label-width="150">
            <n-form-item label="会议系统域名"><n-input v-model:value="settings.baseUrl" /></n-form-item>
            <n-form-item label="TDuck 前端地址"><n-input v-model:value="settings.tduckFrontUrl" /></n-form-item>
            <n-form-item label="TDuck 后端地址"><n-input v-model:value="settings.tduckApiUrl" /></n-form-item>
            <n-form-item label="Webhook Token">
              <n-input v-model:value="settings.webhookToken" type="password" show-password-on="click" :placeholder="settings.webhookTokenMasked || '留空表示不修改'" />
            </n-form-item>
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
              <span>数据展示和导出审批</span>
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
            <div class="policy-row">
              <div>
                <strong>导出审批</strong>
                <span>数据导出前需要管理员确认</span>
              </div>
              <n-switch v-model:value="settings.exportApproval" />
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

      <n-tab-pane name="access" tab="用户与授权">
        <section class="access-shell">
          <div class="access-toolbar">
            <div>
              <h2>用户与会议授权</h2>
              <span>集中维护后台账号，以及当前会议的可访问人员</span>
            </div>
            <n-space class="access-toolbar-actions">
              <n-button :loading="loadingSecurity" @click="loadSecurityData">刷新</n-button>
            </n-space>
          </div>

          <div class="access-overview-grid">
            <div class="access-overview-card">
              <span>后台用户</span>
              <strong>{{ users.length }}</strong>
              <small>{{ activeUserCount }} 个启用账号</small>
            </div>
            <div class="access-overview-card">
              <span>会议授权</span>
              <strong>{{ permissions.length }}</strong>
              <small>{{ authorizedUserCount }} 个已授权用户</small>
            </div>
            <div class="access-overview-card access-meeting-card">
              <span>当前会议</span>
              <strong>{{ currentMeetingName }}</strong>
              <small>{{ currentMeetingMeta }}</small>
            </div>
          </div>

          <n-card class="surface section-card access-tabs-card" :bordered="false">
            <n-tabs v-model:value="activeAccessTab" type="line" animated class="access-sub-tabs">
              <n-tab-pane name="users" tab="后台用户">
                <div class="access-tab-header">
                  <div>
                    <h3>基础用户管理</h3>
                    <span>维护后台登录账号、角色和密码重置</span>
                  </div>
                  <n-button type="primary" @click="showCreateUser = true">新增用户</n-button>
                </div>
                <div class="mobile-scroll-table">
                  <n-data-table
                    class="access-table"
                    :columns="userColumns"
                    :data="users"
                    :loading="loadingSecurity"
                    :pagination="{ pageSize: 8 }"
                    :scroll-x="720"
                    size="small"
                  />
                </div>
              </n-tab-pane>

              <n-tab-pane name="permissions" tab="会议授权">
                <div class="access-tab-header">
                  <div>
                    <h3>会议授权</h3>
                    <span>为用户授予当前会议的管理、签到、住宿或查看权限</span>
                  </div>
                  <n-button type="primary" secondary @click="showGrantPermission = true">授予权限</n-button>
                </div>
                <div class="mobile-scroll-table">
                  <n-data-table
                    class="access-table"
                    :columns="permissionColumns"
                    :data="permissions"
                    :loading="loadingSecurity"
                    :pagination="{ pageSize: 8 }"
                    :scroll-x="820"
                    size="small"
                  />
                </div>
              </n-tab-pane>
            </n-tabs>
          </n-card>
        </section>
      </n-tab-pane>
    </n-tabs>

    <n-modal
      v-model:show="showCreateUser"
      preset="card"
      title="新增后台用户"
      class="settings-modal"
      style="width: min(560px, calc(100vw - 32px))"
    >
      <n-form class="modal-form" label-placement="top">
        <n-form-item label="用户名"><n-input v-model:value="createUserForm.username" placeholder="请输入登录用户名" /></n-form-item>
        <n-form-item label="显示名"><n-input v-model:value="createUserForm.displayName" placeholder="请输入后台显示名称" /></n-form-item>
        <n-form-item label="初始密码">
          <n-input v-model:value="createUserForm.password" type="password" show-password-on="click" placeholder="请输入至少 12 位密码" />
        </n-form-item>
        <n-form-item label="全局角色"><n-select v-model:value="createUserForm.roles" :options="roleOptions" multiple placeholder="请选择全局角色" /></n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateUser = false">取消</n-button>
          <n-button type="primary" :loading="savingUser" @click="createUser">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showEditUser"
      preset="card"
      title="编辑后台用户"
      class="settings-modal"
      style="width: min(560px, calc(100vw - 32px))"
    >
      <n-form class="modal-form" label-placement="top">
        <n-form-item label="用户名">
          <n-input :value="editingUser?.username || ''" disabled />
        </n-form-item>
        <n-form-item label="显示名">
          <n-input v-model:value="editUserForm.displayName" placeholder="请输入后台显示名称" />
        </n-form-item>
        <n-form-item label="账号状态">
          <n-select v-model:value="editUserForm.status" :options="userStatusOptions" :disabled="isEditingBuiltInAdmin" placeholder="请选择账号状态" />
        </n-form-item>
        <n-form-item label="全局角色">
          <n-select v-model:value="editUserForm.roles" :options="roleOptions" multiple :disabled="isEditingBuiltInAdmin" placeholder="请选择全局角色" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditUser = false">取消</n-button>
          <n-button type="primary" :loading="savingUser" @click="submitEditUser">保存</n-button>
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
        <n-form-item label="新密码"><n-input v-model:value="resetPassword" type="password" show-password-on="click" placeholder="请输入至少 12 位新密码" /></n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showResetPassword = false">取消</n-button>
          <n-button type="primary" :loading="savingUser" @click="submitResetPassword">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showGrantPermission"
      preset="card"
      title="授予会议权限"
      class="settings-modal"
      style="width: min(560px, calc(100vw - 32px))"
    >
      <n-form class="modal-form" label-placement="top">
        <n-form-item label="会议">
          <n-select v-model:value="grantForm.meetingId" :options="meetingOptions" placeholder="请选择会议" />
        </n-form-item>
        <n-form-item label="用户">
          <n-select v-model:value="grantForm.userId" :options="userOptions" filterable placeholder="请选择后台用户" />
        </n-form-item>
        <n-form-item label="会议角色">
          <n-select v-model:value="grantForm.role" :options="meetingPermissionOptions" placeholder="请选择会议角色" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showGrantPermission = false">取消</n-button>
          <n-button type="primary" :loading="savingPermission" @click="grantPermission">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import type { UploadCustomRequestOptions } from "naive-ui";
import { computed, h, onMounted, reactive, ref } from "vue";
import { NButton, NDataTable, NSpace, NTag, NUpload, useMessage, type DataTableColumns } from "naive-ui";
import PageHeader from "@/components/PageHeader.vue";
import { useMeetingStore, type AdminUserDto, type MeetingUserPermissionDto } from "@/stores/meeting";
import { assertSafeImageUrl, isAllowedUploadImage, sanitizeImageUrl } from "@/utils/safeUrl";

const store = useMeetingStore();
const message = useMessage();
const users = ref<AdminUserDto[]>([]);
const permissions = ref<MeetingUserPermissionDto[]>([]);
const loadingSecurity = ref(false);
const loadingSettings = ref(false);
const savingSettings = ref(false);
const savingUser = ref(false);
const savingPermission = ref(false);
const activeSettingsTab = ref<"branding" | "integration" | "security" | "access">("branding");
const activeAccessTab = ref<"users" | "permissions">("users");
const showCreateUser = ref(false);
const showEditUser = ref(false);
const showResetPassword = ref(false);
const showGrantPermission = ref(false);
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
  roles: ["MEETING_ADMIN"] as string[]
});

const editUserForm = reactive({
  displayName: "",
  status: "ACTIVE",
  roles: ["MEETING_ADMIN"] as string[]
});

const grantForm = reactive({
  meetingId: "",
  userId: "",
  role: "VIEWER"
});

const settings = reactive({
  baseUrl: "https://meeting.example.com",
  tduckFrontUrl: "https://meeting.example.com/tduck/",
  tduckApiUrl: "https://meeting.example.com/tduck-api/",
  webhookToken: "",
  webhookTokenMasked: "",
  maskSensitive: true,
  exportApproval: true,
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

const meetingPermissionOptions = [
  { label: "会议管理员", value: "MANAGER" },
  { label: "签到", value: "CHECKIN" },
  { label: "住宿", value: "ROOM" },
  { label: "查看", value: "VIEWER" }
];

const roleLabelMap = new Map(roleOptions.map((item) => [item.value, item.label]));
const meetingPermissionLabelMap = new Map(meetingPermissionOptions.map((item) => [item.value, item.label]));

const activeUserCount = computed(() => users.value.filter((user) => isActiveStatus(user.status)).length);
const isEditingBuiltInAdmin = computed(() => isBuiltInAdmin(editingUser.value));

const authorizedUserCount = computed(() => {
  const userIds = new Set(
    permissions.value
      .map((item) => String(item.userId || item.username || item.displayName || item.id || ""))
      .filter(Boolean)
  );
  return userIds.size;
});

const currentMeetingName = computed(() => store.currentMeeting?.name || "未选择会议");

const currentMeetingMeta = computed(() => {
  const meeting = store.currentMeeting;
  if (!meeting) return "请选择会议后再维护授权";
  return meeting.code ? `会议编号：${meeting.code}` : `会议 ID：${meeting.id}`;
});

const userOptions = computed(() => users.value.map((user) => ({
  label: `${user.displayName || user.username}（${user.username}）`,
  value: String(user.id)
})));

const meetingOptions = computed(() => store.meetings.map((meeting) => ({
  label: `${meeting.name}（${meeting.code}）`,
  value: meeting.id
})));

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
          h(NButton, { text: true, type: "primary", onClick: () => openEditUser(row) }, { default: () => "编辑" }),
          h(NButton, { text: true, type: "primary", onClick: () => openResetPassword(row) }, { default: () => "重置密码" })
        ]
      })
  }
];

const permissionColumns: DataTableColumns<MeetingUserPermissionDto> = [
  {
    title: "用户",
    key: "username",
    minWidth: 150,
    render: (row: MeetingUserPermissionDto) => renderUserIdentity(row)
  },
  {
    title: "会议",
    key: "meetingId",
    minWidth: 230,
    render: (row: MeetingUserPermissionDto) => renderPermissionMeeting(row)
  },
  {
    title: "角色",
    key: "role",
    minWidth: 110,
    render: (row: MeetingUserPermissionDto) =>
      h(NTag, { size: "small", type: permissionRoleTagType(row.role || row.permission) }, { default: () => meetingPermissionLabel(row.role || row.permission) })
  },
  {
    title: "权限范围",
    key: "permissions",
    minWidth: 150,
    render: (row: MeetingUserPermissionDto) => {
      const items = row.permissions || (row.permission ? [row.permission] : []);
      return renderTagList(items, meetingPermissionLabel, () => "info");
    }
  },
  {
    title: "状态",
    key: "status",
    width: 90,
    render: (row: MeetingUserPermissionDto) => renderStatusTag(row.status)
  },
  {
    title: "操作",
    key: "actions",
    width: 82,
    align: "right",
    render: (row: MeetingUserPermissionDto) =>
      h(NButton, { text: true, type: "error", disabled: row.id == null, onClick: () => revokePermission(row) }, { default: () => "撤销" })
  }
];

onMounted(() => {
  grantForm.meetingId = store.currentMeetingId;
  void loadSettings();
  void loadSecurityData();
});

function loadSecurityData() {
  loadingSecurity.value = true;
  void Promise.all([store.loadAdminUsers(), store.loadMeetingUserPermissions()])
    .then(([loadedUsers, loadedPermissions]) => {
      users.value = loadedUsers;
      permissions.value = loadedPermissions;
      if (!grantForm.userId && loadedUsers[0]) grantForm.userId = String(loadedUsers[0].id);
      if (!grantForm.meetingId) grantForm.meetingId = store.currentMeetingId;
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "用户与授权信息加载失败");
    })
    .finally(() => {
      loadingSecurity.value = false;
    });
}

function meetingPermissionLabel(value?: string) {
  if (!value) return "-";
  return meetingPermissionLabelMap.get(value) || value;
}

function roleTagType(role?: string): TagType {
  if (role === "SUPER_ADMIN") return "error";
  if (role === "MEETING_ADMIN") return "info";
  if (role === "CHECKIN_STAFF") return "success";
  if (role === "ROOM_STAFF") return "warning";
  return "default";
}

function permissionRoleTagType(role?: string): TagType {
  if (role === "MANAGER" || role === "SUPER_ADMIN" || role === "MEETING_ADMIN") return "info";
  if (role === "CHECKIN" || role === "CHECKIN_STAFF") return "success";
  if (role === "ROOM" || role === "ROOM_STAFF") return "warning";
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

function renderUserIdentity(row: MeetingUserPermissionDto) {
  const primary = row.displayName || row.username || row.userId || "-";
  const secondary = row.displayName && row.username ? row.username : row.userId ? `用户 ID：${row.userId}` : "";
  return h("div", { class: "identity-cell" }, [h("strong", primary), secondary ? h("span", secondary) : null]);
}

function renderPermissionMeeting(row: MeetingUserPermissionDto) {
  const meetingId = row.meetingId == null ? "" : String(row.meetingId);
  const meeting = store.meetings.find((item) => item.id === meetingId);
  const primary = meeting?.name || (meetingId ? `会议 ${meetingId}` : currentMeetingName.value);
  const secondary = meeting?.code ? `编号：${meeting.code}` : meetingId ? `ID：${meetingId}` : currentMeetingMeta.value;
  return h("div", { class: "identity-cell" }, [h("strong", primary), h("span", secondary)]);
}

function loadSettings() {
  loadingSettings.value = true;
  void store.loadSystemSettings()
    .then((loaded) => {
      settings.baseUrl = loaded.baseUrl;
      settings.tduckFrontUrl = loaded.tduckFrontUrl;
      settings.tduckApiUrl = loaded.tduckApiUrl;
      settings.webhookToken = "";
      settings.webhookTokenMasked = loaded.webhookTokenMasked || "";
      settings.maskSensitive = loaded.maskSensitive;
      settings.exportApproval = loaded.exportApproval;
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
    webhookToken: settings.webhookToken.trim() || undefined,
    maskSensitive: settings.maskSensitive,
    exportApproval: settings.exportApproval,
    brandName: settings.brandName.trim(),
    browserTitle: settings.browserTitle.trim(),
    metaDescription: settings.metaDescription.trim(),
    logoUrl: settings.logoUrl.trim(),
    faviconUrl: settings.faviconUrl.trim() || settings.logoUrl.trim()
  })
    .then((saved) => {
      settings.webhookToken = "";
      settings.webhookTokenMasked = saved.webhookTokenMasked || "";
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

function createUser() {
  if (!createUserForm.username.trim() || !createUserForm.displayName.trim() || createUserForm.password.length < 12) {
    message.warning("请填写用户名、显示名和至少 12 位初始密码");
    return;
  }
  savingUser.value = true;
  void store.createAdminUser({
    username: createUserForm.username.trim(),
    displayName: createUserForm.displayName.trim(),
    password: createUserForm.password,
    status: createUserForm.status,
    roles: createUserForm.roles
  })
    .then(() => {
      message.success("用户已创建");
      showCreateUser.value = false;
      createUserForm.username = "";
      createUserForm.displayName = "";
      createUserForm.password = "";
      createUserForm.roles = ["MEETING_ADMIN"];
      return loadSecurityData();
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "用户创建失败");
    })
    .finally(() => {
      savingUser.value = false;
    });
}

function openEditUser(user: AdminUserDto) {
  editingUser.value = user;
  editUserForm.displayName = user.displayName || user.name || user.username;
  editUserForm.status = normalizeUserStatus(user.status);
  editUserForm.roles = user.roles?.length ? [...user.roles] : ["MEETING_ADMIN"];
  showEditUser.value = true;
}

function submitEditUser() {
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
  if (isBuiltInAdmin(editingUser.value)) {
    /*
     * 前端和后端同时保护内置 admin：系统设置权限固定识别该用户名，
     * 允许改显示名，但不允许停用或移除 SUPER_ADMIN，避免把全局后台入口锁死。
     */
    editUserForm.status = "ACTIVE";
    editUserForm.roles = ensureRole(editUserForm.roles, "SUPER_ADMIN");
  }
  savingUser.value = true;
  void store.updateAdminUser(editingUser.value.id, {
    /*
     * 后端当前只允许更新显示名、状态和角色；用户名作为登录标识保持只读，
     * 避免改名后已有授权、审计日志和当前会话出现难以追踪的身份变更。
     */
    displayName: editUserForm.displayName.trim(),
    status: editUserForm.status,
    roles: editUserForm.roles
  })
    .then(() => {
      message.success("用户信息已保存");
      showEditUser.value = false;
      editingUser.value = null;
      return loadSecurityData();
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "用户信息保存失败");
    })
    .finally(() => {
      savingUser.value = false;
    });
}

function normalizeUserStatus(status?: string) {
  return isActiveStatus(status) ? "ACTIVE" : "DISABLED";
}

function isBuiltInAdmin(user?: AdminUserDto | null) {
  return (user?.username || "").trim().toLocaleLowerCase("en-US") === "admin";
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
  if (!resettingUser.value || resetPassword.value.length < 12) {
    message.warning("新密码至少 12 位");
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

function grantPermission() {
  if (!grantForm.meetingId || !grantForm.userId || !grantForm.role) {
    message.warning("请选择会议、用户和权限角色");
    return;
  }
  savingPermission.value = true;
  void store.grantMeetingUserPermission({ ...grantForm })
    .then(() => {
      message.success("会议权限已保存");
      showGrantPermission.value = false;
      return loadSecurityData();
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "会议权限保存失败");
    })
    .finally(() => {
      savingPermission.value = false;
    });
}

function revokePermission(row: MeetingUserPermissionDto) {
  if (row.id == null) return;
  savingPermission.value = true;
  void store.deleteMeetingUserPermission(row.id)
    .then(() => {
      message.success("会议权限已撤销");
      return loadSecurityData();
    })
    .catch((error) => {
      message.error(error instanceof Error ? error.message : "会议权限撤销失败");
    })
    .finally(() => {
      savingPermission.value = false;
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

.access-meeting-card strong {
  font-size: 18px;
  line-height: 30px;
}

.access-tabs-card :deep(.n-card__content) {
  padding: 16px 20px 20px;
}

.access-sub-tabs :deep(.n-tab-pane) {
  padding-top: 16px;
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

.settings-modal :deep(.n-card-header) {
  padding: 22px 24px 10px;
}

.settings-modal :deep(.n-card__content) {
  padding: 8px 24px 4px;
}

.settings-modal :deep(.n-card__footer) {
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

  .access-sub-tabs :deep(.n-tabs-nav) {
    overflow-x: auto;
  }

  .mobile-scroll-table {
    width: 100%;
    overflow-x: auto;
  }

  .mobile-scroll-table :deep(.n-data-table) {
    min-width: 720px;
  }
}
</style>
