package com.meetingos.seating;

import jakarta.validation.constraints.NotNull;

public record SwapSeatRequest(
        @NotNull Long firstAttendeeId,
        @NotNull Long secondAttendeeId
) {
}
