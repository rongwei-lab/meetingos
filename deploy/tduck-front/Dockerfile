FROM node:14-alpine AS build

WORKDIR /app

COPY vendor/tduck-front/package*.json ./
RUN npm ci --legacy-peer-deps
RUN npm install --no-save --legacy-peer-deps html-webpack-plugin@4.5.2

COPY vendor/tduck-front/ .
COPY deploy/tduck-front/patch-tduck-front.js /tmp/patch-tduck-front.js

# TDuck 官方前端生产配置默认挂根路径，MeetingOS 统一通过 /tduck/ 暴露。
# 这里在镜像构建阶段做部署侧适配，避免修改 vendor/tduck-front 源码。
RUN sed -i "s/publicPath: '\\/'/publicPath: '\\/tduck\\/'/" vue.config.js \
    && sed -i "/tduck-form-generator\\$/d" vue.config.js \
    && sed -i "s/mode: 'history',/mode: 'history',\\n  base: process.env.BASE_URL,/" src/router/index.js \
    && printf '%s\n' \
      'VUE_APP_TITLE =' \
      'VUE_APP_API_ROOT = /tduck-api' \
      'VUE_APP_DEBUG_TOOL =' \
      'VUE_APP_MAP_KEY = f2200337d0d08538e78729572749882d' \
      'VUE_APP_SECURITY_JSCODE =' \
      'VUE_APP_WX = OFF' > .env.production \
    && node /tmp/patch-tduck-front.js \
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
