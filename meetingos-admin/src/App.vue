<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted } from "vue";
import { NConfigProvider, NDialogProvider, NMessageProvider, dateZhCN, zhCN, type GlobalThemeOverrides } from "naive-ui";
import { http, unwrapApi } from "@/api/http";
import { usePreferenceStore } from "@/stores/preferences";
import { applyDocumentBranding, type DocumentBranding } from "@/utils/documentBranding";

const preferences = usePreferenceStore();
preferences.initTheme();

onMounted(() => {
  /* 文档标题只依赖轻量公开接口，避免 App 首屏静态加载完整会议业务 store。 */
  void http.get<DocumentBranding>("/public/branding")
    .then((response) => applyDocumentBranding(unwrapApi(response)))
    .catch(() => {
      // 品牌接口不可用时保留 index.html 的默认标题，不阻断页面主流程。
    });
});

const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const theme = preferences.currentTheme.naive;
  return {
    common: {
      primaryColor: theme.primaryColor,
      primaryColorHover: theme.primaryColorHover,
      primaryColorPressed: theme.primaryColorPressed,
      primaryColorSuppl: theme.primaryColorSuppl,
      borderRadius: "8px",
      fontFamily:
        "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    },
    Button: {
      primaryColor: theme.primaryColor,
      primaryColorHover: theme.primaryColorHover,
      primaryColorPressed: theme.primaryColorPressed,
      primaryColorFocus: theme.primaryColor,
      textColorPrimary: "#ffffff",
      borderPrimary: `1px solid ${theme.primaryColor}`,
      borderHoverPrimary: `1px solid ${theme.primaryColorHover}`,
      borderPressedPrimary: `1px solid ${theme.primaryColorPressed}`
    },
    Tag: {
      borderRadius: "6px"
    }
  };
});
</script>
