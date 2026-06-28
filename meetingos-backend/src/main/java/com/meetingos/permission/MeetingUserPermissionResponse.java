package com.meetingos.permission;

import java.time.LocalDateTime;

public record MeetingUserPermissionResponse(
        Long id,
        Long meetingId,
        String meetingCode,
        String meetingName,
        Long userId,
        String username,
        String displayName,
        MeetingPermissionRole role,
        String grantedBy,
        LocalDateTime createdAt
) {
}
