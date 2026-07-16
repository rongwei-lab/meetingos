package com.meetingos.operationlog;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationLogFilterTest {

    @Test
    void parsesRequestValuesAndDefaultsDraftSavesToHidden() {
        OperationLogFilter defaults = OperationLogFilter.fromRequest(
                " CHECKIN ", " invitation ", " 邀请 ", "false",
                "2026-07-01", "2026-07-02", null
        );

        assertThat(defaults.action()).isEqualTo("CHECKIN");
        assertThat(defaults.category()).isEqualTo(OperationLogCategory.INVITATION);
        assertThat(defaults.keyword()).isEqualTo("邀请");
        assertThat(defaults.success()).isFalse();
        assertThat(defaults.fromDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(defaults.toDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(defaults.includeDraftSaves()).isFalse();

        OperationLogFilter includeDraftSaves = OperationLogFilter.fromRequest(
                null, null, null, null, null, null, "true"
        );
        assertThat(includeDraftSaves.includeDraftSaves()).isTrue();
    }

    @Test
    void rejectsInvalidRequestValuesBeforeQueryingLogs() {
        assertThatThrownBy(() -> OperationLogFilter.fromRequest(
                null, "not-a-category", null, null, null, null, null
        )).hasMessage("category 无效");
        assertThatThrownBy(() -> OperationLogFilter.fromRequest(
                null, null, null, "yes", null, null, null
        )).hasMessage("success 必须为 true 或 false");
        assertThatThrownBy(() -> OperationLogFilter.fromRequest(
                null, null, null, null, "2026/07/01", null, null
        )).hasMessage("fromDate 必须为 yyyy-MM-dd 格式");
        assertThatThrownBy(() -> OperationLogFilter.fromRequest(
                null, null, null, null, "2026-07-02", "2026-07-01", null
        )).hasMessage("fromDate 不能晚于 toDate");
        assertThatThrownBy(() -> OperationLogFilter.fromRequest(
                null, null, "x".repeat(OperationLogFilter.MAX_KEYWORD_LENGTH + 1), null,
                null, null, null
        )).hasMessage("keyword 不能超过 100 个字符");
    }
}
