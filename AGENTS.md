# MeetingOS Agent Notes

请默认使用中文交流。当前仓库是 MeetingOS 会议管理系统 monorepo：

- `meetingos-backend/`：Java 17 + Spring Boot 3.x 后端。
- `meetingos-admin/`：Vue 3 + TypeScript + Vite + Naive UI 管理端和公开查询页。
- `deploy/`：Docker Compose、Nginx 和部署配置。
- `docs/`：API、数据模型、验收和测试文档。

开发约定：

- TDuck 保持独立部署，不把 TDuck 源码合并进 MeetingOS。
- 后台 API 使用 `/api/admin` 前缀，公开能力使用 `/api/public` 和 `/r/{meetingCode}`。
- 复杂业务逻辑请写中文注释，尤其是 Webhook 幂等、自动排座、重复签到拦截、导入失败处理和敏感信息脱敏。
- 不要默认安装 Python/Java/Node 依赖到系统环境；前端使用项目内 `npm`，后端优先使用 Maven Wrapper。
- 修改后尽量运行相关测试或构建；如果本机缺少 Java/Maven 等工具，要如实说明。
