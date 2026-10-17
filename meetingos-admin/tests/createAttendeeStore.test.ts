import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../src/api/http";
import { clearMeetingRuntimeCaches, useMeetingStore } from "../src/stores/meeting";
import { mockMeetings } from "../src/utils/mockData";
import type { Attendee } from "../src/utils/types";

function envelope(data: unknown) {
  return { data: { success: true, code: "OK", message: "success", data } };
}

function existingAttendee(meetingId = "7"): Attendee {
  return {
    id: `existing-${meetingId}`, meetingId, name: "原参会人", phone: "13800138000", company: "原单位", title: "",
    attendeeType: "NORMAL", tags: [], auditStatus: "PENDING", registeredAt: "2026-10-14T08:00:00", checkinStatus: "NOT_CHECKED_IN"
  };
}

function createdResponse() {
  return { id: 99, name: "新增人员", phone: "13900139000", organization: "新单位", attendeeType: "GUEST", tags: "现场,嘉宾", reviewStatus: "PENDING", checkinStatus: "NOT_CHECKED_IN", registeredAt: "2026-10-14T09:00:00" };
}

describe("手动新增参会人", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearMeetingRuntimeCaches();
    const values = new Map<string, string>();
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key)
    });
    setActivePinia(createPinia());
  });

  it("保存到指定会议，并立即更新列表和报名计数", async () => {
    const post = vi.spyOn(http, "post").mockResolvedValue(envelope(createdResponse()) as never);
    const get = vi.spyOn(http, "get");
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");
    store.meetings = [{ ...mockMeetings[0], id: "7", registeredCount: 1 }];
    store.loadedAttendeeMeetingIds = ["7"];
    store.attendees = [existingAttendee(), existingAttendee("8")];

    const created = await store.createAttendee({
      name: " 新增人员 ", phone: "139-0013 9000", organization: " 新单位 ", attendeeType: "GUEST", tags: ["现场", " 现场 ", "嘉宾"]
    }, "7");

    expect(post).toHaveBeenCalledWith("/admin/attendees", expect.objectContaining({ meetingId: 7, name: "新增人员", phone: "13900139000", organization: "新单位", attendeeType: "GUEST", tags: "现场,嘉宾" }));
    expect(created).toMatchObject({ id: "99", meetingId: "7", company: "新单位", auditStatus: "PENDING", tags: ["现场", "嘉宾"] });
    expect(store.currentAttendees.map((attendee) => attendee.id)).toEqual(["99", "existing-7"]);
    expect(store.attendees.find((attendee) => attendee.meetingId === "8")).toEqual(existingAttendee("8"));
    expect(store.meetings[0].registeredCount).toBe(2);
    // 创建成功后不依赖一次额外 GET，避免刷新失败诱发重复提交。
    expect(get).not.toHaveBeenCalled();
  });

  it("手机号冲突时显示服务端原因，并保留现有列表", async () => {
    vi.spyOn(http, "post").mockRejectedValue({ isAxiosError: true, response: { status: 409, data: { message: "同一会议内手机号已存在报名记录" } } });
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");
    store.attendees = [existingAttendee()];

    await expect(store.createAttendee({ name: "重复人员", phone: "13800138000" })).rejects.toThrow("同一会议内手机号已存在报名记录");
    expect(store.attendees).toEqual([existingAttendee()]);
  });

  it("网络失败时不生成本地成功记录", async () => {
    vi.spyOn(http, "post").mockRejectedValue(new Error("网络不可用"));
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");

    await expect(store.createAttendee({ name: "新增人员", phone: "13900139000" })).rejects.toThrow("网络不可用");
    expect(store.attendees).toEqual([]);
  });

  it.each([
    { payload: { name: " ", phone: "13900139000" }, message: "请填写姓名" },
    { payload: { name: "新增人员", phone: " " }, message: "请填写手机号" },
    { payload: { name: "新增人员", phone: "12345" }, message: "手机号格式不正确" },
    { payload: { name: "新增人员", phone: "13900139000", tags: ["长".repeat(301)] }, message: "标签总长度不能超过 300 个字符" }
  ])("不提交无效资料：$message", async ({ payload, message }) => {
    const post = vi.spyOn(http, "post");
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");

    await expect(store.createAttendee(payload)).rejects.toThrow(message);
    expect(post).not.toHaveBeenCalled();
  });

  it("请求期间切换会议时，旧响应不影响新会议列表", async () => {
    let resolve!: (value: ReturnType<typeof envelope>) => void;
    vi.spyOn(http, "post").mockReturnValue(new Promise((done) => { resolve = done; }) as never);
    const store = useMeetingStore();
    store.setCurrentMeetingId("7");
    store.attendees = [existingAttendee(), existingAttendee("8")];
    const pending = store.createAttendee({ name: "新增人员", phone: "13900139000" }, "7");

    store.setCurrentMeetingId("8");
    resolve(envelope(createdResponse()));
    expect(await pending).toMatchObject({ id: "99", meetingId: "7" });
    expect(store.currentAttendees).toEqual([existingAttendee("8")]);
    expect(store.attendees.some((attendee) => attendee.id === "99")).toBe(false);
  });
});
