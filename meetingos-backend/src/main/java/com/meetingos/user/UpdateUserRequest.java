package com.meetingos.user;

import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotNull UserStatus status,
        @NotEmpty Set<Role> roles
) {
}
