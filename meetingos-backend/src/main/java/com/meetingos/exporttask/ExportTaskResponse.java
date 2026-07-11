package com.meetingos.exporttask;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExportTaskResponse(
        Long id,
        Long meetingId,
        String meetingCode,
        String meetingName,
        String type,
        String format,
        LocalDate startDate,
        LocalDate endDate,
        ExportTaskStatus status,
        String filename,
        String requestedBy,
        LocalDateTime completedAt,
        Long fileSize,
        String checksum,
        int progress,
        boolean fileAvailable,
        LocalDateTime generatedAt,
        LocalDateTime expiresAt,
        String message,
        LocalDateTime createdAt
) {
}
