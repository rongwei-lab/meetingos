package com.meetingos.importtask;

record ImportTaskWorkItem(
        Long id,
        Long meetingId,
        ImportTaskType type,
        String inputFilePath,
        String requestedBy,
        boolean preflight
) {
}
