package com.meetingos.operationlog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingos.operation-log")
public record OperationLogRetentionProperties(int retentionDays) {
}
