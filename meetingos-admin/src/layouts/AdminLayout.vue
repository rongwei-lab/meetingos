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
        <template v-for="item in visibleNavItems" :key="item.key">
          <button
            class="nav-item"
            :class="{ active: isNavActive(item), 'has-children': hasVisibleNavChildren(item) }"
            :aria-expanded="hasVisibleNavChildren(item) ? isNavExpanded(item) : undefined"
            @click="handleNavItemClick(item)"
          >
            <n-icon class="nav-icon" :component="item.icon" />
            <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
            <n-badge v-if="item.badge && !collapsed" :value="item.badge" type="error" />
            <n-icon
              v-if="!collapsed && hasVisibleNavChildren(item)"
              class="nav-expand-icon"
              :class="{ expanded: isNavExpanded(item) }"
              :component="ChevronForwardOutline"
            />
          </button>
          <transition name="nav-child-collapse">
            <div v-if="!collapsed && isNavExpanded(item)" class="nav-child-list">
              <button
                v-for="child in visibleNavChildren(item)"
                :key="child.key"
                class="nav-child-item"
                :class="{ active: isNavChildActive(child) }"
                @click="navigateNav(child)"
              >
                <n-icon v-if="child.icon" class="nav-child-icon" :component="child.icon" />
                <span>{{ child.label }}</span>
              </button>
            </div>
          </transition>
        </template>
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

          <n-tooltip v-if="showHomeButton" trigger="hover">
            <template #trigger>
              <n-button
                secondary
                size="small"
                class="form-home-button"
                :loading="openingTduckHome"
                :disabled="openingTduckHome"
                @click="openTduckHome"
              >
                <template #icon>
                  <n-icon :component="DocumentTextOutline" />
                </template>
                SORL 表单
              </n-button>
            </template>
            前往 SORL 表单首页
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
          <template v-for="item in visibleNavItems" :key="item.key">
            <button
              class="mobile-nav-item"
              :class="{ active: isNavActive(item), 'has-children': hasVisibleNavChildren(item) }"
              :aria-expanded="hasVisibleNavChildren(item) ? isNavExpanded(item) : undefined"
              @click="handleNavItemClick(item)"
            >
              <n-icon class="nav-icon" :component="item.icon" />
              <span>{{ item.label }}</span>
              <n-badge v-if="item.badge" :value="item.badge" type="error" />
              <n-icon
                v-if="hasVisibleNavChildren(item)"
                class="mobile-nav-expand-icon"
                :class="{ expanded: isNavExpanded(item) }"
                :component="ChevronForwardOutline"
              />
            </button>
            <transition name="nav-child-collapse">
              <div v-if="isNavExpanded(item)" class="mobile-nav-child-list">
                <button
                  v-for="child in visibleNavChildren(item)"
                  :key="child.key"
                  class="mobile-nav-child-item"
                  :class="{ active: isNavChildActive(child) }"
                  @click="navigateNav(child)"
                >
                  <n-icon v-if="child.icon" class="nav-child-icon" :component="child.icon" />
                  <span>{{ child.label }}</span>
                </button>
              </div>
            </transition>
          </template>
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
import { NIcon, useMessage, type DropdownOption } from "naive-ui";
import {
  BarChartOutline,
  BedOutline,
  BusinessOutline,
  ChevronBackOutline,
  ChevronDown,
  ChevronForwardOutline,
  DocumentTextOutline,
  DownloadOutline,
  GridOutline,
  HomeOutline,
  ListOutline,
  LogOutOutline,
  MenuOutline,
  MailOpenOutline,
  PeopleOutline,
  ReaderOutline,
  RestaurantOutline,
  SearchOutline,
  SettingsOutline
} from "@vicons/ionicons5";
import ThemeSwitcher from "@/components/ThemeSwitcher.vue";
import { extractErrorMessage } from "@/api/http";
import { useIsMobile } from "@/composables/useResponsive";
import { useAuthStore } from "@/stores/auth";
import { useMeetingStore } from "@/stores/meeting";
import {
  closeTduckLaunchTab,
  navigateTduckLaunchTab,
  openTduckLaunchTab,
  persistTduckBrowserSession
} from "@/utils/tduckNavigation";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const meetingStore = useMeetingStore();
const message = useMessage();
const collapsed = ref(false);
const mobileNavVisible = ref(false);
const expandedNavKeys = ref(new Set<string>());
const searchFocused = ref(false);
const searchKeyword = ref("");
const pageBodyRef = ref<HTMLElement>();
const pendingNavTarget = ref("");
const openingTduckHome = ref(false);
let queuedNavigation: (() => void) | undefined;
let navUnlockTimer: number | undefined;
const NAV_UNLOCK_DELAY_MS = 500;

