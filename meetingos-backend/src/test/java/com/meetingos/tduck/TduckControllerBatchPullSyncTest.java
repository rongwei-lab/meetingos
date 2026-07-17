package com.meetingos.tduck;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.OperationLogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TduckControllerBatchPullSyncTest {

    @Test
    void legacyPostPathCreatesBackgroundTaskInsteadOfRunningSynchronousLoop() {
        TduckService tduckService = mock(TduckService.class);
        TduckPullSyncTaskService taskService = mock(TduckPullSyncTaskService.class);
        TduckController controller = new TduckController(
                tduckService, taskService, mock(OperationLogService.class)
        );
        TduckBatchPullSyncRequest request = new TduckBatchPullSyncRequest(7L, 100);
        TduckBatchPullSyncResponse pending = response(71L, TduckPullSyncTaskStatus.PENDING);
        when(taskService.create(request)).thenReturn(pending);

        ApiResponse<TduckBatchPullSyncResponse> actual = controller.batchPullSync(request);

        assertThat(actual.data()).isSameAs(pending);
        verify(taskService).create(request);
        verify(tduckService, never()).batchPullSync(request);
    }

    @Test
    void taskDetailReturnsPersistedProgressSnapshot() {
        TduckService tduckService = mock(TduckService.class);
        TduckPullSyncTaskService taskService = mock(TduckPullSyncTaskService.class);
        TduckController controller = new TduckController(
                tduckService, taskService, mock(OperationLogService.class)
        );
        TduckBatchPullSyncResponse running = response(72L, TduckPullSyncTaskStatus.RUNNING);
        when(taskService.detail(7L, 72L)).thenReturn(running);

        ApiResponse<TduckBatchPullSyncResponse> actual = controller.batchPullSyncTask(72L, 7L);

        assertThat(actual.data()).isSameAs(running);
    }

    private TduckBatchPullSyncResponse response(Long id, TduckPullSyncTaskStatus status) {
        return new TduckBatchPullSyncResponse(
                id, status, 0, 0, 0, 0, 0, 0,
                List.of(), "等待同步", null, null, null
        );
    }
}
