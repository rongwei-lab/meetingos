package com.meetingos.user;

import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        UserStatus status,
        Set<Role> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
