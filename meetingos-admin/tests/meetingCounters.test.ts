import { describe, expect, it } from "vitest";
import { updateMeetingCounters, type MeetingCounterTarget } from "../src/utils/meetingCounters";

describe("updateMeetingCounters", () => {
  it("完整列表为空时会把所有旧计数更新为 0", () => {
    const meeting: MeetingCounterTarget = {
      registeredCount: 8,
      approvedCount: 7,
      seatedCount: 6,
      roomedCount: 5,
      checkedInCount: 4
    };

    updateMeetingCounters(meeting, [], true);

    expect(meeting).toEqual({
      registeredCount: 0,
      approvedCount: 0,
      seatedCount: 0,
      roomedCount: 0,
      checkedInCount: 0
    });
  });

  it("列表尚未完整加载时保留后端会议摘要", () => {
    const meeting: MeetingCounterTarget = { registeredCount: 8, approvedCount: 7 };

    updateMeetingCounters(meeting, [], false);

    expect(meeting).toEqual({ registeredCount: 8, approvedCount: 7 });
  });

  it("按参会人状态计算各项计数", () => {
    const meeting: MeetingCounterTarget = {};

    updateMeetingCounters(
      meeting,
      [
        { auditStatus: "APPROVED", seatLabel: "A-01", roomNo: "301", checkinStatus: "CHECKED_IN" },
        { auditStatus: "APPROVED" },
        { auditStatus: "PENDING", roomNo: "302" }
      ],
      true
    );

    expect(meeting).toEqual({
      registeredCount: 3,
      approvedCount: 2,
      seatedCount: 1,
      roomedCount: 2,
      checkedInCount: 1
    });
  });
});
