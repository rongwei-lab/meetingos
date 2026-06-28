package com.meetingos.seating;

import jakarta.validation.constraints.NotNull;

public record SeatDisabledRequest(
        @NotNull Boolean disabled
) {
}
