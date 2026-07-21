package com.meetingos.tduck;

import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TduckPullSyncTaskServiceTest {

    private MeetingRepository meetingRepository;
    private TduckFormBindingRepository bindingRepository;
    private TduckPullSyncTaskRepository taskRepository;
    private TduckPullSyncTaskStateService stateService;
    private TduckPullSyncTaskWorker worker;
    private TduckPullSyncTaskService service;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        bindingRepository = mock(TduckFormBindingRepository.class);
        taskRepository = mock(TduckPullSyncTaskRepository.class);
        stateService = mock(TduckPullSyncTaskStateService.class);
        worker = mock(TduckPullSyncTaskWorker.class);
        service = new TduckPullSyncTaskService(
                meetingRepository, bindingRepository, taskRepository, stateService, worker
        );
        meeting = new Meeting();
        meeting.setId(7L);
        when(meetingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(meeting));
    }

    @Test
    void repeatedRequestReusesActiveTaskForSameMeeting() {
        TduckPullSyncTask active = task(81L, TduckPullSyncTaskStatus.RUNNING);
        TduckBatchPullSyncResponse response = response(81L, TduckPullSyncTaskStatus.RUNNING);
        when(taskRepository.findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(Optional.of(active));
        when(stateService.toResponse(active)).thenReturn(response);

        TduckBatchPullSyncResponse actual = service.create(new TduckBatchPullSyncRequest(7L, 500));

        assertThat(actual).isSameAs(response);
        verify(bindingRepository, never()).findByMeetingId(anyLong());
        verify(taskRepository, never()).saveAndFlush(any());
        verify(worker, never()).process(anyLong());
    }

    @Test
    void explicitIdempotencyKeyReusesCompletedTask() {
        TduckPullSyncTask completed = task(82L, TduckPullSyncTaskStatus.COMPLETED);
        TduckBatchPullSyncResponse response = response(82L, TduckPullSyncTaskStatus.COMPLETED);
        when(taskRepository.findByMeetingIdAndIdempotencyKey(7L, "request-1"))
                .thenReturn(Optional.of(completed));
        when(stateService.toResponse(completed)).thenReturn(response);

        TduckBatchPullSyncResponse actual = service.create(
                new TduckBatchPullSyncRequest(7L, 500, " request-1 ")
        );

        assertThat(actual).isSameAs(response);
        verify(taskRepository, never()).findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(anyLong(), any());
        verify(worker, never()).process(anyLong());
    }

    @Test
    void firstExplicitKeyIsAttachedToAnUnkeyedActiveTask() {
        TduckPullSyncTask active = task(84L, TduckPullSyncTaskStatus.RUNNING);
        TduckBatchPullSyncResponse response = response(84L, TduckPullSyncTaskStatus.RUNNING);
        when(taskRepository.findByMeetingIdAndIdempotencyKey(7L, "request-b")).thenReturn(Optional.empty());
        when(taskRepository.findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(Optional.of(active));
        when(stateService.toResponse(active)).thenReturn(response);

        TduckBatchPullSyncResponse actual = service.create(
                new TduckBatchPullSyncRequest(7L, 500, "request-b")
        );

        assertThat(actual).isSameAs(response);
        assertThat(active.getIdempotencyKey()).isEqualTo("request-b");
        verify(taskRepository).saveAndFlush(active);
    }

    @Test
    void differentExplicitKeyDoesNotSilentlyAliasAnActiveTask() {
        TduckPullSyncTask active = task(85L, TduckPullSyncTaskStatus.RUNNING);
        active.setIdempotencyKey("request-a");
        when(taskRepository.findByMeetingIdAndIdempotencyKey(7L, "request-b")).thenReturn(Optional.empty());
        when(taskRepository.findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.create(new TduckBatchPullSyncRequest(7L, 500, "request-b")))
                .isInstanceOf(com.meetingos.common.BusinessException.class)
                .hasMessageContaining("已有其他同步任务");

        verify(stateService, never()).toResponse(any());
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void newTaskSnapshotsBindingAndDispatchesAfterPersistence() {
        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-7");
        TduckBatchPullSyncResponse response = response(83L, TduckPullSyncTaskStatus.PENDING);
        when(bindingRepository.findByMeetingId(7L)).thenReturn(Optional.of(binding));
        when(taskRepository.findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TduckPullSyncTask task = invocation.getArgument(0);
            task.setId(83L);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            return task;
        });
        when(stateService.toResponse(any())).thenReturn(response);
        when(worker.tryReserve(83L)).thenReturn(true);

        TduckBatchPullSyncResponse actual = service.create(new TduckBatchPullSyncRequest(7L, 250));

        assertThat(actual).isSameAs(response);
        verify(taskRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(task ->
                task.getMeeting() == meeting
                        && "form-7".equals(task.getFormKey())
                        && task.getRequestedLimit() == 250
                        && task.getStatus() == TduckPullSyncTaskStatus.PENDING
        ));
        verify(worker).process(83L);
    }

    @Test
    void defaultTaskUsesUnlimitedScanMarker() {
        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-7");
        when(bindingRepository.findByMeetingId(7L)).thenReturn(Optional.of(binding));
        when(taskRepository.findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TduckPullSyncTask task = invocation.getArgument(0);
            task.setId(84L);
            return task;
        });
        when(stateService.toResponse(any())).thenReturn(response(84L, TduckPullSyncTaskStatus.PENDING));
        when(worker.tryReserve(84L)).thenReturn(true);

        service.create(new TduckBatchPullSyncRequest(7L, null));

        verify(taskRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(task -> task.getRequestedLimit() == 0));
        verify(worker).process(84L);
    }

    @Test
    void recovererDoesNotQueueSameTaskTwiceWhileItIsReserved() {
        when(taskRepository.findRecoverableTaskIds(any(), any(), any(), any()))
                .thenReturn(List.of(91L));
        when(worker.tryReserve(91L)).thenReturn(true, false);

        service.recoverTasks();
        service.recoverTasks();

        verify(worker, times(2)).tryReserve(91L);
        verify(worker).process(91L);
    }

    @Test
    void rejectedDispatchReleasesReservationForLaterRecovery() {
        when(taskRepository.findRecoverableTaskIds(any(), any(), any(), any()))
                .thenReturn(List.of(92L));
        when(worker.tryReserve(92L)).thenReturn(true);
        doThrow(new IllegalStateException("executor saturated")).when(worker).process(92L);

        service.recoverTasks();

        verify(worker).releaseReservation(92L);
    }

    private TduckPullSyncTask task(Long id, TduckPullSyncTaskStatus status) {
        TduckPullSyncTask task = new TduckPullSyncTask();
        task.setId(id);
        task.setMeeting(meeting);
        task.setFormKey("form-7");
        task.setRequestedLimit(1_000);
        task.setStatus(status);
        return task;
    }

    private TduckBatchPullSyncResponse response(Long id, TduckPullSyncTaskStatus status) {
        return new TduckBatchPullSyncResponse(
                id, status, status == TduckPullSyncTaskStatus.COMPLETED ? 100 : 0,
                0, 0, 0, 0, 0, List.of(), "test", null, null, null
        );
    }
}
