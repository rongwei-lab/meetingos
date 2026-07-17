package com.meetingos.tduck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.meeting.Meeting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TduckPullSyncTaskStateServiceTest {

    private TduckPullSyncTaskRepository repository;
    private TduckPullSyncTaskStateService service;
    private TduckPullSyncTask task;

    @BeforeEach
    void setUp() {
        repository = mock(TduckPullSyncTaskRepository.class);
        service = new TduckPullSyncTaskStateService(repository, new ObjectMapper(), 300);
        Meeting meeting = new Meeting();
        meeting.setId(9L);
        task = new TduckPullSyncTask();
        task.setId(91L);
        task.setMeeting(meeting);
        task.setFormKey("form-9");
        task.setRequestedLimit(100);
        when(repository.findByIdForUpdate(91L)).thenReturn(Optional.of(task));
    }

    @Test
    void expiredRunningLeaseIsClaimedWithNewTokenAndResetProgress() {
        task.setStatus(TduckPullSyncTaskStatus.RUNNING);
        task.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        task.setRunToken("old-token");
        task.setProcessedRows(40);
        task.setSuccessRows(39);

        TduckPullSyncTaskWorkItem work = service.claim(91L);

        assertThat(work).isNotNull();
        assertThat(work.runToken()).isNotBlank().isNotEqualTo("old-token");
        assertThat(task.getStatus()).isEqualTo(TduckPullSyncTaskStatus.RUNNING);
        assertThat(task.getProcessedRows()).isZero();
        assertThat(task.getSuccessRows()).isZero();
        assertThat(task.getLeaseUntil()).isAfter(LocalDateTime.now());
        assertThat(task.getMessage()).contains("重新开始");
    }

    @Test
    void staleWorkerCannotOverwriteProgressAfterLeaseTakeover() {
        task.setStatus(TduckPullSyncTaskStatus.RUNNING);
        task.setRunToken("new-token");
        task.setProcessedRows(10);

        service.updateProgress(91L, "old-token", new TduckBatchPullSyncProgress(
                100, 50, 49, 0, 1, List.of()
        ));

        assertThat(task.getProcessedRows()).isEqualTo(10);
    }

    @Test
    void completionPersistsCountsAndFailureSamples() {
        task.setStatus(TduckPullSyncTaskStatus.RUNNING);
        task.setRunToken("run-token");
        TduckBatchPullSyncResponse result = new TduckBatchPullSyncResponse(
                3, 1, 1, 1,
                List.of(new TduckBatchPullSyncFailure("data-3", "字段错误")),
                "已扫描 3 条"
        );

        service.completed(91L, "run-token", result);
        TduckBatchPullSyncResponse response = service.toResponse(task);

        assertThat(task.getStatus()).isEqualTo(TduckPullSyncTaskStatus.COMPLETED);
        assertThat(task.getRunToken()).isNull();
        assertThat(response.scanned()).isEqualTo(3);
        assertThat(response.success()).isEqualTo(1);
        assertThat(response.duplicateIgnored()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.failures()).containsExactly(new TduckBatchPullSyncFailure("data-3", "字段错误"));
    }
}
