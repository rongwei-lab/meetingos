const fs = require("fs");

function patchProjectMyAiCreate() {
  const file = "src/views/project/my/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (source.includes("openAiCreateFromQuery() {")) {
    return;
  }

  const mountedBefore = "  mounted() {\n    this.initResizeObserver()\n  },";
  const mountedAfter = "  mounted() {\n    this.initResizeObserver()\n    this.openAiCreateFromQuery()\n  },";

  if (!source.includes(mountedBefore)) {
    throw new Error("TDuck my-project mounted hook shape changed");
  }

  source = source.replace(mountedBefore, mountedAfter);

  const methodsBefore = "  methods: {\n    initResizeObserver() {";
  const aiCreateMethod = String.raw`  methods: {
    openAiCreateFromQuery() {
      if (this.$route.query.aiCreate !== "1") {
        return
      }
      /*
       * MeetingOS 通过 /project/my?aiCreate=1 跳入 TDuck。AI 建表是本页内的抽屉组件，
       * 这里给 TDuck 首屏和 Element Drawer 一个短暂稳定窗口，再自动打开抽屉。
       * query 只在打开成功后清理，避免刷新时重复弹出，也避免过早 replace 打断抽屉动画状态。
       */
      const clearAiCreateQuery = () => {
        const url = new URL(window.location.href)
        url.searchParams.delete("aiCreate")
        /*
         * 这里只需要清理地址栏参数，不需要触发 Vue Router 导航。
         * 使用 router.replace 会让当前页面状态重新计算，刚打开的 Drawer 在部分环境下会被重置关闭。
         */
        window.history.replaceState(window.history.state, document.title, url.pathname + url.search + url.hash)
      }
      const tryOpenDialog = (remaining = 8) => {
        this.$nextTick(() => {
          const dialog = this.$refs.chatFormDialog
          if (dialog && typeof dialog.open === "function") {
            dialog.open()
            window.setTimeout(clearAiCreateQuery, 800)
            return
          }
          if (remaining > 0) {
            window.setTimeout(() => tryOpenDialog(remaining - 1), 150)
            return
          }
          clearAiCreateQuery()
        })
      }
      window.setTimeout(() => {
        tryOpenDialog()
      }, 300)
    },
    initResizeObserver() {`;

  if (!source.includes(methodsBefore)) {
    throw new Error("TDuck my-project methods shape changed");
  }

  source = source.replace(methodsBefore, aiCreateMethod);
  fs.writeFileSync(file, source);
}

function patchWritePageSupportText() {
  const file = "src/views/form/write/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (source.includes("loadSystemSupportText()")) {
    return;
  }

  const importBefore = "import FormQrcode from '@/views/form/write/FormQrcode.vue'\n";
  const importAfter =
    "import FormQrcode from '@/views/form/write/FormQrcode.vue'\n" +
    "import { getSystemInfoConfig } from '@/api/mange/config'\n";

  if (!source.includes(importBefore)) {
    throw new Error("TDuck write page import shape changed");
  }

  source = source.replace(importBefore, importAfter);

  const createdBefore =
    "  async created() {\n" +
    "    this.formKey = this.$route.query.key || this.$route.params.key\n";
  const createdAfter =
    "  async created() {\n" +
    "    this.formKey = this.$route.query.key || this.$route.params.key\n" +
    "    await this.loadSystemSupportText()\n";

  if (!source.includes(createdBefore)) {
    throw new Error("TDuck write page created hook shape changed");
  }

  source = source.replace(createdBefore, createdAfter);

  const methodsBefore = "  methods: {\n    queryProjectSettingStatus() {";
  const supportMethods = String.raw`  methods: {
    async loadSystemSupportText() {
      try {
        const res = await getSystemInfoConfig()
        const systemConfig = this.parseSystemInfoConfig(res ? res.data : null)
        if (Object.prototype.hasOwnProperty.call(systemConfig, 'copyright')) {
          /*
           * TDuck 后台“版权说明信息”是富文本配置，而表单生成器底部只接收纯文本。
           * 这里转成纯文本后再传入 supportText；如果后台清空版权，则同步清空底部文案。
           */
          this.formConfig.supportText = this.normalizeSupportText(systemConfig.copyright)
        }
      } catch (error) {
        // 版权配置读取失败不影响报名填写，保留表单生成器原有默认文案作为兜底。
        console.warn('获取表单版权配置失败:', error)
      }
    },
    parseSystemInfoConfig(value) {
      if (!value) {
        return {}
      }
      if (typeof value === 'object') {
        return value
      }
      try {
        return JSON.parse(value)
      } catch (error) {
        console.warn('解析系统配置失败:', error)
        return {}
      }
    },
    normalizeSupportText(value) {
      if (value === null || value === undefined) {
        return ''
      }
      const text = String(value)
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/p>\s*<p[^>]*>/gi, '\n')
        .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/gi, ' ')
        .replace(/&amp;/gi, '&')
        .replace(/&lt;/gi, '<')
        .replace(/&gt;/gi, '>')
        .replace(/&quot;/gi, '"')
        .replace(/&#39;/gi, "'")
      return text
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean)
        .join('\n')
    },
    queryProjectSettingStatus() {`;

  if (!source.includes(methodsBefore)) {
    throw new Error("TDuck write page methods shape changed");
  }

  source = source.replace(methodsBefore, supportMethods);
  fs.writeFileSync(file, source);
}

