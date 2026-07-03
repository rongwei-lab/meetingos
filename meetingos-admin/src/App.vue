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
import { useMeetingStore } from "@/stores/meeting";
import { usePreferenceStore } from "@/stores/preferences";

const preferences = usePreferenceStore();
const meetingStore = useMeetingStore();
preferences.initTheme();

onMounted(() => {
  void meetingStore.loadBranding();
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
