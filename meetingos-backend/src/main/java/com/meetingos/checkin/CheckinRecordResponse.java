package com.meetingos.checkin;

import com.meetingos.common.SensitiveDataMasker;

import java.time.LocalDateTime;
import java.util.Map;

public record CheckinRecordResponse(
        Long id,
        Long meetingId,
        Long attendeeId,
        String attendeeName,
        CheckinType checkinType,
        CheckinResultStatus resultStatus,
        String entrance,
        String operator,
        LocalDateTime checkedInAt,
        LocalDateTime createdAt,
        String message,
        Map<String, Object> requestSnapshot
) {

    public static CheckinRecordResponse masked(CheckinRecord record) {
        return new CheckinRecordResponse(
                record.getId(),
                record.getMeeting() == null ? null : record.getMeeting().getId(),
                record.getAttendee() == null ? null : record.getAttendee().getId(),
                record.getAttendee() == null ? null : record.getAttendee().getName(),
                record.getCheckinType(),
                record.getResultStatus(),
                record.getEntrance(),
                record.getOperator(),
                record.getCheckedInAt(),
                record.getCreatedAt(),
                record.getMessage(),
                SensitiveDataMasker.maskMap(record.getRequestSnapshot())
        );
    }
}
