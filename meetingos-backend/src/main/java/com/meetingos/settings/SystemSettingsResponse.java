package com.meetingos.settings;

public record SystemSettingsResponse(
        String baseUrl,
        String tduckFrontUrl,
        String tduckApiUrl,
        boolean webhookTokenConfigured,
        String webhookTokenMasked,
        boolean maskSensitive,
        String brandName,
        String browserTitle,
        String metaDescription,
        String logoUrl,
        String faviconUrl
) {
}
