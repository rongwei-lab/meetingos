import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../src/api/http";
import { useMeetingStore, type StatisticsOverviewDto } from "../src/stores/meeting";
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
