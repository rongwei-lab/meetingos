import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const devApiTarget = env.VITE_DEV_API_TARGET || "http://localhost:8080";

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url))
      }
    },
    server: {
      port: 5173,
      proxy: {
        "/api": devApiTarget,
        "/uploads": devApiTarget
      }
    },
    build: {
      /*
       * 后台页面按路由拆包较多。Vite 默认会为动态路由注入 modulepreload helper，
       * 在手动 chunk 拆分下 helper 可能被并入导出或二维码 chunk，导致首屏静态加载重包。
       * 关闭 modulepreload 后，非当前路由的大包只在真正进入对应页面或点击导出时加载，
       * 可显著降低快速切换菜单时的主线程解析压力。
       */
      modulePreload: false
    }
  };
});
