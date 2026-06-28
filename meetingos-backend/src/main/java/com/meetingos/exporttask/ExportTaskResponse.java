package com.meetingos.exporttask;

import java.time.LocalDateTime;

public record ExportTaskResponse(
        Long id,
        Long meetingId,
        String meetingCode,
        String meetingName,
        String type,
        ExportTaskStatus status,
        String filename,
        String requestedBy,
        LocalDateTime completedAt,
        String message,
        LocalDateTime createdAt
) {
}
