import { describe, expect, it } from "vitest";
import { hasGlobalMeetingAccess, planMeetingAuthorizationChanges } from "../src/utils/meetingPermissions";

describe("用户会议权限变更", () => {
  it("可一次为同一用户授权多个会议", () => {
    expect(planMeetingAuthorizationChanges("7", ["1", "2"], ["1", "2"], [])).toEqual([
      { type: "grant", meetingId: "1", role: "MANAGER" },
      { type: "grant", meetingId: "2", role: "MANAGER" }
    ]);
  });

  it("将旧会议角色归一为访问绑定并撤销未勾选会议", () => {
    const existing = [
      { id: 11, meetingId: 1, userId: 7, role: "VIEWER" },
      { id: 12, meetingId: 2, userId: 7, role: "ROOM" }
    ];

    expect(planMeetingAuthorizationChanges(7, ["1", "2"], ["1"], existing)).toEqual([
      { type: "grant", meetingId: "1", role: "MANAGER" },
      { type: "revoke", meetingId: "2", permissionId: 12 }
    ]);
  });

  it("不会误删当前可管理会议之外的授权", () => {
    const existing = [{ id: 21, meetingId: 3, userId: 7, role: "VIEWER" }];

    expect(planMeetingAuthorizationChanges(7, ["1", "2"], [], existing)).toEqual([]);
  });

  it("超级管理员自动拥有全部会议权限且不写入逐会议授权", () => {
    const existing = [{ id: 31, meetingId: 1, userId: 7, role: "MANAGER" }];

    expect(hasGlobalMeetingAccess(["MEETING_ADMIN", "SUPER_ADMIN"])).toBe(true);
    expect(planMeetingAuthorizationChanges(7, ["1", "2"], ["2"], existing, ["SUPER_ADMIN"])).toEqual([]);
  });

  it("普通角色仍需逐会议授权", () => {
    expect(hasGlobalMeetingAccess(["MEETING_ADMIN"])).toBe(false);
    expect(planMeetingAuthorizationChanges(7, ["1"], ["1"], [], ["MEETING_ADMIN"])).toEqual([
      { type: "grant", meetingId: "1", role: "MANAGER" }
    ]);
  });
});
