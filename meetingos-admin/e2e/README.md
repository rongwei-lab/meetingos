# 邀请函 Playwright E2E

这些测试使用环境中已经准备好的会议、邀请方案和渠道，不依赖仓库内的固定 ID；其中真实 TDuck
测试会提交一条报名，必须单独显式开启。Playwright
浏览器运行时应由开发机或 CI 镜像预先提供；本项目不执行 `install-deps`，也不会在测试脚本中安装
系统浏览器。

## 基础命令

```bash
cd meetingos-admin
npm run test:e2e:list
npm run test:e2e
```

默认连接 `http://127.0.0.1:5173`。连接已部署环境时设置 `E2E_BASE_URL`；需要 Playwright 启动本地
Vite 时设置 `E2E_START_WEB_SERVER=true`，后端仍需单独启动，且可通过 `VITE_DEV_API_TARGET` 指向它。

```bash
E2E_BASE_URL=http://127.0.0.1:5174 \
E2E_INVITATION_SHARE_TOKEN=<已启用的发布渠道 token> \
npm run test:e2e -- --project invitation-mobile-chromium
```

## 环境变量

| 测试 | 环境变量 |
| --- | --- |
| 移动公开页 | `E2E_INVITATION_SHARE_TOKEN` |
| 桌面设计器 | `E2E_ADMIN_USERNAME`、`E2E_ADMIN_PASSWORD`、`E2E_MEETING_ID`、`E2E_INVITATION_ID` |
| 真实 TDuck | 上述分享 token，另见下节 |

缺少对应变量时测试会显示为 skipped。跳过只表示环境未配置，不能作为验收通过证据。

## 真实 TDuck extValue

该测试会创建一条真实报名，默认关闭。只有显式设置以下变量才运行：

- `E2E_TDUCK_ATTRIBUTION=true`
- `E2E_TDUCK_BASE_URL`：例如 `https://meeting.example/tduck/`
- `E2E_TDUCK_FORM_KEY`：邀请函当前绑定的表单 key
- `E2E_TDUCK_FORM_ACTIONS_JSON`：按顺序执行的表单动作数组
- `E2E_TDUCK_SUBMIT_SELECTOR`：真实提交按钮的 CSS selector

动作支持 `fill`、`check`、`click` 和 `selectOption`。示例：

```bash
E2E_TDUCK_ATTRIBUTION=true \
E2E_TDUCK_BASE_URL=https://meeting.example/tduck/ \
E2E_TDUCK_FORM_KEY=form-key \
E2E_TDUCK_FORM_ACTIONS_JSON='[{"action":"fill","selector":"input[name=name]","value":"E2E 邀请测试"}]' \
E2E_TDUCK_SUBMIT_SELECTOR='button:has-text("提交")' \
npm run test:e2e -- --project invitation-tduck-chromium
```

测试会验证 capability 文件、`/r` 中转 fragment、按表单隔离的 `sessionStorage`、真实提交请求中的
`extValue`，以及提交成功后的 token 清理。Webhook 落库、补偿同步和最终统计仍需结合后端测试及管理端
归因明细验收，不能用此浏览器测试单独替代。
