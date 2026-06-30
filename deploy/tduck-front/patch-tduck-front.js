const fs = require("fs");

const file = "src/views/project/my/index.vue";
let source = fs.readFileSync(file, "utf8");

if (!source.includes("openAiCreateFromQuery() {")) {
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
