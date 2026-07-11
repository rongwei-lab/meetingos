package com.meetingos.importtask;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.security.CurrentUser;
import com.meetingos.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportTaskServiceTest {

    private final ImportTaskRepository repository = mock(ImportTaskRepository.class);
    private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
    private final ImportTaskFileStore fileStore = mock(ImportTaskFileStore.class);
    private final ImportTaskWorker worker = mock(ImportTaskWorker.class);
    private final ImportTaskStateService stateService = mock(ImportTaskStateService.class);
    private final Meeting meeting = meeting();
    private final ImportTaskService service = new ImportTaskService(
            repository,
            meetingRepository,
            mock(MeetingAccessService.class),
            fileStore,
            worker,
            stateService,
            2,
            60
    );

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        when(meetingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(meeting));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @EnumSource(value = ImportTaskStatus.class, names = {"FAILED", "EXPIRED"})
    void retryableTaskReusesIdResetsAttemptAndCleansOldFilesAfterCommit(ImportTaskStatus status) {
        ImportTask existing = existingTask(status);
        stubRetry(existing, "input/new.xlsx");
        authenticate("retry-admin");

        ImportTaskResponse response = service.create(1L, "attendees", true, "same-key", upload("new.xlsx"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(existing.getStatus()).isEqualTo(ImportTaskStatus.PENDING);
        assertThat(existing.getOriginalFilename()).isEqualTo("new.xlsx");
        assertThat(existing.getInputFilePath()).isEqualTo("input/new.xlsx");
        assertThat(existing.getFailureFilePath()).isNull();
        assertThat(existing.isPreflight()).isTrue();
        assertThat(existing.getTotalRows()).isZero();
        assertThat(existing.getProcessedRows()).isZero();
        assertThat(existing.getImportedRows()).isZero();
        assertThat(existing.getFailedRows()).isZero();
        assertThat(existing.getProgress()).isZero();
        assertThat(existing.getRequestedBy()).isEqualTo("retry-admin");
        assertThat(existing.getStartedAt()).isNull();
        assertThat(existing.getCompletedAt()).isNull();
        assertThat(existing.getExpiresAt()).isNull();
        assertThat(existing.getMessage()).isEqualTo("等待文件格式检查");
        verify(fileStore, never()).deleteQuietly("input/old.xlsx");
        verify(worker, never()).processCreated(10L);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileStore).deleteQuietly("input/old.xlsx");
        verify(fileStore).deleteQuietly("failures/old.xlsx");
        verify(fileStore, never()).deleteQuietly("input/new.xlsx");
        verify(worker).processCreated(10L);
    }

    @ParameterizedTest
    @EnumSource(value = ImportTaskStatus.class, names = {
            "PENDING", "PREFLIGHTING", "PREVIEW_READY", "RUNNING", "COMPLETED"
    })
    void nonRetryableIdempotentRequestReturnsExistingTask(ImportTaskStatus status) {
        ImportTask existing = existingTask(status);
        when(repository.findByMeetingIdAndImportTypeAndIdempotencyKey(1L, "attendees", "same-key"))
                .thenReturn(Optional.of(existing));

        ImportTaskResponse response = service.create(1L, "attendees", true, "same-key", upload("ignored.xlsx"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(status);
        verify(fileStore, never()).saveInput(any());
        verify(repository, never()).saveAndFlush(any());
        verify(worker, never()).processCreated(any());
    }

    @Test
    void rollbackDeletesOnlyNewFileAndKeepsPreviousAttemptFiles() {
        ImportTask existing = existingTask(ImportTaskStatus.FAILED);
        stubRetry(existing, "input/new.xlsx");

        service.create(1L, "attendees", false, "same-key", upload("new.xlsx"));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStore).deleteQuietly("input/new.xlsx");
        verify(fileStore, never()).deleteQuietly("input/old.xlsx");
        verify(fileStore, never()).deleteQuietly("failures/old.xlsx");
        verify(worker, never()).processCreated(any());
    }

    @Test
    void persistenceFailureDeletesNewFileWithoutTouchingPreviousAttempt() {
        ImportTask existing = existingTask(ImportTaskStatus.FAILED);
        when(repository.findByMeetingIdAndImportTypeAndIdempotencyKey(1L, "attendees", "same-key"))
                .thenReturn(Optional.of(existing));
        when(fileStore.saveInput(any())).thenReturn(stored("new.xlsx", "input/new.xlsx"));
        when(repository.saveAndFlush(existing)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.create(1L, "attendees", false, "same-key", upload("new.xlsx")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(fileStore).deleteQuietly("input/new.xlsx");
        verify(fileStore, never()).deleteQuietly("input/old.xlsx");
        verify(fileStore, never()).deleteQuietly("failures/old.xlsx");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void sameNormalizedPathIsNotDeletedOnCommit() {
        ImportTask existing = existingTask(ImportTaskStatus.EXPIRED);
        existing.setInputFilePath("input/shared.xlsx");
        existing.setFailureFilePath("input/./shared.xlsx");
        stubRetry(existing, "input/shared.xlsx");

        service.create(1L, "attendees", false, "same-key", upload("new.xlsx"));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileStore, never()).deleteQuietly(any());
        verify(worker).processCreated(10L);
    }

    @Test
    void sameNormalizedPathIsPreservedOnRollback() {
        ImportTask existing = existingTask(ImportTaskStatus.FAILED);
        existing.setInputFilePath("input/shared.xlsx");
        stubRetry(existing, "input/./shared.xlsx");

        service.create(1L, "attendees", false, "same-key", upload("new.xlsx"));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStore, never()).deleteQuietly(any());
        verify(worker, never()).processCreated(any());
    }

    @Test
    void commitRejectsAnotherQueuedTaskOfSameType() {
        ImportTask task = existingTask(ImportTaskStatus.PREVIEW_READY);
        task.setInputFilePath("input/source.xlsx");
        when(repository.findByIdAndMeetingIdForUpdate(10L, 1L)).thenReturn(Optional.of(task));
        when(fileStore.exists("input/source.xlsx")).thenReturn(true);
        when(repository.existsByMeetingIdAndImportTypeAndStatusInAndIdNot(
                eq(1L),
                eq("attendees"),
                anyCollection(),
                eq(10L)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.commit(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前会议已有同类型导入任务正在处理，请等待完成后重试");
    }

    @Test
    void recoveryDispatchesOnlyLeasedUnstartedAttempts() {
        ImportTask pending = existingTask(ImportTaskStatus.PENDING);
        pending.setId(11L);
        ImportTask committed = existingTask(ImportTaskStatus.RUNNING);
        committed.setId(12L);
        ImportTask skipped = existingTask(ImportTaskStatus.PENDING);
        skipped.setId(13L);
        when(repository.findTop100ByStatusInAndStartedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                anyCollection(),
                any(LocalDateTime.class)
        )).thenReturn(List.of(pending, committed, skipped));
        when(stateService.leaseUnstartedTask(eq(11L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new ImportTaskRecoveryLease(11L, false));
        when(stateService.leaseUnstartedTask(eq(12L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new ImportTaskRecoveryLease(12L, true));

        service.recoverUnstartedTasks();

        verify(worker).processCreated(11L);
        verify(worker).processCommitted(12L);
        verify(worker, never()).processCreated(13L);
    }

    @Test
    void rejectedRecoveryDispatchDoesNotAbortRemainingCandidates() {
        ImportTask first = existingTask(ImportTaskStatus.PENDING);
        first.setId(11L);
        ImportTask second = existingTask(ImportTaskStatus.PENDING);
        second.setId(12L);
        when(repository.findTop100ByStatusInAndStartedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                anyCollection(),
                any(LocalDateTime.class)
        )).thenReturn(List.of(first, second));
        when(stateService.leaseUnstartedTask(eq(11L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new ImportTaskRecoveryLease(11L, false));
        when(stateService.leaseUnstartedTask(eq(12L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new ImportTaskRecoveryLease(12L, false));
        doThrow(new IllegalStateException("queue full")).when(worker).processCreated(11L);

        service.recoverUnstartedTasks();

        verify(worker).processCreated(12L);
    }

    private void stubRetry(ImportTask existing, String newPath) {
        when(repository.findByMeetingIdAndImportTypeAndIdempotencyKey(1L, "attendees", "same-key"))
                .thenReturn(Optional.of(existing));
        when(fileStore.saveInput(any())).thenReturn(stored("new.xlsx", newPath));
        when(repository.saveAndFlush(existing)).thenReturn(existing);
    }

    private ImportTask existingTask(ImportTaskStatus status) {
        ImportTask task = new ImportTask();
        task.setId(10L);
        task.setMeeting(meeting);
        task.setImportType("attendees");
        task.setStatus(status);
        task.setOriginalFilename("old.xlsx");
        task.setInputFilePath("input/old.xlsx");
        task.setFailureFilePath("failures/old.xlsx");
        task.setIdempotencyKey("same-key");
        task.setPreflight(false);
        task.setTotalRows(9);
        task.setProcessedRows(8);
        task.setImportedRows(6);
        task.setFailedRows(2);
        task.setProgress(100);
        task.setRequestedBy("old-admin");
        task.setStartedAt(LocalDateTime.now().minusHours(2));
        task.setCompletedAt(LocalDateTime.now().minusHours(1));
        task.setExpiresAt(LocalDateTime.now().plusHours(1));
        task.setMessage("旧任务消息");
        task.setUpdatedAt(LocalDateTime.now().minusHours(1));
        return task;
    }

    private ImportTaskFileStore.StoredInput stored(String filename, String path) {
        return new ImportTaskFileStore.StoredInput(filename, path, Path.of("/tmp").resolve(filename));
    }

    private MockMultipartFile upload(String filename) {
        return new MockMultipartFile("file", filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
    }

    private void authenticate(String username) {
        CurrentUser user = new CurrentUser(7L, username, username, Set.of(Role.MEETING_ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", List.of())
        );
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Meeting meeting() {
        Meeting value = new Meeting();
        value.setId(1L);
        return value;
    }
}
