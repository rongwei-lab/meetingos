package com.meetingos.operationlog;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationLogServiceTest {

    @Test
    void recordTruncatesMessageBeforeSaving() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository);
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
}
