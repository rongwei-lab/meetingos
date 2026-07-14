import type { MeetingStatus } from "@/utils/types";

export const DEFAULT_NEW_MEETING_STATUS: MeetingStatus = "REGISTRATION_OPEN";

export interface MeetingStatusAction {
  label: string;
  status: MeetingStatus;
}

const standardActions: Partial<Record<MeetingStatus, MeetingStatusAction[]>> = {
  DRAFT: [{ label: "发布报名", status: "REGISTRATION_OPEN" }],
  REGISTRATION_OPEN: [{ label: "截止报名", status: "REGISTRATION_CLOSED" }],
  REGISTRATION_CLOSED: [{ label: "准备开始", status: "READY" }],
  READY: [{ label: "开始会议", status: "IN_PROGRESS" }],
  IN_PROGRESS: [{ label: "结束会议", status: "ENDED" }],
  ENDED: [{ label: "归档会议", status: "ARCHIVED" }]
};

export function meetingStatusActions(status: MeetingStatus, canRestart: boolean): MeetingStatusAction[] {
  const actions = [...(standardActions[status] || [])];
  if (canRestart && (status === "ENDED" || status === "ARCHIVED")) {
    actions.unshift({ label: "重启会议", status: "REGISTRATION_OPEN" });
  }
  return actions;
}

export function isMeetingRestartTransition(current: MeetingStatus, target: MeetingStatus) {
  return (current === "ENDED" || current === "ARCHIVED") && target === "REGISTRATION_OPEN";
}
