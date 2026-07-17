package com.meetingos.publicapi;

import com.meetingos.security.PublicQueryProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicQueryHistoryRetentionServiceTest {

    @Test
    void deletesExpiredRowsInBoundedBatches() {
        PublicQueryHistoryRepository repository = mock(PublicQueryHistoryRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        PublicQueryProperties properties = new PublicQueryProperties();
        properties.setHistoryRetentionDays(30);
        properties.setHistoryCleanupBatchSize(2);
        properties.setHistoryCleanupMaxBatches(3);
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        PublicQueryHistoryRetentionService service = new PublicQueryHistoryRetentionService(
                repository, transactionManager, properties, clock
        );
        when(repository.findDeletableExpiredIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L), List.of(3L));
        when(repository.deleteByIdIn(List.of(1L, 2L))).thenReturn(2);
        when(repository.deleteByIdIn(List.of(3L))).thenReturn(1);

        int deleted = service.cleanupExpiredHistory();

        assertThat(deleted).isEqualTo(3);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository, org.mockito.Mockito.times(2))
                .findDeletableExpiredIds(cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getAllValues()).containsOnly(LocalDateTime.of(2026, 6, 16, 12, 0));
    }
}
