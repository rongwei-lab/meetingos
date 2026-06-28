package com.meetingos.publicapi;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record AdminQueryRequest(
        @NotNull Map<PublicQueryField, String> values
) {
}
