package com.meetingos.meeting;

public record MeetingDetail(
        Meeting meeting,
        long registrationCount,
        long approvedCount,
        long seatedCount,
        long roomAssignedCount,
        long checkedInCount
) {
}
