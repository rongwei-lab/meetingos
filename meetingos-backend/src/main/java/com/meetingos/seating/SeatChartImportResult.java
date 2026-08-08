package com.meetingos.seating;

import java.util.List;

public record SeatChartImportResult(
        int rowCount,
        int seatsPerRow,
        int totalSeatCount,
        int structuralBlankCount,
        int aisleCount,
        int namedSeatCount,
        int assignedCount,
        int unassignedCount,
        List<SeatChartImportIssue> issues
) {
}
