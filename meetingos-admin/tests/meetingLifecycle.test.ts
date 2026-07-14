import { describe, expect, it } from "vitest";
import {
  DEFAULT_NEW_MEETING_STATUS,
  isMeetingRestartTransition,
  meetingStatusActions
} from "../src/utils/meetingLifecycle";

describe("会议生命周期前端规则", () => {
  it("新建会议默认直接进入报名中", () => {
    expect(DEFAULT_NEW_MEETING_STATUS).toBe("REGISTRATION_OPEN");
  });

  it("超级管理员可重新开启已结束和已归档会议", () => {
    expect(meetingStatusActions("ENDED", true)).toEqual([
      { label: "重启会议", status: "REGISTRATION_OPEN" },
      { label: "归档会议", status: "ARCHIVED" }
    ]);
    expect(meetingStatusActions("ARCHIVED", true)).toEqual([
      { label: "重启会议", status: "REGISTRATION_OPEN" }
    ]);
  });

  it("普通用户不获得重新开启权限且保留原有状态操作", () => {
    expect(meetingStatusActions("ENDED", false)).toEqual([
      { label: "归档会议", status: "ARCHIVED" }
    ]);
    expect(meetingStatusActions("ARCHIVED", false)).toEqual([]);
  });

  it("仅将结束或归档后回到报名中识别为重启", () => {
    expect(isMeetingRestartTransition("ENDED", "REGISTRATION_OPEN")).toBe(true);
    expect(isMeetingRestartTransition("ARCHIVED", "REGISTRATION_OPEN")).toBe(true);
    expect(isMeetingRestartTransition("DRAFT", "REGISTRATION_OPEN")).toBe(false);
  });
});
