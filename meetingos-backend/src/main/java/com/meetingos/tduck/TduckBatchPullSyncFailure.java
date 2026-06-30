package com.meetingos.tduck;

public record TduckBatchPullSyncFailure(
        String dataId,
        String message
) {
}
