package com.meetingos.exporttask;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ExportTaskWorker {

    private final ExportTaskService exportTaskService;

    public ExportTaskWorker(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @Async("fileTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(ExportTaskRequestedEvent event) {
        exportTaskService.processTask(event.taskId());
    }

    @Async("fileTaskExecutor")
    public void resume(Long taskId) {
        exportTaskService.processTask(taskId);
    }
}
