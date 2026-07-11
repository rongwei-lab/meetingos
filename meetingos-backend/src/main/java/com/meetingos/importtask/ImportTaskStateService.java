package com.meetingos.importtask;

import com.meetingos.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ImportTaskStateService {

    private static final Set<ImportTaskStatus> ACTIVE_STATUSES = Set.of(
            ImportTaskStatus.PENDING,
            ImportTaskStatus.PREFLIGHTING,
            ImportTaskStatus.RUNNING
    );
    private static final Set<ImportTaskStatus> EXPIRABLE_STATUSES = Set.of(
            ImportTaskStatus.PREVIEW_READY,
            ImportTaskStatus.COMPLETED,
            ImportTaskStatus.FAILED,
            ImportTaskStatus.EXPIRED
    );

    private final ImportTaskRepository repository;
    private final ImportTaskFileStore fileStore;
    private final long retentionHours;

    public ImportTaskStateService(ImportTaskRepository repository,
                                  ImportTaskFileStore fileStore,
                                  @Value("${meetingos.import-task.retention-hours:168}") long retentionHours) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.retentionHours = Math.max(1, retentionHours);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportTaskWorkItem claimCreated(Long taskId) {
        ImportTask task = repository.findByIdForUpdate(taskId)
                .orElseThrow(() -> BusinessException.notFound("导入任务不存在"));
        if (task.getStatus() != ImportTaskStatus.PENDING || task.getStartedAt() != null) {
            return null;
        }
        task.setStatus(task.isPreflight() ? ImportTaskStatus.PREFLIGHTING : ImportTaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setProgress(0);
        task.setMessage(task.isPreflight() ? "正在检查文件格式" : "正在导入数据");
        return workItem(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportTaskWorkItem claimCommitted(Long taskId) {
        ImportTask task = repository.findByIdForUpdate(taskId)
                .orElseThrow(() -> BusinessException.notFound("导入任务不存在"));
        if (task.getStatus() != ImportTaskStatus.RUNNING || task.getStartedAt() != null) {
            return null;
        }
        task.setStartedAt(LocalDateTime.now());
        task.setProgress(0);
        task.setProcessedRows(0);
        task.setImportedRows(0);
        task.setFailedRows(0);
        task.setMessage("正在导入已完成格式检查的文件");
        return workItem(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void previewReady(Long taskId, int totalRows) {
        ImportTask task = repository.findByIdForUpdate(taskId)
                .orElseThrow(() -> BusinessException.notFound("导入任务不存在"));
        if (task.getStatus() != ImportTaskStatus.PREFLIGHTING) {
            return;
        }
        task.setStatus(ImportTaskStatus.PREVIEW_READY);
        task.setTotalRows(totalRows);
        task.setProcessedRows(totalRows);
        task.setProgress(100);
        task.setMessage("文件格式检查通过，共 " + totalRows + " 行，可确认导入");
        task.setExpiresAt(expiresAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportTaskRecoveryLease leaseUnstartedTask(Long taskId,
                                                      LocalDateTime cutoff,
                                                      LocalDateTime leasedAt) {
        ImportTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null
                || task.getStartedAt() != null
                || (task.getStatus() != ImportTaskStatus.PENDING && task.getStatus() != ImportTaskStatus.RUNNING)
                || task.getUpdatedAt() == null
                || !task.getUpdatedAt().isBefore(cutoff)) {
            return null;
        }
        boolean committed = task.getStatus() == ImportTaskStatus.RUNNING;
        if (committed) {
            task.setMessage("等待导入已完成格式检查的文件（任务已重新排队）");
        } else {
            task.setMessage(task.isPreflight()
                    ? "等待文件格式检查（任务已重新排队）"
                    : "等待导入（任务已重新排队）");
        }
        // 即使消息与上一轮相同也必须刷新租约，防止多实例调度器重复提交同一个任务。
        task.setUpdatedAt(leasedAt);
        return new ImportTaskRecoveryLease(task.getId(), committed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long taskId, int totalRows, int processedRows, int importedRows, int failedRows) {
        ImportTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != ImportTaskStatus.RUNNING) {
            return;
        }
        task.setTotalRows(totalRows);
        task.setProcessedRows(processedRows);
        task.setImportedRows(importedRows);
        task.setFailedRows(failedRows);
        task.setProgress(totalRows == 0 ? 100 : Math.min(99, processedRows * 100 / totalRows));
        task.setMessage("正在导入：已处理 " + processedRows + "/" + totalRows + " 行");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completed(Long taskId,
                          int totalRows,
                          int importedRows,
                          int failedRows,
                          String message,
                          String failureFilePath) {
        ImportTask task = repository.findByIdForUpdate(taskId)
                .orElseThrow(() -> BusinessException.notFound("导入任务不存在"));
        if (task.getStatus() != ImportTaskStatus.RUNNING) {
            return;
        }
        task.setStatus(ImportTaskStatus.COMPLETED);
        task.setTotalRows(totalRows);
        task.setProcessedRows(totalRows);
        task.setImportedRows(importedRows);
        task.setFailedRows(failedRows);
        task.setProgress(100);
        task.setMessage(truncate(message));
        task.setFailureFilePath(failureFilePath);
        task.setCompletedAt(LocalDateTime.now());
        task.setExpiresAt(expiresAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long taskId, String message) {
        ImportTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() == ImportTaskStatus.EXPIRED || task.getStatus() == ImportTaskStatus.COMPLETED) {
            return;
        }
        task.setStatus(ImportTaskStatus.FAILED);
        task.setProgress(100);
        task.setMessage(truncate(message));
        task.setCompletedAt(LocalDateTime.now());
        task.setExpiresAt(expiresAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failIfStuck(Long taskId, LocalDateTime cutoff) {
        ImportTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null
                || !ACTIVE_STATUSES.contains(task.getStatus())
                || task.getUpdatedAt() == null
                || !task.getUpdatedAt().isBefore(cutoff)) {
            return;
        }
        task.setStatus(ImportTaskStatus.FAILED);
        task.setProgress(100);
        task.setMessage("导入任务因服务中断或执行超时而终止，请核对已导入计数后重新提交");
        task.setCompletedAt(LocalDateTime.now());
        task.setExpiresAt(expiresAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireFilesIfDue(Long taskId, LocalDateTime now) {
        ImportTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null
                || !EXPIRABLE_STATUSES.contains(task.getStatus())
                || task.getExpiresAt() == null
                || !task.getExpiresAt().isBefore(now)) {
            return;
        }
        boolean inputDeleted = fileStore.delete(task.getInputFilePath());
        boolean failureDeleted = fileStore.delete(task.getFailureFilePath());
        if (!inputDeleted || !failureDeleted) {
            return;
        }
        task.setStatus(ImportTaskStatus.EXPIRED);
        task.setInputFilePath(null);
        task.setFailureFilePath(null);
        task.setMessage("导入任务文件已过期并清理");
    }

    private ImportTaskWorkItem workItem(ImportTask task) {
        return new ImportTaskWorkItem(
                task.getId(),
                task.getMeeting().getId(),
                ImportTaskType.from(task.getImportType()),
                task.getInputFilePath(),
                task.getRequestedBy(),
                task.isPreflight()
        );
    }

    private LocalDateTime expiresAt() {
        return LocalDateTime.now().plusHours(retentionHours);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 500) {
            return message;
        }
        return message.substring(0, 497) + "...";
    }
}
