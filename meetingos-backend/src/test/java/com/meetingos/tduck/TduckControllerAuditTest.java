package com.meetingos.tduck;

import com.meetingos.common.BusinessException;
import com.meetingos.common.ErrorCode;
import com.meetingos.operationlog.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TduckControllerAuditTest {

    @Mock
    private TduckService tduckService;

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private HttpServletRequest httpRequest;

    private TduckController controller;

    @BeforeEach
    void setUp() {
        controller = new TduckController(tduckService, operationLogService);
    }

    @Test
    void verifiedWebhookAuditsSafeSuccessMetadataWithoutPayloadOrToken() {
        String rawBody = "{\"phone\":\"13800000001\",\"token\":\"secret-token\"}";
        when(tduckService.handleWebhook(rawBody, "signature", "header-token"))
                .thenReturn(new TduckWebhookResult(SyncStatus.SUCCESS, 71L, 29L, "同步成功"));

        controller.webhook("signature", "header-token", "query-token", rawBody, httpRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operationLogService).record(
                eq("TDUCK_WEBHOOK_SYNC"),
                eq("TDUCK_SYNC"),
                eq("71"),
                eq(true),
                eq("TDuck Webhook 同步成功"),
                same(httpRequest),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).containsOnly(
                Map.entry("syncLogId", 71L),
                Map.entry("attendeeId", 29L),
                Map.entry("status", "SUCCESS")
        );
        assertThat(detailsCaptor.getValue().toString())
                .doesNotContain("13800000001", "secret-token", "header-token", "query-token");
    }

    @Test
    void verifiedWebhookFailureUsesFixedErrorCodeInsteadOfPayloadMessage() {
        String rawBody = "{\"phone\":\"13800000001\"}";
        when(tduckService.handleWebhook(rawBody, "signature", "header-token"))
                .thenReturn(new TduckWebhookResult(SyncStatus.FAILED, 72L, null, "手机号字段不合法: 13800000001"));

        controller.webhook("signature", "header-token", null, rawBody, httpRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operationLogService).record(
                eq("TDUCK_WEBHOOK_SYNC"),
                eq("TDUCK_SYNC"),
                eq("72"),
                eq(false),
                eq("TDuck Webhook 同步失败"),
                same(httpRequest),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue())
                .containsOnlyKeys("syncLogId", "attendeeId", "status", "errorCode")
                .containsEntry("syncLogId", 72L)
                .containsEntry("attendeeId", null)
                .containsEntry("status", "FAILED")
                .containsEntry("errorCode", "TDUCK_SYNC_FAILED");
    }

    @Test
    void duplicateWebhookDoesNotExpandTheOperationLog() {
        String rawBody = "{\"eventId\":\"replayed-event\"}";
        when(tduckService.handleWebhook(rawBody, "signature", "header-token"))
                .thenReturn(new TduckWebhookResult(SyncStatus.DUPLICATE_IGNORED, 71L, 29L, "重复 Webhook 已忽略"));

        controller.webhook("signature", "header-token", null, rawBody, httpRequest);

        verifyNoInteractions(operationLogService);
    }

    @Test
    void rejectedWebhookDoesNotWriteOperationLog() {
        String rawBody = "{\"phone\":\"13800000001\"}";
        when(tduckService.handleWebhook(rawBody, "invalid-signature", "invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook 签名校验失败"));

        assertThatThrownBy(() -> controller.webhook(
                "invalid-signature", "invalid-token", null, rawBody, httpRequest
        )).isInstanceOf(BusinessException.class);

        verifyNoInteractions(operationLogService);
    }

    @Test
    void verifiedButUnparseableWebhookAuditsAFixedFailureSummary() {
        String rawBody = "{not-json";
        when(tduckService.handleWebhook(rawBody, "signature", "header-token"))
                .thenThrow(new VerifiedWebhookParseException(73L));

        assertThatThrownBy(() -> controller.webhook("signature", "header-token", null, rawBody, httpRequest))
                .isInstanceOf(VerifiedWebhookParseException.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operationLogService).record(
                eq("TDUCK_WEBHOOK_SYNC"),
                eq("TDUCK_SYNC"),
                eq("73"),
                eq(false),
                eq("TDuck Webhook 同步失败"),
                same(httpRequest),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue())
                .containsOnlyKeys("syncLogId", "attendeeId", "status", "errorCode")
                .containsEntry("syncLogId", 73L)
                .containsEntry("attendeeId", null)
                .containsEntry("status", "FAILED")
                .containsEntry("errorCode", "TDUCK_WEBHOOK_PARSE_FAILED");
    }
}
