package com.meetingos.checkin;

import jakarta.validation.constraints.NotNull;

public record CheckinRequest(
        @NotNull Long meetingId,
        String qrCode,
        String attendeeCode,
        String phone,
        String name,
        String entrance,
        CheckinType checkinType,
        String remark
) {
}
