package com.meetingos.attendee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AttendeeRequest(
        @NotNull Long meetingId,
        @NotBlank String name,
        @NotBlank String phone,
        String organization,
        String title,
        String identityNo,
        String email,
        AttendeeType attendeeType,
        Boolean accommodationRequired,
        String roomNo,
        String roomRemark,
        String remark,
        String tags
) {
}
