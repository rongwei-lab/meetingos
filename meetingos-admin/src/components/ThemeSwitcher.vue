<template>
  <n-popover trigger="click" placement="bottom-end" :show-arrow="false" raw>
    <template #trigger>
      <button type="button" class="theme-trigger" :class="`theme-trigger--${variant}`" title="切换主题" :aria-label="triggerLabel">
        <n-icon :component="ColorPaletteOutline" />
      </button>
    </template>

    <div class="theme-menu">
      <button
        v-for="theme in preferences.themes"
        :key="theme.key"
        type="button"
        class="theme-option"
        :class="{ active: theme.key === preferences.theme }"
        @click="preferences.setTheme(theme.key)"
      >
        <span class="theme-swatch" aria-hidden="true">
          <span v-for="color in theme.swatch" :key="color" :style="{ background: color }"></span>
        </span>
        <span class="theme-text">
          <strong>{{ theme.label }}</strong>
          <small>{{ theme.description }}</small>
        </span>
        <n-icon v-if="theme.key === preferences.theme" class="theme-check" :component="CheckmarkOutline" />
      </button>
    </div>
  </n-popover>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { CheckmarkOutline, ColorPaletteOutline } from "@vicons/ionicons5";
import { usePreferenceStore } from "@/stores/preferences";

withDefaults(
  defineProps<{
    variant?: "topbar" | "login";
  }>(),
  {
    variant: "topbar"
  }
);

const preferences = usePreferenceStore();
const triggerLabel = computed(() => `切换主题，当前为${preferences.currentTheme.label}`);
</script>

<style scoped>
.theme-trigger {
  width: 34px;
  height: 34px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  display: inline-grid;
  place-items: center;
  color: var(--accent-pressed);
  background: var(--surface-bg);
  cursor: pointer;
  transition:
    color 160ms ease,
    background 160ms ease,
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.theme-trigger:hover {
  color: var(--accent-color);
  background: var(--accent-soft);
  border-color: var(--accent-border);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
}

.theme-trigger :deep(.n-icon) {
  font-size: 19px;
}

.theme-trigger--login {
  width: 38px;
  height: 38px;
  border-color: color-mix(in srgb, var(--accent-border), white 26%);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
}

.theme-menu {
  width: 254px;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-bg);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.14);
}

.theme-option {
  width: 100%;
  min-height: 54px;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 8px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  color: var(--text-primary);
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.theme-option:hover {
  background: var(--surface-muted);
}

.theme-option.active {
  border-color: var(--accent-border);
  background: var(--accent-soft);
}

.theme-swatch {
  width: 38px;
  height: 26px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 8px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  overflow: hidden;
}

.theme-text {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.theme-text strong,
.theme-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.theme-text strong {
  font-size: 13px;
  line-height: 18px;
  font-weight: 700;
}

.theme-text small {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 16px;
}

.theme-check {
  color: var(--accent-pressed);
  font-size: 17px;
}
</style>