function patchWritePageAttribution() {
  const file = "src/views/form/write/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (!source.includes("meetingOsAttributionToken: ''")) {
    const dataBefore =
      "      formKey: '',\n" +
      "      writePassword: '',\n";
    const dataAfter =
      "      formKey: '',\n" +
      "      meetingOsAttributionToken: '',\n" +
      "      writePassword: '',\n";

    if (!source.includes(dataBefore)) {
      throw new Error("TDuck write page attribution data shape changed");
    }
    source = source.replace(dataBefore, dataAfter);
  }

  if (!source.includes("    this.captureMeetingOsAttribution()\n")) {
    const createdBefore =
      "  async created() {\n" +
      "    this.formKey = this.$route.query.key || this.$route.params.key\n";
    const createdAfter =
      "  async created() {\n" +
      "    this.formKey = this.$route.query.key || this.$route.params.key\n" +
      "    this.captureMeetingOsAttribution()\n";

    if (!source.includes(createdBefore)) {
      throw new Error("TDuck write page attribution created hook shape changed");
    }
    source = source.replace(createdBefore, createdAfter);
  }

  if (!source.includes("captureMeetingOsAttribution() {")) {
    const methodsBefore = "  methods: {\n";
    const attributionMethods = String.raw`  methods: {
    captureMeetingOsAttribution() {
      const storageKey = 'meetingos:attribution:' + this.formKey
      const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''))
      if (hashParams.has('mos_at')) {
        const token = hashParams.get('mos_at') || ''
        /*
         * 归因 token 只允许 URL-safe 字符且受 TDuck extValue(255) 长度约束。
         * fragment 不会进入代理日志；读取后立即清理地址栏，并仅在当前标签页内暂存，
         * 以便微信 OAuth 回跳或用户刷新页面后仍能完成同一次报名。
         */
        this.meetingOsAttributionToken = /^[A-Za-z0-9._~-]{1,255}$/.test(token) ? token : ''
        try {
          if (this.meetingOsAttributionToken) {
            window.sessionStorage.setItem(storageKey, this.meetingOsAttributionToken)
          } else {
            window.sessionStorage.removeItem(storageKey)
          }
        } catch (error) {
          // 浏览器禁用存储时仍可完成当前页面报名，不让归因能力影响主流程。
        }
        hashParams.delete('mos_at')
        const remainingHash = hashParams.toString()
        try {
          window.history.replaceState(
            window.history.state,
            document.title,
            window.location.pathname + window.location.search + (remainingHash ? '#' + remainingHash : '')
          )
        } catch (error) {
          // 地址栏清理受限时仍继续报名；fragment 不会发送给服务器或进入代理日志。
        }
        return
      }
      try {
        const storedToken = window.sessionStorage.getItem(storageKey) || ''
        this.meetingOsAttributionToken = /^[A-Za-z0-9._~-]{1,255}$/.test(storedToken) ? storedToken : ''
      } catch (error) {
        this.meetingOsAttributionToken = ''
      }
    },
    clearMeetingOsAttribution() {
      try {
        window.sessionStorage.removeItem('meetingos:attribution:' + this.formKey)
      } catch (error) {
        // 清理失败只会让 token 保留到标签页关闭，不影响提交结果。
      }
      this.meetingOsAttributionToken = ''
    },
`;

    if (!source.includes(methodsBefore)) {
      throw new Error("TDuck write page attribution methods shape changed");
    }
    source = source.replace(methodsBefore, attributionMethods);
  }

  if (!source.includes("      this.submitFormData.extValue = this.meetingOsAttributionToken || null\n")) {
    const submitBefore = "      this.submitFormData.originalData = data.formModel\n";
    const submitAfter =
      "      this.submitFormData.originalData = data.formModel\n" +
      "      this.submitFormData.extValue = this.meetingOsAttributionToken || null\n";

    if (!source.includes(submitBefore)) {
      throw new Error("TDuck write page attribution submit shape changed");
    }
    source = source.replace(submitBefore, submitAfter);
  }

  if (!source.includes("      this.clearMeetingOsAttribution()\n")) {
    const successBefore = "      this.submitResult = data\n";
    const successAfter =
      "      this.submitResult = data\n" +
      "      this.clearMeetingOsAttribution()\n";

    if (!source.includes(successBefore)) {
      throw new Error("TDuck write page attribution success shape changed");
    }
    source = source.replace(successBefore, successAfter);
  }

  fs.writeFileSync(file, source);
}

function assertWritePageAttributionPatch() {
  const source = fs.readFileSync("src/views/form/write/index.vue", "utf8");
  const requiredMarkers = [
    "captureMeetingOsAttribution() {",
    "window.sessionStorage.setItem(storageKey, this.meetingOsAttributionToken)",
    "this.submitFormData.extValue = this.meetingOsAttributionToken || null",
    "this.clearMeetingOsAttribution()"
  ];
  for (const marker of requiredMarkers) {
    if (!source.includes(marker)) {
      throw new Error("TDuck attribution patch self-check failed: " + marker);
    }
  }
}

