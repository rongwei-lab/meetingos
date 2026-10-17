package com.meetingos.tduck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TduckPhonePrecheckRequest(
        @NotBlank @Size(max = 120) String formKey,
        @NotBlank @Size(max = 255) String token,
        @NotBlank @Size(max = 40) String phone
) {
}
