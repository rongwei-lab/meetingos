package com.meetingos.settings;

public record SystemSettingsResponse(
        String baseUrl,
        String tduckFrontUrl,
        String tduckApiUrl,
        boolean webhookTokenConfigured,
        String webhookTokenMasked,
        boolean maskSensitive,
        boolean exportApproval,
        String brandName,
        String browserTitle,
        String metaDescription,
        String logoUrl,
        String faviconUrl
) {
}
