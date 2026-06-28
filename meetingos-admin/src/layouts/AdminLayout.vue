<template>
  <div class="admin-shell">
    <aside v-if="showSidebar && !isMobile" class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">
          <img v-if="branding.logoUrl" :src="branding.logoUrl" :alt="branding.brandName" />
          <span v-else>{{ brandInitial }}</span>
        </div>
        <div v-if="!collapsed">
          <strong>{{ branding.brandName }}</strong>
        </div>
      </div>

      <nav class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: isNavActive(item) }"
          @click="navigateNav(item)"
        >
          <n-icon class="nav-icon" :component="item.icon" />
          <span v-if="!collapsed">{{ item.label }}</span>
          <n-badge v-if="item.badge && !collapsed" :value="item.badge" type="error" />
        </button>
      </nav>

      <div class="sidebar-footer">
        <button
          v-if="auth.canAccessSystemSettings"
          class="nav-item"
          title="系统设置"
          aria-label="系统设置"
          @click="goSettings"
        >
          <n-icon class="nav-icon" :component="SettingsOutline" />
          <span v-if="!collapsed">系统设置</span>
        </button>
        <button class="nav-item" title="退出登录" aria-label="退出登录" @click="logout">
          <n-icon class="nav-icon" :component="LogOutOutline" />
          <span v-if="!collapsed">退出登录</span>
        </button>
        <button
          class="collapse-button"
          :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
          :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="collapsed = !collapsed"
        >
          <n-icon class="nav-icon" :component="collapsed ? ChevronForwardOutline : ChevronBackOutline" />
          <span v-if="!collapsed">收起</span>
        </button>
      </div>
    </aside>

    <section class="main-shell" :class="{ 'full-shell': !showSidebar }">
      <header class="topbar">
        <n-button
          v-if="showSidebar && isMobile"
          class="mobile-menu-button"
          circle
          secondary
          size="small"
          :aria-label="mobileNavVisible ? '关闭菜单' : '打开菜单'"
          @click="mobileNavVisible = true"
        >
          <template #icon>
            <n-icon :component="MenuOutline" />
          </template>
        </n-button>
        <div class="topbar-title">
          <strong>{{ currentTitle }}</strong>
          <span v-if="route.name !== 'meetings' && meetingStore.currentMeeting">
            {{ meetingStore.currentMeeting.name }}
          </span>
        </div>
        <div class="topbar-actions">
          <n-tooltip v-if="showHomeButton" trigger="hover">
            <template #trigger>
              <n-button secondary size="small" class="home-button" @click="goHome">
                <template #icon>
                  <n-icon :component="HomeOutline" />
                </template>
                主页
              </n-button>
            </template>
            返回会议选择页
          </n-tooltip>

          <n-dropdown
            trigger="manual"
            placement="bottom-start"
            :show="searchDropdownVisible"
            :options="searchOptions"
            @select="handleSearchSelect"
          >
            <n-input
              v-model:value="searchKeyword"
              round
              clearable
              placeholder="搜索功能、参会人"
              class="search-input"
              @focus="searchFocused = true"
              @blur="scheduleCloseSearch"
              @keyup.enter="submitSearch"
            >
              <template #prefix>
                <n-icon :component="SearchOutline" />
              </template>
            </n-input>
          </n-dropdown>

          <ThemeSwitcher />

          <n-dropdown trigger="click" placement="bottom-end" :options="userMenuOptions" @select="handleUserMenuSelect">
            <button type="button" class="user-chip">
              <span>{{ userInitial }}</span>
              <div>
                <strong>{{ auth.user.name }}</strong>
                <small>{{ auth.user.email }}</small>
              </div>
              <n-icon class="user-chip-arrow" :component="ChevronDown" />
            </button>
          </n-dropdown>
        </div>
      </header>
      <main ref="pageBodyRef" class="page-body">
        <router-view />
      </main>
    </section>

    <n-drawer
      v-model:show="mobileNavVisible"
      class="mobile-nav-drawer"
      placement="left"
      :width="300"
      display-directive="show"
      :trap-focus="false"
    >
      <n-drawer-content :native-scrollbar="false" closable>
        <template #header>
          <div class="mobile-drawer-brand">
            <div class="brand-mark">
              <img v-if="branding.logoUrl" :src="branding.logoUrl" :alt="branding.brandName" />
              <span v-else>{{ brandInitial }}</span>
            </div>
            <div>
              <strong>{{ branding.brandName }}</strong>
              <span>{{ meetingStore.currentMeeting?.name || "选择会议后管理" }}</span>
            </div>
          </div>
        </template>

        <nav class="mobile-nav-list" aria-label="手机端后台导航">
          <button
            v-for="item in navItems"
            :key="item.key"
            class="mobile-nav-item"
            :class="{ active: isNavActive(item) }"
            @click="navigateNav(item)"
          >
            <n-icon class="nav-icon" :component="item.icon" />
            <span>{{ item.label }}</span>
            <n-badge v-if="item.badge" :value="item.badge" type="error" />
          </button>
        </nav>

        <template #footer>
          <div class="mobile-nav-footer">
            <n-button v-if="auth.canAccessSystemSettings" secondary block @click="goSettings">
              <template #icon><n-icon :component="SettingsOutline" /></template>
              系统设置
            </n-button>
            <n-button secondary block @click="logout">
              <template #icon><n-icon :component="LogOutOutline" /></template>
              退出登录
            </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import type { Component } from "vue";
