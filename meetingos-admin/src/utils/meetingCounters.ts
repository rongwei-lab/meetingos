export interface MeetingCounterTarget {
  registeredCount?: number;
  approvedCount?: number;
  seatedCount?: number;
  roomedCount?: number;
  checkedInCount?: number;
}

export interface MeetingCounterAttendee {
  auditStatus?: string;
  seatLabel?: string;
  roomNo?: string;
  checkinStatus?: string;
}

/**
 * 只有完整参会人列表已经加载时才覆盖会议摘要计数。
 * 这里必须直接写入 0，不能用 `新值 || 旧值`，否则最后一名人员被移除后页面会永久保留旧计数。
 */
export function updateMeetingCounters(
  meeting: MeetingCounterTarget,
  attendees: MeetingCounterAttendee[],
  attendeeListLoaded: boolean
) {
  if (!attendeeListLoaded) return;

  meeting.registeredCount = attendees.length;
  meeting.approvedCount = attendees.filter((attendee) => attendee.auditStatus === "APPROVED").length;
  meeting.seatedCount = attendees.filter((attendee) => Boolean(attendee.seatLabel)).length;
  meeting.roomedCount = attendees.filter((attendee) => Boolean(attendee.roomNo)).length;
  meeting.checkedInCount = attendees.filter((attendee) => attendee.checkinStatus === "CHECKED_IN").length;
}
