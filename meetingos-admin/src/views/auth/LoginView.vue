<template>
  <div class="login-page">
    <div class="login-theme-switcher">
      <ThemeSwitcher variant="login" />
    </div>

    <main class="login-shell" aria-label="账号登录">
      <section class="login-visual" aria-hidden="true">
        <LoginThemeVisual :variant="preferences.currentTheme.visual" />
      </section>

      <section class="login-panel">
        <div class="login-panel-inner">
          <h1>Hello,</h1>
          <div class="login-tabs">
            <button type="button" class="login-tab active">账号密码登录</button>
          </div>

          <n-form class="login-form" :show-label="false" :show-feedback="false" @submit.prevent="submit">
            <div class="login-field">
              <n-input v-model:value="username" size="large" placeholder="请输入手机号 / 邮箱">
                <template #prefix>
                  <n-icon :component="PersonOutline" />
                </template>
              </n-input>
              <p v-if="showUsernameError">请输入账号</p>
            </div>

            <div class="login-field">
              <n-input v-model:value="password" size="large" type="password" show-password-on="click" placeholder="请输入密码">
                <template #prefix>
                  <n-icon :component="LockClosedOutline" />
                </template>
              </n-input>
              <p v-if="showPasswordError">请输入密码</p>
            </div>

            <n-button class="login-submit" type="primary" size="large" block :loading="loading" @click="submit">登 录</n-button>
          </n-form>

          <div class="login-links">
            <n-button text size="tiny" @click="message.info('请联系系统管理员开通账号')">立即注册</n-button>
            <span></span>
            <n-button text size="tiny" @click="message.info('请联系系统管理员重置密码')">忘记密码</n-button>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { LockClosedOutline, PersonOutline } from "@vicons/ionicons5";
import LoginThemeVisual from "@/components/LoginThemeVisual.vue";
import ThemeSwitcher from "@/components/ThemeSwitcher.vue";
import { useAuthStore } from "@/stores/auth";
import { usePreferenceStore } from "@/stores/preferences";

const username = ref("");
const password = ref("");
const loading = ref(false);
const submitted = ref(false);
const router = useRouter();
const route = useRoute();
const message = useMessage();
const auth = useAuthStore();
const preferences = usePreferenceStore();
const showUsernameError = computed(() => submitted.value && !username.value.trim());
const showPasswordError = computed(() => submitted.value && !password.value.trim());

async function submit() {
  submitted.value = true;
  if (showUsernameError.value || showPasswordError.value) return;
  loading.value = true;
  try {
    await auth.login(username.value.trim(), password.value);
    message.success("登录成功");
    router.push((route.query.redirect as string) || "/meetings");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "登录失败");
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: var(--login-page-bg);
}

.login-theme-switcher {
  position: fixed;
  top: 26px;
  right: 30px;
  z-index: 5;
}

.login-shell {
  width: min(860px, 100%);
  min-height: 456px;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(270px, 0.75fr);
  overflow: hidden;
  border-radius: 16px;
  background: var(--login-shell-bg);
  box-shadow: var(--login-shadow);
  backdrop-filter: blur(10px);
}

.login-visual {
  display: grid;
  place-items: center;
  padding: 48px 32px 44px 48px;
}

.login-panel {
  display: grid;
  align-items: center;
  border-left: 1px solid var(--login-panel-border);
  background: var(--login-panel-bg);
}

.login-panel-inner {
  width: 100%;
  padding: 48px 28px;
}

.login-panel h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 30px;
  line-height: 38px;
  font-weight: 800;
}

.login-tabs {
  margin: 30px 0 20px;
  border-bottom: 1px solid var(--login-panel-border);
}

.login-tab {
  height: 34px;
  border: 0;
  border-bottom: 2px solid transparent;
  padding: 0 0 8px;
  background: transparent;
  color: var(--accent-color);
  font-size: 13px;
  font-weight: 700;
  cursor: default;
}

.login-tab.active {
  border-color: var(--accent-color);
}

.login-form {
  display: grid;
  gap: 10px;
}

.login-field {
  min-height: 58px;
}

.login-field :deep(.n-input) {
  border-radius: 8px;
}

.login-field :deep(.n-input .n-input-wrapper) {
  padding-inline: 12px;
}

.login-field :deep(.n-input__input-el) {
  font-size: 13px;
}

.login-field p {
  margin: 5px 0 0;
  color: var(--accent-tertiary);
  font-size: 12px;
  line-height: 16px;
}

.login-submit {
  height: 40px;
  margin-top: 2px;
  border-radius: 8px;
  font-weight: 700;
}

.login-links {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  margin-top: 22px;
  color: var(--text-secondary);
}

.login-links span {
  width: 1px;
  height: 14px;
  background: var(--login-panel-border);
}

.login-links :deep(.n-button:first-child) {
  justify-self: start;
}

.login-links :deep(.n-button:last-child) {
  justify-self: end;
}

@media (max-width: 860px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 460px;
  }

  .login-visual {
    padding: 34px 26px 16px;
  }

  .login-panel {
    border-left: 0;
    border-top: 1px solid var(--login-panel-border);
  }

  .login-panel-inner {
    padding: 30px 26px 34px;
  }
}

@media (max-width: 520px) {
  .login-page {
    padding: 18px;
  }

  .login-theme-switcher {
    top: 16px;
    right: 16px;
  }

  .login-shell {
    min-height: auto;
    border-radius: 14px;
  }

  .login-panel h1 {
    font-size: 28px;
  }
}
</style>
