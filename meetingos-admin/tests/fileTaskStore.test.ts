import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../src/api/http";
import { useMeetingStore } from "../src/stores/meeting";

function envelope<T>(data: T) {
  return {
    data: {
      success: true,
      code: "OK",
      message: "success",
      timestamp: "2026-07-11T00:00:00Z",
      data
    }
  };
}

describe("meeting store 文件任务契约", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    const values = new Map<string, string>();
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
      clear: () => values.clear()
    });
    setActivePinia(createPinia());
  });

  it("导入先以稳定幂等键创建文件格式检查任务", async () => {
    const post = vi.spyOn(http, "post").mockResolvedValue(envelope({
      id: 31,
      meetingId: 7,
      type: "attendees",
      status: "PREFLIGHTING",
      originalFilename: "attendees.xlsx",
      totalRows: 0,
      processedRows: 0,
      importedRows: 0,
      failedRows: 0,
      progress: 0,
      failureFileAvailable: false
    }) as never);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");
    const file = new File(["xlsx"], "attendees.xlsx");

    const task = await store.preflightImportTask("attendees", file, "stable-key");

    expect(task).toMatchObject({ id: "31", meetingId: 7, type: "attendees", status: "PREFLIGHTING" });
    expect(post).toHaveBeenCalledWith("/admin/imports", expect.any(FormData), expect.objectContaining({
      params: { meetingId: 7, type: "attendees", preflight: true, idempotencyKey: "stable-key" },
      timeout: 120_000
    }));
  });

  it("分页加载导入历史并保留任务结果字段", async () => {
    const controller = new AbortController();
    const get = vi.spyOn(http, "get").mockResolvedValue(envelope({
      content: [{
        id: 31,
        meetingId: 7,
        type: "rooms",
        status: "COMPLETED",
        originalFilename: "rooms.xlsx",
        totalRows: 18,
        processedRows: 18,
        importedRows: 17,
        failedRows: 1,
        progress: 100,
        message: "导入完成",
        failureFileAvailable: true,
        startedAt: "2026-07-11T00:00:10Z",
        completedAt: "2026-07-11T00:00:20Z",
        createdAt: "2026-07-11T00:00:00Z"
      }],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2
    }) as never);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");

    const result = await store.loadImportTasks({ page: 1, size: 20, signal: controller.signal });

    expect(get).toHaveBeenCalledWith("/admin/imports", {
      params: { meetingId: 7, page: 1, size: 20 },
      signal: controller.signal
    });
    expect(result).toMatchObject({ page: 1, totalElements: 21, totalPages: 2 });
    expect(result.content[0]).toMatchObject({
      id: "31",
      meetingId: 7,
      type: "rooms",
      status: "COMPLETED",
      importedRows: 17,
      failedRows: 1,
      failureFileAvailable: true,
      startedAt: "2026-07-11T00:00:10Z"
    });
  });

  it("导出任务固定使用 XLSX 并携带统计范围", async () => {
    const post = vi.spyOn(http, "post").mockResolvedValue(envelope({
      id: 42,
      meetingId: 7,
      type: "statistics",
      status: "PENDING",
      format: "xlsx",
      startDate: "2026-07-01",
      endDate: "2026-07-11",
      createdAt: "2026-07-11T00:00:00Z",
      fileAvailable: false
    }) as never);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");

    await store.createExportTask("statistics", { startDate: "2026-07-01", endDate: "2026-07-11" });

    expect(post).toHaveBeenCalledWith("/admin/exports", null, expect.objectContaining({
      params: {
        meetingId: 7,
        type: "statistics",
        format: "xlsx",
        startDate: "2026-07-01",
        endDate: "2026-07-11"
      }
    }));
  });

  it("分页导出历史保留分页元数据并映射固定文件字段", async () => {
    vi.spyOn(http, "get").mockResolvedValue(envelope({
      content: [{
        id: 9,
        meetingId: 7,
        type: "attendees",
        status: "COMPLETED",
        format: "xlsx",
        fileAvailable: true,
        fileSize: 2048,
        generatedAt: "2026-07-11T00:01:00Z",
        createdAt: "2026-07-11T00:00:00Z"
      }],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2
    }) as never);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");

    const result = await store.loadExportTasks({ page: 1, size: 20 });

    expect(result).toMatchObject({ page: 1, totalElements: 21, totalPages: 2 });
    expect(result.content[0]).toMatchObject({
      taskId: "9",
      status: "COMPLETED",
      fileAvailable: true,
      fileSize: 2048,
      generatedAt: "2026-07-11T00:01:00Z"
    });
  });

  it("导出文件下载透传取消信号", async () => {
    const controller = new AbortController();
    const canceled = new DOMException("已取消", "AbortError");
    const get = vi.spyOn(http, "get").mockRejectedValue(canceled);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");

    await expect(store.downloadExportTask("42", {
      filename: "attendees.xlsx",
      signal: controller.signal
    })).rejects.toBe(canceled);

    expect(get).toHaveBeenCalledWith("/admin/exports/42/download", {
      params: { meetingId: 7 },
      responseType: "blob",
      timeout: 120_000,
      signal: controller.signal
    });
  });
});