import { computed, h, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter, type RouteRecordName } from "vue-router";
import { NIcon, type DropdownOption } from "naive-ui";
import {
  BarChartOutline,
  BedOutline,
  BusinessOutline,
  ChevronBackOutline,
  ChevronDown,
  ChevronForwardOutline,
  DownloadOutline,
  GridOutline,
  HomeOutline,
  ListOutline,
  LogOutOutline,
  MenuOutline,
  PeopleOutline,
  ReaderOutline,
  SearchOutline,
  SettingsOutline
} from "@vicons/ionicons5";
import ThemeSwitcher from "@/components/ThemeSwitcher.vue";
import { useIsMobile } from "@/composables/useResponsive";
import { useAuthStore } from "@/stores/auth";
import { useMeetingStore } from "@/stores/meeting";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const meetingStore = useMeetingStore();
const collapsed = ref(false);
const mobileNavVisible = ref(false);
const searchFocused = ref(false);
const searchKeyword = ref("");
const pageBodyRef = ref<HTMLElement>();
const pendingNavTarget = ref("");
let queuedNavigation: (() => void) | undefined;
let navUnlockTimer: number | undefined;
const NAV_UNLOCK_DELAY_MS = 500;

interface NavItem {
  key: string;
  label: string;
  icon: Component;
  routeName: RouteRecordName;
  scope: "global" | "meeting";
  badge?: number;
}

const navItems: NavItem[] = [
  { key: "workspace", label: "工作区", routeName: "meeting-workspace", scope: "meeting", icon: GridOutline },
  { key: "registration", label: "报名配置", routeName: "meeting-registration", scope: "meeting", icon: ReaderOutline },
  { key: "attendees", label: "参会人管理", routeName: "meeting-attendees", scope: "meeting", icon: PeopleOutline },
  { key: "seating", label: "座位管理", routeName: "meeting-seating", scope: "meeting", icon: BusinessOutline },
  { key: "rooms", label: "住宿管理", routeName: "meeting-rooms", scope: "meeting", icon: BedOutline },
  { key: "detail", label: "查询设置", routeName: "meeting-detail", scope: "meeting", icon: SettingsOutline },
  { key: "statistics", label: "数据统计", routeName: "meeting-statistics", scope: "meeting", icon: BarChartOutline },
  { key: "exports", label: "数据导出", routeName: "meeting-exports", scope: "meeting", icon: DownloadOutline },
  { key: "logs", label: "操作日志", routeName: "meeting-logs", scope: "meeting", icon: ListOutline }
];

interface SearchResult {
  key: string;
  label: string;
  type: string;
  path: string;
  query?: Record<string, string>;
}

const currentTitle = computed(() => {
  if (route.name === "meetings") return "会议工作台";
  if (route.path.startsWith("/settings")) return "系统设置";
  return navItems.find((item) => item.routeName === route.name)?.label || "会议管理系统";
});

const userInitial = computed(() => auth.user.name?.trim().slice(0, 1) || "管");
const showHomeButton = computed(() => route.name !== "meetings");
const showSidebar = computed(() => route.name !== "meetings");
const branding = computed(() => meetingStore.branding);
const brandInitial = computed(() => branding.value.brandName?.trim().slice(0, 1) || "M");
const isMobile = useIsMobile();

watch(
  () => route.fullPath,
  () => {
    mobileNavVisible.value = false;
    scheduleUnlockNavigation();
    // 主体区域是独立滚动容器，路由切换后主动回到顶部，
    // 避免从工作区返回会议选择页时沿用上一页的滚动位置。
    void nextTick(() => {
      pageBodyRef.value?.scrollTo({ top: 0, left: 0 });
    });
  }
);

