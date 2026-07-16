package com.meetingos.operationlog;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 操作日志仅用于近期审计与排查；超过保留期后按创建时间清理，避免审计表无限增长。
 */
@Service
public class OperationLogRetentionService {

    private static final int MIN_RETENTION_DAYS = 1;
    private static final int MAX_RETENTION_DAYS = 3650;
    private static final Logger log = LoggerFactory.getLogger(OperationLogRetentionService.class);

    private final OperationLogRepository operationLogRepository;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public OperationLogRetentionService(OperationLogRepository operationLogRepository,
                                        OperationLogRetentionProperties properties) {
        this(operationLogRepository, properties, Clock.systemDefaultZone());
    }

    OperationLogRetentionService(OperationLogRepository operationLogRepository,
                                 OperationLogRetentionProperties properties,
                                 Clock clock) {
        int configuredRetentionDays = properties.retentionDays();
        if (configuredRetentionDays < MIN_RETENTION_DAYS || configuredRetentionDays > MAX_RETENTION_DAYS) {
            throw new IllegalStateException("操作日志保留天数必须在 " + MIN_RETENTION_DAYS + " 至 "
                    + MAX_RETENTION_DAYS + " 之间");
        }
        this.operationLogRepository = operationLogRepository;
        this.retentionDays = configuredRetentionDays;
        this.clock = clock;
    }

    @Scheduled(cron = "${meetingos.operation-log.cleanup-cron:0 45 3 * * *}")
    @Transactional
    public int cleanupExpiredLogs() {
        return cleanupExpiredLogs(LocalDateTime.now(clock));
    }

    /**
     * 边界采用严格早于 cutoff：恰好位于保留期限的日志保留到下一次调度，避免提前删除。
     */
    int cleanupExpiredLogs(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(retentionDays);
        int deleted = operationLogRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            // 仅写应用日志，不调用审计服务，避免清理任务自己制造新的 operation_logs。
            log.info("Deleted expired operation logs, cutoff={}, deleted={}", cutoff, deleted);
        }
        return deleted;
    }
}
