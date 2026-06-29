package com.meetingos.dinner;

public record DinnerSettingsResponse(
        Long meetingId,
        String venue,
        String dinnerTime
) {
}