interface NavLocationItem {
  key: string;
  label: string;
  routeName: RouteRecordName;
  scope: "global" | "meeting";
  icon?: Component;
  query?: Record<string, string>;
  badge?: number;
  systemAdminOnly?: boolean;
}

interface NavChildItem extends NavLocationItem {}

interface NavItem extends NavLocationItem {
  icon: Component;
  children?: NavChildItem[];
}

const navItems: NavItem[] = [
  { key: "workspace", label: "工作区", routeName: "meeting-workspace", scope: "meeting", icon: GridOutline },
  { key: "registration", label: "报名配置", routeName: "meeting-registration", scope: "meeting", icon: ReaderOutline },
  { key: "invitations", label: "邀请函", routeName: "meeting-invitations", scope: "meeting", icon: MailOpenOutline },
  { key: "attendees", label: "参会人管理", routeName: "meeting-attendees", scope: "meeting", icon: PeopleOutline },
  { key: "seating", label: "座位管理", routeName: "meeting-seating", scope: "meeting", icon: BusinessOutline },
  { key: "rooms", label: "住宿管理", routeName: "meeting-rooms", scope: "meeting", icon: BedOutline },
  { key: "dinner", label: "晚宴管理", routeName: "meeting-dinner", scope: "meeting", icon: RestaurantOutline },
  {
    key: "detail",
    label: "查询设置",
    routeName: "meeting-detail",
    scope: "meeting",
    icon: SettingsOutline,
    query: { section: "entry" },
    children: [
      { key: "detail-entry", label: "入口总览", routeName: "meeting-detail", scope: "meeting", icon: HomeOutline, query: { section: "entry" } },
      { key: "detail-content", label: "公开页设置", routeName: "meeting-detail", scope: "meeting", icon: ReaderOutline, query: { section: "content" } },
      { key: "detail-test", label: "后台测试", routeName: "meeting-detail", scope: "meeting", icon: SearchOutline, query: { section: "test" } },
      { key: "detail-history", label: "查询结果", routeName: "meeting-detail", scope: "meeting", icon: ListOutline, query: { section: "history" } }
    ]
  },
  { key: "statistics", label: "数据统计", routeName: "meeting-statistics", scope: "meeting", icon: BarChartOutline },
  { key: "exports", label: "数据导出", routeName: "meeting-exports", scope: "meeting", icon: DownloadOutline },
  { key: "logs", label: "操作日志", routeName: "meeting-logs", scope: "meeting", icon: ListOutline, systemAdminOnly: true }
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
  return visibleNavItems.value.find((item) => item.routeName === route.name)?.label || "会议管理系统";
});

const userInitial = computed(() => auth.user.name?.trim().slice(0, 1) || "管");
const showHomeButton = computed(() => route.name !== "meetings");
const showSidebar = computed(() => route.name !== "meetings");
const branding = computed(() => meetingStore.branding);
const brandInitial = computed(() => branding.value.brandName?.trim().slice(0, 1) || "M");
const isMobile = useIsMobile();

const visibleNavItems = computed(() =>
  navItems.filter((item) => {
    if (!item.systemAdminOnly) return true;
    return auth.canAccessSystemSettings;
  })
);

const searchableNavItems = computed(() =>
  visibleNavItems.value.flatMap((item) => [
    { item, label: item.label },
    ...visibleNavChildren(item).map((child) => ({ item: child, label: `${item.label} / ${child.label}` }))
  ])
);

