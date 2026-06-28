package com.meetingos.tduck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TduckPullSyncRequest(
        @NotNull Long meetingId,
        @NotBlank String dataId
) {
}
