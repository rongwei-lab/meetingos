package com.meetingos.tduck;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "meetingos.tianyancha")
public record TianyanchaProperties(String token, int maxCallsPerDay, int perClientPerMinute, Duration timeout) {
}
