package com.meetingos.publicapi;

import java.time.LocalDateTime;

public record PublicQuerySummaryResponse(
        String groupKey,
        String groupType,
        Long attendeeId,
        String attendeeName,
        String attendeePhone,
        String failureReason,
        long totalCount,
        long successCount,
        long failedCount,
        LocalDateTime latestQueryAt,
        String latestResult
) {
}
