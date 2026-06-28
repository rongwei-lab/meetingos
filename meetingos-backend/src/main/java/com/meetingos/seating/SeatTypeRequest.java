package com.meetingos.seating;

import jakarta.validation.constraints.NotNull;

public record SeatTypeRequest(
        @NotNull SeatType seatType
) {
}
