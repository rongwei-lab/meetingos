package com.meetingos.security;

import java.util.Set;

public record CurrentUser(
        Long id,
        String username,
        String displayName,
        Set<Role> roles
) {
}
