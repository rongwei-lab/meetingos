package com.meetingos.operationlog;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogControllerTest {

    @Test
    void firstPageReturnsServerSnapshotInResponseHeaderWithoutChangingResponseBody() {
        OperationLogService service = mock(OperationLogService.class);
        OperationLogController controller = new OperationLogController(service);
        PageResponse<OperationLog> page = new PageResponse<>(List.of(), 0, 20, 0, 0);
        when(service.list(null, 0, 20, null))
                .thenReturn(new OperationLogService.SnapshotPage(page, 118L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<PageResponse<OperationLogListItem>> result = controller.list(null, 0, 20, null, response);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).extracting(PageResponse::page, PageResponse::size, PageResponse::totalElements)
                .containsExactly(0, 20, 0L);
        assertThat(result.data().content()).isEmpty();
        assertThat(response.getHeader(OperationLogController.LOG_SNAPSHOT_HEADER)).isEqualTo("118");
        verify(service).list(null, 0, 20, null);
    }

    @Test
    void laterPagePassesClientSnapshotToServiceAndReturnsTheSameHeader() {
        OperationLogService service = mock(OperationLogService.class);
        OperationLogController controller = new OperationLogController(service);
        PageResponse<OperationLog> page = new PageResponse<>(List.of(), 1, 20, 25, 2);
        when(service.list(eq("CHECKIN"), eq(1), eq(20), eq(118L)))
                .thenReturn(new OperationLogService.SnapshotPage(page, 118L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.list("CHECKIN", 1, 20, 118L, response);

        verify(service).list("CHECKIN", 1, 20, 118L);
        assertThat(response.getHeader(OperationLogController.LOG_SNAPSHOT_HEADER)).isEqualTo("118");
    }

    @Test
    void fullFilterRequestBuildsOneFilterAndPassesItToTheSnapshotService() {
        OperationLogService service = mock(OperationLogService.class);
        OperationLogController controller = new OperationLogController(service);
        PageResponse<OperationLog> page = new PageResponse<>(List.of(), 0, 20, 0, 0);
        OperationLogFilter expectedFilter = new OperationLogFilter(
                null, OperationLogCategory.INVITATION, "邀请", false,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), true
        );
        when(service.listFiltered(eq(expectedFilter), eq(0), eq(20), eq(118L)))
                .thenReturn(new OperationLogService.SnapshotPage(page, 118L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse<PageResponse<OperationLogListItem>> result = controller.list(
                null, "INVITATION", "邀请", "false", "2026-07-01", "2026-07-02", "true",
                0, 20, 118L, response
        );

        assertThat(result.data()).extracting(PageResponse::page, PageResponse::size, PageResponse::totalElements)
                .containsExactly(0, 20, 0L);
        assertThat(response.getHeader(OperationLogController.LOG_SNAPSHOT_HEADER)).isEqualTo("118");
        verify(service).listFiltered(expectedFilter, 0, 20, 118L);
    }
}
