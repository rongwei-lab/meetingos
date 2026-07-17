package com.meetingos.publicapi;

import com.meetingos.security.PublicQueryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublicQueryHistoryRetentionService {

    private static final Logger log = LoggerFactory.getLogger(PublicQueryHistoryRetentionService.class);
    private static final int MAX_RETENTION_DAYS = 3650;
    private static final int MAX_BATCH_SIZE = 10_000;
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final PublicQueryHistoryRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int retentionDays;
    private final int batchSize;
    private final int maxBatches;

    @Autowired
    public PublicQueryHistoryRetentionService(PublicQueryHistoryRepository repository,
                                              org.springframework.transaction.PlatformTransactionManager transactionManager,
                                              PublicQueryProperties properties) {
        this(repository, transactionManager, properties, Clock.systemDefaultZone());
    }

    PublicQueryHistoryRetentionService(PublicQueryHistoryRepository repository,
                                       org.springframework.transaction.PlatformTransactionManager transactionManager,
                                       PublicQueryProperties properties,
                                       Clock clock) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.retentionDays = bounded(properties.getHistoryRetentionDays(), 1, MAX_RETENTION_DAYS, "公开查询历史保留天数");
        this.batchSize = bounded(properties.getHistoryCleanupBatchSize(), 1, MAX_BATCH_SIZE, "公开查询历史清理批大小");
        this.maxBatches = bounded(properties.getHistoryCleanupMaxBatches(), 1, MAX_BATCHES_PER_RUN, "公开查询历史单次清理批数");
    }

    @Scheduled(cron = "${meetingos.public-query.history-cleanup-cron:0 15 4 * * *}")
    public int cleanupExpiredHistory() {
        return cleanupExpiredHistory(LocalDateTime.now(clock));
    }

    int cleanupExpiredHistory(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(retentionDays);
        int deleted = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            Integer batchDeleted = transactionTemplate.execute(status -> deleteBatch(cutoff));
            int current = batchDeleted == null ? 0 : batchDeleted;
            deleted += current;
            if (current < batchSize) {
                break;
            }
        }
        if (deleted > 0) {
            log.info("Deleted expired public query history, cutoff={}, deleted={}", cutoff, deleted);
        }
        return deleted;
    }

    private int deleteBatch(LocalDateTime cutoff) {
        /*
         * 座位、住宿、晚宴的“曾查询”状态参与拒审和导出，不能随明细过期而消失。
         * 仓储查询会为每位参会人、每种业务查询永久保留最新一条，其余历史仍分批清理。
         */
        List<Long> ids = repository.findDeletableExpiredIds(cutoff, PageRequest.of(0, batchSize));
        return ids.isEmpty() ? 0 : repository.deleteByIdIn(ids);
    }

    private int bounded(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalStateException(label + "必须在 " + min + " 至 " + max + " 之间");
        }
        return value;
    }
}
