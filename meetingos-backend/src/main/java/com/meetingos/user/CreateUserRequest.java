package com.meetingos.user;

import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 12, max = 120) String password,
        UserStatus status,
        @NotEmpty Set<Role> roles
) {
}
