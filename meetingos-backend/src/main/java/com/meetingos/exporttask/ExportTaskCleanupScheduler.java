package com.meetingos.exporttask;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExportTaskCleanupScheduler {

    private final ExportTaskService exportTaskService;

    public ExportTaskCleanupScheduler(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @Scheduled(
            fixedDelayString = "${meetingos.export.cleanup-interval-ms:3600000}",
            initialDelayString = "${meetingos.export.cleanup-initial-delay-ms:60000}"
    )
    public void cleanup() {
        exportTaskService.cleanupExpiredTasks();
    }
}
