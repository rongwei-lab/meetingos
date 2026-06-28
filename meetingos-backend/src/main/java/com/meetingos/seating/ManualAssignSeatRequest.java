package com.meetingos.seating;

import jakarta.validation.constraints.NotNull;

public record ManualAssignSeatRequest(
        @NotNull Long attendeeId,
        @NotNull Long seatId
) {
}
