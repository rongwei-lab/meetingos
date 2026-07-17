package com.meetingos.tduck;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TduckBatchPullSyncRequest(
        @NotNull Long meetingId,
        Integer limit,
        @Size(max = 128) String idempotencyKey
) {
    public TduckBatchPullSyncRequest(Long meetingId, Integer limit) {
        this(meetingId, limit, null);
    }
}
