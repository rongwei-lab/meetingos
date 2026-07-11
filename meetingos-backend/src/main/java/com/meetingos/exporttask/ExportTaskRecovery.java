package com.meetingos.exporttask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExportTaskRecovery {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskRecovery.class);

    private final ExportTaskService exportTaskService;
    private final ExportTaskWorker exportTaskWorker;

    public ExportTaskRecovery(ExportTaskService exportTaskService, ExportTaskWorker exportTaskWorker) {
        this.exportTaskService = exportTaskService;
        this.exportTaskWorker = exportTaskWorker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedTasks() {
        dispatchRecoverableTasks();
    }

    @Scheduled(
            fixedDelayString = "${meetingos.export.recovery-delay-ms:60000}",
            initialDelayString = "${meetingos.export.recovery-initial-delay-ms:60000}"
    )
    public void resumeStaleTasks() {
        dispatchRecoverableTasks();
    }

    private void dispatchRecoverableTasks() {
        final List<Long> taskIds;
        try {
            taskIds = exportTaskService.recoverableTaskIds();
        } catch (RuntimeException ex) {
            // 调度扫描失败时保留数据库状态，下一个周期继续尝试，避免调度线程因瞬时数据库故障中断。
            log.error("Failed to scan recoverable export tasks; will retry on the next cycle", ex);
            return;
        }
        for (Long taskId : taskIds) {
            try {
                exportTaskWorker.resume(taskId);
            } catch (RuntimeException ex) {
                // 执行器若在关闭等边界状态抛出拒绝异常，任务仍保持 PENDING，可由后续周期恢复。
                log.warn("Failed to dispatch recoverable export task {}; will retry later", taskId, ex);
            }
        }
    }
}
