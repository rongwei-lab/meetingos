package com.meetingos.tduck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TduckPullSyncTaskWorkerTest {

    private TduckPullSyncTaskStateService stateService;
    private TduckService tduckService;
    private TduckPullSyncTaskWorker worker;

    @BeforeEach
    void setUp() {
        stateService = mock(TduckPullSyncTaskStateService.class);
        tduckService = mock(TduckService.class);
        worker = new TduckPullSyncTaskWorker(stateService, tduckService);
    }

    @Test
    void claimMissReleasesReservation() {
        assertThat(worker.tryReserve(71L)).isTrue();
        when(stateService.claim(71L)).thenReturn(null);

        worker.process(71L);

        assertThat(worker.tryReserve(71L)).isTrue();
        verify(tduckService, never()).batchPullSync(any(), anyInt(), any());
    }

    @Test
    void completedTaskReleasesReservation() {
        TduckPullSyncTaskWorkItem work = new TduckPullSyncTaskWorkItem(72L, 7L, "form-7", 250, "run-72");
        TduckBatchPullSyncResponse response = new TduckBatchPullSyncResponse(
                1, 1, 0, 0, List.of(), "completed"
        );
        assertThat(worker.tryReserve(72L)).isTrue();
        when(stateService.claim(72L)).thenReturn(work);
        when(tduckService.batchPullSync(eq("form-7"), eq(250), any())).thenReturn(response);

        worker.process(72L);

        verify(stateService).completed(72L, "run-72", response);
        assertThat(worker.tryReserve(72L)).isTrue();
    }

    @Test
    void failedTaskReleasesReservation() {
        TduckPullSyncTaskWorkItem work = new TduckPullSyncTaskWorkItem(73L, 7L, "form-7", 250, "run-73");
        IllegalStateException failure = new IllegalStateException("remote failure");
        assertThat(worker.tryReserve(73L)).isTrue();
        when(stateService.claim(73L)).thenReturn(work);
        when(tduckService.batchPullSync(eq("form-7"), eq(250), any())).thenThrow(failure);

        worker.process(73L);

        verify(stateService).failed(73L, "run-73", failure);
        assertThat(worker.tryReserve(73L)).isTrue();
    }
}
