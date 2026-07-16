package com.meetingos.operationlog;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogRetentionServiceTest {

    @Test
    void usesConfiguredRetentionDaysAndReturnsDeletedCount() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);
        when(repository.deleteByCreatedAtBefore(LocalDateTime.of(2026, 7, 9, 12, 0))).thenReturn(3);
        OperationLogRetentionService service = new OperationLogRetentionService(
                repository, new OperationLogRetentionProperties(7), clock
        );

        assertThat(service.cleanupExpiredLogs()).isEqualTo(3);
        verify(repository).deleteByCreatedAtBefore(LocalDateTime.of(2026, 7, 9, 12, 0));
    }

    @Test
    void safelyReportsNoExpiredLogs() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        when(repository.deleteByCreatedAtBefore(now.minusDays(90))).thenReturn(0);
        OperationLogRetentionService service = new OperationLogRetentionService(
                repository, new OperationLogRetentionProperties(90), Clock.systemUTC()
        );

        assertThat(service.cleanupExpiredLogs(now)).isZero();
        verify(repository).deleteByCreatedAtBefore(now.minusDays(90));
    }

    @Test
    void rejectsUnsafeRetentionConfiguration() {
        OperationLogRepository repository = mock(OperationLogRepository.class);

        assertThatThrownBy(() -> new OperationLogRetentionService(
                repository, new OperationLogRetentionProperties(0), Clock.systemUTC()
        )).hasMessage("操作日志保留天数必须在 1 至 3650 之间");
        assertThatThrownBy(() -> new OperationLogRetentionService(
                repository, new OperationLogRetentionProperties(3651), Clock.systemUTC()
        )).hasMessage("操作日志保留天数必须在 1 至 3650 之间");
    }
}
