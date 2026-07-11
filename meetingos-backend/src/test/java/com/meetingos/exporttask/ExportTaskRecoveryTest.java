package com.meetingos.exporttask;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportTaskRecoveryTest {

    @Test
    void startupAndScheduledRecoveryDispatchDatabaseCandidates() {
        ExportTaskService service = mock(ExportTaskService.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        ExportTaskRecovery recovery = new ExportTaskRecovery(service, worker);
        when(service.recoverableTaskIds()).thenReturn(List.of(11L, 12L));

        recovery.resumeInterruptedTasks();
        recovery.resumeStaleTasks();

        verify(worker, org.mockito.Mockito.times(2)).resume(11L);
        verify(worker, org.mockito.Mockito.times(2)).resume(12L);
    }

    @Test
    void dispatchFailureDoesNotPreventLaterCandidatesOrEscapeScheduler() {
        ExportTaskService service = mock(ExportTaskService.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        ExportTaskRecovery recovery = new ExportTaskRecovery(service, worker);
        when(service.recoverableTaskIds()).thenReturn(List.of(21L, 22L));
        doThrow(new IllegalStateException("executor stopping")).when(worker).resume(21L);

        recovery.resumeStaleTasks();

        verify(worker).resume(21L);
        verify(worker).resume(22L);
    }

    @Test
    void databaseScanFailureLeavesTasksForNextCycle() {
        ExportTaskService service = mock(ExportTaskService.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        ExportTaskRecovery recovery = new ExportTaskRecovery(service, worker);
        when(service.recoverableTaskIds()).thenThrow(new IllegalStateException("database unavailable"));

        recovery.resumeStaleTasks();

        verify(worker, never()).resume(org.mockito.ArgumentMatchers.anyLong());
    }
}
