package com.meetingos.operationlog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OperationLogTransactionIntegrationTest {

    @Autowired
    private OperationLogService operationLogService;
    @Autowired
    private OperationLogRepository operationLogRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void transactionScopedLogRollsBackWithCaller() {
        String targetId = "business-log-rollback-probe";
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            operationLogService.recordWithinTransaction(
                    "ROOM_ASSIGNMENT_CHANGE",
                    "ROOM",
                    targetId,
                    true,
                    "事务回滚探针",
                    null,
                    Map.of("source", "TEST")
            );
            operationLogRepository.flush();
            throw new RollbackProbeException();
        })).isInstanceOf(RollbackProbeException.class);

        assertThat(operationLogRepository.findAll())
                .noneMatch(log -> targetId.equals(log.getTargetId()));
    }

    @Test
    void independentAuditLogSurvivesCallerRollback() {
        String targetId = "audit-log-rollback-probe";
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                operationLogService.record(
                        "TEST_AUDIT",
                        "SYSTEM",
                        targetId,
                        false,
                        "独立事务探针",
                        null,
                        Map.of("source", "TEST")
                );
                throw new RollbackProbeException();
            })).isInstanceOf(RollbackProbeException.class);

            assertThat(operationLogRepository.findAll())
                    .anyMatch(log -> targetId.equals(log.getTargetId()));
        } finally {
            operationLogRepository.findAll().stream()
                    .filter(log -> targetId.equals(log.getTargetId()))
                    .forEach(operationLogRepository::delete);
            operationLogRepository.flush();
        }
    }

    private static final class RollbackProbeException extends RuntimeException {
    }
}
