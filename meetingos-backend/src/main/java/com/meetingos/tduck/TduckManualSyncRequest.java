package com.meetingos.tduck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record TduckManualSyncRequest(
        @NotNull Long meetingId,
        @NotBlank String dataId,
        Map<String, Object> data,
        String eventId,
        String eventType
) {
}
