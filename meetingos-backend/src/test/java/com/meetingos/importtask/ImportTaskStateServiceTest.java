package com.meetingos.importtask;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportTaskStateServiceTest {

    private final ImportTaskRepository repository = mock(ImportTaskRepository.class);
    private final ImportTaskFileStore fileStore = mock(ImportTaskFileStore.class);
    private final ImportTaskStateService service = new ImportTaskStateService(repository, fileStore, 168);

    @Test
    void expirationRechecksStatusAfterTakingTaskLock() {
        LocalDateTime now = LocalDateTime.now();
        ImportTask task = task(ImportTaskStatus.RUNNING, now.minusHours(1));
        task.setExpiresAt(now.minusMinutes(1));
        task.setInputFilePath("input/source.xlsx");
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));

        service.expireFilesIfDue(1L, now);

        assertThat(task.getStatus()).isEqualTo(ImportTaskStatus.RUNNING);
        verifyNoInteractions(fileStore);
    }

    @Test
    void expirationClearsPathsOnlyAfterBothFilesAreDeleted() {
        LocalDateTime now = LocalDateTime.now();
        ImportTask task = task(ImportTaskStatus.COMPLETED, now.minusHours(1));
        task.setExpiresAt(now.minusMinutes(1));
        task.setInputFilePath("input/source.xlsx");
        task.setFailureFilePath("failures/result.xlsx");
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(fileStore.delete("input/source.xlsx")).thenReturn(true);
        when(fileStore.delete("failures/result.xlsx")).thenReturn(true);

        service.expireFilesIfDue(1L, now);

        assertThat(task.getStatus()).isEqualTo(ImportTaskStatus.EXPIRED);
        assertThat(task.getInputFilePath()).isNull();
        assertThat(task.getFailureFilePath()).isNull();
        verify(fileStore).delete("input/source.xlsx");
        verify(fileStore).delete("failures/result.xlsx");
    }

    @Test
    void expirationKeepsPathsWhenADeleteNeedsRetry() {
        LocalDateTime now = LocalDateTime.now();
        ImportTask task = task(ImportTaskStatus.FAILED, now.minusHours(1));
        task.setExpiresAt(now.minusMinutes(1));
        task.setInputFilePath("input/source.xlsx");
        task.setFailureFilePath("failures/result.xlsx");
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(fileStore.delete("input/source.xlsx")).thenReturn(true);
        when(fileStore.delete("failures/result.xlsx")).thenReturn(false);

        service.expireFilesIfDue(1L, now);

        assertThat(task.getStatus()).isEqualTo(ImportTaskStatus.FAILED);
        assertThat(task.getInputFilePath()).isEqualTo("input/source.xlsx");
        assertThat(task.getFailureFilePath()).isEqualTo("failures/result.xlsx");
    }

    @Test
    void stuckRecoveryRechecksLastUpdateAfterTakingTaskLock() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        ImportTask recentlyUpdated = task(ImportTaskStatus.RUNNING, cutoff.plusMinutes(1));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(recentlyUpdated));

        service.failIfStuck(1L, cutoff);

        assertThat(recentlyUpdated.getStatus()).isEqualTo(ImportTaskStatus.RUNNING);
    }

    @Test
    void stuckRecoveryFailsStillStaleActiveTask() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        ImportTask stale = task(ImportTaskStatus.PREFLIGHTING, cutoff.minusMinutes(1));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(stale));

        service.failIfStuck(1L, cutoff);

        assertThat(stale.getStatus()).isEqualTo(ImportTaskStatus.FAILED);
        assertThat(stale.getProgress()).isEqualTo(100);
        assertThat(stale.getCompletedAt()).isNotNull();
        assertThat(stale.getExpiresAt()).isNotNull();
    }

    @Test
    void leasesStalePendingFormatCheckAndRefreshesUpdatedAt() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        LocalDateTime leasedAt = LocalDateTime.now();
        ImportTask pending = task(ImportTaskStatus.PENDING, cutoff.minusSeconds(1));
        pending.setPreflight(true);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));

        ImportTaskRecoveryLease lease = service.leaseUnstartedTask(1L, cutoff, leasedAt);

        assertThat(lease).isEqualTo(new ImportTaskRecoveryLease(1L, false));
        assertThat(pending.getUpdatedAt()).isEqualTo(leasedAt);
        assertThat(pending.getMessage()).isEqualTo("等待文件格式检查（任务已重新排队）");
    }

    @Test
    void leasesCommittedButUnstartedTaskForCommittedWorker() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        LocalDateTime leasedAt = LocalDateTime.now();
        ImportTask committed = task(ImportTaskStatus.RUNNING, cutoff.minusSeconds(1));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(committed));

        ImportTaskRecoveryLease lease = service.leaseUnstartedTask(1L, cutoff, leasedAt);

        assertThat(lease).isEqualTo(new ImportTaskRecoveryLease(1L, true));
        assertThat(committed.getMessage()).isEqualTo("等待导入已完成格式检查的文件（任务已重新排队）");
    }

    @Test
    void doesNotLeaseStartedOrFreshTask() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        ImportTask started = task(ImportTaskStatus.RUNNING, cutoff.minusSeconds(1));
        started.setStartedAt(LocalDateTime.now().minusMinutes(2));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(started));

        assertThat(service.leaseUnstartedTask(1L, cutoff, LocalDateTime.now())).isNull();

        ImportTask fresh = task(ImportTaskStatus.PENDING, cutoff.plusSeconds(1));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(fresh));
        assertThat(service.leaseUnstartedTask(2L, cutoff, LocalDateTime.now())).isNull();
        assertThat(fresh.getMessage()).isNull();
    }

    private ImportTask task(ImportTaskStatus status, LocalDateTime updatedAt) {
        ImportTask task = new ImportTask();
        task.setId(1L);
        task.setStatus(status);
        task.setUpdatedAt(updatedAt);
        return task;
    }
}
