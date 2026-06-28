package com.meetingos.tduck;

import com.meetingos.common.SensitiveDataMasker;

import java.time.LocalDateTime;
import java.util.Map;

public record TduckSyncLogResponse(
        Long id,
        Long meetingId,
        String formKey,
        String eventId,
        String eventType,
        SyncStatus status,
        String message,
        Long attendeeId,
        String tduckDataId,
        LocalDateTime createdAt,
        Map<String, Object> rawPayload
) {

    public static TduckSyncLogResponse masked(TduckSyncLog log) {
        return new TduckSyncLogResponse(
                log.getId(),
                log.getMeeting() == null ? null : log.getMeeting().getId(),
                log.getFormKey(),
                log.getEventId(),
                log.getEventType(),
                log.getStatus(),
                log.getMessage(),
                log.getAttendeeId(),
                log.getTduckDataId(),
                log.getCreatedAt(),
                SensitiveDataMasker.maskMap(log.getRawPayload())
        );
    }
}
