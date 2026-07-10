import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useMeetingStore, type StatisticsOverviewDto } from "../src/stores/meeting";

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
});
