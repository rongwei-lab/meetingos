package com.meetingos.importtask;

import java.time.LocalDateTime;

public record ImportTaskResponse(
        Long id,
        Long meetingId,
        String type,
        ImportTaskStatus status,
        String originalFilename,
        String idempotencyKey,
        boolean preflight,
        int totalRows,
        int processedRows,
        int importedRows,
        int failedRows,
        int progress,
        String message,
        boolean failureFileAvailable,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
