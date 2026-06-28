package com.meetingos.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoomRequest(
        @NotNull Long meetingId,
        @NotBlank String roomNo,
        String roomType,
        String hotelName,
        String remark,
        @NotNull @Min(1) Integer capacity,
        RoomStatus status
) {
}
