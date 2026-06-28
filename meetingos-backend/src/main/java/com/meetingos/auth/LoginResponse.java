package com.meetingos.auth;

import com.meetingos.security.Role;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String username,
        String displayName,
        Set<Role> roles
) {
}
