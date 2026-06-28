package com.meetingos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingos.security")
public record SecurityProperties(
        String jwtSecret,
        String jwtIssuer,
        long accessTokenMinutes,
        boolean secureCookies
) {
}
