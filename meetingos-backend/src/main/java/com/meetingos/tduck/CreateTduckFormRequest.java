package com.meetingos.tduck;

import jakarta.validation.constraints.NotNull;

public record CreateTduckFormRequest(
        @NotNull Long meetingId,
        String formName,
        String description
) {
}
