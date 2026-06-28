package com.meetingos.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(
        @NotNull Long meetingId,
        @NotBlank String title,
        @NotBlank String content,
        String channel,
        String scope,
        String status,
        Integer expectedCount
) {
}
