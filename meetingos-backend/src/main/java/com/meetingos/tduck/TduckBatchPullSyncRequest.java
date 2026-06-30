package com.meetingos.tduck;

import jakarta.validation.constraints.NotNull;

public record TduckBatchPullSyncRequest(
        @NotNull Long meetingId,
        Integer limit
) {
}