watch(
  () => route.fullPath,
  () => {
    syncActiveNavExpansion();
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
  const menuResults = searchableNavItems.value
    .filter(({ item, label }) => matchesKeyword(normalizedKeyword, label, navPath(item), ...Object.values(navQuery(item))))
    .slice(0, 4)
    .map(({ item, label }) => ({
      key: `menu:${item.key}`,
      label,
      type: "功能",
      path: navPath(item),
      query: navQuery(item)
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

function navPath(item: NavLocationItem, meetingId = meetingStore.currentMeetingId) {
  if (item.scope === "global") return "/meetings";
  return meetingPath(item.routeName === "meeting-detail" ? "detail" : item.key, meetingId);
}

function navQuery(item: NavLocationItem) {
  return item.query || {};
}

function visibleNavChildren(item: NavItem) {
  return (item.children || []).filter((child) => {
    if (!child.systemAdminOnly) return true;
    return auth.canAccessSystemSettings;
  });
}

function hasVisibleNavChildren(item: NavItem) {
  return visibleNavChildren(item).length > 0;
}

function setNavExpanded(key: string, expanded: boolean) {
  const next = new Set(expandedNavKeys.value);
  if (expanded) next.add(key);
  else next.delete(key);
  expandedNavKeys.value = next;
}

function toggleNavExpansion(item: NavItem) {
  setNavExpanded(item.key, !isNavExpanded(item));
}

function findParentNavItem(child: NavLocationItem) {
  return visibleNavItems.value.find((item) => visibleNavChildren(item).some((candidate) => candidate.key === child.key));
}

function syncActiveNavExpansion() {
  const activeParent = visibleNavItems.value.find((item) => hasVisibleNavChildren(item) && item.routeName === route.name);
  if (activeParent) setNavExpanded(activeParent.key, true);
}

function normalizedRouteQueryValue(key: string) {
  const value = route.query[key];
  if (Array.isArray(value)) return String(value[0] || "");
  if (value !== undefined && value !== null) return String(value);
  // 查询设置详情页旧地址没有 section，按入口总览处理，保证旧链接和二级菜单高亮一致。
  if (route.name === "meeting-detail" && key === "section") return "entry";
  return "";
}

function navQueryMatches(item: NavLocationItem) {
  return Object.entries(navQuery(item)).every(([key, value]) => normalizedRouteQueryValue(key) === value);
}

function navLocationKey(item: NavLocationItem, meetingId = meetingStore.currentMeetingId) {
  return buildLocationKey(navPath(item, meetingId), navQuery(item));
}

function buildLocationKey(path: string, query: Record<string, string> = {}) {
  const entries = Object.entries(query)
    .filter(([, value]) => value !== "")
    .sort(([left], [right]) => left.localeCompare(right));
  if (!entries.length) return path;
  return `${path}?${new URLSearchParams(entries).toString()}`;
}

function currentLocationKey() {
  const query: Record<string, string> = route.name === "meeting-detail" ? { section: normalizedRouteQueryValue("section") || "entry" } : {};
  return buildLocationKey(route.path, query);
}

function isNavActive(item: NavItem) {
  if (visibleNavChildren(item).length) {
    return item.routeName === route.name || visibleNavChildren(item).some((child) => isNavChildActive(child));
  }
  return item.routeName === route.name && navQueryMatches(item);
}

function isNavExpanded(item: NavItem) {
  return hasVisibleNavChildren(item) && expandedNavKeys.value.has(item.key);
}

function isNavChildActive(item: NavChildItem) {
  return item.routeName === route.name && navQueryMatches(item);
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

function clearPendingNavigation(targetKey: string) {
  if (pendingNavTarget.value === targetKey) pendingNavTarget.value = "";
}

function pushNavTarget(targetKey: string, push: () => ReturnType<typeof router.push>) {
  if (targetKey === currentLocationKey()) {
    mobileNavVisible.value = false;
    return;
  }

  if (pendingNavTarget.value) {
    queuedNavigation = () => pushNavTarget(targetKey, push);
    mobileNavVisible.value = false;
    return;
  }

  pendingNavTarget.value = targetKey;
  void push()
    .then((failure) => {
      if (failure) clearPendingNavigation(targetKey);
    })
    .catch(() => {
      clearPendingNavigation(targetKey);
    });
}

function handleNavItemClick(item: NavItem) {
  if (hasVisibleNavChildren(item)) {
    toggleNavExpansion(item);
    if (!isNavActive(item)) navigateNav(item);
    return;
  }
  navigateNav(item);
}

function navigateNav(item: NavLocationItem) {
  if (item.systemAdminOnly && !auth.canAccessSystemSettings) {
    mobileNavVisible.value = false;
    return;
  }

  const parent = findParentNavItem(item);
  if (parent) setNavExpanded(parent.key, true);

  const targetKey = navLocationKey(item);
  if (targetKey === currentLocationKey()) {
    mobileNavVisible.value = false;
    return;
  }

  if (item.scope === "global") {
    pushNavTarget(targetKey, () => router.push({ name: item.routeName, query: navQuery(item) }));
    return;
  }
  if (!meetingStore.currentMeetingId) {
    pushNavTarget(targetKey, () => router.push({ name: "meetings" }));
    return;
  }
  pushNavTarget(targetKey, () => router.push({ name: item.routeName, params: { meetingId: meetingStore.currentMeetingId }, query: navQuery(item) }));
}

function goHome() {
  pushNavTarget("/meetings", () => router.push({ name: "meetings" }));
}

async function openTduckHome() {
  const meetingId = meetingStore.currentMeetingId;
  if (!meetingId) {
    message.warning("请先进入一个可管理的会议，再打开 SORL 表单");
    return;
  }

  // 必须在用户点击的同步阶段打开标签页，否则等待接口返回后会被浏览器当作弹窗拦截。
  const targetWindow = openTduckLaunchTab();
  if (!targetWindow) {
    message.warning("浏览器阻止了新标签页，请允许本站打开新页面后重试");
    return;
  }

  openingTduckHome.value = true;
  try {
    const session = await meetingStore.loadTduckBrowserSession(meetingId);
    persistTduckBrowserSession(session);
    if (!navigateTduckLaunchTab(targetWindow, "/tduck/home")) {
      message.warning("新标签页已关闭，请重新操作");
    }
  } catch (error) {
    closeTduckLaunchTab(targetWindow);
    message.error(extractErrorMessage(error, "同步 TDuck 登录态失败，请检查 TDuck 管理员账号配置"));
  } finally {
    openingTduckHome.value = false;
  }
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
  syncActiveNavExpansion();
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
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
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

.nav-item.has-children {
  padding-right: 10px;
}

.nav-label {
  min-width: 0;
  flex: 1 1 auto;
}

.nav-expand-icon,
.mobile-nav-expand-icon {
  flex: 0 0 auto;
  color: currentColor;
  font-size: 15px;
  opacity: 0.72;
  transition:
    transform 180ms ease,
    opacity 160ms ease;
}

.nav-expand-icon.expanded,
.mobile-nav-expand-icon.expanded {
  opacity: 1;
  transform: rotate(90deg);
}

.nav-child-list {
  display: grid;
  gap: 2px;
  margin: 2px 0 8px 32px;
  padding-left: 0;
}

.nav-child-item {
  width: 100%;
  min-height: 32px;
  border: 0;
  border-radius: 6px;
  padding: 0 10px;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--sidebar-muted);
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.2;
  text-align: left;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
}

.nav-child-item:hover {
  color: var(--text-primary);
  background: var(--surface-muted);
  transform: translateX(2px);
}

.nav-child-item.active {
  color: var(--sidebar-active-text);
  background: var(--sidebar-active-bg);
  font-weight: 650;
}

.nav-child-icon {
  width: 16px;
  height: 16px;
  font-size: 16px;
  color: currentColor;
  opacity: 0.82;
}

.nav-child-collapse-enter-active,
.nav-child-collapse-leave-active {
  max-height: 220px;
  overflow: hidden;
  transition:
    max-height 220ms ease,
    opacity 180ms ease,
    transform 180ms ease;
}

.nav-child-collapse-enter-from,
.nav-child-collapse-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-6px);
}

.nav-child-collapse-enter-to,
.nav-child-collapse-leave-from {
  max-height: 220px;
  opacity: 1;
  transform: translateY(0);
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

.home-button,
.form-home-button {
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
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.mobile-nav-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-active-text);
  font-weight: 700;
}

.mobile-nav-child-list {
  display: grid;
  gap: 4px;
  margin: -2px 0 6px 28px;
  padding-left: 0;
}

.mobile-nav-child-item {
  min-height: 36px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
}

.mobile-nav-child-item:hover {
  transform: translateX(2px);
}

.mobile-nav-child-item.active {
  color: var(--sidebar-active-text);
  background: var(--sidebar-active-bg);
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

@media (prefers-reduced-motion: reduce) {
  .nav-item,
  .collapse-button,
  .nav-expand-icon,
  .mobile-nav-expand-icon,
  .nav-child-item,
  .mobile-nav-child-item,
  .nav-child-collapse-enter-active,
  .nav-child-collapse-leave-active {
    transition: none;
  }
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
  .form-home-button,
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
