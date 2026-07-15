FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_AMAP_KEY=""
ARG VITE_AMAP_SECURITY_CODE=""
ENV VITE_AMAP_KEY=${VITE_AMAP_KEY}
ENV VITE_AMAP_SECURITY_CODE=${VITE_AMAP_SECURITY_CODE}
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