const searchResults = computed<SearchResult[]>(() => {
  const keyword = searchKeyword.value.trim();
  if (!keyword) return [];
  const normalizedKeyword = normalizeSearchText(keyword);

  // 顶部全局搜索只基于当前前端已有数据生成入口，避免为了搜索框额外触发跨模块后端请求。
  const menuResults = navItems
    .filter((item) => matchesKeyword(normalizedKeyword, item.label, navPath(item)))
    .slice(0, 4)
    .map((item) => ({
      key: `menu:${item.key}`,
      label: item.label,
      type: "功能",
      path: navPath(item)
    }));

  const attendeeResults = meetingStore.currentAttendees
    .filter((attendee) => matchesKeyword(normalizedKeyword, attendee.name, attendee.phone, attendee.company, attendee.title))
    .slice(0, 4)
    .map((attendee) => ({
      key: `attendee:${attendee.id}`,
      label: `${attendee.name} · ${attendee.company}`,
      type: "参会人",
      path: meetingPath("attendees"),
      query: { keyword: attendee.name }
    }));

  return [...menuResults, ...attendeeResults].slice(0, 10);
});

const searchResultMap = computed(() => new Map(searchResults.value.map((item) => [item.key, item])));

const searchDropdownVisible = computed(() => searchFocused.value && searchKeyword.value.trim().length > 0);

const searchOptions = computed<DropdownOption[]>(() => {
  if (!searchKeyword.value.trim()) return [];
  if (!searchResults.value.length) {
    return [{ key: "empty", label: "没有匹配结果", disabled: true }];
  }
  return searchResults.value.map((item) => ({
    key: item.key,
    label: `${item.type}：${item.label}`
  }));
});

const userMenuOptions = computed<DropdownOption[]>(() => {
  const options: DropdownOption[] = [];
  if (auth.canAccessSystemSettings) {
    options.push({ label: "系统设置", key: "settings", icon: renderIcon(SettingsOutline) }, { type: "divider", key: "divider" });
  }
  options.push({ label: "退出登录", key: "logout", icon: renderIcon(LogOutOutline) });
  return options;
});

function normalizeSearchText(text: string) {
  return text.toLocaleLowerCase("zh-CN");
}

function matchesKeyword(keyword: string, ...fields: Array<string | undefined>) {
  return fields.some((field) => normalizeSearchText(field || "").includes(keyword));
}

function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) });
}

function scheduleCloseSearch() {
  window.setTimeout(() => {
    searchFocused.value = false;
  }, 150);
}

function openSearchResult(result: SearchResult) {
  searchKeyword.value = "";
  searchFocused.value = false;
  mobileNavVisible.value = false;
  void router.push({ path: result.path, query: result.query });
}

function meetingPath(suffix: string, meetingId = meetingStore.currentMeetingId) {
  return meetingId ? `/meetings/${meetingId}/${suffix}` : "/meetings";
}

function navPath(item: NavItem, meetingId = meetingStore.currentMeetingId) {
  if (item.scope === "global") return "/meetings";
  return meetingPath(item.key, meetingId);
}

function isNavActive(item: NavItem) {
  return item.routeName === route.name;
}

function scheduleUnlockNavigation() {
  if (navUnlockTimer) window.clearTimeout(navUnlockTimer);

  /*
   * 会议业务页包含图表、表格、座位图等重组件。导航后给页面一个短暂稳定窗口，
   * 如果用户期间又点了其他模块，不禁用按钮也不丢点击，而是在窗口结束后执行最后一次点击。
   * 这样避免多页面挂载任务同时堆叠，也不会出现“单击没反应，需要再点一次”的体验。
   */
  navUnlockTimer = window.setTimeout(() => {
    pendingNavTarget.value = "";
    navUnlockTimer = undefined;
    const nextNavigation = queuedNavigation;
    queuedNavigation = undefined;
    nextNavigation?.();
  }, NAV_UNLOCK_DELAY_MS);
}

function clearPendingNavigation(targetPath: string) {
  if (pendingNavTarget.value === targetPath) pendingNavTarget.value = "";
}

function pushNavTarget(targetPath: string, push: () => ReturnType<typeof router.push>) {
  if (targetPath === route.path) {
    mobileNavVisible.value = false;
    return;
  }

  if (pendingNavTarget.value) {
    queuedNavigation = () => pushNavTarget(targetPath, push);
    mobileNavVisible.value = false;
    return;
  }

  pendingNavTarget.value = targetPath;
  void push()
    .then((failure) => {
      if (failure) clearPendingNavigation(targetPath);
    })
    .catch(() => {
      clearPendingNavigation(targetPath);
    });
}

function navigateNav(item: NavItem) {
  const targetPath = navPath(item);
  if (targetPath === route.path) {
    mobileNavVisible.value = false;
    return;
  }

  if (item.scope === "global") {
    pushNavTarget(targetPath, () => router.push({ name: item.routeName }));
    return;
  }
  if (!meetingStore.currentMeetingId) {
    pushNavTarget(targetPath, () => router.push({ name: "meetings" }));
    return;
  }
  pushNavTarget(targetPath, () => router.push({ name: item.routeName, params: { meetingId: meetingStore.currentMeetingId } }));
}

