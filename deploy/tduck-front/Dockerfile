FROM node:14-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci --legacy-peer-deps
RUN npm install --no-save --legacy-peer-deps html-webpack-plugin@4.5.2

COPY . .

# TDuck 官方前端生产配置默认挂根路径，MeetingOS 统一通过 /tduck/ 暴露。
# 这里在镜像构建阶段做部署侧适配，避免修改 vendor/tduck-front 源码。
RUN sed -i "s/publicPath: '\\/'/publicPath: '\\/tduck\\/'/" vue.config.js \
    && sed -i "/tduck-form-generator\\$/d" vue.config.js \
    && sed -i "s/mode: 'history',/mode: 'history',\\n  base: process.env.BASE_URL,/" src/router/index.js \
    && printf '%s\n' \
      'VUE_APP_TITLE = Tduck填鸭表单' \
      'VUE_APP_API_ROOT = /tduck-api' \
      'VUE_APP_DEBUG_TOOL =' \
      'VUE_APP_MAP_KEY = f2200337d0d08538e78729572749882d' \
      'VUE_APP_SECURITY_JSCODE =' \
      'VUE_APP_WX = OFF' > .env.production \
    && node -e 'const fs = require("fs"); const file = "src/views/project/my/index.vue"; let source = fs.readFileSync(file, "utf8"); if (!source.includes("openAiCreateFromQuery() {")) { const mountedBefore = "  mounted() {\n    this.initResizeObserver()\n  },"; const mountedAfter = "  mounted() {\n    this.initResizeObserver()\n    this.openAiCreateFromQuery()\n  },"; if (!source.includes(mountedBefore)) throw new Error("TDuck my-project mounted hook shape changed"); source = source.replace(mountedBefore, mountedAfter); const methodsBefore = "  methods: {\n    initResizeObserver() {"; const aiCreateMethod = `  methods: {\n    openAiCreateFromQuery() {\n      if (this.$route.query.aiCreate !== "1") {\n        return\n      }\n      /*\n       * MeetingOS 通过 /project/my?aiCreate=1 跳入 TDuck。AI 建表是本页内的抽屉组件，\n       * 这里给 TDuck 首屏和 Element Drawer 一个短暂稳定窗口，再自动打开抽屉。\n       * query 只在打开成功后清理，避免刷新时重复弹出，也避免过早 replace 打断抽屉动画状态。\n       */\n      const clearAiCreateQuery = () => {\n        const url = new URL(window.location.href)\n        url.searchParams.delete("aiCreate")\n        /*\n         * 这里只需要清理地址栏参数，不需要触发 Vue Router 导航。\n         * 使用 router.replace 会让当前页面状态重新计算，刚打开的 Drawer 在部分环境下会被重置关闭。\n         */\n        window.history.replaceState(window.history.state, document.title, `${url.pathname}${url.search}${url.hash}`)\n      }\n      const tryOpenDialog = (remaining = 8) => {\n        this.$nextTick(() => {\n          const dialog = this.$refs.chatFormDialog\n          if (dialog && typeof dialog.open === "function") {\n            dialog.open()\n            window.setTimeout(clearAiCreateQuery, 800)\n            return\n          }\n          if (remaining > 0) {\n            window.setTimeout(() => tryOpenDialog(remaining - 1), 150)\n            return\n          }\n          clearAiCreateQuery()\n        })\n      }\n      window.setTimeout(() => {\n        tryOpenDialog()\n      }, 300)\n    },\n    initResizeObserver() {`; if (!source.includes(methodsBefore)) throw new Error("TDuck my-project methods shape changed"); source = source.replace(methodsBefore, aiCreateMethod); fs.writeFileSync(file, source) }' \
    && npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

RUN printf '%s\n' \
    'server {' \
    '    listen 80;' \
    '    server_name _;' \
    '' \
    '    root /usr/share/nginx/html;' \
    '    index index.html;' \
    '' \
    '    location / {' \
    '        try_files $uri $uri/ /index.html;' \
    '    }' \
    '}' > /etc/nginx/conf.d/default.conf