function patchWritePagePhonePrecheck() {
  const file = "src/views/form/write/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (source.includes("markMeetingOsPrecheckFieldsReady()")) {
    return;
  }

  const dataBefore =
    "      formKey: '',\n" +
    "      meetingOsAttributionToken: '',\n" +
    "      writePassword: '',\n";
  const dataAfter =
    "      formKey: '',\n" +
    "      meetingOsAttributionToken: '',\n" +
    "      meetingOsPrecheckReady: null,\n" +
    "      meetingOsPrecheck: {\n" +
    "        enabled: false,\n" +
    "        token: '',\n" +
    "        phoneFieldKey: ''\n" +
    "      },\n" +
    "      meetingOsPrecheckFieldsReady: false,\n" +
    "      writePassword: '',\n";
  if (!source.includes(dataBefore)) {
    throw new Error("TDuck write page precheck data shape changed");
  }
  source = source.replace(dataBefore, dataAfter);

  const formBefore =
    "      <biz-project-form v-if=\"formConfig.formKey\" ref=\"bizProjectForm\" :form-config=\"formConfig\" @submit=\"submitForm\" />";
  const formAfter =
    "      <biz-project-form v-if=\"formConfig.formKey\" ref=\"bizProjectForm\" :form-config=\"formConfig\" @submit=\"submitForm\" @completed=\"markMeetingOsPrecheckFieldsReady\" />";
  if (!source.includes(formBefore)) {
    throw new Error("TDuck write page precheck form component shape changed");
  }
  source = source.replace(formBefore, formAfter);

  const createdBefore =
    "    this.formKey = this.$route.query.key || this.$route.params.key\n" +
    "    this.captureMeetingOsAttribution()\n";
  const createdAfter =
    "    this.formKey = this.$route.query.key || this.$route.params.key\n" +
    "    this.captureMeetingOsAttribution()\n" +
    "    this.meetingOsPrecheckReady = this.loadMeetingOsPrecheck()\n";
  if (!source.includes(createdBefore)) {
    throw new Error("TDuck write page precheck created shape changed");
  }
  source = source.replace(createdBefore, createdAfter);

  const methodsBefore = "  methods: {\n";
  const precheckMethods = String.raw`  methods: {
    markMeetingOsPrecheckFieldsReady() {
      this.meetingOsPrecheckFieldsReady = true
    },
    async loadMeetingOsPrecheck() {
      this.meetingOsPrecheck = {
        enabled: false,
        token: '',
        phoneFieldKey: ''
      }
      if (!this.formKey) {
        return this.meetingOsPrecheck
      }
      const controller = typeof AbortController === 'function' ? new AbortController() : null
      const timeoutId = setTimeout(() => {
        if (controller) {
          controller.abort()
        }
      }, 5000)
      try {
        const response = await fetch(
          '/api/public/tduck/precheck-token?formKey=' + encodeURIComponent(this.formKey),
          {
            method: 'GET',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { Accept: 'application/json' },
            signal: controller ? controller.signal : undefined
          }
        )
        if (!response.ok) {
          return this.meetingOsPrecheck
        }
        const payload = await response.json()
        const config = payload && payload.success === true ? payload.data : null
        if (!config || config.enabled !== true || !config.token || !config.phoneFieldKey) {
          return this.meetingOsPrecheck
        }
        this.meetingOsPrecheck = {
          enabled: true,
          token: String(config.token),
          phoneFieldKey: String(config.phoneFieldKey)
        }
      } catch (error) {
        // 预检查是提交前增强能力，接口异常时仍允许 TDuck 提交，由 MeetingOS Webhook 最终兜底。
        console.warn('加载 MeetingOS 手机号重复校验失败:', error)
      } finally {
        clearTimeout(timeoutId)
      }
      return this.meetingOsPrecheck
    },
    async runMeetingOsPhonePrecheck(formModel, allowRefresh = true) {
      if (this.meetingOsPrecheckReady) {
        await this.meetingOsPrecheckReady
      }
      const config = this.meetingOsPrecheck || {}
      if (!config.enabled || !config.token || !config.phoneFieldKey) {
        return true
      }
      if (!this.isMeetingOsPhonePrecheckEnabled(config.phoneFieldKey)) {
        return true
      }
      const phone = this.readMeetingOsPhone(formModel, config.phoneFieldKey)
      // 手机号为空时交给 TDuck 原有的“必填”校验处理，避免重复提示。
      if (!phone) {
        return true
      }
      const controller = typeof AbortController === 'function' ? new AbortController() : null
      const timeoutId = setTimeout(() => {
        if (controller) {
          controller.abort()
        }
      }, 5000)
      try {
        const response = await fetch('/api/public/tduck/phone-precheck', {
          method: 'POST',
          credentials: 'same-origin',
          cache: 'no-store',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json'
          },
          signal: controller ? controller.signal : undefined,
          body: JSON.stringify({
            formKey: this.formKey,
            token: config.token,
            phone
          })
        })
        const payload = await response.json().catch(() => null)
        const result = payload && payload.success === true ? payload.data : null
        if (result && result.available === false && result.code === 'DUPLICATE') {
          this.$message.error(result.message || '该手机号已报名，请勿重复提交')
          return false
        }
        if (result && result.available === false && result.code === 'INVALID_TOKEN' && allowRefresh) {
          // 令牌过期时刷新一次配置；刷新失败则按可用放行，让 Webhook 继续兜底。
          this.meetingOsPrecheckReady = this.loadMeetingOsPrecheck()
          await this.meetingOsPrecheckReady
          return this.runMeetingOsPhonePrecheck(formModel, false)
        }
        if (result && result.available === true) {
          return true
        }
        // 网络抖动、服务暂时不可用或表单未完成映射时不阻断报名主流程。
        return true
      } catch (error) {
        console.warn('MeetingOS 手机号重复校验失败:', error)
        return true
      } finally {
        clearTimeout(timeoutId)
      }
    },
    isMeetingOsPhonePrecheckEnabled(fieldKey) {
      const form = this.$refs.bizProjectForm
      const fields = form && form.formConf && Array.isArray(form.formConf.fields)
        ? form.formConf.fields
        : []
      const field = this.findMeetingOsField(fields, fieldKey)
      return Boolean(field && field.config && field.config.meetingOsPhonePrecheckEnabled === true)
    },
    findMeetingOsField(fields, fieldKey) {
      for (const field of fields || []) {
        const config = field && field.config ? field.config : {}
        if (
          String(field && field.vModel || '') === String(fieldKey) ||
          String(field && field.formItemId || '') === String(fieldKey) ||
          String(config.formId || '') === String(fieldKey)
        ) {
          return field
        }
        const child = this.findMeetingOsField(field && field.childList, fieldKey)
        if (child) {
          return child
        }
      }
      return null
    },
    readMeetingOsPhone(formModel, fieldKey) {
      if (!formModel || !fieldKey) {
        return ''
      }
      const value = formModel[fieldKey]
      if (value === null || value === undefined) {
        return ''
      }
      if (typeof value === 'object') {
        return String(value.value || value.label || '').trim()
      }
      return String(value).trim()
    },
`;
  if (!source.includes(methodsBefore)) {
    throw new Error("TDuck write page precheck methods shape changed");
  }
  source = source.replace(methodsBefore, precheckMethods);

  const submitBefore = "    async submitForm(data) {\n";
  const submitAfter =
    "    async submitForm(data) {\n" +
    "      if (!(await this.runMeetingOsPhonePrecheck(data.formModel))) {\n" +
    "        return\n" +
    "      }\n";
  if (!source.includes(submitBefore)) {
    throw new Error("TDuck write page precheck submit shape changed");
  }
  source = source.replace(submitBefore, submitAfter);

  fs.writeFileSync(file, source);
}

