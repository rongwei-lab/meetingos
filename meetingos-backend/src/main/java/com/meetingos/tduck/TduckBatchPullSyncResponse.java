package com.meetingos.tduck;

import java.util.List;

public record TduckBatchPullSyncResponse(
        int scanned,
        int success,
        int duplicateIgnored,
        int failed,
        List<TduckBatchPullSyncFailure> failures,
        String message
) {
}
