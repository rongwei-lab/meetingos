package com.meetingos.tduck;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class TduckPullSyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(TduckPullSyncTaskService.class);
    private static final Set<TduckPullSyncTaskStatus> ACTIVE_STATUSES = Set.of(
            TduckPullSyncTaskStatus.PENDING,
            TduckPullSyncTaskStatus.RUNNING
    );

    private final MeetingRepository meetingRepository;
    private final TduckFormBindingRepository bindingRepository;
    private final TduckPullSyncTaskRepository taskRepository;
    private final TduckPullSyncTaskStateService stateService;
    private final TduckPullSyncTaskWorker worker;

    public TduckPullSyncTaskService(MeetingRepository meetingRepository,
                                    TduckFormBindingRepository bindingRepository,
                                    TduckPullSyncTaskRepository taskRepository,
                                    TduckPullSyncTaskStateService stateService,
                                    TduckPullSyncTaskWorker worker) {
        this.meetingRepository = meetingRepository;
        this.bindingRepository = bindingRepository;
        this.taskRepository = taskRepository;
        this.stateService = stateService;
        this.worker = worker;
    }

    @Transactional
    public TduckBatchPullSyncResponse create(TduckBatchPullSyncRequest request) {
        /*
         * 锁住会议行后检查活动任务，使同一会议的并发点击、重试请求和多实例请求串行化。
         * TDuck 单条同步本身幂等，但禁止两批同时扫描可避免远程请求与本地锁竞争成倍放大。
         */
        Meeting meeting = meetingRepository.findByIdForUpdate(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        if (idempotencyKey != null) {
            TduckPullSyncTask idempotent = taskRepository
                    .findByMeetingIdAndIdempotencyKey(meeting.getId(), idempotencyKey)
                    .orElse(null);
            if (idempotent != null) {
                return stateService.toResponse(idempotent);
            }
        }
        TduckPullSyncTask active = taskRepository
                .findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(meeting.getId(), ACTIVE_STATUSES)
                .orElse(null);
        if (active != null) {
            if (idempotencyKey != null && active.getIdempotencyKey() == null) {
                // 无 key 的活动任务可接纳第一个显式 key，之后同 key 重试仍返回同一任务。
                active.setIdempotencyKey(idempotencyKey);
                taskRepository.saveAndFlush(active);
            } else if (idempotencyKey != null && !idempotencyKey.equals(active.getIdempotencyKey())) {
                /*
                 * 不把新的 key 静默映射到无法持久记录的旧任务；否则旧任务结束后同 key 重试会
                 * 再创建一批全量扫描。不同显式 key 代表不同操作，等待当前任务完成后再提交。
                 */
                throw BusinessException.conflict("当前会议已有其他同步任务正在执行，请等待完成后重试");
            }
            return stateService.toResponse(active);
        }

        TduckFormBinding binding = bindingRepository.findByMeetingId(meeting.getId())
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        if (!StringUtils.hasText(binding.getFormKey())) {
            throw BusinessException.badRequest("TDuck 表单绑定不完整，请管理员重新选择表单");
        }

        TduckPullSyncTask task = new TduckPullSyncTask();
        task.setMeeting(meeting);
        task.setFormKey(binding.getFormKey().trim());
        task.setRequestedLimit(normalizeLimit(request.limit()));
        task.setIdempotencyKey(idempotencyKey);
        task.setStatus(TduckPullSyncTaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("等待同步 TDuck 报名数据");
        task.setRequestedBy(SecurityUtils.currentUsernameOrSystem());
        task = taskRepository.saveAndFlush(task);
        Long taskId = task.getId();
        dispatchAfterCommit(() -> dispatch(taskId));
        return stateService.toResponse(task);
    }

    @Transactional(readOnly = true)
    public TduckBatchPullSyncResponse detail(Long meetingId, Long taskId) {
        TduckPullSyncTask task = taskRepository.findByIdAndMeetingId(taskId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("TDuck 批量同步任务不存在"));
        return stateService.toResponse(task);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeAfterStartup() {
        recoverTasks();
    }

    @Scheduled(
            fixedDelayString = "${meetingos.tduck.pull-sync.recovery-delay-ms:60000}",
            initialDelayString = "${meetingos.tduck.pull-sync.recovery-initial-delay-ms:60000}"
    )
    public void recoverTasks() {
        List<Long> taskIds;
        try {
            taskIds = taskRepository.findRecoverableTaskIds(
                    TduckPullSyncTaskStatus.PENDING,
                    TduckPullSyncTaskStatus.RUNNING,
                    LocalDateTime.now(),
                    PageRequest.of(0, 100)
            );
        } catch (RuntimeException ex) {
            log.error("Failed to scan recoverable TDuck pull sync tasks", ex);
            return;
        }
        taskIds.forEach(this::dispatch);
    }

    private int normalizeLimit(Integer limit) {
        // 0 是持久化的“全量”标记；不把空请求默认为固定上限，确保一键同步能扫完整个 TDuck 表单。
        return limit == null || limit <= 0 ? 0 : limit;
    }

    private String normalizeIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
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

    private void dispatch(Long taskId) {
        if (!worker.tryReserve(taskId)) {
            return;
        }
        try {
            worker.process(taskId);
        } catch (RuntimeException ex) {
            worker.releaseReservation(taskId);
            // 有界队列拒绝时保留 PENDING/过期租约，周期恢复器会在容量释放后重新派发。
            log.warn("TDuck pull sync task dispatch rejected, taskId={}", taskId, ex);
        }
    }
}
