package com.meetingos.meeting;

import java.time.LocalDateTime;

public record MeetingSummary(
        Long id,
        String code,
        String name,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        long registrationCount,
        long approvedCount,
        long seatedCount,
        long roomAssignedCount,
        long checkedInCount,
        MeetingStatus status,
        boolean seatQueryEnabled,
        boolean roomQueryEnabled,
        boolean checkinEnabled,
        String publicQueryFields,
        String publicQueryMatchMode
) {
}