function assertWritePagePhonePrecheckPatch() {
  const source = fs.readFileSync("src/views/form/write/index.vue", "utf8");
  const requiredMarkers = [
    "meetingOsPrecheckReady",
    "markMeetingOsPrecheckFieldsReady()",
    "isMeetingOsPhonePrecheckEnabled(fieldKey)",
    "loadMeetingOsPrecheck()",
    "'/api/public/tduck/precheck-token?formKey='",
    "'/api/public/tduck/phone-precheck'",
    "runMeetingOsPhonePrecheck(data.formModel)"
  ];
  for (const marker of requiredMarkers) {
    if (!source.includes(marker)) {
      throw new Error("TDuck phone precheck patch self-check failed: " + marker);
    }
  }
}

function patchFormGeneratorPhonePrecheckSetting() {
  const file = "node_modules/tduck-form-generator/dist/TduckForm.common.js";
  let source = fs.readFileSync(file, "utf8");

  if (source.includes("meetingOsPhonePrecheckEnabled")) {
    return;
  }

  const templateBefore =
    "  })], 1) : _vm._e(), _vm.getComponent ? _c(_vm.getComponent, {";
  const templateAfter = String.raw`  })], 1) : _vm._e(), _vm.isMeetingOsPhonePrecheckField ? _c('el-form-item', {
    attrs: {
      "label": "手机号重复预检查"
    }
  }, [_c('span', {
    attrs: {
      "slot": "label"
    },
    slot: "label"
  }, [_vm._v(" 手机号重复预检查 "), _c('el-tooltip', {
    staticClass: "item",
    attrs: {
      "content": "开启后，公开填写页会先向 MeetingOS 查询该手机号是否已报名；需先在 MeetingOS 完成表单绑定和手机号字段映射。",
      "effect": "dark",
      "placement": "top-start"
    }
  }, [_c('i', {
    staticClass: "el-icon-question"
  })])], 1), _c('el-switch', {
    model: {
      value: _vm.activeData.config.meetingOsPhonePrecheckEnabled,
      callback: function callback($$$$v) {
        _vm.$set(_vm.activeData.config, "meetingOsPhonePrecheckEnabled", $$$$v);
      },
      expression: "activeData.config.meetingOsPhonePrecheckEnabled"
    }
  })], 1) : _vm._e(), _vm.getComponent ? _c(_vm.getComponent, {`;
  if (!source.includes(templateBefore)) {
    throw new Error("TDuck form generator precheck setting template shape changed");
  }
  source = source.replace(templateBefore, templateAfter);

  const computedBefore =
    "      return prefix + lodash_default.a.lowerCase(typeId).replaceAll(' ', '-');\n" +
    "    }\n" +
    "  },\n" +
    "  watch: {\n";
  const computedAfter =
    "      return prefix + lodash_default.a.lowerCase(typeId).replaceAll(' ', '-');\n" +
    "    },\n" +
    "    isMeetingOsPhonePrecheckField: function isMeetingOsPhonePrecheckField() {\n" +
    "      var config = this.activeData && this.activeData.config;\n" +
    "      return this.activeData && this.activeData.typeId === 'INPUT' && config && config.dataType && config.dataType.type === 'phone';\n" +
    "    }\n" +
    "  },\n" +
    "  watch: {\n";
  if (!source.includes(computedBefore)) {
    throw new Error("TDuck form generator precheck setting computed shape changed");
  }
  source = source.replace(computedBefore, computedAfter);

  fs.writeFileSync(file, source);
}

