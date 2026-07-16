// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, defineComponent, h, nextTick, ref, type App, type Component, watch } from "vue";
import OperationLogView from "../src/views/system/OperationLogView.vue";

const state = vi.hoisted(() => ({
  get: vi.fn(),
  isMobile: false,
  isForbidden: false
}));

vi.mock("@/api/http", () => ({
  http: { get: state.get },
  unwrapApi: <T>(response: { data: T }) => response.data,
  isForbidden: () => state.isForbidden,
  extractErrorMessage: () => "操作日志加载失败"
}));

vi.mock("@/composables/useResponsive", async () => {
  const { ref } = await import("vue");
  return { useIsMobile: () => ref(state.isMobile) };
});

vi.mock("@/components/PageHeader.vue", () => ({
  default: defineComponent({
    setup: () => () => h("h1", "操作日志")
  })
}));

vi.mock("@/utils/meetingDisplay", () => ({
  formatDateTimeLabel: (value?: string) => value || "-",
  operationActionLabel: (value?: string) => value || "-",
  operationLogCategories: [
    "ACCOUNT_PERMISSION",
    "MEETING_CONFIGURATION",
    "ATTENDEE_CHECKIN",
    "VENUE_SEATING",
    "REGISTRATION_TDUCK",
    "INVITATION",
    "IMPORT_EXPORT",
    "SYSTEM"
  ],
  operationLogCategoryLabel: (value?: string) => ({
    ACCOUNT_PERMISSION: "账户与权限",
    MEETING_CONFIGURATION: "会议与配置",
    ATTENDEE_CHECKIN: "参会人与签到",
    VENUE_SEATING: "场地与排座",
    REGISTRATION_TDUCK: "报名与 TDuck",
    INVITATION: "邀请函",
    IMPORT_EXPORT: "导入导出",
    SYSTEM: "系统"
  })[value || "SYSTEM"] || value || "系统",
  operationMessageLabel: (value?: string) => value || "-",
  operationTargetLabel: (targetType?: string, targetId?: string) => [targetType, targetId].filter(Boolean).join(" ") || "-"
}));

