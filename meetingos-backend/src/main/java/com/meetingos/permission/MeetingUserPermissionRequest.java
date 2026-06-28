package com.meetingos.permission;

import jakarta.validation.constraints.NotNull;

public record MeetingUserPermissionRequest(
        @NotNull Long meetingId,
        @NotNull Long userId,
        @NotNull MeetingPermissionRole role
) {
}
