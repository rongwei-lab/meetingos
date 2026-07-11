package com.meetingos.importtask;

import com.meetingos.common.BusinessException;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.TableFileReader;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ImportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ImportTaskService.class);

    private static final Set<ImportTaskStatus> EXPIRABLE_STATUSES = Set.of(
            ImportTaskStatus.PREVIEW_READY,
            ImportTaskStatus.COMPLETED,
            ImportTaskStatus.FAILED,
            ImportTaskStatus.EXPIRED
    );
    private static final Set<ImportTaskStatus> ACTIVE_CREATE_STATUSES = Set.of(
            ImportTaskStatus.PENDING,
            ImportTaskStatus.PREFLIGHTING,
            ImportTaskStatus.RUNNING
    );
    private static final Set<ImportTaskStatus> RETRYABLE_STATUSES = Set.of(
            ImportTaskStatus.FAILED,
            ImportTaskStatus.EXPIRED
    );
    private static final Set<ImportTaskStatus> RECOVERABLE_STATUSES = Set.of(
            ImportTaskStatus.PENDING,
            ImportTaskStatus.RUNNING
    );
    private static final List<String> ROOM_TYPES = List.of(
            ImportTaskType.ROOMS.value(),
            ImportTaskType.ROOM_ASSIGNMENTS.value()
    );

    private final ImportTaskRepository repository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccessService meetingAccessService;
    private final ImportTaskFileStore fileStore;
    private final ImportTaskWorker worker;
    private final ImportTaskStateService stateService;
    private final long stuckHours;
    private final long recoveryLeaseSeconds;

    public ImportTaskService(ImportTaskRepository repository,
                             MeetingRepository meetingRepository,
                             MeetingAccessService meetingAccessService,
                             ImportTaskFileStore fileStore,
                             ImportTaskWorker worker,
                             ImportTaskStateService stateService,
                             @org.springframework.beans.factory.annotation.Value("${meetingos.import-task.stuck-hours:2}") long stuckHours,
                             @org.springframework.beans.factory.annotation.Value("${meetingos.import-task.recovery-lease-seconds:60}") long recoveryLeaseSeconds) {
        this.repository = repository;
        this.meetingRepository = meetingRepository;
        this.meetingAccessService = meetingAccessService;
        this.fileStore = fileStore;
        this.worker = worker;
        this.stateService = stateService;
        this.stuckHours = Math.max(1, stuckHours);
        this.recoveryLeaseSeconds = Math.max(10, recoveryLeaseSeconds);
    }

    @Transactional
    public ImportTaskResponse create(Long meetingId,
                                     String typeValue,
                                     boolean preflight,
                                     String idempotencyKey,
                                     MultipartFile file) {
        ImportTaskType type = ImportTaskType.from(typeValue);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        ImportTask existing = repository.findByMeetingIdAndImportTypeAndIdempotencyKey(
                meetingId,
                type.value(),
                normalizedKey
        ).orElse(null);
        if (existing != null && !RETRYABLE_STATUSES.contains(existing.getStatus())) {
            return toResponse(existing);
        }
        assertNoActiveTask(meetingId, type);
        validateUpload(file);

        String previousInputPath = existing == null ? null : existing.getInputFilePath();
        String previousFailurePath = existing == null ? null : existing.getFailureFilePath();
        ImportTaskFileStore.StoredInput stored = fileStore.saveInput(file);
        ImportTask task = existing == null ? new ImportTask() : existing;
        prepareAttempt(task, meeting, type, normalizedKey, preflight, stored);
        try {
            ImportTask saved = repository.saveAndFlush(task);
            ImportTaskResponse response = toResponse(saved);
            registerFileAttemptLifecycle(
                    stored.storedPath(),
                    previousInputPath,
                    previousFailurePath,
                    () -> dispatchCreated(saved.getId())
            );
            return response;
        } catch (RuntimeException ex) {
            deleteNewFileAfterRollback(stored.storedPath(), previousInputPath, previousFailurePath);
            throw ex;
        }
    }

    private void assertNoActiveTask(Long meetingId, ImportTaskType type) {
        repository.findFirstByMeetingIdAndImportTypeAndStatusInOrderByCreatedAtDesc(
                meetingId,
                type.value(),
                ACTIVE_CREATE_STATUSES
        ).ifPresent(active -> {
            throw BusinessException.conflict("当前会议已有同类型导入任务正在处理，请等待完成后重试");
        });
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传 .xlsx 模板文件");
        }
        if (file.getSize() > TableFileReader.MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("导入文件不能超过 10MB");
        }
    }

    private void prepareAttempt(ImportTask task,
                                Meeting meeting,
                                ImportTaskType type,
                                String idempotencyKey,
                                boolean preflight,
                                ImportTaskFileStore.StoredInput stored) {
        task.setMeeting(meeting);
        task.setImportType(type.value());
        task.setStatus(ImportTaskStatus.PENDING);
        task.setOriginalFilename(stored.originalFilename());
        task.setInputFilePath(stored.storedPath());
        task.setFailureFilePath(null);
        task.setIdempotencyKey(idempotencyKey);
        task.setPreflight(preflight);
        task.setTotalRows(0);
        task.setProcessedRows(0);
        task.setImportedRows(0);
        task.setFailedRows(0);
        task.setProgress(0);
        task.setRequestedBy(SecurityUtils.currentUsernameOrSystem());
        task.setStartedAt(null);
        task.setCompletedAt(null);
        task.setExpiresAt(null);
        task.setMessage(preflight ? "等待文件格式检查" : "等待导入");
    }

    @Transactional(readOnly = true)
    public ImportTaskResponse detail(Long taskId, Long meetingId) {
        return toResponse(findOwned(taskId, meetingId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportTaskResponse> list(Long meetingId, int page, int size) {
        var pageable = PageRequests.of(page, size);
        var tasks = meetingAccessService.canManage(meetingId)
                ? repository.findByMeetingIdOrderByCreatedAtDesc(meetingId, pageable)
                : repository.findByMeetingIdAndImportTypeInOrderByCreatedAtDesc(meetingId, ROOM_TYPES, pageable);
        return PageResponse.from(tasks.map(this::toResponse));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ImportTaskResponse commit(Long taskId, Long meetingId) {
        meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        ImportTask task = repository.findByIdAndMeetingIdForUpdate(taskId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("导入任务不存在"));
        if (task.getStatus() == ImportTaskStatus.RUNNING || task.getStatus() == ImportTaskStatus.COMPLETED) {
            return toResponse(task);
        }
        if (task.getStatus() != ImportTaskStatus.PREVIEW_READY) {
            throw BusinessException.badRequest("只有文件格式检查通过的任务可以确认导入");
        }
        if (task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now())) {
            task.setStatus(ImportTaskStatus.EXPIRED);
            throw BusinessException.badRequest("导入任务已过期，请重新上传");
        }
        if (!fileStore.exists(task.getInputFilePath())) {
            task.setStatus(ImportTaskStatus.EXPIRED);
            throw BusinessException.badRequest("导入源文件不存在或已清理");
        }
        if (repository.existsByMeetingIdAndImportTypeAndStatusInAndIdNot(
                meetingId,
                task.getImportType(),
                ACTIVE_CREATE_STATUSES,
                task.getId()
        )) {
            throw BusinessException.conflict("当前会议已有同类型导入任务正在处理，请等待完成后重试");
        }
        task.setStatus(ImportTaskStatus.RUNNING);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        task.setProgress(0);
        task.setMessage("等待导入已完成格式检查的文件");
        dispatchAfterCommit(() -> dispatchCommitted(task.getId()));
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public ImportFailureFile failureFile(Long taskId, Long meetingId) {
        ImportTask task = findOwned(taskId, meetingId);
        if (task.getStatus() == ImportTaskStatus.EXPIRED || !fileStore.exists(task.getFailureFilePath())) {
            throw BusinessException.notFound("失败明细文件不存在或已清理");
        }
        var path = fileStore.resolve(task.getFailureFilePath());
        try {
            return new ImportFailureFile("meetingos-import-failures-" + task.getId() + ".xlsx", path, Files.size(path));
        } catch (IOException ex) {
            throw new IllegalStateException("失败明细文件读取失败", ex);
        }
    }

    @Scheduled(fixedDelayString = "${meetingos.import-task.cleanup-delay-ms:3600000}")
    public void cleanupExpiredFiles() {
        LocalDateTime now = LocalDateTime.now();
        List<ImportTask> stuck = repository.findByStatusInAndUpdatedAtBefore(
                ACTIVE_CREATE_STATUSES,
                now.minusHours(stuckHours)
        );
        for (ImportTask task : stuck) {
            stateService.failIfStuck(task.getId(), now.minusHours(stuckHours));
        }

        List<ImportTask> expired = repository.findExpiredWithFiles(EXPIRABLE_STATUSES, now);
        for (ImportTask task : expired) {
            stateService.expireFilesIfDue(task.getId(), now);
        }
    }

    @Scheduled(
            fixedDelayString = "${meetingos.import-task.recovery-delay-ms:30000}",
            initialDelayString = "${meetingos.import-task.recovery-initial-delay-ms:30000}"
    )
    public void recoverUnstartedTasks() {
        LocalDateTime leasedAt = LocalDateTime.now();
        LocalDateTime cutoff = leasedAt.minusSeconds(recoveryLeaseSeconds);
        List<ImportTask> candidates = repository
                .findTop100ByStatusInAndStartedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        RECOVERABLE_STATUSES,
                        cutoff
                );
        for (ImportTask candidate : candidates) {
            ImportTaskRecoveryLease lease = stateService.leaseUnstartedTask(candidate.getId(), cutoff, leasedAt);
            if (lease == null) {
                continue;
            }
            if (lease.committed()) {
                dispatchCommitted(lease.taskId());
            } else {
                dispatchCreated(lease.taskId());
            }
        }
    }

    private ImportTask findOwned(Long taskId, Long meetingId) {
        return repository.findByIdAndMeetingId(taskId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("导入任务不存在"));
    }

    private ImportTaskResponse toResponse(ImportTask task) {
        return new ImportTaskResponse(
                task.getId(),
                task.getMeeting().getId(),
                task.getImportType(),
                task.getStatus(),
                task.getOriginalFilename(),
                task.getIdempotencyKey(),
                task.isPreflight(),
                task.getTotalRows(),
                task.getProcessedRows(),
                task.getImportedRows(),
                task.getFailedRows(),
                task.getProgress(),
                task.getMessage(),
                fileStore.exists(task.getFailureFilePath()),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getExpiresAt(),
                task.getCreatedAt()
        );
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        String normalized = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalized.isBlank()) {
            throw BusinessException.badRequest("idempotencyKey 不能为空");
        }
        if (normalized.length() > 128 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw BusinessException.badRequest("idempotencyKey 格式不正确或超过 128 个字符");
        }
        return normalized;
    }

    private void dispatchAfterCommit(Runnable dispatch) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }

    private void registerFileAttemptLifecycle(String newInputPath,
                                              String previousInputPath,
                                              String previousFailurePath,
                                              Runnable dispatch) {
        List<String> stalePaths = stalePaths(newInputPath, previousInputPath, previousFailurePath);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            stalePaths.forEach(fileStore::deleteQuietly);
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    stalePaths.forEach(fileStore::deleteQuietly);
                } finally {
                    dispatch.run();
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteNewFileAfterRollback(newInputPath, previousInputPath, previousFailurePath);
                }
            }
        });
    }

    private List<String> stalePaths(String newInputPath, String previousInputPath, String previousFailurePath) {
        List<String> paths = new ArrayList<>(2);
        addStalePath(paths, newInputPath, previousInputPath);
        addStalePath(paths, newInputPath, previousFailurePath);
        return List.copyOf(paths);
    }

    private void addStalePath(List<String> paths, String newInputPath, String candidate) {
        if (candidate == null
                || candidate.isBlank()
                || sameStoredPath(candidate, newInputPath)
                || paths.stream().anyMatch(existing -> sameStoredPath(existing, candidate))) {
            return;
        }
        paths.add(candidate);
    }

    private void deleteNewFileAfterRollback(String newInputPath,
                                            String previousInputPath,
                                            String previousFailurePath) {
        if (!sameStoredPath(newInputPath, previousInputPath)
                && !sameStoredPath(newInputPath, previousFailurePath)) {
            fileStore.deleteQuietly(newInputPath);
        }
    }

    private boolean sameStoredPath(String first, String second) {
        if (Objects.equals(first, second)) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        try {
            return Path.of(first).normalize().equals(Path.of(second).normalize());
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    private void dispatchCreated(Long taskId) {
        try {
            worker.processCreated(taskId);
        } catch (RuntimeException ex) {
            // 任务仍保持未开始状态，周期恢复会在租约到期后重新提交。
            log.warn("Import task dispatch rejected, task={}", taskId, ex);
        }
    }

    private void dispatchCommitted(Long taskId) {
        try {
            worker.processCommitted(taskId);
        } catch (RuntimeException ex) {
            // commit 已持久化，不能回滚业务意图；保留 startedAt=null 交给周期恢复。
            log.warn("Committed import task dispatch rejected, task={}", taskId, ex);
        }
    }
}
