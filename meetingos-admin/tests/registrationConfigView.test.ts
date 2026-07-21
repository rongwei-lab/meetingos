// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, defineComponent, h, ref, type App, type Component } from "vue";
import RegistrationConfigView from "../src/views/registration/RegistrationConfigView.vue";

const state = vi.hoisted(() => ({
  meeting: {
    id: "3",
    code: "MEET-730",
    name: "730",
    tduckFormKey: "",
    tduckFormName: "",
    registrationUrl: ""
  },
  binding: null as null | {
    formKey: string;
    formName: string;
    registrationUrl: string;
    registrationEnabled: boolean;
    tduckPublished: boolean;
    validationStatus: string;
  },
  loadTduckBinding: vi.fn(),
  loadTduckForms: vi.fn(),
  loadTduckMappings: vi.fn(),
  loadSyncLogs: vi.fn(),
  loadTduckSyncLogDetail: vi.fn(),
  syncLogs: [] as Array<Record<string, unknown>>,
  loadTduckSubmissionConflicts: vi.fn(),
  loadTduckSubmissionConflict: vi.fn(),
  resolveTduckSubmissionConflict: vi.fn(),
  loadAttendees: vi.fn(),
  loadStatistics: vi.fn(),
  dialogWarning: vi.fn(),
  auth: {
    user: { roles: ["MEETING_ADMIN"] as string[] }
  },
  submissionConflicts: { content: [] as Array<Record<string, unknown>>, page: 0, size: 20, totalElements: 0, totalPages: 0 },
  syncMeetingFromRoute: vi.fn()
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() })
}));

vi.mock("@/composables/useRouteMeeting", () => ({
  useRouteMeeting: () => ({
    routeMeetingId: ref("3"),
    syncMeetingFromRoute: state.syncMeetingFromRoute
  })
}));

vi.mock("@/stores/meeting", () => ({
  useMeetingStore: () => ({
    currentMeetingId: "3",
    currentMeeting: state.meeting,
    syncLogs: state.syncLogs,
    currentTduckSubmissionConflicts: state.submissionConflicts,
    loadTduckBinding: state.loadTduckBinding,
    loadTduckForms: state.loadTduckForms,
    loadTduckMappings: state.loadTduckMappings,
    loadSyncLogs: state.loadSyncLogs,
    loadTduckSyncLogDetail: state.loadTduckSyncLogDetail,
    loadTduckSubmissionConflicts: state.loadTduckSubmissionConflicts,
    loadTduckSubmissionConflict: state.loadTduckSubmissionConflict,
    resolveTduckSubmissionConflict: state.resolveTduckSubmissionConflict,
    loadAttendees: state.loadAttendees,
    loadStatistics: state.loadStatistics
  })
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => state.auth
}));

vi.mock("@/api/http", () => ({
  extractErrorMessage: (_error: unknown, fallback: string) => fallback
}));

vi.mock("qrcode", () => ({
  default: { toCanvas: vi.fn().mockResolvedValue(undefined) }
}));

vi.mock("@/components/PageHeader.vue", () => ({
  default: defineComponent({
    props: { title: String },
    setup: (props, { slots }) => () => h("header", [h("h1", props.title), slots.default?.()])
  })
}));

vi.mock("@/components/StatusTag.vue", () => ({
  default: defineComponent({
    props: { value: String },
    setup: (props) => () => h("span", props.value)
  })
}));

vi.mock("naive-ui", () => {
  const button = defineComponent({
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("button", attrs, slots.default?.())
  });
  const passthrough = defineComponent({
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, slots.default?.())
  });
  const dataTable = defineComponent({
    props: {
      data: { type: Array, default: () => [] },
      columns: { type: Array, default: () => [] }
    },
    setup: (props) => () => h(
      "div",
      { class: "data-table-stub" },
      (props.data as Array<Record<string, unknown>>).map((row) => h(
        "div",
        { class: "data-table-row" },
        (props.columns as Array<{ key?: string; render?: (item: Record<string, unknown>) => unknown }>).map((column) => h(
          "div",
          { class: "data-table-cell" },
          column.render ? column.render(row) : String(row[column.key || ""] || "")
        ))
      ))
    )
  });
  const modal = defineComponent({
    inheritAttrs: false,
    setup: (_props, { slots }) => () => h("div", [slots.default?.(), slots.footer?.()])
  });
  const pagination = defineComponent({
    inheritAttrs: false,
    setup: (_props, { slots }) => () => h("div", slots.default?.())
  });
  return {
    NButton: button,
    NDataTable: dataTable,
    NIcon: passthrough,
    NModal: modal,
    NPagination: pagination,
    NSelect: passthrough,
    NSwitch: passthrough,
    useMessage: () => ({
      error: vi.fn(),
      info: vi.fn(),
      success: vi.fn(),
      warning: vi.fn()
    }),
    useDialog: () => ({ warning: state.dialogWarning })
  };
});

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  state.meeting.tduckFormKey = "";
  state.meeting.tduckFormName = "";
  state.meeting.registrationUrl = "";
  state.binding = null;
  state.loadTduckBinding.mockReset().mockImplementation(async () => state.binding);
  state.loadTduckForms.mockReset().mockResolvedValue([]);
  state.loadTduckMappings.mockReset().mockResolvedValue([]);
  state.syncLogs = [];
  state.loadSyncLogs.mockReset().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  state.loadTduckSyncLogDetail.mockReset();
  state.submissionConflicts = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  state.loadTduckSubmissionConflicts.mockReset().mockResolvedValue(state.submissionConflicts);
  state.loadTduckSubmissionConflict.mockReset();
  state.resolveTduckSubmissionConflict.mockReset();
  state.loadAttendees.mockReset().mockResolvedValue(undefined);
  state.loadStatistics.mockReset().mockResolvedValue({});
  state.dialogWarning.mockReset();
  state.auth.user.roles = ["MEETING_ADMIN"];
  state.syncMeetingFromRoute.mockReset().mockResolvedValue(state.meeting);
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
});

