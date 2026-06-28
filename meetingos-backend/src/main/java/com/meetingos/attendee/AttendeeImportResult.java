package com.meetingos.attendee;

public record AttendeeImportResult(
        int totalRows,
        int importedRows,
        int skippedRows,
        String message
) {
}
