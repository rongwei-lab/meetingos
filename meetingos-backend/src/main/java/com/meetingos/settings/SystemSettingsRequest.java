package com.meetingos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemSettingsRequest(
        @NotBlank @Size(max = 300) String baseUrl,
        @NotBlank @Size(max = 300) String tduckFrontUrl,
        @NotBlank @Size(max = 300) String tduckApiUrl,
        @Size(max = 300) String webhookToken,
        boolean maskSensitive,
        @Size(max = 80) String brandName,
        @Size(max = 120) String browserTitle,
        @Size(max = 240) String metaDescription,
        @Size(max = 12000) String logoUrl,
        @Size(max = 12000) String faviconUrl
) {
}
