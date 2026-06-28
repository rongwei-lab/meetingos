package com.meetingos.publicapi;

import java.time.LocalDateTime;
import java.util.Map;

public record PublicQueryHistoryResponse(
        Long id,
        PublicQueryType queryType,
        PublicQueryStatus queryStatus,
        Map<String, Object> queryValues,
        String errorMessage,
        Long attendeeId,
        String attendeeName,
        String attendeePhone,
        String resultSummary,
        String clientIp,
        String userAgent,
        LocalDateTime createdAt
) {
}
