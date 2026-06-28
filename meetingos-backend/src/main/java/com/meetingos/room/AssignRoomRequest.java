package com.meetingos.room;

import jakarta.validation.constraints.NotNull;

public record AssignRoomRequest(
        @NotNull Long attendeeId,
        String roomNo,
        String roomRemark
) {
}
