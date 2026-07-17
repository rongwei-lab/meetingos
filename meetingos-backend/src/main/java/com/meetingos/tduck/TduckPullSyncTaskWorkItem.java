package com.meetingos.tduck;

public record TduckPullSyncTaskWorkItem(
        Long taskId,
        Long meetingId,
        String formKey,
        int limit,
        String runToken
) {
}
