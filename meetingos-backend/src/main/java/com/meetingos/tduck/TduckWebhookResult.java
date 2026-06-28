package com.meetingos.tduck;

public record TduckWebhookResult(
        SyncStatus status,
        Long syncLogId,
        Long attendeeId,
        String message
) {
}