function assertFormGeneratorPhonePrecheckSettingPatch() {
  const source = fs.readFileSync("node_modules/tduck-form-generator/dist/TduckForm.common.js", "utf8");
  const requiredMarkers = [
    "手机号重复预检查",
    "meetingOsPhonePrecheckEnabled",
    "isMeetingOsPhonePrecheckField"
  ];
  for (const marker of requiredMarkers) {
    if (!source.includes(marker)) {
      throw new Error("TDuck form generator precheck setting patch self-check failed: " + marker);
    }
  }
}

function patchHomeFooterCopyright() {
  const file = "src/views/home/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (source.includes("homeCopyright")) {
    return;
  }

  const footerBefore = String.raw`          <div class="about-container">
            <span class="desc-text">
              <a href="https://www.tduckcloud.com" target="view_window">TDUCK 官网</a>
              <span class="copyright-text">© 2026 TDUCK Group</span>
            </span>
          </div>`;
  const footerAfter = String.raw`          <div v-if="homeCopyright" class="about-container">
            <span class="desc-text copyright-text">{{ homeCopyright }}</span>
          </div>`;

  if (!source.includes(footerBefore)) {
    throw new Error("TDuck home footer template shape changed");
  }

  source = source.replace(footerBefore, footerAfter);

  const importBefore = "import { createFormRequest } from '@/api/project/form'\n";
  const importAfter =
    "import { createFormRequest } from '@/api/project/form'\n" +
    "import { getSystemInfoConfig } from '@/api/mange/config'\n";

  if (!source.includes(importBefore)) {
    throw new Error("TDuck home imports shape changed");
  }

  source = source.replace(importBefore, importAfter);

  const dataBefore =
    "    return {\n" +
    "      defaultActiveMenu: '/home',\n" +
    "      menuList: [";
  const dataAfter =
    "    return {\n" +
    "      defaultActiveMenu: '/home',\n" +
    "      homeCopyright: '',\n" +
    "      menuList: [";

  if (!source.includes(dataBefore)) {
    throw new Error("TDuck home data shape changed");
  }

  source = source.replace(dataBefore, dataAfter);

  const createdBefore =
    "  created() {\n" +
    "    let user = this.getStore.getters['user/userInfo'] || {}\n";
  const createdAfter =
    "  created() {\n" +
    "    this.loadHomeCopyright()\n" +
    "    let user = this.getStore.getters['user/userInfo'] || {}\n";

  if (!source.includes(createdBefore)) {
    throw new Error("TDuck home created hook shape changed");
  }

  source = source.replace(createdBefore, createdAfter);

  const methodsBefore = "  methods: {\n    menuClickHandle(menu) {";
  const copyrightMethods = String.raw`  methods: {
    async loadHomeCopyright() {
      // 先使用 App.vue 已缓存的系统配置，减少进入首页时底部文案闪烁；随后再请求接口刷新一次。
      this.applyHomeCopyright(localStorage.getItem('systemConfig'))
      try {
        const res = await getSystemInfoConfig()
        this.applyHomeCopyright(res ? res.data : null)
      } catch (error) {
        console.warn('获取首页版权配置失败:', error)
      }
    },
    applyHomeCopyright(value) {
      const systemConfig = this.parseSystemInfoConfig(value)
      if (Object.prototype.hasOwnProperty.call(systemConfig, 'copyright')) {
        /*
         * 系统配置里的版权说明是富文本；侧边栏底部空间很小，只渲染纯文本，
         * 避免 p/div 等标签带来的默认边距破坏菜单布局。
         */
        this.homeCopyright = this.normalizeCopyrightText(systemConfig.copyright)
      }
    },
    parseSystemInfoConfig(value) {
      if (!value) {
        return {}
      }
      if (typeof value === 'object') {
        return value
      }
      try {
        return JSON.parse(value)
      } catch (error) {
        console.warn('解析系统配置失败:', error)
        return {}
      }
    },
    normalizeCopyrightText(value) {
      if (value === null || value === undefined) {
        return ''
      }
      const text = String(value)
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/p>\s*<p[^>]*>/gi, '\n')
        .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/gi, ' ')
        .replace(/&amp;/gi, '&')
        .replace(/&lt;/gi, '<')
        .replace(/&gt;/gi, '>')
        .replace(/&quot;/gi, '"')
        .replace(/&#39;/gi, "'")
      return text
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean)
        .join('\n')
    },
    menuClickHandle(menu) {`;

  if (!source.includes(methodsBefore)) {
    throw new Error("TDuck home methods shape changed");
  }

  source = source.replace(methodsBefore, copyrightMethods);

  const styleBefore = String.raw`      .desc-text {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        line-height: 1.2;
      }`;
  const styleAfter = String.raw`      .desc-text {
        display: flex;
        white-space: pre-line;
        align-items: center;
        justify-content: center;
        line-height: 1.45;
      }`;

  if (!source.includes(styleBefore)) {
    throw new Error("TDuck home footer style shape changed");
  }

  source = source.replace(styleBefore, styleAfter);
  fs.writeFileSync(file, source);
}

