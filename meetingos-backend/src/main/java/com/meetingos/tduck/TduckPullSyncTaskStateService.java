package com.meetingos.tduck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TduckPullSyncTaskStateService {

    private static final TypeReference<List<TduckBatchPullSyncFailure>> FAILURE_LIST_TYPE = new TypeReference<>() { };

    private final TduckPullSyncTaskRepository repository;
    private final ObjectMapper objectMapper;
    private final long leaseSeconds;

    public TduckPullSyncTaskStateService(TduckPullSyncTaskRepository repository,
                                         ObjectMapper objectMapper,
                                         @Value("${meetingos.tduck.pull-sync.lease-seconds:900}") long leaseSeconds) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.leaseSeconds = Math.max(60, leaseSeconds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TduckPullSyncTaskWorkItem claim(Long taskId) {
        TduckPullSyncTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean pending = task.getStatus() == TduckPullSyncTaskStatus.PENDING;
        boolean expiredLease = task.getStatus() == TduckPullSyncTaskStatus.RUNNING
                && (task.getLeaseUntil() == null || task.getLeaseUntil().isBefore(now));
        if (!pending && !expiredLease) {
            return null;
        }

        String runToken = UUID.randomUUID().toString();
        task.setStatus(TduckPullSyncTaskStatus.RUNNING);
        task.setRunToken(runToken);
        task.setLeaseUntil(now.plusSeconds(leaseSeconds));
        task.setStartedAt(now);
        task.setCompletedAt(null);
        task.setTotalRows(0);
        task.setProcessedRows(0);
        task.setSuccessRows(0);
        task.setDuplicateRows(0);
        task.setFailedRows(0);
        task.setProgress(0);
        task.setFailureDetails(null);
        task.setMessage(expiredLease ? "任务中断后已重新开始同步" : "正在读取 TDuck 报名列表");
        return new TduckPullSyncTaskWorkItem(
                task.getId(),
                task.getMeeting().getId(),
                task.getFormKey(),
                task.getRequestedLimit(),
                runToken
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long taskId, String runToken, TduckBatchPullSyncProgress progress) {
        TduckPullSyncTask task = ownedRunningTask(taskId, runToken);
        if (task == null) {
            return;
        }
        task.setTotalRows(progress.total());
        task.setProcessedRows(progress.processed());
        task.setSuccessRows(progress.success());
        task.setDuplicateRows(progress.duplicateIgnored());
        task.setFailedRows(progress.failed());
        task.setProgress(progress.total() == 0 ? 0 : Math.min(99, progress.processed() * 100 / progress.total()));
        task.setFailureDetails(writeFailures(progress.failures()));
        task.setMessage("正在同步：已处理 " + progress.processed() + "/" + progress.total() + " 条");
        task.setLeaseUntil(LocalDateTime.now().plusSeconds(leaseSeconds));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completed(Long taskId, String runToken, TduckBatchPullSyncResponse result) {
        TduckPullSyncTask task = ownedRunningTask(taskId, runToken);
        if (task == null) {
            return;
        }
        task.setStatus(TduckPullSyncTaskStatus.COMPLETED);
        task.setTotalRows(result.total());
        task.setProcessedRows(result.scanned());
        task.setSuccessRows(result.success());
        task.setDuplicateRows(result.duplicateIgnored());
        task.setFailedRows(result.failed());
        task.setProgress(100);
        task.setFailureDetails(writeFailures(result.failures()));
        task.setMessage(truncate(result.message()));
        task.setCompletedAt(LocalDateTime.now());
        task.setLeaseUntil(null);
        task.setRunToken(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long taskId, String runToken, RuntimeException exception) {
        TduckPullSyncTask task = ownedRunningTask(taskId, runToken);
        if (task == null) {
            return;
        }
        task.setStatus(TduckPullSyncTaskStatus.FAILED);
        task.setProgress(100);
        String message = exception instanceof BusinessException && exception.getMessage() != null
                ? exception.getMessage()
                : "TDuck 批量同步任务执行失败，请查看服务端日志";
        task.setMessage(truncate(message));
        task.setCompletedAt(LocalDateTime.now());
        task.setLeaseUntil(null);
        task.setRunToken(null);
    }

    public TduckBatchPullSyncResponse toResponse(TduckPullSyncTask task) {
        return new TduckBatchPullSyncResponse(
                task.getId(),
                task.getStatus(),
                task.getProgress(),
                task.getTotalRows(),
                task.getProcessedRows(),
                task.getSuccessRows(),
                task.getDuplicateRows(),
                task.getFailedRows(),
                readFailures(task.getFailureDetails()),
                task.getMessage(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreatedAt()
        );
    }

    private TduckPullSyncTask ownedRunningTask(Long taskId, String runToken) {
        TduckPullSyncTask task = repository.findByIdForUpdate(taskId).orElse(null);
        if (task == null
                || task.getStatus() != TduckPullSyncTaskStatus.RUNNING
                || runToken == null
                || !runToken.equals(task.getRunToken())) {
            return null;
        }
        return task;
    }

    private String writeFailures(List<TduckBatchPullSyncFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(failures);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<TduckBatchPullSyncFailure> readFailures(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, FAILURE_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 497) + "...";
    }
}
