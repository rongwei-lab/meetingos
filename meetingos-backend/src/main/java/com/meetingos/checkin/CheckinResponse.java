package com.meetingos.checkin;

import java.time.LocalDateTime;

public record CheckinResponse(
        CheckinResultStatus status,
        Long attendeeId,
        String attendeeName,
        String phone,
        LocalDateTime checkedInAt,
        String entrance,
        String operator,
        String message
) {
}