function patchHomeSystemNameBranding() {
  const file = "src/views/home/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (!source.includes("class=\"home-system-name\"")) {
    const logoBefore = String.raw`        <div class="logo-wrapper" @click="$router.push({ path: '/' })">
          <img src="@/assets/images/logo-tduck-ce.svg" alt="TDUCK Logo" class="home-logo-img" />
        </div>`;
    const logoAfter = String.raw`        <div v-if="systemName" class="logo-wrapper" @click="$router.push({ path: '/' })">
          <span class="home-system-name">{{ systemName }}</span>
        </div>`;

    if (!source.includes(logoBefore)) {
      throw new Error("TDuck home logo template shape changed");
    }
    source = source.replace(logoBefore, logoAfter);
  }

  if (!source.includes("systemName: ''")) {
    const dataBefore =
      "      defaultActiveMenu: '/home',\n" +
      "      homeCopyright: '',\n";
    const dataAfter =
      "      defaultActiveMenu: '/home',\n" +
      "      homeCopyright: '',\n" +
      "      systemName: '',\n";

    if (!source.includes(dataBefore)) {
      throw new Error("TDuck home data branding shape changed");
    }
    source = source.replace(dataBefore, dataAfter);
  }

  if (source.includes("this.loadHomeCopyright()")) {
    source = source.replace("    this.loadHomeCopyright()\n", "    this.loadHomeBranding()\n");
  }

  if (!source.includes("async loadHomeBranding()")) {
    const methodBefore = String.raw`    async loadHomeCopyright() {
      // 先使用 App.vue 已缓存的系统配置，减少进入首页时底部文案闪烁；随后再请求接口刷新一次。
      this.applyHomeCopyright(localStorage.getItem('systemConfig'))
      try {
        const res = await getSystemInfoConfig()
        this.applyHomeCopyright(res ? res.data : null)
      } catch (error) {
        console.warn('获取首页版权配置失败:', error)
      }
    },
    applyHomeCopyright(value) {`;
    const methodAfter = String.raw`    async loadHomeBranding() {
      // 先使用 App.vue 已缓存的系统配置，减少进入首页时品牌名称和版权文案闪烁；随后再请求接口刷新一次。
      this.applyHomeBranding(localStorage.getItem('systemConfig'))
      try {
        const res = await getSystemInfoConfig()
        this.applyHomeBranding(res ? res.data : null)
      } catch (error) {
        console.warn('获取首页品牌配置失败:', error)
      }
    },
    applyHomeBranding(value) {
      this.applyHomeCopyright(value)
      this.applyHomeSystemName(value)
    },
    applyHomeSystemName(value) {
      const systemConfig = this.parseSystemInfoConfig(value)
      if (Object.prototype.hasOwnProperty.call(systemConfig, 'systemName')) {
        // 只展示 TDuck 后台明确配置的系统名称；未配置时不要回退显示 Tduck。
        this.systemName = this.normalizeSystemName(systemConfig.systemName)
      }
    },
    applyHomeCopyright(value) {`;

    if (!source.includes(methodBefore)) {
      throw new Error("TDuck home branding method shape changed");
    }
    source = source.replace(methodBefore, methodAfter);
  }

  if (!source.includes("normalizeSystemName(value)")) {
    const methodBefore = "    normalizeCopyrightText(value) {";
    const methodAfter = String.raw`    normalizeSystemName(value) {
      if (value === null || value === undefined) {
        return ''
      }
      return String(value).trim()
    },
    normalizeCopyrightText(value) {`;

    if (!source.includes(methodBefore)) {
      throw new Error("TDuck home normalize method shape changed");
    }
    source = source.replace(methodBefore, methodAfter);
  }

  if (!source.includes(".home-system-name")) {
    const styleBefore = String.raw`      .home-logo-img {
        width: auto;
        display: block;
      }`;
    const styleAfter = String.raw`      .home-system-name {
        max-width: 190px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: #3370ff;
        font-size: 23px;
        font-weight: 850;
        line-height: 1;
      }`;

    if (!source.includes(styleBefore)) {
      throw new Error("TDuck home logo style shape changed");
    }
    source = source.replace(styleBefore, styleAfter);
  }

  fs.writeFileSync(file, source);
}

