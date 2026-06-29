package com.meetingos.dinner;

import jakarta.validation.constraints.NotNull;

public record DinnerSettingsRequest(
        @NotNull Long meetingId,
        String venue,
        String dinnerTime
) {
}