function goHome() {
  pushNavTarget("/meetings", () => router.push({ name: "meetings" }));
}

function goSettings() {
  if (!auth.canAccessSystemSettings) return;
  pushNavTarget("/settings", () => router.push("/settings"));
}

function handleSearchSelect(key: string | number) {
  const result = searchResultMap.value.get(String(key));
  if (result) openSearchResult(result);
}

function submitSearch() {
  const firstResult = searchResults.value[0];
  if (firstResult) openSearchResult(firstResult);
}

function handleUserMenuSelect(key: string | number) {
  if (key === "settings") {
    if (!auth.canAccessSystemSettings) return;
    goSettings();
    return;
  }
  if (key === "logout") {
    logout();
  }
}

async function logout() {
  await auth.logout();
  router.push("/login");
}

onMounted(() => {
  void meetingStore.bootstrap();
});
</script>

<style scoped>
.admin-shell {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background:
    var(--admin-shell-gradient),
    var(--app-bg);
  color: var(--text-primary);
}

.sidebar {
  width: 220px;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-bg);
  color: var(--sidebar-text);
  border-right: 1px solid var(--sidebar-border);
  transition: width 180ms ease;
}

.sidebar.collapsed {
  width: 76px;
}

.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--sidebar-border);
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  overflow: hidden;
  font-weight: 800;
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  color: var(--accent-pressed);
}

.brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  color: var(--text-primary);
  font-size: 14px;
}

.brand span {
  color: var(--sidebar-muted);
  font-size: 12px;
}

.nav-list {
  flex: 1;
  padding: 16px 8px;
  overflow-y: auto;
}

.nav-item,
.collapse-button {
  width: 100%;
  min-height: 40px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--sidebar-text);
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
}

.nav-item:hover {
  background: var(--surface-muted);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-active-text);
  font-weight: 650;
}

.nav-icon {
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.sidebar-footer {
  padding: 12px 8px;
  border-top: 1px solid var(--sidebar-border);
}

.collapse-button {
  justify-content: center;
  color: var(--sidebar-muted);
}

.sidebar:not(.collapsed) .collapse-button {
  justify-content: flex-start;
}

.main-shell {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 56px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface-bg);
  border-bottom: 1px solid var(--border-color);
}

.mobile-menu-button {
  flex: 0 0 auto;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.topbar-title {
  min-width: 0;
}

.topbar-title strong,
.topbar-title span {
  display: block;
}

.topbar-title span {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 12px;
}

.search-input {
  width: 260px;
}

.home-button {
  flex: 0 0 auto;
}

.user-chip {
  border: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 0 0 12px;
  border-left: 1px solid var(--border-color);
  color: var(--text-primary);
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.user-chip > span {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  color: var(--accent-pressed);
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
}

.user-chip strong,
.user-chip small {
  display: block;
}

.user-chip strong {
  font-size: 13px;
}

.user-chip small {
  color: var(--text-secondary);
}

.user-chip-arrow {
  color: var(--text-muted);
  font-size: 14px;
}

.page-body {
  flex: 1;
  overflow: auto;
  padding: 24px;
}

.mobile-drawer-brand {
  min-width: 0;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.mobile-drawer-brand strong,
.mobile-drawer-brand span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-drawer-brand strong {
  color: var(--text-primary);
  font-size: 15px;
}

.mobile-drawer-brand span {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 12px;
}

.mobile-nav-list {
  display: grid;
  gap: 6px;
  padding: 4px 0;
}

.mobile-nav-item {
  width: 100%;
  min-height: 46px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  color: var(--sidebar-text);
  background: transparent;
  cursor: pointer;
  font-size: 15px;
  text-align: left;
}

.mobile-nav-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-active-text);
  font-weight: 700;
}

.mobile-nav-item:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.mobile-nav-footer {
  display: grid;
  gap: 10px;
}

@media (max-width: 800px) {
  .admin-shell {
    min-width: 0;
  }

  .main-shell {
    min-width: 0;
  }

  .topbar {
    min-height: 56px;
    gap: 8px;
    padding: 0 12px;
  }

  .topbar-title {
    flex: 1 1 auto;
  }

  .topbar-title strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .topbar-title span {
    display: none;
  }

  .topbar-actions {
    gap: 6px;
  }

  .page-body {
    padding: 14px;
    padding-bottom: calc(18px + env(safe-area-inset-bottom));
  }

  .search-input,
  .home-button,
  .user-chip div {
    display: none;
  }

  .user-chip {
    padding-left: 6px;
    border-left: 0;
  }

  .user-chip > span {
    width: 30px;
    height: 30px;
  }
}
</style>
