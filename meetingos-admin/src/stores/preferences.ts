import { defineStore } from "pinia";
import { APP_THEMES, DEFAULT_APP_THEME_KEY, resolveAppTheme, type AppThemeKey } from "@/theme/appThemes";

const LEGACY_THEME_KEYS = ["meetingos.theme", "meetingos.theme.v2"];
const THEME_STORAGE_KEY = "meetingos.app.theme";

function persistTheme(theme: AppThemeKey) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(THEME_STORAGE_KEY, theme);
}

function readSavedTheme() {
  if (typeof window === "undefined") return DEFAULT_APP_THEME_KEY;
  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
  return resolveAppTheme(savedTheme).key;
}

function applyAppTheme(themeKey: AppThemeKey) {
  if (typeof document === "undefined") return;
  const theme = resolveAppTheme(themeKey);
  const root = document.documentElement;
  // 系统已移除深色模式，这里主动清理旧版本保存的深色偏好，避免历史 localStorage 继续影响页面。
  document.documentElement.removeAttribute("data-theme");
  root.dataset.appTheme = theme.key;
  root.style.colorScheme = "light";
  Object.entries(theme.cssVars).forEach(([name, value]) => {
    root.style.setProperty(name, value);
  });
  if (typeof window !== "undefined") {
    LEGACY_THEME_KEYS.forEach((key) => window.localStorage.removeItem(key));
  }
}

export const usePreferenceStore = defineStore("preferences", {
  state: () => ({
    theme: DEFAULT_APP_THEME_KEY as AppThemeKey
  }),
  getters: {
    themes: () => APP_THEMES,
    currentTheme: (state) => resolveAppTheme(state.theme)
  },
  actions: {
    initTheme() {
      this.theme = readSavedTheme();
      applyAppTheme(this.theme);
    },
    setTheme(themeKey: AppThemeKey | string) {
      this.theme = resolveAppTheme(themeKey).key;
      applyAppTheme(this.theme);
      persistTheme(this.theme);
    }
  }
});
