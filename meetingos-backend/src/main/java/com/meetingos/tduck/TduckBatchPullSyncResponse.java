package com.meetingos.tduck;

import java.time.LocalDateTime;
import java.util.List;

public record TduckBatchPullSyncResponse(
        Long taskId,
        TduckPullSyncTaskStatus status,
        int progress,
        int total,
        int scanned,
        int success,
        int duplicateIgnored,
        int failed,
        List<TduckBatchPullSyncFailure> failures,
        String message,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public TduckBatchPullSyncResponse(int scanned,
                                      int success,
                                      int duplicateIgnored,
                                      int failed,
                                      List<TduckBatchPullSyncFailure> failures,
                                      String message) {
        this(
                null,
                TduckPullSyncTaskStatus.COMPLETED,
                100,
                scanned,
                scanned,
                success,
                duplicateIgnored,
                failed,
                failures,
                message,
                null,
                null,
                null
        );
    }
}