function patchFormSystemNameBranding() {
  const file = "src/views/form/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (!source.includes("class=\"form-system-name\"")) {
    const logoBefore = String.raw`        <div class="logo-wrapper" @click="$router.push({ path: '/home' })">
          <img src="@/assets/images/logo-tduck-ce.svg" alt="TDUCK Logo" class="form-logo-img" />
        </div>`;
    const logoAfter = String.raw`        <div v-if="systemName" class="logo-wrapper" @click="$router.push({ path: '/home' })">
          <span class="form-system-name">{{ systemName }}</span>
        </div>`;

    if (!source.includes(logoBefore)) {
      throw new Error("TDuck form logo template shape changed");
    }
    source = source.replace(logoBefore, logoAfter);
  }

  if (!source.includes("import { getSystemInfoConfig } from '@/api/mange/config'")) {
    const importBefore = "import TemplateCreate from '@/views/project/template/create'\n";
    const importAfter =
      "import TemplateCreate from '@/views/project/template/create'\n" +
      "import { getSystemInfoConfig } from '@/api/mange/config'\n";

    if (!source.includes(importBefore)) {
      throw new Error("TDuck form imports branding shape changed");
    }
    source = source.replace(importBefore, importAfter);
  }

  if (!source.includes("systemName: ''")) {
    const dataBefore =
      "      previewDialogVisible: false,\n" +
      "      defaultActiveMenu: 'editor',\n";
    const dataAfter =
      "      previewDialogVisible: false,\n" +
      "      systemName: '',\n" +
      "      defaultActiveMenu: 'editor',\n";

    if (!source.includes(dataBefore)) {
      throw new Error("TDuck form data branding shape changed");
    }
    source = source.replace(dataBefore, dataAfter);
  }

  if (!source.includes("async created()")) {
    const createdBefore = String.raw`  created() {
    this.projectKey = this.$route.query.key
    this.defaultActiveMenu = this.$route.path
  },`;
    const createdAfter = String.raw`  async created() {
    this.applySystemName(localStorage.getItem('systemConfig'))
    try {
      const res = await getSystemInfoConfig()
      this.applySystemName(res ? res.data : null)
    } catch (error) {
      console.warn('获取表单编辑器品牌配置失败:', error)
    }
    this.projectKey = this.$route.query.key
    this.defaultActiveMenu = this.$route.path
  },`;

    if (!source.includes(createdBefore)) {
      throw new Error("TDuck form created branding shape changed");
    }
    source = source.replace(createdBefore, createdAfter);
  }

  if (!source.includes("applySystemName(value)")) {
    const methodsBefore = "  methods: {\n    menuSelectHandle(index) {";
    const methodsAfter = String.raw`  methods: {
    applySystemName(value) {
      const systemConfig = this.parseSystemInfoConfig(value)
      if (Object.prototype.hasOwnProperty.call(systemConfig, 'systemName')) {
        // 只展示 TDuck 后台明确配置的系统名称；未配置时不要回退显示 Tduck。
        this.systemName = this.normalizeSystemName(systemConfig.systemName)
      }
    },
    parseSystemInfoConfig(value) {
      if (!value) {
        return {}
      }
      if (typeof value === 'object') {
        return value
      }
      try {
        return JSON.parse(value)
      } catch (error) {
        console.warn('解析系统配置失败:', error)
        return {}
      }
    },
    normalizeSystemName(value) {
      if (value === null || value === undefined) {
        return ''
      }
      return String(value).trim()
    },
    menuSelectHandle(index) {`;

    if (!source.includes(methodsBefore)) {
      throw new Error("TDuck form methods branding shape changed");
    }
    source = source.replace(methodsBefore, methodsAfter);
  }

  if (!source.includes(".form-system-name")) {
    const styleBefore = String.raw`    .form-logo-img {
      height: 34px;
      width: auto;
      display: block;
    }`;
    const styleAfter = String.raw`    .form-system-name {
      max-width: 220px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #3370ff;
      font-size: 21px;
      font-weight: 850;
      line-height: 1;
    }`;

    if (!source.includes(styleBefore)) {
      throw new Error("TDuck form logo style shape changed");
    }
    source = source.replace(styleBefore, styleAfter);
  }

  fs.writeFileSync(file, source);
}

function patchLoginSystemNameBranding() {
  const file = "src/views/account/login/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (!source.includes("{{ systemName }}")) {
    const bannerBefore = String.raw`        <div class="banner-header">
          <h1 class="brand-title">Tduck <span class="brand-badge">CE</span></h1>
          <p class="brand-description">简单 · 专业 · 现代的问卷数据收集平台</p>
        </div>`;
    const bannerAfter = String.raw`        <div v-if="systemName" class="banner-header">
          <h1 class="brand-title">{{ systemName }}</h1>
        </div>`;

    if (!source.includes(bannerBefore)) {
      throw new Error("TDuck login banner branding shape changed");
    }
    source = source.replace(bannerBefore, bannerAfter);

    const welcomeBefore = "          <span class=\"tips\">欢迎使用 Tduck！</span>";
    const welcomeAfter = "          <span v-if=\"systemName\" class=\"tips\">欢迎使用 {{ systemName }}！</span>";

    if (!source.includes(welcomeBefore)) {
      throw new Error("TDuck login welcome branding shape changed");
    }
    source = source.replace(welcomeBefore, welcomeAfter);
  }

  source = source.replace(/\n\s*<p class="brand-description">简单 · 专业 · 现代的问卷数据收集平台<\/p>/g, "");

  const brandDescriptionStyle = String.raw`
    .brand-description {
      font-size: 15px;
      color: #646a73;
      margin: 0;
      font-weight: 400;
      letter-spacing: 0.5px;
    }
`;
  source = source.replace(brandDescriptionStyle, "");

  source = source.replace(
    '<div v-if="formType == \'login\'" class="welcome-header">',
    '<div v-if="formType == \'login\' && systemName" class="welcome-header">'
  );

  source = source.replace(
    '<register v-else @success="registerSuccessHandle" @back="formType = \'login\'" />',
    '<register v-else :system-name="systemName" @success="registerSuccessHandle" @back="formType = \'login\'" />'
  );

  source = source.replace('alt="Tduck Illustration"', 'alt="登录插图"');

  if (!source.includes("systemName: ''")) {
    const dataBefore =
      "      enableWx: false,\n" +
      "      copyright: ''\n";
    const dataAfter =
      "      enableWx: false,\n" +
      "      copyright: '',\n" +
      "      systemName: ''\n";

    if (!source.includes(dataBefore)) {
      throw new Error("TDuck login data branding shape changed");
    }
    source = source.replace(dataBefore, dataAfter);
  }

  const createdBefore = String.raw`  async created() {
    let sysRes = await this.$api.get('/public/systemInfoConfig')
    let { openWxMpLogin, copyright } = JSON.parse(sysRes.data)
    this.enableWx = openWxMpLogin || false
    this.copyright = copyright || ''`;
  const createdAfter = String.raw`  async created() {
    let sysRes = await this.$api.get('/public/systemInfoConfig')
    let systemConfig = JSON.parse(sysRes.data)
    let { openWxMpLogin, copyright } = systemConfig
    this.enableWx = openWxMpLogin || false
    this.copyright = copyright || ''
    // 只展示 TDuck 后台明确配置的系统名称；未配置时不要回退显示 Tduck。
    this.systemName = this.normalizeSystemName(systemConfig.systemName)`;

  if (source.includes(createdBefore)) {
    source = source.replace(createdBefore, createdAfter);
  } else if (!source.includes("this.systemName = this.normalizeSystemName(systemConfig.systemName)")) {
    throw new Error("TDuck login created branding shape changed");
  }

  if (!source.includes("normalizeSystemName(value)")) {
    const methodsBefore = "  methods: {\n    // 获取微信登录二维码\n";
    const methodsAfter = String.raw`  methods: {
    normalizeSystemName(value) {
      if (value === null || value === undefined) {
        return ''
      }
      return String(value).trim()
    },
    // 获取微信登录二维码
`;

    if (!source.includes(methodsBefore)) {
      throw new Error("TDuck login methods branding shape changed");
    }
    source = source.replace(methodsBefore, methodsAfter);
  }

  fs.writeFileSync(file, source);
}

