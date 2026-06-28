package com.meetingos.seating;

import java.util.List;

public record AutoSeatingResult(
        int assignedCount,
        int skippedCount,
        List<String> warnings
) {
}
