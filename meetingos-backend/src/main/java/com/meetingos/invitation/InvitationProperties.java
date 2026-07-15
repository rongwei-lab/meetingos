package com.meetingos.invitation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingos.invitation")
public record InvitationProperties(
        String trackingSecret,
        int eventRetentionDays,
        String amapKey
) {
}
