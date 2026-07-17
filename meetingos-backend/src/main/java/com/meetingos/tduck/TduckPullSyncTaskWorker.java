package com.meetingos.tduck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TduckPullSyncTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TduckPullSyncTaskWorker.class);
    private static final int PROGRESS_UPDATE_INTERVAL = 10;

    private final TduckPullSyncTaskStateService stateService;
    private final TduckService tduckService;
    private final Set<Long> reservedTaskIds = ConcurrentHashMap.newKeySet();

    public TduckPullSyncTaskWorker(TduckPullSyncTaskStateService stateService,
                                   TduckService tduckService) {
        this.stateService = stateService;
        this.tduckService = tduckService;
    }

    /**
     * 避免恢复器在任务仍位于本进程队列时重复派发。多实例间仍由数据库行锁和租约仲裁。
     */
    public boolean tryReserve(Long taskId) {
        return taskId != null && reservedTaskIds.add(taskId);
    }

    public void releaseReservation(Long taskId) {
        if (taskId != null) {
            reservedTaskIds.remove(taskId);
        }
    }

    @Async("tduckPullSyncExecutor")
    public void process(Long taskId) {
        try {
            TduckPullSyncTaskWorkItem work = stateService.claim(taskId);
            if (work == null) {
                return;
            }
            try {
                TduckBatchPullSyncResponse result = tduckService.batchPullSync(
                        work.formKey(),
                        work.limit(),
                        progress -> {
                            if (progress.processed() == 0
                                    || progress.processed() == progress.total()
                                    || progress.processed() % PROGRESS_UPDATE_INTERVAL == 0) {
                                stateService.updateProgress(work.taskId(), work.runToken(), progress);
                            }
                        }
                );
                stateService.completed(work.taskId(), work.runToken(), result);
            } catch (RuntimeException ex) {
                log.error("TDuck pull sync task failed, taskId={}, meetingId={}", work.taskId(), work.meetingId(), ex);
                stateService.failed(work.taskId(), work.runToken(), ex);
            }
        } finally {
            releaseReservation(taskId);
        }
    }
}
