import { createApp } from "vue";
import { createPinia } from "pinia";
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NSpace,
  NSpin
} from "naive-ui";
import App from "./App.vue";
import router from "./router";
import "./styles/base.css";

const app = createApp(App);

/*
 * 只在入口注册高频、相对轻量的基础组件。
 * NDataTable、NUpload、NDatePicker、NColorPicker、NDynamicTags 等重组件由页面按需 import，
 * 避免首屏主包把后台所有复杂控件一次性解析完，快速切换菜单时拖住浏览器主线程。
 */
const globalNaiveComponents = {
  /*
   * Naive UI 组件的内部 name 是 Button/Input/Form 这类无前缀名称，
   * 但模板里使用的是 <n-button>/<n-input>。这里必须按 NButton 这种注册名显式注册，
   * 否则 Vue 会把 n-input 等标签当成普通自定义元素，登录页就会出现“没有输入框”。
  */
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NSpace,
  NSpin
};

Object.entries(globalNaiveComponents).forEach(([name, component]) => {
  app.component(name, component);
});

app.use(createPinia()).use(router).mount("#app");