describe("TDuck 表单新建入口", () => {
  it("会议已有绑定表单时隐藏新建入口并保留编辑入口", async () => {
    state.meeting.tduckFormKey = "RbFOPlV5";
    state.meeting.tduckFormName = "730 报名表";
    state.binding = {
      formKey: "RbFOPlV5",
      formName: "730 报名表",
      registrationUrl: "/tduck/s/RbFOPlV5",
      registrationEnabled: true,
      tduckPublished: true,
      validationStatus: "VALID"
    };

    mountView();
    await vi.waitFor(() => expect(state.loadTduckBinding).toHaveBeenCalledWith("3"));
    await vi.waitFor(() => expect(root?.textContent).toContain("已绑定"));

    expect(root?.textContent).not.toContain("新建 TDuck 表单");
    expect(root?.textContent).toContain("编辑当前表单");
  });

  it("会议尚未绑定表单时保留新建入口", async () => {
    mountView();
    await vi.waitFor(() => expect(state.loadTduckBinding).toHaveBeenCalledWith("3"));

    expect(root?.textContent).toContain("新建 TDuck 表单");
  });
});

describe("字段与补偿同步折叠", () => {
  it("默认收起，展开后可再次收起字段映射与补偿同步内容", async () => {
    mountView();
    await vi.waitFor(() => expect(state.loadTduckBinding).toHaveBeenCalledWith("3"));

    expect(root?.querySelector("#sync-workbench")).toBeNull();
    expect(findButton("展开").getAttribute("aria-expanded")).toBe("false");

    findButton("展开").click();
    await vi.waitFor(() => expect(root?.querySelector("#sync-workbench")).not.toBeNull());
    expect(root?.textContent).toContain("报名字段");
    expect(root?.textContent).toContain("补偿同步");
    expect(findButton("收起").getAttribute("aria-expanded")).toBe("true");

    findButton("收起").click();
    await vi.waitFor(() => expect(root?.querySelector("#sync-workbench")).toBeNull());
  });
});

describe("TDuck 同步日志详情", () => {
  it("点击数据 ID 后展示该次同步保存的完整表单内容", async () => {
    state.syncLogs = [{
      id: "91",
      meetingId: "3",
      formKey: "form-3",
      dataId: "211809",
      eventType: "PULL_SYNC",
      status: "SUCCESS",
      createdAt: "2026-07-21T19:04:25"
    }];
    state.loadSyncLogs.mockResolvedValue({ content: state.syncLogs, page: 0, size: 20, totalElements: 1, totalPages: 1 });
    state.loadTduckMappings.mockResolvedValue([
      { tduckFieldKey: "phone", tduckFieldLabel: "手机号", tduckRequired: true, requiredMapping: true }
    ]);
    state.loadTduckSyncLogDetail.mockResolvedValue({
      id: "91",
      meetingId: "3",
      formKey: "form-3",
      dataId: "211809",
      eventType: "PULL_SYNC",
      status: "SUCCESS",
      createdAt: "2026-07-21T19:04:25",
      formValues: { phone: "13800138000", company: "研发中心" }
    });

    mountView();
    await vi.waitFor(() => expect(root?.textContent).toContain("211809"));
    await vi.waitFor(() => expect(state.loadSyncLogs).toHaveBeenCalledWith("3", { page: 0, size: 20 }));

    findButton("211809").click();
    await vi.waitFor(() => expect(state.loadTduckSyncLogDetail).toHaveBeenCalledWith("91", "3"));
    await vi.waitFor(() => expect(root?.textContent).toContain("13800138000"));
    expect(root?.textContent).toContain("研发中心");
  });
});