function patchForgetPasswordRemoveLogo() {
  const file = "src/views/account/ForgetPwd.vue";
  let source = fs.readFileSync(file, "utf8");

  if (!source.includes("forget-password-logo-removed")) {
    const logoBlock = String.raw`      <!-- 品牌Logo -->
      <div class="brand-header">
        <img class="header-logo-img" src="~@/assets/images/logo-blue.png" @click="$router.push({ path: '/' })" />
      </div>
`;
    const logoRemovedMarker = "      <!-- forget-password-logo-removed -->\n";

    if (!source.includes(logoBlock)) {
      throw new Error("TDuck forget password logo block shape changed");
    }
    source = source.replace(logoBlock, logoRemovedMarker);
  }

  fs.writeFileSync(file, source);
}

function patchRegisterSystemNameBranding() {
  const file = "src/views/account/register/index.vue";
  let source = fs.readFileSync(file, "utf8");

  if (!source.includes("注册成为{{ systemName }}用户")) {
    const tipsBefore = '    <p class="tips">注册成为Tduck体验用户~</p>';
    const tipsAfter = '    <p v-if="systemName" class="tips">注册成为{{ systemName }}用户~</p>';

    if (!source.includes(tipsBefore)) {
      throw new Error("TDuck register tips branding shape changed");
    }
    source = source.replace(tipsBefore, tipsAfter);
  }

  if (!source.includes("systemName: {")) {
    const componentBefore = "  name: 'Register',\n";
    const componentAfter =
      "  name: 'Register',\n" +
      "  props: {\n" +
      "    systemName: {\n" +
      "      type: String,\n" +
      "      default: ''\n" +
      "    }\n" +
      "  },\n";

    if (!source.includes(componentBefore)) {
      throw new Error("TDuck register component shape changed");
    }
    source = source.replace(componentBefore, componentAfter);
  }

  fs.writeFileSync(file, source);
}

function patchAppSystemNameTitle() {
  const file = "src/App.vue";
  let source = fs.readFileSync(file, "utf8");

  if (source.includes("const systemName = rawSystemName === null || rawSystemName === undefined ? '' : String(rawSystemName).trim()")) {
    return;
  }

  const templateBefore = [
    "  metaInfo: {",
    "    titleTemplate: (title) => {",
    "      try {",
    "        const systemConfig = JSON.parse(localStorage.getItem('systemConfig'))",
    "        const systemName = systemConfig?.systemName || process.env.VUE_APP_TITLE",
    "        return title ? `${title} - ${systemName}` : systemName",
    "      } catch (error) {",
    "        console.error('获取系统名称失败:', error)",
    "        return title ? `${title} - ${process.env.VUE_APP_TITLE}` : process.env.VUE_APP_TITLE",
    "      }",
    "    }",
    "  }"
  ].join("\n");
  const templateAfter = [
    "  metaInfo: {",
    "    titleTemplate: (title) => {",
    "      try {",
    "        const systemConfig = JSON.parse(localStorage.getItem('systemConfig'))",
    "        const rawSystemName = systemConfig?.systemName",
    "        const systemName = rawSystemName === null || rawSystemName === undefined ? '' : String(rawSystemName).trim()",
    "        // 系统名称未配置时，不再回退到 TDuck 默认标题，避免浏览器标签页暴露旧品牌。",
    "        if (!systemName) {",
    "          return title || ''",
    "        }",
    "        return title ? `${title} - ${systemName}` : systemName",
    "      } catch (error) {",
    "        console.error('获取系统名称失败:', error)",
    "        return title || ''",
    "      }",
    "    }",
    "  }"
  ].join("\n");

  if (!source.includes(templateBefore)) {
    throw new Error("TDuck app title template shape changed");
  }

  source = source.replace(templateBefore, templateAfter);
  fs.writeFileSync(file, source);
}

patchProjectMyAiCreate();
patchWritePageSupportText();
patchWritePageAttribution();
assertWritePageAttributionPatch();
patchWritePagePhonePrecheck();
assertWritePagePhonePrecheckPatch();
patchFormGeneratorPhonePrecheckSetting();
assertFormGeneratorPhonePrecheckSettingPatch();
patchHomeFooterCopyright();
patchHomeSystemNameBranding();
patchFormSystemNameBranding();
patchLoginSystemNameBranding();
patchForgetPasswordRemoveLogo();
patchRegisterSystemNameBranding();
patchAppSystemNameTitle();
