package com.meetingos.settings;

public record BrandingResponse(
        String brandName,
        String browserTitle,
        String metaDescription,
        String logoUrl,
        String faviconUrl
) {
}