vi.mock("naive-ui", () => {
  const passthrough = defineComponent({
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, slots.default?.())
  });
  const dataTable = defineComponent({
    props: { data: { type: Array, default: () => [] } },
    setup: (props) => () => h(
      "div",
      { class: "data-table-stub" },
      (props.data as Array<{ operator: string; category: string; type: string; success: boolean }>).map((row) =>
        h("div", `${row.operator} ${row.category} ${row.type} ${row.success ? "成功" : "失败"}`)
      )
    )
  });
  const pagination = defineComponent({
    props: { page: { type: Number, default: 1 } },
    emits: ["update:page"],
    setup: (props, { emit }) => () => h(
      "button",
      { class: "log-next-page", onClick: () => emit("update:page", props.page + 1) },
      "下一页"
    )
  });
  const button = defineComponent({
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("button", attrs, slots.default?.())
  });
  const select = defineComponent({
    inheritAttrs: false,
    props: {
      value: { type: [String, Number, Boolean], default: null },
      options: { type: Array, default: () => [] }
    },
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => () => {
      const options = props.options as Array<{ label: string; value: unknown }>;
      const selectedIndex = options.findIndex((option) => option.value === props.value);
      return h("select", {
        ...attrs,
        value: selectedIndex < 0 ? "" : String(selectedIndex),
        onChange: (event: Event) => {
          const index = Number((event.target as HTMLSelectElement).value);
          emit("update:value", Number.isInteger(index) && index >= 0 ? options[index]?.value ?? null : null);
        }
      }, [
        h("option", { value: "" }, "全部"),
        ...options.map((option, index) => h("option", { value: String(index) }, option.label))
      ]);
    }
  });
  const input = defineComponent({
    inheritAttrs: false,
    props: { value: { type: String, default: "" } },
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => () => h("input", {
      ...attrs,
      value: props.value,
      onInput: (event: Event) => emit("update:value", (event.target as HTMLInputElement).value),
      onKeyup: (event: KeyboardEvent) => invokeListener(attrs.onKeyup, event)
    })
  });
  const datePicker = defineComponent({
    inheritAttrs: false,
    props: { value: { type: Array, default: null } },
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => {
      const startValue = ref(dateInputValue((props.value as number[] | null)?.[0]));
      const endValue = ref(dateInputValue((props.value as number[] | null)?.[1]));
      watch(() => props.value, (value) => {
        const range = value as number[] | null;
        startValue.value = dateInputValue(range?.[0]);
        endValue.value = dateInputValue(range?.[1]);
      });
      const updateRange = (index: 0 | 1, value: string) => {
        if (index === 0) startValue.value = value;
        else endValue.value = value;
        if (!startValue.value || !endValue.value) {
          emit("update:value", null);
          return;
        }
        emit("update:value", [toLocalTimestamp(startValue.value), toLocalTimestamp(endValue.value)]);
      };
      return () => h("div", attrs, [
        h("input", {
          class: "log-filter-date-from",
          type: "date",
          value: startValue.value,
          onChange: (event: Event) => updateRange(0, (event.target as HTMLInputElement).value)
        }),
        h("input", {
          class: "log-filter-date-to",
          type: "date",
          value: endValue.value,
          onChange: (event: Event) => updateRange(1, (event.target as HTMLInputElement).value)
        })
      ]);
    }
  });
  const switchControl = defineComponent({
    inheritAttrs: false,
    props: { value: { type: Boolean, default: false } },
    emits: ["update:value"],
    setup: (props, { attrs, emit }) => () => h("input", {
      ...attrs,
      type: "checkbox",
      checked: props.value,
      onChange: (event: Event) => emit("update:value", (event.target as HTMLInputElement).checked)
    })
  });

  return {
    NAlert: passthrough,
    NButton: button,
    NDataTable: dataTable,
    NDatePicker: datePicker,
    NInput: input,
    NPagination: pagination,
    NSelect: select,
    NSpin: passthrough,
    NSwitch: switchControl,
    NTag: passthrough
  };
});

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  state.get.mockReset();
  state.isMobile = false;
  state.isForbidden = false;
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("操作日志筛选与分页", () => {
  it("默认隐藏邀请函草稿保存，兼容 AxiosHeaders，并在翻页时复用快照", async () => {
    const snapshotHeaders = axiosHeaders("310");
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "admin", true)], 0, 41, undefined, snapshotHeaders))
      .mockResolvedValueOnce(pageResponse([log(3, "next-page", true)], 1, 41, "310"));
    mountView();

    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(1));
    expect(root?.querySelector<HTMLInputElement>(".log-draft-switch input")?.checked).toBe(false);
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: { page: 0, size: 20, includeDraftSaves: false }
    });
    expect(snapshotHeaders.get).toHaveBeenCalledWith("x-meetingos-log-snapshot");

    await vi.waitFor(() => expect(root?.querySelector(".log-next-page")).not.toBeNull());
    root?.querySelector<HTMLButtonElement>(".log-next-page")?.click();

    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(2));
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: { page: 1, size: 20, includeDraftSaves: false },
      headers: { "X-MeetingOS-Log-Snapshot": "310" }
    });
    await vi.waitFor(() => expect(root?.textContent).toContain("next-page"));
  });

  it("查询时提交分类、结果、日期、关键词和草稿开关，并为新条件清空旧快照", async () => {
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "before-filter", true)], 0, 41, "310"))
      .mockResolvedValueOnce(pageResponse([log(2, "filtered", false, "INVITATION")], 0, 41, "410"));
    mountView();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(1));

    await selectByText(".log-filter-category", "邀请函");
    await selectByText(".log-filter-success", "失败");
    setDate(".log-filter-date-from", "2026-07-01");
    setDate(".log-filter-date-to", "2026-07-16");
    setInput(".log-filter-keyword", "  发布人  ");
    setChecked(".log-draft-switch input", true);
    root?.querySelector<HTMLButtonElement>(".log-filter-submit")?.click();

    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(2));
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: {
        page: 0,
        size: 20,
        category: "INVITATION",
        success: false,
        fromDate: "2026-07-01",
        toDate: "2026-07-16",
        keyword: "发布人",
        includeDraftSaves: true
      }
    });
    await vi.waitFor(() => expect(root?.textContent).toContain("filtered"));
  });

  it("未点击查询的编辑态筛选不会影响已有快照的翻页请求", async () => {
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "first-page", true)], 0, 41, "310"))
      .mockResolvedValueOnce(pageResponse([log(2, "second-page", true)], 1, 41, "310"));
    mountView();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(1));

    await selectByText(".log-filter-category", "邀请函");
    setInput(".log-filter-keyword", "尚未查询");
    root?.querySelector<HTMLButtonElement>(".log-next-page")?.click();

    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(2));
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: { page: 1, size: 20, includeDraftSaves: false },
      headers: { "X-MeetingOS-Log-Snapshot": "310" }
    });
  });

  it("重置恢复默认筛选、第一页和草稿默认隐藏状态", async () => {
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "initial", true)], 0, 41, "310"))
      .mockResolvedValueOnce(pageResponse([log(2, "filtered", false, "INVITATION")], 0, 41, "410"))
      .mockResolvedValueOnce(pageResponse([log(3, "reset", true)], 0, 41, "510"));
    mountView();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(1));

    await selectByText(".log-filter-category", "邀请函");
    setChecked(".log-draft-switch input", true);
    root?.querySelector<HTMLButtonElement>(".log-filter-submit")?.click();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(2));

    root?.querySelector<HTMLButtonElement>(".log-filter-reset")?.click();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(3));
    expect(root?.querySelector<HTMLInputElement>(".log-draft-switch input")?.checked).toBe(false);
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: { page: 0, size: 20, includeDraftSaves: false }
    });
  });

  it("关键词输入框按回车等同于点击查询", async () => {
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "initial", true)], 0, 1, "310"))
      .mockResolvedValueOnce(pageResponse([log(2, "searched", true)], 0, 1, "410"));
    mountView();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(1));

    setInput(".log-filter-keyword", "签到");
    const input = root?.querySelector<HTMLInputElement>(".log-filter-keyword");
    input?.dispatchEvent(new KeyboardEvent("keyup", { key: "Enter", bubbles: true }));

    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(2));
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: { page: 0, size: 20, keyword: "签到", includeDraftSaves: false }
    });
  });

  it("新筛选的响应先返回时，不会被旧请求的迟到响应覆盖", async () => {
    const initial = deferred<ReturnType<typeof pageResponse>>();
    state.get
      .mockReturnValueOnce(initial.promise)
      .mockResolvedValueOnce(pageResponse([log(2, "new-filter-result", true)], 0, 1, "410"));
    mountView();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(1));

    setInput(".log-filter-keyword", "新条件");
    root?.querySelector<HTMLButtonElement>(".log-filter-submit")?.click();
    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(2));
    await vi.waitFor(() => expect(root?.textContent).toContain("new-filter-result"));

    initial.resolve(pageResponse([log(1, "stale-result", true)], 0, 1, "310"));
    await nextTick();
    await nextTick();
    expect(root?.textContent).not.toContain("stale-result");
    expect(root?.textContent).toContain("new-filter-result");
  });

  it("在移动端展示分类和结果", async () => {
    state.isMobile = true;
    state.get.mockResolvedValue(pageResponse([log(1, "admin", false, "INVITATION")], 0, 1));
    mountView();

    await vi.waitFor(() => expect(root?.textContent).toContain("分类：邀请函"));
    expect(root?.textContent).toContain("失败");
    expect(root?.textContent).toContain("操作人");
    expect(root?.textContent).toContain("admin");
  });

  it("分页加载失败时保留上一页并允许重试目标页", async () => {
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "first-page", true)], 0, 41, "310"))
      .mockRejectedValueOnce(new Error("network failed"))
      .mockResolvedValueOnce(pageResponse([log(21, "recovered-page", true)], 1, 41, "310"));
    mountView();

    await vi.waitFor(() => expect(root?.textContent).toContain("first-page"));
    root?.querySelector<HTMLButtonElement>(".log-next-page")?.click();

    await vi.waitFor(() => expect(root?.textContent).toContain("操作日志加载失败"));
    expect(root?.textContent).toContain("first-page");
    expect(root?.textContent).toContain("共 41 条记录");

    root?.querySelector<HTMLButtonElement>(".log-retry-button")?.click();

    await vi.waitFor(() => expect(state.get).toHaveBeenCalledTimes(3));
    expect(state.get).toHaveBeenLastCalledWith("/admin/operation-logs", {
      params: { page: 1, size: 20, includeDraftSaves: false },
      headers: { "X-MeetingOS-Log-Snapshot": "310" }
    });
    await vi.waitFor(() => expect(root?.textContent).toContain("recovered-page"));
    expect(root?.textContent).not.toContain("操作日志加载失败");
  });

  it("已加载后遇到权限拒绝时清空敏感日志且不提供重试", async () => {
    state.get
      .mockResolvedValueOnce(pageResponse([log(1, "visible-before-revoke", true)], 0, 41, "310"))
      .mockRejectedValueOnce(new Error("forbidden"));
    mountView();

    await vi.waitFor(() => expect(root?.textContent).toContain("visible-before-revoke"));
    state.isForbidden = true;
    root?.querySelector<HTMLButtonElement>(".log-next-page")?.click();

    await vi.waitFor(() => expect(root?.textContent).toContain("当前账号无权查看操作日志"));
    expect(root?.textContent).not.toContain("visible-before-revoke");
    expect(root?.textContent).toContain("共 0 条记录");
    expect(root?.querySelector(".log-retry-button")).toBeNull();
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(OperationLogView);
  const passthrough: Component = { setup: (_props, { slots }) => () => h("div", slots.default?.()) };
  app.component("NCard", passthrough);
  app.component("NEmpty", passthrough);
  app.config.warnHandler = () => undefined;
  app.mount(root);
}

async function selectByText(selector: string, label: string) {
  const select = root?.querySelector<HTMLSelectElement>(selector);
  const option = [...(select?.options || [])].find((item) => item.textContent === label);
  if (!select || !option) throw new Error(`找不到筛选项：${label}`);
  select.value = option.value;
  select.dispatchEvent(new Event("change", { bubbles: true }));
  await nextTick();
}

function setInput(selector: string, value: string) {
  const input = root?.querySelector<HTMLInputElement>(selector);
  if (!input) throw new Error(`找不到输入框：${selector}`);
  input.value = value;
  input.dispatchEvent(new Event("input", { bubbles: true }));
}

function setDate(selector: string, value: string) {
  const input = root?.querySelector<HTMLInputElement>(selector);
  if (!input) throw new Error(`找不到日期输入框：${selector}`);
  input.value = value;
  input.dispatchEvent(new Event("change", { bubbles: true }));
}

function setChecked(selector: string, value: boolean) {
  const input = root?.querySelector<HTMLInputElement>(selector);
  if (!input) throw new Error(`找不到开关：${selector}`);
  input.checked = value;
  input.dispatchEvent(new Event("change", { bubbles: true }));
}

function invokeListener(listener: unknown, event: Event) {
  if (typeof listener === "function") {
    listener(event);
    return;
  }
  if (Array.isArray(listener)) {
    listener.forEach((item) => {
      if (typeof item === "function") item(event);
    });
  }
}

function dateInputValue(timestamp?: number) {
  if (typeof timestamp !== "number") return "";
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function toLocalTimestamp(value: string) {
  return new Date(`${value}T00:00:00`).getTime();
}

function pageResponse(
  content: ReturnType<typeof log>[],
  page: number,
  totalElements: number,
  snapshotId?: string,
  headers?: { get: ReturnType<typeof vi.fn> } | Record<string, string>
) {
  return {
    data: {
      content,
      page,
      size: 20,
      totalElements,
      totalPages: Math.ceil(totalElements / 20)
    },
    headers: headers ?? (snapshotId ? { "x-meetingos-log-snapshot": snapshotId } : {})
  };
}

function axiosHeaders(snapshotId: string) {
  return {
    get: vi.fn((name: string) => name === "x-meetingos-log-snapshot" ? snapshotId : undefined)
  };
}

function log(id: number, actor: string, success: boolean, category = "ATTENDEE_CHECKIN") {
  return {
    id,
    actor,
    action: "CHECKIN",
    category,
    targetType: "ATTENDEE",
    targetId: String(id),
    message: "操作完成",
    createdAt: "2026-07-16T10:00:00",
    success
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((innerResolve) => { resolve = innerResolve; });
  return { promise, resolve };
}
