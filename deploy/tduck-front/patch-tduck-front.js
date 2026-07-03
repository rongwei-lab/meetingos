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
patchHomeFooterCopyright();
patchHomeSystemNameBranding();
patchFormSystemNameBranding();
patchLoginSystemNameBranding();
patchForgetPasswordRemoveLogo();
patchRegisterSystemNameBranding();
patchAppSystemNameTitle();
