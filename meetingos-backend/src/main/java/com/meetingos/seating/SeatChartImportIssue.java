package com.meetingos.seating;

public record SeatChartImportIssue(
        int rowNo,
        int seatNo,
        String name,
        String reason
) {
}
