package com.meetingos.auth;

import com.meetingos.security.Role;

import java.util.Set;

public record CurrentUserResponse(
        Long userId,
        String username,
        String displayName,
        Set<Role> roles
) {
}
