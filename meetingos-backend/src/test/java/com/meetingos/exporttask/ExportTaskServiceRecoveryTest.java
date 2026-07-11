package com.meetingos.exporttask;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.checkin.CheckinRecordRepository;
import com.meetingos.dinner.DinnerService;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.room.RoomService;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.statistics.StatisticsService;
import com.meetingos.tduck.TduckFieldMappingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportTaskServiceRecoveryTest {

    @TempDir
    Path uploadRoot;

    @Test
    void recoveryOnlyLeasesOldPendingAndStillStaleRunningTasks() {
        ExportTaskRepository repository = mock(ExportTaskRepository.class);
        LocalDateTime oldUpdate = LocalDateTime.now().minusHours(3);
        ExportTask staleRunning = task(1L, ExportTaskStatus.RUNNING, oldUpdate);
        staleRunning.setProgress(70);
        ExportTask stalePending = task(2L, ExportTaskStatus.PENDING, oldUpdate);
        ExportTask refreshedRunning = task(3L, ExportTaskStatus.RUNNING, LocalDateTime.now());

        when(repository.findRecoverableIds(
                eq(ExportTaskStatus.RUNNING), any(LocalDateTime.class), any(Pageable.class)
        )).thenReturn(List.of(1L, 3L));
        when(repository.findRecoverableIds(
                eq(ExportTaskStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)
        )).thenReturn(List.of(2L));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(staleRunning));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(stalePending));
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(refreshedRunning));

        List<Long> recovered = service(repository).recoverableTaskIds();

        assertThat(recovered).containsExactly(1L, 2L);
        assertThat(staleRunning.getStatus()).isEqualTo(ExportTaskStatus.PENDING);
        assertThat(staleRunning.getProgress()).isZero();
        assertThat(staleRunning.getMessage()).contains("执行超时");
        assertThat(staleRunning.getUpdatedAt()).isAfter(oldUpdate);
        assertThat(stalePending.getMessage()).contains("重新排队");
        assertThat(stalePending.getUpdatedAt()).isAfter(oldUpdate);
        assertThat(refreshedRunning.getStatus()).isEqualTo(ExportTaskStatus.RUNNING);
    }

    @Test
    void claimDatabaseFailureIsContainedForPeriodicRecovery() {
        ExportTaskRepository repository = mock(ExportTaskRepository.class);
        when(repository.findByIdForUpdate(99L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatCode(() -> service(repository).processTask(99L))
                .doesNotThrowAnyException();

        verify(repository).findByIdForUpdate(99L);
    }

    private ExportTask task(Long id, ExportTaskStatus status, LocalDateTime updatedAt) {
        ExportTask task = new ExportTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(updatedAt);
        return task;
    }

    private ExportTaskService service(ExportTaskRepository repository) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> mock(TransactionStatus.class));
        return new ExportTaskService(
                mock(AttendeeService.class),
                mock(AttendeeRepository.class),
                mock(RoomService.class),
                mock(DinnerService.class),
                mock(SeatRepository.class),
                mock(SeatAssignmentRepository.class),
                mock(CheckinRecordRepository.class),
                mock(MeetingRepository.class),
                mock(MeetingAccessService.class),
                repository,
                mock(SystemSettingsService.class),
                mock(StatisticsService.class),
                mock(TduckFieldMappingRepository.class),
                mock(EntityManager.class),
                mock(ApplicationEventPublisher.class),
                transactionManager,
                uploadRoot.toString(),
                7,
                120,
                2
        );
    }
}
