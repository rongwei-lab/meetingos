package com.meetingos.meeting;

import jakarta.validation.constraints.NotNull;

public record MeetingStatusRequest(
        @NotNull MeetingStatus status
) {
}
