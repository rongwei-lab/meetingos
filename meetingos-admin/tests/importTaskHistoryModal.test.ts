// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, defineComponent, h, nextTick, ref, type App, type Component } from "vue";
import ImportTaskHistoryModal from "../src/components/ImportTaskHistoryModal.vue";

const state = vi.hoisted(() => ({
  loadImportTasks: vi.fn(),
  loadImportTask: vi.fn(),
  downloadImportFailures: vi.fn()
}));

vi.mock("@/stores/meeting", () => ({
  useMeetingStore: () => state
}));

vi.mock("@/composables/useResponsive", () => ({
  useIsMobile: () => ref(false)
}));

vi.mock("naive-ui", () => {
  const passthrough = defineComponent({
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, slots.default?.())
  });
  const paginationPassthrough = defineComponent({
    props: {
      prefix: Function
    },
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, slots.default?.())
  });
  return {
    NButton: passthrough,
    NDataTable: passthrough,
    NPagination: paginationPassthrough,
    NTag: passthrough,
    useMessage: () => ({ error: vi.fn(), success: vi.fn() })
  };
});

let app: App<Element> | null = null;
let root: HTMLDivElement | null = null;

beforeEach(() => {
  vi.useFakeTimers();
  state.loadImportTasks.mockReset().mockResolvedValue({
    content: [activeTask()],
    page: 0,
    size: 200,
    totalElements: 1,
    totalPages: 1
  });
  state.loadImportTask.mockReset().mockResolvedValue({ ...activeTask(), status: "COMPLETED", progress: 100 });
  state.downloadImportFailures.mockReset().mockResolvedValue(undefined);
});

afterEach(() => {
  app?.unmount();
  root?.remove();
  app = null;
  root = null;
  vi.useRealTimers();
});

describe("导入历史轮询", () => {
  it("只刷新当前页活动任务，不重复读取全部历史分页", async () => {
    const show = ref(false);
    const wrapper = defineComponent({
      setup: () => () => h(ImportTaskHistoryModal, {
        show: show.value,
        meetingId: "1",
        typeOptions: [{ label: "参会人", value: "attendees" }],
        "onUpdate:show": (value: boolean) => { show.value = value; }
      })
    });
    mount(wrapper);

    show.value = true;
    await nextTick();
    await flushPromises();
    expect(state.loadImportTasks).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(2500);
    await flushPromises();

    expect(state.loadImportTask).toHaveBeenCalledTimes(1);
    expect(state.loadImportTask).toHaveBeenCalledWith("task-1", expect.objectContaining({ meetingId: "1" }));
    expect(state.loadImportTasks).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("单个任务刷新失败时仍合并其他成功状态且只重试失败任务", async () => {
    state.loadImportTasks.mockResolvedValue({
      content: [activeTask("task-1"), activeTask("task-2")],
      page: 0,
      size: 200,
      totalElements: 2,
      totalPages: 1
    });
    let task2Attempts = 0;
    state.loadImportTask.mockImplementation(async (taskId: string) => {
      if (taskId === "task-1") return { ...activeTask(taskId), status: "COMPLETED", progress: 100 };
      task2Attempts += 1;
      if (task2Attempts === 1) throw new Error("temporary failure");
      return { ...activeTask(taskId), status: "COMPLETED", progress: 100 };
    });
    const show = ref(false);
    const wrapper = defineComponent({
      setup: () => () => h(ImportTaskHistoryModal, {
        show: show.value,
        meetingId: "1",
        typeOptions: [{ label: "参会人", value: "attendees" }],
        "onUpdate:show": (value: boolean) => { show.value = value; }
      })
    });
    mount(wrapper);
    show.value = true;
    await nextTick();
    await flushPromises();

    await vi.advanceTimersByTimeAsync(2500);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(2500);
    await flushPromises();

    expect(state.loadImportTask.mock.calls.map(([taskId]) => taskId)).toEqual([
      "task-1",
      "task-2",
      "task-2"
    ]);
    expect(vi.getTimerCount()).toBe(0);
  });
});

function activeTask(id = "task-1") {
  return {
    id,
    meetingId: "1",
    type: "attendees",
    status: "RUNNING",
    originalFilename: "attendees.xlsx",
    totalRows: 100,
    processedRows: 20,
    importedRows: 20,
    failedRows: 0,
    progress: 20,
    failureFileAvailable: false,
    createdAt: "2026-07-16T00:00:00Z"
  };
}

function mount(component: Component) {
  root = document.createElement("div");
  document.body.appendChild(root);
  app = createApp(component);
  const passthrough: Component = {
    inheritAttrs: false,
    setup: (_props, { attrs, slots }) => () => h("div", attrs, [slots.header?.(), slots.default?.()])
  };
  for (const name of ["NAlert", "NEmpty", "NIcon", "NModal", "NSpin", "NTabPane", "NTabs"]) {
    app.component(name, passthrough);
  }
  app.mount(root);
}

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}
