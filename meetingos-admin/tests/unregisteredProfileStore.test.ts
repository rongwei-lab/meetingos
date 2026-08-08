import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../src/api/http";
import { useMeetingStore } from "../src/stores/meeting";
import type { Attendee } from "../src/utils/types";

function envelope<T>(data: T) {
  return {
    data: {
      success: true,
      code: "OK",
      message: "success",
      timestamp: "2026-07-30T00:00:00Z",
      data
    }
  };
}

function unregisteredAttendee(): Attendee {
  return {
    id: "11",
    meetingId: "7",
    name: "现场人员",
    phone: "13800138000",
    unregistered: true,
    company: "旧单位",
    title: "旧职务",
    attendeeType: "NORMAL",
    tags: ["现场"],
    auditStatus: "APPROVED",
    registeredAt: "2026-07-30T08:00:00Z",
    seatLabel: "主会场 1排1座",
    seatAssigned: true,
    checkinStatus: "NOT_CHECKED_IN",
    formValues: {
      input_name: "现场人员",
      radio_rest: "1",
      radio_restlabel: "满意"
    }
  };
}

describe("未报名入座人员资料编辑", () => {
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

  it("按 TDuck 字段提交资料，并保留已加载的座位状态", async () => {
    const put = vi.spyOn(http, "put").mockResolvedValue(envelope({
      id: 11,
      name: "现场新人员",
      phone: "13900139000",
      organization: "新单位",
      title: "旧职务",
      reviewStatus: "APPROVED",
      registeredAt: "2026-07-30T08:00:00Z",
      checkinStatus: "NOT_CHECKED_IN",
      unregistered: true,
      formValues: {
        input_name: "现场新人员",
        input_phone: "13900139000",
        input_org: "新单位",
        radio_rest: "很好",
        radio_restlabel: "很好"
      }
    }) as never);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");
    store.attendees = [unregisteredAttendee()];
    store.seats = [{
      id: "s1",
      meetingId: "7",
      areaName: "主会场",
      rowNo: 1,
      seatNo: 1,
      seatType: "NORMAL",
      status: "ASSIGNED",
      locked: false,
      attendeeId: "11",
      attendeeName: "现场人员",
      attendeeTags: "现场"
    }];

    await store.updateUnregisteredAttendeeProfile("11", {
      formValues: {
        input_name: "现场新人员",
        input_phone: "13900139000",
        input_org: "新单位",
        radio_rest: "很好",
        radio_restlabel: "很好"
      },
      mappings: [
        { attendeeField: "NAME", tduckFieldKey: "input_name", tduckFieldLabel: "您的姓名", requiredMapping: true },
        { attendeeField: "PHONE", tduckFieldKey: "input_phone", tduckFieldLabel: "请输入手机号码", requiredMapping: true },
        { attendeeField: "ORGANIZATION", tduckFieldKey: "input_org", tduckFieldLabel: "您所在的部门", requiredMapping: false },
        { attendeeField: null, tduckFieldKey: "radio_rest", tduckFieldLabel: "您认为午休时间是否充足？", requiredMapping: false }
      ]
    });

    expect(put).toHaveBeenCalledWith("/admin/attendees/11/unregistered-profile", {
      formValues: {
        input_name: "现场新人员",
        input_phone: "13900139000",
        input_org: "新单位",
        radio_rest: "很好",
        radio_restlabel: "很好"
      }
    });
    expect(store.currentAttendees[0]).toMatchObject({
      name: "现场新人员",
      phone: "13900139000",
      company: "新单位",
      seatLabel: "主会场 1排1座",
      seatAssigned: true,
      formValues: {
        radio_restlabel: "很好"
      }
    });
    expect(store.currentSeats[0]).toMatchObject({ attendeeName: "现场新人员", attendeeTags: "现场" });
  });
});
