package com.meetingos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingos.app")
public record AppProperties(
        String publicBaseUrl
) {
}
