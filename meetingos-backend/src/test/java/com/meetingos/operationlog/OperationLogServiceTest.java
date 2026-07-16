package com.meetingos.operationlog;

import com.meetingos.security.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {

    @Test
    void recordTruncatesMessageBeforeSaving() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        OperationLogService service = new OperationLogService(repository, clientIpResolver);
        String longMessage = "x".repeat(800);

        service.record("TDUCK_MAPPING_SAVE", "TDUCK", false, longMessage, null, Map.of());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(logCaptor.capture());

        /*
         * operation_logs.message 的数据库长度是 500。审计日志保存前必须主动截断，
         * 避免日志失败把真实业务异常覆盖成“系统异常”。
         */
        OperationLog saved = logCaptor.getValue();
        assertThat(saved.getMessage()).hasSize(500);
        assertThat(saved.getMessage()).endsWith("...");
    }

    @Test
    void recordForActorUsesTheAuthenticatedActorBeforeSecurityContextIsEstablished() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        OperationLogService service = new OperationLogService(repository, clientIpResolver);

        service.recordForActor("login-user", "AUTH_LOGIN", "APP_USER", "login-user", true, "登录成功", null, Map.of());

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActor()).isEqualTo("login-user");
        assertThat(logCaptor.getValue().getTargetId()).isEqualTo("login-user");
    }

    @Test
    void firstListRequestFreezesCurrentMaxIdAndSortsByCreationTimeAndIdDescending() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        when(repository.findMaxId()).thenReturn(98L);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        OperationLogService service = new OperationLogService(repository, clientIpResolver);

        OperationLogService.SnapshotPage result = service.list(null, 2, 25, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findMaxId();
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        assertDescendingLogSort(pageableCaptor.getValue(), 2, 25);
        assertThat(result.snapshotId()).isEqualTo(98L);
    }

    @Test
    void laterListRequestUsesProvidedSnapshotWithoutRecalculatingIt() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        OperationLogService service = new OperationLogService(repository, clientIpResolver);

        OperationLogService.SnapshotPage result = service.list(null, 1, 20, 52L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        assertDescendingLogSort(pageableCaptor.getValue(), 1, 20);
        assertThat(result.snapshotId()).isEqualTo(52L);
        verifyNoInteractions(clientIpResolver);
    }

    @Test
    void filteredListKeepsTheSameSnapshotBoundary() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        OperationLogService service = new OperationLogService(repository, clientIpResolver);

        service.list("CHECKIN", 1, 20, 52L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        assertDescendingLogSort(pageableCaptor.getValue(), 1, 20);
    }

    @Test
    void listRejectsNegativeSnapshotId() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        OperationLogService service = new OperationLogService(repository, clientIpResolver);

        assertThatThrownBy(() -> service.list(null, 0, 20, -1L))
                .hasMessage("操作日志快照 ID 不能小于 0");
        verifyNoInteractions(repository);
    }

    private void assertDescendingLogSort(Pageable pageable, int expectedPage, int expectedSize) {
        assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
        assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }
}
