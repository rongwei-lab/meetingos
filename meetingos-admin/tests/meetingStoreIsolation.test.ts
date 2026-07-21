import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../src/api/http";
import { clearMeetingRuntimeCaches, useMeetingStore, type StatisticsOverviewDto } from "../src/stores/meeting";
import type { Attendee } from "../src/utils/types";

function statistics(registrationCount: number): StatisticsOverviewDto {
  return {
    registrationCount,
    approvedCount: registrationCount,
    seatedCount: 0,
    roomAssignedCount: 0,
    checkedInCount: 0,
    checkinRate: 0
  };
}

function installLocalStorage() {
  const values = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear()
  });
}

describe("meeting store 会议隔离", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearMeetingRuntimeCaches();
    installLocalStorage();
    setActivePinia(createPinia());
  });

  it("签到、统计和查询历史只暴露当前会议分桶", () => {
    const store = useMeetingStore();
    store.checkinRecords = [
      { id: "c1", meetingId: "1", attendeeId: "a1", attendeeName: "甲", method: "MANUAL", entrance: "A", operator: "u", checkinTime: "t1" },
      { id: "c2", meetingId: "2", attendeeId: "a2", attendeeName: "乙", method: "MANUAL", entrance: "B", operator: "u", checkinTime: "t2" }
    ];
    store.statisticsByRequestKey = { "1:2026-07-01:2026-07-07": statistics(11), "2::": statistics(22) };
    store.activeStatisticsRequestKeyByMeetingId = { "1": "1:2026-07-01:2026-07-07", "2": "2::" };
    store.publicQueryHistoryByRequestKey = {
      "1:0:20:SEAT:SUCCESS:": { content: [], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      "2:0:20::::": { content: [], page: 0, size: 20, totalElements: 2, totalPages: 1 }
    };
    store.activePublicQueryHistoryKeyByMeetingId = {
      "1": "1:0:20:SEAT:SUCCESS:",
      "2": "2:0:20::::"
    };

    store.setCurrentMeetingId("1");
    expect(store.currentCheckinRecords.map((record) => record.id)).toEqual(["c1"]);
    expect(store.overview?.registrationCount).toBe(11);
    expect(store.currentPublicQueryHistory?.totalElements).toBe(1);

    store.setCurrentMeetingId("2");
    expect(store.currentCheckinRecords.map((record) => record.id)).toEqual(["c2"]);
    expect(store.overview?.registrationCount).toBe(22);
    expect(store.currentPublicQueryHistory?.totalElements).toBe(2);
  });

  it("账号变化 reset 会清空业务数据和当前会议持久化", () => {
    const store = useMeetingStore();
    store.setCurrentMeetingId("1");
    store.checkinRecords = [
      { id: "c1", meetingId: "1", attendeeId: "a1", attendeeName: "甲", method: "MANUAL", entrance: "A", operator: "u", checkinTime: "t1" }
    ];
    store.statisticsByRequestKey = { "1::": statistics(11) };
    store.activeStatisticsRequestKeyByMeetingId = { "1": "1::" };

    store.resetForAuthChange();

    expect(store.currentMeetingId).toBe("");
    expect(store.checkinRecords).toEqual([]);
    expect(store.statisticsByRequestKey).toEqual({});
    expect(localStorage.getItem("meetingos.currentMeetingId")).toBeNull();
  });

  it("会议列表读取全部分页并为每页复用同一取消信号", async () => {
    const meetingPages = [
      {
        content: [{ id: 201, code: "NEW201", name: "最新会议", status: "REGISTRATION_OPEN" }],
        page: 0,
        size: 200,
        totalElements: 2,
        totalPages: 2
      },
      {
        content: [{ id: 1, code: "OLD001", name: "历史归档会议", status: "ARCHIVED" }],
        page: 1,
        size: 200,
        totalElements: 2,
        totalPages: 2
      }
    ];
    const get = vi.spyOn(http, "get").mockImplementation(async (_url, config) => {
      const page = Number((config?.params as { page?: number } | undefined)?.page || 0);
      return {
        data: {
          success: true,
          code: "OK",
          message: "success",
          timestamp: "2026-07-13T00:00:00Z",
          data: meetingPages[page]
        }
      } as never;
    });
    const store = useMeetingStore();

    await store.loadMeetings();

    expect(get.mock.calls.map(([, config]) => config?.params)).toEqual([
      { page: 0, size: 200 },
      { page: 1, size: 200 }
    ]);
    expect(get.mock.calls[1]?.[1]?.signal).toBe(get.mock.calls[0]?.[1]?.signal);
    expect(store.meetings.map((meeting) => ({ id: meeting.id, status: meeting.status }))).toEqual([
      { id: "201", status: "REGISTRATION_OPEN" },
      { id: "1", status: "ARCHIVED" }
    ]);
  });

  it("参会人搜索不再向后端发送参会类型筛选", async () => {
    const get = vi.spyOn(http, "get").mockResolvedValue({
      data: {
        success: true,
        code: "OK",
        message: "success",
        timestamp: "2026-07-10T00:00:00Z",
        data: { content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 }
      }
    });
    const store = useMeetingStore();

    await store.searchAttendees(
      { keyword: "张", auditStatus: "APPROVED", attendeeType: "VIP", hasSeat: true, tags: ["嘉宾"] } as Parameters<typeof store.searchAttendees>[0],
      { meetingId: "1" }
    );

    const requestConfig = get.mock.calls[0]?.[1] as { params?: Record<string, unknown> };
    expect(requestConfig.params).toMatchObject({
      meetingId: 1,
      keyword: "张",
      reviewStatus: "APPROVED",
      hasSeat: true,
      tags: "嘉宾"
    });
    expect(requestConfig.params).not.toHaveProperty("attendeeType");
  });

  it("参会人分页请求限制并发并只缓存最近两个会议", async () => {
    let activeRequests = 0;
    let peakRequests = 0;
    vi.spyOn(http, "get").mockImplementation(async (_url, config) => {
      const params = config?.params as { meetingId: number; page: number; size: number };
      activeRequests += 1;
      peakRequests = Math.max(peakRequests, activeRequests);
      await new Promise((resolve) => setTimeout(resolve, params.page % 2 ? 4 : 1));
      activeRequests -= 1;
      return {
        data: {
          success: true,
          code: "OK",
          message: "success",
          timestamp: "2026-07-16T00:00:00Z",
          data: {
            content: [{
              id: params.meetingId * 100 + params.page,
              name: `参会人${params.meetingId}-${params.page}`,
              phone: "13800000000",
              reviewStatus: "APPROVED"
            }],
            page: params.page,
            size: params.size,
            totalElements: 6,
            totalPages: 6
          }
        }
      } as never;
    });
    const store = useMeetingStore();

    await store.loadAttendees("1");
    await store.loadAttendees("2");
    await store.loadAttendees("3");

    expect(peakRequests).toBeLessThanOrEqual(4);
    expect([...new Set(store.attendees.map((attendee) => attendee.meetingId))]).toEqual(["2", "3"]);
    expect(store.loadedAttendeeMeetingIds).toEqual(["2", "3"]);
    expect(store.attendees.filter((attendee) => attendee.meetingId === "3").map((attendee) => attendee.id)).toEqual([
      "300", "301", "302", "303", "304", "305"
    ]);
  });

  it("旧会议请求晚返回时不会淘汰当前会议缓存", async () => {
    const completeRequest = new Map<number, () => void>();
    vi.spyOn(http, "get").mockImplementation((_url, config) => {
      const meetingId = Number((config?.params as { meetingId: number }).meetingId);
      return new Promise((resolve) => {
        completeRequest.set(meetingId, () => resolve({
          data: {
            success: true,
            code: "OK",
            message: "success",
            timestamp: "2026-07-16T00:00:00Z",
            data: {
              content: [{
                id: meetingId * 100,
                name: `参会人${meetingId}`,
                phone: "13800000000",
                reviewStatus: "APPROVED"
              }],
              page: 0,
              size: 200,
              totalElements: 1,
              totalPages: 1
            }
          }
        } as never));
      });
    });
    const store = useMeetingStore();

    store.setCurrentMeetingId("1");
    const first = store.loadAttendees("1");
    store.setCurrentMeetingId("2");
    const second = store.loadAttendees("2");
    store.setCurrentMeetingId("3");
    const current = store.loadAttendees("3");

    completeRequest.get(3)?.();
    await current;
    completeRequest.get(1)?.();
    await first;
    completeRequest.get(2)?.();
    await second;

    expect(store.currentMeetingId).toBe("3");
    expect([...new Set(store.attendees.map((attendee) => attendee.meetingId))]).toEqual(["3", "2"]);
    expect(store.currentAttendees.map((attendee) => attendee.id)).toEqual(["300"]);
    expect(store.loadedAttendeeMeetingIds).toEqual(["3", "2"]);
  });

  it("TDuck 一键同步会轮询异步任务并在终态后刷新关联数据", async () => {
    vi.useFakeTimers();
    try {
      const post = vi.spyOn(http, "post").mockResolvedValue({
        data: {
          success: true,
          code: "OK",
          message: "success",
          timestamp: "2026-07-16T00:00:00Z",
          data: {
            taskId: 88,
            status: "PENDING",
            progress: 0,
            total: 10,
            scanned: 0,
            success: 0,
            duplicateIgnored: 0,
            failed: 0,
            failures: [],
            message: "等待同步"
          }
        }
      } as never);
      const get = vi.spyOn(http, "get").mockResolvedValue({
        data: {
          success: true,
          code: "OK",
          message: "success",
          timestamp: "2026-07-16T00:00:01Z",
          data: {
            taskId: 88,
            status: "COMPLETED",
            progress: 100,
            total: 10,
            scanned: 10,
            success: 8,
            duplicateIgnored: 2,
            failed: 0,
            failures: [],
            message: "同步完成"
          }
        }
      } as never);
      const store = useMeetingStore();
      const loadAttendees = vi.spyOn(store, "loadAttendees").mockResolvedValue(undefined);
      const loadSyncLogs = vi.spyOn(store, "loadSyncLogs").mockResolvedValue(undefined);
      const loadStatistics = vi.spyOn(store, "loadStatistics").mockResolvedValue({} as StatisticsOverviewDto);

      const resultPromise = store.pullAllTduckSync("1");
      await vi.advanceTimersByTimeAsync(1_000);
      const result = await resultPromise;

      expect(post).toHaveBeenCalledWith("/admin/tduck/sync/pull/all", { meetingId: 1 }, { signal: undefined });
      expect(get).toHaveBeenCalledWith("/admin/tduck/sync/pull/tasks/88", { params: { meetingId: 1 }, signal: undefined });
      expect(result).toMatchObject({ status: "COMPLETED", scanned: 10, success: 8 });
      expect(loadAttendees).toHaveBeenCalledWith("1");
      expect(loadSyncLogs).toHaveBeenCalledWith("1");
      expect(loadStatistics).toHaveBeenCalledWith("1");
    } finally {
      vi.useRealTimers();
    }
  });

  it("TDuck 同步日志按服务端分页读取，并可加载指定日志的表单快照", async () => {
    const envelope = (data: unknown) => ({
      data: { success: true, code: "OK", message: "success", timestamp: "2026-07-21T19:04:25Z", data }
    });
    const get = vi.spyOn(http, "get").mockImplementation(async (url) => {
      if (url === "/admin/tduck/sync-logs") {
        return envelope({
          content: [{ id: 91, formKey: "form-3", tduckDataId: "211809", eventType: "PULL_SYNC", status: "SUCCESS", createdAt: "2026-07-21T19:04:25" }],
          page: 1,
          size: 100,
          totalElements: 151,
          totalPages: 2
        }) as never;
      }
      if (url === "/admin/tduck/sync-logs/91") {
        return envelope({
          id: 91,
          meetingId: 1,
          formKey: "form-3",
          tduckDataId: "211809",
          eventType: "PULL_SYNC",
          status: "SUCCESS",
          createdAt: "2026-07-21T19:04:25",
          formValues: { phone: "13800138000", company: "研发中心" }
        }) as never;
      }
      throw new Error(`unexpected URL: ${url}`);
    });
    const store = useMeetingStore();

    const page = await store.loadSyncLogs("1", { page: 1, size: 100 });
    const detail = await store.loadTduckSyncLogDetail("91", "1");

    expect(get.mock.calls[0]?.[1]?.params).toEqual({ meetingId: 1, page: 1, size: 100 });
    expect(get).toHaveBeenCalledWith("/admin/tduck/sync-logs/91", { params: { meetingId: 1 } });
    expect(page).toMatchObject({ page: 1, size: 100, totalElements: 151, totalPages: 2 });
    expect(store.syncLogs).toMatchObject([{ id: "91", dataId: "211809", meetingId: "1" }]);
    expect(detail.formValues).toMatchObject({ phone: "13800138000", company: "研发中心" });
  });

  it("TDuck 重复报名队列按会议读取详情，并在裁决后从待处理缓存移除", async () => {
    const conflict = {
      id: 44,
      meetingId: 1,
      attendeeId: 12,
      currentTduckDataId: "10001",
      incomingTduckDataId: "10002",
      status: "PENDING_CONFLICT",
      submittedAt: "2026-07-21T09:00:00",
      createdAt: "2026-07-21T09:00:01",
      existing: { name: "张三", phone: "13800138000", organization: "原单位", accommodationRequired: false, formValues: { name: "张三", phone: "13800138000", company: "原单位" } },
      incoming: { name: "张三", phone: "13800138000", organization: "新单位", accommodationRequired: true, formValues: { name: "张三", phone: "13800138000", company: "新单位" } }
    };
    const envelope = (data: unknown) => ({
      data: { success: true, code: "OK", message: "success", timestamp: "2026-07-21T09:00:00Z", data }
    });
    const get = vi.spyOn(http, "get").mockImplementation(async (url) => {
      if (url === "/admin/tduck/sync-conflicts") {
        return envelope({ content: [conflict], page: 0, size: 50, totalElements: 1, totalPages: 1 }) as never;
      }
      if (url === "/admin/tduck/sync-conflicts/44") return envelope(conflict) as never;
      throw new Error(`unexpected URL: ${url}`);
    });
    const post = vi.spyOn(http, "post").mockResolvedValue(envelope({ ...conflict, status: "IGNORED" }) as never);
    const store = useMeetingStore();

    const page = await store.loadTduckSubmissionConflicts("1");
    const detail = await store.loadTduckSubmissionConflict("44", "1");
    const resolved = await store.resolveTduckSubmissionConflict("44", "1", "KEEP_EXISTING", " 已核实 ");

    expect(get.mock.calls[0]?.[0]).toBe("/admin/tduck/sync-conflicts");
    expect(get.mock.calls[0]?.[1]?.params).toEqual({ meetingId: 1, page: 0, size: 50 });
    expect(get).toHaveBeenCalledWith("/admin/tduck/sync-conflicts/44", { params: { meetingId: 1 } });
    expect(post).toHaveBeenCalledWith("/admin/tduck/sync-conflicts/44/resolve", {
      meetingId: 1,
      resolution: "KEEP_EXISTING",
      note: "已核实"
    });
    expect(page.content[0]).toMatchObject({ id: "44", attendeeId: "12", incomingTduckDataId: "10002" });
    expect(detail.existing.phone).toBe("13800138000");
    expect(detail.incoming.formValues).toMatchObject({ company: "新单位" });
    expect(resolved.status).toBe("IGNORED");
    expect(store.currentTduckSubmissionConflicts).toMatchObject({ content: [], totalElements: 0, totalPages: 0 });
  });

  it("旧后端缺少综合查询接口时只回退当前启用的独立查询", async () => {
    const notFound = Object.assign(new Error("Request failed with status code 404"), {
      isAxiosError: true,
      response: { status: 404, data: "" }
    });
    const envelope = (data: unknown) => ({
      data: {
        success: true,
        code: "OK",
        message: "success",
        timestamp: "2026-07-16T00:00:00Z",
        data
      }
    });
    const post = vi.spyOn(http, "post").mockImplementation(async (url) => {
      if (url === "/public/combined-query") throw notFound;
      if (url === "/public/seat-query") return envelope({ seatCode: "A-1" }) as never;
      if (url === "/public/dinner-query") return envelope({ tableNo: "T-1" }) as never;
      throw new Error(`unexpected URL: ${url}`);
    });
    const store = useMeetingStore();
    const payload = { publicQueryToken: "token", values: { NAME: "张三" } as never };

    const result = await store.publicCombinedQuery(payload, { queryTypes: ["SEAT", "DINNER"] });

    expect(post.mock.calls.map(([url]) => url)).toEqual([
      "/public/combined-query",
      "/public/seat-query",
      "/public/dinner-query"
    ]);
    expect(result).toMatchObject({ seat: { seatCode: "A-1" }, dinner: { tableNo: "T-1" } });
    expect(result.room).toBeUndefined();
  });

  it("备注保存会修剪空白、支持清空并保留聚合状态", async () => {
    const response = (remark: string | null) => ({
      data: {
        success: true,
        code: "OK",
        message: "success",
        timestamp: "2026-07-10T00:00:00Z",
        data: {
          id: 10,
          name: "张三",
          phone: "138****0000",
          organization: "示例单位",
          title: "经理",
          attendeeType: "VIP",
          remark,
          tags: "嘉宾",
          reviewStatus: "APPROVED",
          registeredAt: "2026-07-10T08:00:00"
        }
      }
    });
    const put = vi.spyOn(http, "put")
      .mockResolvedValueOnce(response("现场联系人") as never)
      .mockResolvedValueOnce(response(null) as never);
    const store = useMeetingStore();
    const attendee: Attendee = {
      id: "10",
      meetingId: "1",
      name: "张三",
      phone: "138****0000",
      company: "示例单位",
      title: "经理",
      attendeeType: "VIP",
      remark: "旧备注",
      tags: ["嘉宾"],
      auditStatus: "APPROVED",
      registeredAt: "2026-07-10T08:00:00",
      seatLabel: "A区 1排1座",
      roomNo: "101",
      roomId: "8",
      seatAssigned: true,
      roomAssigned: true,
      dinnerAssigned: true,
      seatQueried: true,
      roomQueried: true,
      dinnerQueried: true,
      checkinStatus: "NOT_CHECKED_IN"
    };
    store.attendees = [attendee];
    store.setCurrentMeetingId("1");

    await store.updateAttendeeRemark("10", "  现场联系人  ");

    expect(put).toHaveBeenNthCalledWith(1, "/admin/attendees/10/remark", { remark: "现场联系人" });
    expect(store.attendees[0]).toMatchObject({
      remark: "现场联系人",
      seatLabel: "A区 1排1座",
      roomId: "8",
      seatAssigned: true,
      roomAssigned: true,
      dinnerAssigned: true,
      seatQueried: true,
      roomQueried: true,
      dinnerQueried: true
    });

    await store.updateAttendeeRemark("10", "   ");

    expect(put).toHaveBeenNthCalledWith(2, "/admin/attendees/10/remark", { remark: null });
    expect(store.attendees[0]?.remark).toBeUndefined();
    expect(store.attendees[0]?.seatLabel).toBe("A区 1排1座");
  });
});
