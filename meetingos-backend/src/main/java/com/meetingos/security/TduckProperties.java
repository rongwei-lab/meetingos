package com.meetingos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "meetingos.tduck")
public record TduckProperties(
        String webhookSecret,
        String webhookInternalUrl,
        String publicBaseUrl,
        String apiBaseUrl,
        String adminApiBaseUrl,
        String adminEmail,
        String adminPassword,
        Duration connectTimeout,
        Duration readTimeout
) {
}