describe("TDuck 重复报名处理", () => {
  it("打开对比详情后可确认采用新的 TDuck 提交", async () => {
    const conflict = {
      id: "44",
      meetingId: "3",
      attendeeId: "12",
      currentTduckDataId: "10001",
      incomingTduckDataId: "10002",
      status: "PENDING_CONFLICT",
      submittedAt: "2026-07-21T09:00:00",
      createdAt: "2026-07-21T09:00:01",
      existing: { name: "张三", phone: "13800138000", organization: "原单位", accommodationRequired: false, formValues: { name: "张三", phone: "13800138000", company: "原单位" } },
      incoming: { name: "张三", phone: "13800138000", organization: "新单位", accommodationRequired: true, formValues: { name: "张三", phone: "13800138000", company: "新单位" } }
    };
    state.submissionConflicts = { content: [conflict], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    state.loadTduckSubmissionConflicts.mockResolvedValue(state.submissionConflicts);
    state.loadTduckSubmissionConflict.mockResolvedValue(conflict);
    state.resolveTduckSubmissionConflict.mockResolvedValue({ ...conflict, status: "ACTIVE" });
    state.loadTduckMappings.mockResolvedValue([
      { tduckFieldKey: "name", tduckFieldLabel: "姓名", tduckRequired: true, requiredMapping: true },
      { tduckFieldKey: "phone", tduckFieldLabel: "手机号", tduckRequired: true, requiredMapping: true },
      { tduckFieldKey: "company", tduckFieldLabel: "单位名称", tduckRequired: false, requiredMapping: false }
    ]);

    mountView();
    await vi.waitFor(() => expect(root?.textContent).toContain("1 条待处理"));

    const openButton = findButton("核对处理");
    openButton.click();
    await vi.waitFor(() => expect(state.loadTduckSubmissionConflict).toHaveBeenCalledWith("44", "3"));
    await vi.waitFor(() => expect(root?.textContent).toContain("新的 TDuck 提交"));
    expect(root?.textContent).toContain("当前 TDuck 数据 ID：10001");
    expect(root?.textContent).toContain("新 TDuck 数据 ID：10002");
    expect(root?.textContent).toContain("13800138000");
    expect(root?.textContent).toContain("单位名称");
    expect(root?.textContent).toContain("原单位");
    expect(root?.textContent).toContain("新单位");

    findButton("采用新提交").click();
    await vi.waitFor(() => expect(state.dialogWarning).toHaveBeenCalledTimes(1));
    const dialogOptions = state.dialogWarning.mock.calls[0]?.[0] as { onPositiveClick: () => void };
    dialogOptions.onPositiveClick();

    await vi.waitFor(() => expect(state.resolveTduckSubmissionConflict).toHaveBeenCalledWith("44", "3", "ADOPT_INCOMING", ""));
    await vi.waitFor(() => expect(root?.textContent).not.toContain("核对重复 TDuck 报名"));
  });

  it("只读角色可以查看对比资料，但不能裁决重复报名", async () => {
    const conflict = {
      id: "45",
      meetingId: "3",
      attendeeId: "12",
      currentTduckDataId: "10001",
      incomingTduckDataId: "10003",
      status: "PENDING_CONFLICT",
      existing: { name: "李四", phone: "138****0000" },
      incoming: { name: "李四", phone: "138****0000" }
    };
    state.auth.user.roles = ["VIEWER"];
    state.submissionConflicts = { content: [conflict], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    state.loadTduckSubmissionConflicts.mockResolvedValue(state.submissionConflicts);
    state.loadTduckSubmissionConflict.mockResolvedValue(conflict);

    mountView();
    await vi.waitFor(() => expect(root?.textContent).toContain("查看资料"));

    findButton("查看资料").click();
    await vi.waitFor(() => expect(state.loadTduckSubmissionConflict).toHaveBeenCalledWith("45", "3"));
    expect(root?.textContent).toContain("新的 TDuck 提交");
    const buttonLabels = Array.from(root?.querySelectorAll("button") || []).map((button) => button.textContent?.trim());
    expect(buttonLabels).not.toContain("保留现有");
    expect(buttonLabels).not.toContain("采用新提交");
  });
});

function mountView() {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(RegistrationConfigView);

  const passthrough: Component = {
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, [slots.header?.(), slots.default?.()])
  };
  for (const name of ["NAlert", "NButton", "NCard", "NDropdown", "NEmpty", "NForm", "NFormItem", "NInput", "NSpace", "NSpin", "NTag"]) {
    app.component(name, passthrough);
  }
  app.mount(root);
}

function findButton(label: string) {
  const button = Array.from(root?.querySelectorAll("button") || []).find((candidate) => candidate.textContent?.trim() === label);
  if (!button) throw new Error(`未找到按钮：${label}`);
  return button;
}
