export type AppThemeKey = "sky" | "violet" | "sunset" | "rose";
export type LoginVisualVariant = "checkin" | "dashboard" | "rooms" | "seating";

interface ThemeColors {
  primaryColor: string;
  primaryColorHover: string;
  primaryColorPressed: string;
  primaryColorSuppl: string;
}

export interface AppTheme {
  key: AppThemeKey;
  label: string;
  description: string;
  visual: LoginVisualVariant;
  swatch: [string, string, string];
  naive: ThemeColors;
  cssVars: Record<string, string>;
}

export const DEFAULT_APP_THEME_KEY: AppThemeKey = "sky";

export const APP_THEMES: AppTheme[] = [
  {
    key: "sky",
    label: "晴空蓝",
    description: "清爽、明亮的默认主题",
    visual: "checkin",
    swatch: ["#3b82f6", "#60a5fa", "#fbbf24"],
    naive: {
      primaryColor: "#3b82f6",
      primaryColorHover: "#60a5fa",
      primaryColorPressed: "#2563eb",
      primaryColorSuppl: "#93c5fd"
    },
    cssVars: {
      "--app-bg": "#f7fbff",
      "--surface-bg": "#ffffff",
      "--surface-muted": "#f2f8ff",
      "--text-primary": "#102033",
      "--text-secondary": "#5f7185",
      "--text-muted": "#90a4b0",
      "--border-color": "#dbe8f7",
      "--shadow-soft": "0 8px 22px rgba(59, 130, 246, 0.065)",
      "--accent-color": "#3b82f6",
      "--accent-hover": "#60a5fa",
      "--accent-pressed": "#2563eb",
      "--accent-soft": "#eaf4ff",
      "--accent-border": "#bfdbfe",
      "--accent-secondary": "#fbbf24",
      "--accent-tertiary": "#fb7185",
      "--sidebar-bg": "#ffffff",
      "--sidebar-text": "#475569",
      "--sidebar-muted": "#91a3b0",
      "--sidebar-active-bg": "#eaf4ff",
      "--sidebar-active-text": "#2563eb",
      "--sidebar-border": "#dbe8f7",
      "--admin-shell-gradient": "linear-gradient(180deg, rgba(219, 234, 254, 0.58), rgba(247, 251, 255, 0) 260px)",
      "--login-page-bg":
        "radial-gradient(circle at 15% 28%, rgba(96, 165, 250, 0.18), transparent 30%), radial-gradient(circle at 84% 20%, rgba(191, 219, 254, 0.56), transparent 28%), radial-gradient(circle at 78% 82%, rgba(254, 202, 202, 0.24), transparent 24%), linear-gradient(135deg, #f7fbff 0%, #ffffff 45%, #f2f8ff 100%)",
      "--login-shell-bg": "rgba(255, 255, 255, 0.9)",
      "--login-panel-bg": "rgba(255, 255, 255, 0.78)",
      "--login-panel-border": "#eef2f8",
      "--login-shadow": "0 24px 70px rgba(58, 103, 174, 0.13)",
      "--login-visual-bg": "#eef7ff",
      "--login-visual-surface": "#ffffff",
      "--login-line": "#c9dcf4"
    }
  },
  {
    key: "violet",
    label: "浅紫",
    description: "柔和、精致的紫色主题",
    visual: "dashboard",
    swatch: ["#8b5cf6", "#c084fc", "#fb7185"],
    naive: {
      primaryColor: "#8b5cf6",
      primaryColorHover: "#a78bfa",
      primaryColorPressed: "#7c3aed",
      primaryColorSuppl: "#c4b5fd"
    },
    cssVars: {
      "--app-bg": "#fbf8ff",
      "--surface-bg": "#ffffff",
      "--surface-muted": "#f6f0ff",
      "--text-primary": "#23183b",
      "--text-secondary": "#756487",
      "--text-muted": "#a797b6",
      "--border-color": "#e8ddf8",
      "--shadow-soft": "0 8px 22px rgba(139, 92, 246, 0.075)",
      "--accent-color": "#8b5cf6",
      "--accent-hover": "#a78bfa",
      "--accent-pressed": "#7c3aed",
      "--accent-soft": "#f3edff",
      "--accent-border": "#d8c7ff",
      "--accent-secondary": "#fb7185",
      "--accent-tertiary": "#fbbf24",
      "--sidebar-bg": "#ffffff",
      "--sidebar-text": "#61536f",
      "--sidebar-muted": "#a797b6",
      "--sidebar-active-bg": "#f3edff",
      "--sidebar-active-text": "#7c3aed",
      "--sidebar-border": "#e8ddf8",
      "--admin-shell-gradient": "linear-gradient(180deg, rgba(237, 233, 254, 0.72), rgba(251, 248, 255, 0) 260px)",
      "--login-page-bg":
        "radial-gradient(circle at 18% 24%, rgba(167, 139, 250, 0.22), transparent 30%), radial-gradient(circle at 86% 18%, rgba(251, 113, 133, 0.2), transparent 27%), radial-gradient(circle at 74% 84%, rgba(251, 191, 36, 0.17), transparent 24%), linear-gradient(135deg, #fbf8ff 0%, #ffffff 45%, #f6f0ff 100%)",
      "--login-shell-bg": "rgba(255, 255, 255, 0.91)",
      "--login-panel-bg": "rgba(255, 255, 255, 0.8)",
      "--login-panel-border": "#ede4fb",
      "--login-shadow": "0 24px 70px rgba(109, 40, 217, 0.13)",
      "--login-visual-bg": "#f4efff",
      "--login-visual-surface": "#ffffff",
      "--login-line": "#d9ccf2"
    }
  },
  {
    key: "sunset",
    label: "暖橙",
    description: "轻快、有温度的橙色主题",
    visual: "rooms",
    swatch: ["#f97316", "#fb923c", "#38bdf8"],
    naive: {
      primaryColor: "#f97316",
      primaryColorHover: "#fb923c",
      primaryColorPressed: "#ea580c",
      primaryColorSuppl: "#fdba74"
    },
    cssVars: {
      "--app-bg": "#fff8f1",
      "--surface-bg": "#ffffff",
      "--surface-muted": "#fff1df",
      "--text-primary": "#2d1c10",
      "--text-secondary": "#7b6652",
      "--text-muted": "#b49b82",
      "--border-color": "#f3dfc7",
      "--shadow-soft": "0 8px 22px rgba(249, 115, 22, 0.075)",
      "--accent-color": "#f97316",
      "--accent-hover": "#fb923c",
      "--accent-pressed": "#ea580c",
      "--accent-soft": "#fff0df",
      "--accent-border": "#fed7aa",
      "--accent-secondary": "#38bdf8",
      "--accent-tertiary": "#a78bfa",
      "--sidebar-bg": "#ffffff",
      "--sidebar-text": "#6f5a44",
      "--sidebar-muted": "#b49b82",
      "--sidebar-active-bg": "#fff0df",
      "--sidebar-active-text": "#ea580c",
      "--sidebar-border": "#f3dfc7",
      "--admin-shell-gradient": "linear-gradient(180deg, rgba(255, 237, 213, 0.74), rgba(255, 248, 241, 0) 260px)",
      "--login-page-bg":
        "radial-gradient(circle at 15% 28%, rgba(251, 146, 60, 0.2), transparent 30%), radial-gradient(circle at 84% 20%, rgba(56, 189, 248, 0.18), transparent 27%), radial-gradient(circle at 78% 82%, rgba(167, 139, 250, 0.17), transparent 24%), linear-gradient(135deg, #fff8f1 0%, #ffffff 45%, #fff1df 100%)",
      "--login-shell-bg": "rgba(255, 255, 255, 0.91)",
      "--login-panel-bg": "rgba(255, 255, 255, 0.8)",
      "--login-panel-border": "#f4e2cd",
      "--login-shadow": "0 24px 70px rgba(194, 87, 12, 0.13)",
      "--login-visual-bg": "#fff1df",
      "--login-visual-surface": "#ffffff",
      "--login-line": "#efd1b0"
    }
  },
  {
    key: "rose",
    label: "樱粉蓝",
    description: "活泼、轻盈的粉蓝主题",
    visual: "seating",
    swatch: ["#ec4899", "#60a5fa", "#fbbf24"],
    naive: {
      primaryColor: "#ec4899",
      primaryColorHover: "#f472b6",
      primaryColorPressed: "#db2777",
      primaryColorSuppl: "#f9a8d4"
    },
    cssVars: {
      "--app-bg": "#fff7fb",
      "--surface-bg": "#ffffff",
      "--surface-muted": "#fff0f7",
      "--text-primary": "#321827",
      "--text-secondary": "#7a6170",
      "--text-muted": "#b79aa9",
      "--border-color": "#f3d9e7",
      "--shadow-soft": "0 8px 22px rgba(236, 72, 153, 0.07)",
      "--accent-color": "#ec4899",
      "--accent-hover": "#f472b6",
      "--accent-pressed": "#db2777",
      "--accent-soft": "#fff0f7",
      "--accent-border": "#fbcfe8",
      "--accent-secondary": "#60a5fa",
      "--accent-tertiary": "#fbbf24",
      "--sidebar-bg": "#ffffff",
      "--sidebar-text": "#715666",
      "--sidebar-muted": "#b79aa9",
      "--sidebar-active-bg": "#fff0f7",
      "--sidebar-active-text": "#db2777",
      "--sidebar-border": "#f3d9e7",
      "--admin-shell-gradient": "linear-gradient(180deg, rgba(252, 231, 243, 0.72), rgba(255, 247, 251, 0) 260px)",
      "--login-page-bg":
        "radial-gradient(circle at 15% 28%, rgba(244, 114, 182, 0.22), transparent 30%), radial-gradient(circle at 84% 20%, rgba(96, 165, 250, 0.22), transparent 28%), radial-gradient(circle at 78% 82%, rgba(251, 191, 36, 0.18), transparent 24%), linear-gradient(135deg, #fff7fb 0%, #ffffff 45%, #fff0f7 100%)",
      "--login-shell-bg": "rgba(255, 255, 255, 0.91)",
      "--login-panel-bg": "rgba(255, 255, 255, 0.8)",
      "--login-panel-border": "#f5dce9",
      "--login-shadow": "0 24px 70px rgba(190, 24, 93, 0.12)",
      "--login-visual-bg": "#fff0f7",
      "--login-visual-surface": "#ffffff",
      "--login-line": "#eecbdc"
    }
  }
];

const THEME_MAP = new Map(APP_THEMES.map((theme) => [theme.key, theme]));

export function resolveAppTheme(themeKey?: string | null) {
  return THEME_MAP.get(themeKey as AppThemeKey) || THEME_MAP.get(DEFAULT_APP_THEME_KEY)!;
}
