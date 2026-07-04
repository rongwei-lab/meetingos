package com.meetingos.meeting;

import java.time.LocalDateTime;

public record MeetingSummary(
        Long id,
        String code,
        String publicQueryToken,
        String name,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String location,
        String dinnerVenue,
        String dinnerTime,
        long registrationCount,
        long approvedCount,
        long seatedCount,
        long roomAssignedCount,
        long dinnerAssignedCount,
        long checkedInCount,
        MeetingStatus status,
        boolean seatQueryEnabled,
        boolean roomQueryEnabled,
        boolean dinnerQueryEnabled,
        boolean checkinEnabled,
        boolean selfCheckinEnabled,
        String publicQueryFields,
        String publicQueryMatchMode
) {
}
