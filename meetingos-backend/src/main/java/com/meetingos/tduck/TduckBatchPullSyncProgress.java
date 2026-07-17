package com.meetingos.tduck;

import java.util.List;

public record TduckBatchPullSyncProgress(
        int total,
        int processed,
        int success,
        int duplicateIgnored,
        int failed,
        List<TduckBatchPullSyncFailure> failures
) {
}
