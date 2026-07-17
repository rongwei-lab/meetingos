export const CURRENT_MEETING_KEY = "meetingos.currentMeetingId";

export function currentMeetingIdFromStorage() {
  return localStorage.getItem(CURRENT_MEETING_KEY) || "";
}
