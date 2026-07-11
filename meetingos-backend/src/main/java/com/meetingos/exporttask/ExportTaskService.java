package com.meetingos.exporttask;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.checkin.CheckinRecord;
import com.meetingos.checkin.CheckinRecordRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ExportDisplayFormatter;
import com.meetingos.common.ExportRowProducer;
import com.meetingos.common.ExportTable;
import com.meetingos.common.PageResponse;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.common.SpreadsheetFiles;
import com.meetingos.dinner.DinnerService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.room.RoomService;
import com.meetingos.security.SecurityUtils;
import com.meetingos.seating.SeatAssignment;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.statistics.StatisticsOverview;
import com.meetingos.statistics.StatisticsService;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import com.meetingos.tduck.TduckSubmissionFields;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskService.class);
    private static final Set<ExportTaskStatus> ACTIVE_STATUSES = Set.of(
            ExportTaskStatus.PENDING,
            ExportTaskStatus.RUNNING
    );
    private static final Set<String> ROOM_EXPORT_TYPES = Set.of("rooms", "accommodations", "room-inventory");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 500;
    private static final long SYNCHRONOUS_EXPORT_ROW_LIMIT = 10_000L;
    // 与文件任务执行器的 2 个工作线程 + 32 个有界队列槽位一致，避免一次恢复扫描制造无效派发洪峰。
    private static final int RECOVERY_BATCH_SIZE = 34;
    private static final List<String> ATTENDEE_HEADERS = List.of(
            "ID", "姓名", "手机号", "单位", "职务", "证件号", "邮箱", "参会类型",
            "是否住宿", "房间号", "房间备注", "审核状态", "审核原因", "审核备注",
            "审核人", "审核时间", "报名时间", "签到状态", "签到时间", "签到入口",
            "签到操作人", "参会码", "标签", "备注"
    );
    private static final List<String> CHECKIN_HEADERS = List.of(
            "记录ID", "姓名", "手机号", "签到方式", "结果", "入口", "操作人", "签到时间", "消息"
    );

    private final AttendeeService attendeeService;
    private final AttendeeRepository attendeeRepository;
    private final RoomService roomService;
    private final DinnerService dinnerService;
    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckinRecordRepository checkinRecordRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccessService meetingAccessService;
    private final ExportTaskRepository exportTaskRepository;
    private final SystemSettingsService systemSettingsService;
    private final StatisticsService statisticsService;
    private final TduckFieldMappingRepository tduckFieldMappingRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Path uploadRoot;
    private final Path exportsRoot;
    private final long retentionDays;
    private final long taskTimeoutMinutes;
    private final long pendingRecoveryMinutes;

    public ExportTaskService(AttendeeService attendeeService,
                             AttendeeRepository attendeeRepository,
                             RoomService roomService,
                             DinnerService dinnerService,
                             SeatRepository seatRepository,
                             SeatAssignmentRepository seatAssignmentRepository,
                             CheckinRecordRepository checkinRecordRepository,
                             MeetingRepository meetingRepository,
                             MeetingAccessService meetingAccessService,
                             ExportTaskRepository exportTaskRepository,
                             SystemSettingsService systemSettingsService,
                             StatisticsService statisticsService,
                             TduckFieldMappingRepository tduckFieldMappingRepository,
                             EntityManager entityManager,
                             ApplicationEventPublisher eventPublisher,
                             PlatformTransactionManager transactionManager,
                             @Value("${meetingos.app.upload-dir:uploads}") String uploadDir,
                             @Value("${meetingos.export.retention-days:7}") long retentionDays,
                             @Value("${meetingos.export.task-timeout-minutes:120}") long taskTimeoutMinutes,
                             @Value("${meetingos.export.pending-recovery-minutes:2}") long pendingRecoveryMinutes) {
        this.attendeeService = attendeeService;
        this.attendeeRepository = attendeeRepository;
        this.roomService = roomService;
        this.dinnerService = dinnerService;
        this.seatRepository = seatRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.checkinRecordRepository = checkinRecordRepository;
        this.meetingRepository = meetingRepository;
        this.meetingAccessService = meetingAccessService;
        this.exportTaskRepository = exportTaskRepository;
        this.systemSettingsService = systemSettingsService;
        this.statisticsService = statisticsService;
        this.tduckFieldMappingRepository = tduckFieldMappingRepository;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.exportsRoot = this.uploadRoot.resolve("exports").normalize();
        this.retentionDays = Math.max(1, retentionDays);
        this.taskTimeoutMinutes = Math.max(10, taskTimeoutMinutes);
        this.pendingRecoveryMinutes = Math.max(1, pendingRecoveryMinutes);
    }

    @Transactional
    public ExportTaskResponse create(Long meetingId, String type) {
        return create(meetingId, type, ExportFormat.XLSX.extension(), null, null);
    }

    @Transactional
    public ExportTaskResponse create(Long meetingId,
                                     String type,
                                     String format,
                                     LocalDate startDate,
                                     LocalDate endDate) {
        String normalizedType = normalizeType(type);
        ensureSupportedType(normalizedType, type);
        requireExportAccess(meetingId, normalizedType);
        ExportFormat exportFormat = ExportFormat.from(format);
        if ("statistics".equals(normalizedType) && startDate == null && endDate == null) {
            // 默认范围也在创建时固化，避免任务排队跨日后导出的时间窗口悄然变化。
            endDate = LocalDate.now();
            startDate = endDate.minusDays(6);
        }
        validateDateRange(normalizedType, startDate, endDate);

        /*
         * 锁会议行后再检查活动任务，确保同一数据库实例中两个并发创建请求不会同时通过 exists。
         * 锁粒度是会议而不是全局，不同会议仍可并行创建导出任务。
         */
        Meeting meeting = entityManager.find(Meeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
        if (meeting == null) {
            throw BusinessException.notFound("会议不存在");
        }
        if (exportTaskRepository.existsByMeeting_IdAndExportTypeAndStatusIn(meetingId, normalizedType, ACTIVE_STATUSES)) {
            throw BusinessException.conflict("该会议已有同类型导出任务正在生成，请稍后重试");
        }

        ExportTask task = new ExportTask();
        task.setMeeting(meeting);
        task.setExportType(normalizedType);
        task.setFormat(exportFormat.extension());
        task.setStartDate(startDate);
        task.setEndDate(endDate);
        task.setFilename(filename(normalizedType, exportFormat));
        task.setRequestedBy(SecurityUtils.currentUsernameOrSystem());
        task.setStatus(ExportTaskStatus.PENDING);
        task.setProgress(0);
        task.setMessage("导出任务已提交，等待生成");
        ExportTask saved = exportTaskRepository.save(task);
        saved.setFilename(taskFilename(normalizedType, exportFormat, saved.getId()));

        // 监听器在事务提交后才调度 worker，避免异步线程早于提交而查不到任务。
        eventPublisher.publishEvent(new ExportTaskRequestedEvent(saved.getId()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ExportTaskResponse get(Long meetingId, Long taskId) {
        ExportTask task = requireTask(meetingId, taskId);
        requireExportAccess(meetingId, task.getExportType());
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExportTaskResponse> list(Long meetingId, int page, int size) {
        if (meetingId == null) {
            throw BusinessException.badRequest("meetingId 不能为空");
        }
        if (page < 0) {
            throw BusinessException.badRequest("page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.badRequest("size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        var pageable = PageRequest.of(page, size);
        var result = meetingAccessService.canManage(meetingId)
                ? exportTaskRepository.findByMeeting_IdOrderByCreatedAtDesc(meetingId, pageable)
                : roomTaskPage(meetingId, pageable);
        return PageResponse.from(result.map(this::toResponse));
    }

    /**
     * 兼容旧服务调用；Web 列表接口始终使用分页重载。
     */
    @Transactional(readOnly = true)
    public List<ExportTaskResponse> list(Long meetingId) {
        return list(meetingId, 0, MAX_PAGE_SIZE).content();
    }

    public ExportFile build(Long meetingId, String type) {
        return build(meetingId, type, ExportFormat.XLSX.extension(), null, null);
    }

    public ExportFile build(Long meetingId,
                            String type,
                            String format,
                            LocalDate startDate,
                            LocalDate endDate) {
        String normalizedType = normalizeType(type);
        ensureSupportedType(normalizedType, type);
        requireExportAccess(meetingId, normalizedType);
        ExportFormat exportFormat = ExportFormat.from(format);
        validateDateRange(normalizedType, startDate, endDate);
        List<ExportTable> tables = collectTablesInTransaction(
                meetingId,
                normalizedType,
                startDate,
                endDate,
                true
        );
        return render(normalizedType, exportFormat, tables);
    }

    @Transactional(readOnly = true)
    public void assertDownloadAllowed(Long meetingId, String type) {
        String normalizedType = normalizeType(type);
        ensureSupportedType(normalizedType, type);
        requireExportAccess(meetingId, normalizedType);
    }

    /**
     * 由提交后异步监听器调用。数据库采集、状态更新和文件写入使用独立边界，避免生成大文件时
     * 长时间占用数据库连接，也确保 RUNNING/FAILED 状态能被轮询看到。
     */
    public void processTask(Long taskId) {
        final TaskWork work;
        try {
            work = transactionTemplate.execute(status -> markRunning(taskId));
        } catch (RuntimeException ex) {
            /*
             * claim 失败时不能让异步异常影响任务创建请求，也不能把数据库状态误写成 FAILED。
             * 事务会回滚到 PENDING；极少数提交结果不明确的情况会停在 RUNNING，二者都能被恢复器重试。
             */
            log.error("Failed to claim export task {}; task remains eligible for database recovery", taskId, ex);
            return;
        }
        if (work == null) {
            return;
        }

        Path finalPath = taskOutputPath(work.meetingId(), work.taskId(), work.format());
        Path temporaryPath = finalPath.resolveSibling(finalPath.getFileName() + ".part");
        try {
            Files.createDirectories(finalPath.getParent());
            Files.deleteIfExists(temporaryPath);
            updateProgress(taskId, 15, "正在读取导出数据");
            if (supportsStreaming(work.type())) {
                writeStreamingTaskFile(temporaryPath, work);
            } else {
                /*
                 * 参会人和签到是最容易无限增长的两类数据，已走分页流式路径。座位、住宿、晚宴
                 * 当前业务服务仍只暴露 List<ExportTable>，这里至少用 SXSSF 直接写 Path，避免再复制
                 * 整个 Workbook/byte[]；后续仓储提供分页接口后可直接接入同一 StreamingExport。
                 */
                List<ExportTable> tables = collectTablesInTransaction(
                        work.meetingId(),
                        work.type(),
                        work.startDate(),
                        work.endDate()
                );
                updateProgress(taskId, 55, "正在生成导出文件");
                writeTaskFile(temporaryPath, work.format(), tables);
            }
            moveAtomically(temporaryPath, finalPath);
            updateProgress(taskId, 90, "正在校验导出文件");
            long fileSize = Files.size(finalPath);
            String checksum = sha256(finalPath);
            transactionTemplate.executeWithoutResult(status -> markCompleted(taskId, finalPath, fileSize, checksum));
        } catch (Exception ex) {
            deleteQuietly(temporaryPath);
            deleteQuietly(finalPath);
            log.error("Export task generation failed, taskId={}", taskId, ex);
            transactionTemplate.executeWithoutResult(status -> markFailed(taskId, ex));
        }
    }

    public ExportDownload openDownload(Long meetingId, Long taskId) {
        TaskSnapshot task = transactionTemplate.execute(status -> snapshotForDownload(meetingId, taskId));
        if (task == null) {
            throw BusinessException.notFound("导出任务不存在");
        }
        requireExportAccess(meetingId, task.type());
        if (task.status() == ExportTaskStatus.EXPIRED
                || (task.expiresAt() != null && !task.expiresAt().isAfter(LocalDateTime.now()))) {
            expireTask(task.taskId(), "导出文件已过期");
            throw BusinessException.badRequest("导出文件已过期，请重新创建任务");
        }
        if (task.status() != ExportTaskStatus.COMPLETED) {
            throw BusinessException.conflict("导出文件尚未生成完成，当前状态: " + task.status());
        }

        Path path = resolveValidatedTaskPath(task);
        try {
            if (!Files.isRegularFile(path)) {
                invalidateCompletedTask(task.taskId(), "导出文件不存在，请重新创建任务");
                throw BusinessException.notFound("导出文件不存在，请重新创建任务");
            }
            long actualSize = Files.size(path);
            if (task.fileSize() == null || actualSize != task.fileSize()) {
                invalidateCompletedTask(task.taskId(), "导出文件校验失败，请重新创建任务");
                throw BusinessException.badRequest("导出文件校验失败，请重新创建任务");
            }
            ExportFormat format = ExportFormat.from(task.format());
            return new ExportDownload(task.filename(), new FileSystemResource(path), actualSize, format.mediaType());
        } catch (IOException ex) {
            throw BusinessException.badRequest("读取导出文件失败，请重新创建任务");
        }
    }

    public void cleanupExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<TaskSnapshot> expired = transactionTemplate.execute(status ->
                exportTaskRepository.findByStatusInAndExpiresAtLessThanEqual(
                                Set.of(
                                        ExportTaskStatus.COMPLETED,
                                        ExportTaskStatus.EXPIRED,
                                        ExportTaskStatus.FAILED
                                ),
                                now
                        ).stream()
                        .map(this::toSnapshot)
                        .toList()
        );
        if (expired == null) {
            return;
        }
        for (TaskSnapshot task : expired) {
            expireTask(task.taskId(), "导出文件已过期");
        }
    }

    public List<Long> recoverableTaskIds() {
        List<Long> taskIds = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime runningCutoff = now.minusMinutes(taskTimeoutMinutes);
            LocalDateTime pendingCutoff = now.minusMinutes(pendingRecoveryMinutes);
            var firstPage = PageRequest.of(0, RECOVERY_BATCH_SIZE);
            List<Long> candidates = new ArrayList<>(exportTaskRepository.findRecoverableIds(
                    ExportTaskStatus.RUNNING,
                    runningCutoff,
                    firstPage
            ));
            int remaining = RECOVERY_BATCH_SIZE - candidates.size();
            if (remaining > 0) {
                candidates.addAll(exportTaskRepository.findRecoverableIds(
                        ExportTaskStatus.PENDING,
                        pendingCutoff,
                        PageRequest.of(0, remaining)
                ));
            }

            List<Long> ids = new ArrayList<>(candidates.size());
            for (Long taskId : candidates) {
                ExportTask task = exportTaskRepository.findByIdForUpdate(taskId).orElse(null);
                if (task == null || task.getUpdatedAt() == null) {
                    continue;
                }
                if (task.getStatus() == ExportTaskStatus.RUNNING
                        && task.getUpdatedAt().isBefore(runningCutoff)) {
                    task.setStatus(ExportTaskStatus.PENDING);
                    task.setProgress(0);
                    task.setMessage("导出任务执行超时，已重新排队生成");
                    task.setUpdatedAt(now);
                    ids.add(taskId);
                    continue;
                }
                if (task.getStatus() == ExportTaskStatus.PENDING
                        && task.getUpdatedAt().isBefore(pendingCutoff)) {
                    // 更新消息会刷新 updatedAt，形成恢复派发租期，避免下一分钟重复提交同一新鲜任务。
                    task.setMessage("导出任务正在重新排队，等待生成");
                    task.setUpdatedAt(now);
                    ids.add(taskId);
                }
            }
            return ids;
        });
        return taskIds == null ? List.of() : taskIds;
    }

    private List<ExportTable> collectTablesInTransaction(Long meetingId,
                                                         String normalizedType,
                                                         LocalDate startDate,
                                                         LocalDate endDate) {
        return collectTablesInTransaction(meetingId, normalizedType, startDate, endDate, false);
    }

    private List<ExportTable> collectTablesInTransaction(Long meetingId,
                                                         String normalizedType,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         boolean enforceSynchronousLimit) {
        List<ExportTable> tables = transactionTemplate.execute(status -> {
            if (!meetingRepository.existsById(meetingId)) {
                throw BusinessException.notFound("会议不存在");
            }
            if (enforceSynchronousLimit) {
                enforceSynchronousExportLimit(meetingId, normalizedType);
            }
            // 一次导出只读取一次开关，避免座位/签到每一行重复查询 system_settings。
            boolean masked = systemSettingsService.maskSensitiveEnabled();
            return switch (normalizedType) {
                case "attendees" -> List.of(attendeeService.exportTable(meetingId, "attendees"));
                case "approved" -> List.of(attendeeService.exportTable(meetingId, "approved"));
                case "no-checkin" -> List.of(attendeeService.exportTable(meetingId, "no-checkin"));
                case "rooms", "accommodations" -> List.of(roomService.exportRoomsTable(meetingId));
                case "room-inventory" -> List.of(roomService.exportRoomInventoryTable(meetingId));
                case "dinner" -> List.of(dinnerService.exportDinnerTable(meetingId));
                case "seats" -> List.of(exportSeatsTable(meetingId, masked));
                case "checkin", "checkin-records" -> List.of(exportCheckinTable(meetingId, masked));
                case "statistics" -> exportStatisticsTables(meetingId, startDate, endDate);
                default -> throw BusinessException.badRequest("暂不支持的导出类型: " + normalizedType);
            };
        });
        return tables == null ? List.of() : tables;
    }

    private void enforceSynchronousExportLimit(Long meetingId, String normalizedType) {
        if ("statistics".equals(normalizedType)) {
            // 统计导出读取的是聚合结果，行数由维度数量决定，不会加载全量业务记录。
            return;
        }
        String countJpql = switch (normalizedType) {
            case "attendees" -> "select count(attendee) from Attendee attendee "
                    + "where attendee.meeting.id = :meetingId";
            case "approved" -> "select count(attendee) from Attendee attendee "
                    + "where attendee.meeting.id = :meetingId and attendee.reviewStatus = :reviewStatus";
            case "no-checkin" -> "select count(attendee) from Attendee attendee "
                    + "where attendee.meeting.id = :meetingId and attendee.reviewStatus = :reviewStatus "
                    + "and attendee.checkinStatus = :checkinStatus";
            case "rooms", "accommodations" -> "select count(attendee) from Attendee attendee "
                    + "where attendee.meeting.id = :meetingId and attendee.roomNo is not null";
            case "room-inventory" -> "select count(room) from Room room where room.meeting.id = :meetingId";
            case "dinner" -> "select count(assignment) from DinnerAssignment assignment "
                    + "where assignment.meeting.id = :meetingId";
            case "seats" -> "select count(seat) from Seat seat where seat.meeting.id = :meetingId";
            case "checkin", "checkin-records" -> "select count(record) from CheckinRecord record "
                    + "where record.meeting.id = :meetingId";
            default -> throw BusinessException.badRequest("暂不支持的导出类型: " + normalizedType);
        };
        var query = entityManager.createQuery(countJpql, Long.class)
                .setParameter("meetingId", meetingId);
        if ("approved".equals(normalizedType) || "no-checkin".equals(normalizedType)) {
            query.setParameter("reviewStatus", ReviewStatus.APPROVED);
        }
        if ("no-checkin".equals(normalizedType)) {
            query.setParameter("checkinStatus", CheckinStatus.NOT_CHECKED_IN);
        }
        long rowCount = query.getSingleResult();
        if (rowCount > SYNCHRONOUS_EXPORT_ROW_LIMIT) {
            throw BusinessException.badRequest(
                    "同步导出最多支持 " + SYNCHRONOUS_EXPORT_ROW_LIMIT
                            + " 行，当前数据共 " + rowCount + " 行，请使用异步导出任务"
            );
        }
    }

    private TaskWork markRunning(Long taskId) {
        ExportTask task = exportTaskRepository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() != ExportTaskStatus.PENDING) {
            return null;
        }
        task.setStatus(ExportTaskStatus.RUNNING);
        task.setProgress(5);
        task.setMessage("导出任务正在生成");
        return new TaskWork(
                task.getId(),
                task.getMeeting().getId(),
                task.getExportType(),
                ExportFormat.from(task.getFormat()),
                task.getStartDate(),
                task.getEndDate()
        );
    }

    private void updateProgress(Long taskId, int progress, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ExportTaskStatus.RUNNING) {
                return;
            }
            task.setProgress(Math.max(task.getProgress(), progress));
            task.setMessage(message);
        });
    }

    private void markCompleted(Long taskId, Path path, long fileSize, String checksum) {
        ExportTask task = exportTaskRepository.findById(taskId)
                .orElseThrow(() -> BusinessException.notFound("导出任务不存在"));
        if (task.getStatus() != ExportTaskStatus.RUNNING) {
            throw BusinessException.conflict("导出任务状态已变化，不能写入完成结果");
        }
        LocalDateTime generatedAt = LocalDateTime.now();
        task.setFilePath(relativeStoredPath(path));
        task.setFileSize(fileSize);
        task.setChecksum(checksum);
        task.setGeneratedAt(generatedAt);
        task.setCompletedAt(generatedAt);
        task.setExpiresAt(generatedAt.plusDays(retentionDays));
        task.setProgress(100);
        task.setStatus(ExportTaskStatus.COMPLETED);
        task.setMessage("导出文件已生成，可直接下载");
    }

    private void markFailed(Long taskId, Exception exception) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task == null || (task.getStatus() != ExportTaskStatus.PENDING && task.getStatus() != ExportTaskStatus.RUNNING)) {
            return;
        }
        task.setStatus(ExportTaskStatus.FAILED);
        task.setCompletedAt(LocalDateTime.now());
        task.setMessage(failureMessage(exception));
    }

    private String failureMessage(Exception exception) {
        if (exception instanceof BusinessException && exception.getMessage() != null && !exception.getMessage().isBlank()) {
            String message = exception.getMessage().strip();
            return message.length() > 450 ? message.substring(0, 450) : message;
        }
        return "导出文件生成失败，请稍后重试";
    }

    private TaskSnapshot snapshotForDownload(Long meetingId, Long taskId) {
        return toSnapshot(requireTask(meetingId, taskId));
    }

    private void invalidateCompletedTask(Long taskId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ExportTaskStatus.COMPLETED) {
                return;
            }
            task.setStatus(ExportTaskStatus.FAILED);
            task.setMessage(message);
        });
    }

    private void expireTask(Long taskId, String message) {
        TaskSnapshot snapshot = transactionTemplate.execute(status ->
                exportTaskRepository.findById(taskId).map(this::toSnapshot).orElse(null)
        );
        if (snapshot == null) {
            return;
        }
        if (snapshot.filePath() != null && !snapshot.filePath().isBlank()) {
            try {
                if (!deleteQuietly(resolveValidatedTaskPath(snapshot))) {
                    // 保持 COMPLETED 使定时任务下轮继续清理；已是 EXPIRED 的记录也会被查询重试。
                    return;
                }
            } catch (BusinessException ex) {
                // 数据库路径异常时拒绝删除并封禁下载，绝不能越过 exportsRoot 清理其他文件。
                log.warn("Rejected unsafe export cleanup path, taskId={}, path={}", taskId, snapshot.filePath());
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            task.setStatus(ExportTaskStatus.EXPIRED);
            task.setProgress(100);
            task.setMessage(message);
        });
    }

    private ExportTask requireTask(Long meetingId, Long taskId) {
        if (meetingId == null || taskId == null) {
            throw BusinessException.badRequest("meetingId 和 taskId 不能为空");
        }
        return exportTaskRepository.findByIdAndMeeting_Id(taskId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("导出任务不存在"));
    }

    private org.springframework.data.domain.Page<ExportTask> roomTaskPage(Long meetingId,
                                                                          org.springframework.data.domain.Pageable pageable) {
        if (!meetingAccessService.canManageRooms(meetingId)) {
            throw BusinessException.forbidden("当前账号无权查看该会议的导出任务");
        }
        return exportTaskRepository.findByMeeting_IdAndExportTypeInOrderByCreatedAtDesc(
                meetingId,
                ROOM_EXPORT_TYPES,
                pageable
        );
    }

    private void requireExportAccess(Long meetingId, String type) {
        if (ROOM_EXPORT_TYPES.contains(type)) {
            meetingAccessService.requireRoom(meetingId);
            return;
        }
        meetingAccessService.requireManage(meetingId);
    }

    private void writeTaskFile(Path path, ExportFormat format, List<ExportTable> tables) throws IOException {
        if (format == ExportFormat.XLSX) {
            SpreadsheetFiles.writeXlsx(path, tables);
            return;
        }
        ExportTable table = mergeForCsv(tables);
        Files.writeString(
                path,
                CsvFiles.toCsv(table.headers(), table.rows()),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private boolean supportsStreaming(String type) {
        return switch (type) {
            case "attendees", "approved", "no-checkin", "checkin", "checkin-records" -> true;
            default -> false;
        };
    }

    private void writeStreamingTaskFile(Path path, TaskWork work) throws IOException {
        StreamingExport export = switch (work.type()) {
            case "attendees", "approved", "no-checkin" -> attendeeStreamingExport(work);
            case "checkin", "checkin-records" -> checkinStreamingExport(work);
            default -> throw BusinessException.badRequest("该导出类型不支持流式生成: " + work.type());
        };
        updateProgress(work.taskId(), Math.max(35, export.initialProgress()), "正在分页生成导出文件");
        if (work.format() == ExportFormat.XLSX) {
            SpreadsheetFiles.writeStreamingXlsx(
                    path,
                    export.sheetName(),
                    export.headers(),
                    export.producer()
            );
            return;
        }
        CsvFiles.writeCsv(path, export.headers(), export.producer());
    }

    private StreamingExport checkinStreamingExport(TaskWork work) {
        boolean masked = currentMaskSetting();
        ExportRange range = checkinExportRange(work.meetingId());
        ExportRowProducer producer = consumer -> {
            long cursor = 0L;
            long processed = 0L;
            int lastProgress = 35;
            while (true) {
                long requestedCursor = cursor;
                CursorPage batch = transactionTemplate.execute(status -> {
                    List<CheckinRecord> records = entityManager.createQuery("""
                                    select record
                                    from CheckinRecord record
                                    left join fetch record.attendee
                                    where record.meeting.id = :meetingId
                                      and record.id > :cursor
                                      and record.id <= :maxId
                                    order by record.id asc
                                    """, CheckinRecord.class)
                            .setParameter("meetingId", work.meetingId())
                            .setParameter("cursor", requestedCursor)
                            .setParameter("maxId", range.maxId())
                            .setMaxResults(EXPORT_BATCH_SIZE)
                            .getResultList();
                    List<List<?>> rows = records.stream()
                            .<List<?>>map(record -> checkinRow(record, masked))
                            .toList();
                    long nextCursor = records.isEmpty() ? requestedCursor : records.get(records.size() - 1).getId();
                    return new CursorPage(rows, nextCursor, records.size() < EXPORT_BATCH_SIZE || nextCursor >= range.maxId());
                });
                if (batch == null) {
                    return;
                }
                for (List<?> row : batch.rows()) {
                    consumer.accept(row);
                }
                processed += batch.rows().size();
                int progress = rowProgress(35, 80, processed, range.totalRows());
                if (progress > lastProgress) {
                    updateProgress(work.taskId(), progress, "正在分页生成签到记录");
                    lastProgress = progress;
                }
                if (batch.last()) {
                    return;
                }
                cursor = batch.nextCursor();
            }
        };
        return new StreamingExport("签到记录", CHECKIN_HEADERS, producer, 35);
    }

    private StreamingExport attendeeStreamingExport(TaskWork work) {
        boolean masked = currentMaskSetting();
        ExportRange range = attendeeExportRange(work);
        List<DynamicColumn> dynamicColumns = discoverAttendeeColumns(work, range);
        List<String> headers = new ArrayList<>(ATTENDEE_HEADERS);
        headers.addAll(dynamicColumns.stream().map(DynamicColumn::header).toList());

        ExportRowProducer producer = consumer -> {
            long cursor = 0L;
            long processed = 0L;
            int lastProgress = 35;
            while (true) {
                long requestedCursor = cursor;
                CursorPage batch = transactionTemplate.execute(status -> {
                    List<Attendee> attendees = loadAttendeeBatch(work, range.maxId(), requestedCursor);
                    List<List<?>> rows = attendees.stream()
                            .<List<?>>map(attendee -> attendeeRow(attendee, dynamicColumns, masked))
                            .toList();
                    long nextCursor = attendees.isEmpty() ? requestedCursor : attendees.get(attendees.size() - 1).getId();
                    return new CursorPage(rows, nextCursor, attendees.size() < EXPORT_BATCH_SIZE || nextCursor >= range.maxId());
                });
                if (batch == null) {
                    return;
                }
                for (List<?> row : batch.rows()) {
                    consumer.accept(row);
                }
                processed += batch.rows().size();
                int progress = rowProgress(35, 80, processed, range.totalRows());
                if (progress > lastProgress) {
                    updateProgress(work.taskId(), progress, "正在分页生成参会人数据");
                    lastProgress = progress;
                }
                if (batch.last()) {
                    return;
                }
                cursor = batch.nextCursor();
            }
        };
        return new StreamingExport(attendeeSheetName(work.type()), headers, producer, 35);
    }

    private List<DynamicColumn> discoverAttendeeColumns(TaskWork work, ExportRange range) {
        Map<String, String> configuredLabels = transactionTemplate.execute(status -> {
            Map<String, String> labels = new LinkedHashMap<>();
            for (TduckFieldMapping mapping : tduckFieldMappingRepository.findByMeetingId(work.meetingId())) {
                String key = mapping.getTduckFieldKey();
                if (!hasExportableFormValueKey(key)) {
                    continue;
                }
                String label = mapping.getTduckFieldLabel();
                labels.putIfAbsent(key, label == null || label.isBlank() ? key : label.strip());
            }
            return labels;
        });
        Map<String, DynamicColumn> columns = new LinkedHashMap<>();

        long cursor = 0L;
        long processed = 0L;
        int lastProgress = 15;
        while (true) {
            long requestedCursor = cursor;
            ColumnPage batch = transactionTemplate.execute(status -> {
                List<Attendee> attendees = loadAttendeeBatch(work, range.maxId(), requestedCursor);
                List<Map<String, Object>> snapshots = attendees.stream()
                        .map(Attendee::getRawDataSnapshot)
                        .filter(snapshot -> snapshot != null && !snapshot.isEmpty())
                        .toList();
                long nextCursor = attendees.isEmpty() ? requestedCursor : attendees.get(attendees.size() - 1).getId();
                return new ColumnPage(
                        snapshots,
                        nextCursor,
                        attendees.size(),
                        attendees.size() < EXPORT_BATCH_SIZE || nextCursor >= range.maxId()
                );
            });
            if (batch == null) {
                break;
            }
            for (Map<String, Object> snapshot : batch.snapshots()) {
                for (String key : snapshot.keySet()) {
                    if (!hasExportableFormValueKey(key)) {
                        continue;
                    }
                    String label = configuredLabels == null ? key : configuredLabels.getOrDefault(key, key);
                    columns.putIfAbsent(key, new DynamicColumn(label, key, key + "label"));
                }
            }
            processed += batch.scannedRows();
            int progress = rowProgress(15, 35, processed, range.totalRows());
            if (progress > lastProgress) {
                updateProgress(work.taskId(), progress, "正在分析动态报名字段");
                lastProgress = progress;
            }
            if (batch.last()) {
                break;
            }
            cursor = batch.nextCursor();
        }
        return new ArrayList<>(columns.values());
    }

    private ExportRange attendeeExportRange(TaskWork work) {
        ExportRange range = transactionTemplate.execute(status -> {
            var query = entityManager.createQuery(
                    "select coalesce(max(attendee.id), 0), count(attendee) " + attendeeFilterClause(work.type()),
                    Object[].class
            );
            applyAttendeeFilterParameters(query, work.meetingId(), work.type());
            Object[] values = query.getSingleResult();
            return new ExportRange(number(values[0]), number(values[1]));
        });
        return range == null ? new ExportRange(0L, 0L) : range;
    }

    private ExportRange checkinExportRange(Long meetingId) {
        ExportRange range = transactionTemplate.execute(status -> {
            Object[] values = entityManager.createQuery("""
                            select coalesce(max(record.id), 0), count(record)
                            from CheckinRecord record
                            where record.meeting.id = :meetingId
                            """, Object[].class)
                    .setParameter("meetingId", meetingId)
                    .getSingleResult();
            return new ExportRange(number(values[0]), number(values[1]));
        });
        return range == null ? new ExportRange(0L, 0L) : range;
    }

    private List<Attendee> loadAttendeeBatch(TaskWork work, long maxId, long cursor) {
        var query = entityManager.createQuery(
                "select attendee " + attendeeFilterClause(work.type())
                        + " and attendee.id > :cursor and attendee.id <= :maxId order by attendee.id asc",
                Attendee.class
        );
        applyAttendeeFilterParameters(query, work.meetingId(), work.type());
        return query
                .setParameter("cursor", cursor)
                .setParameter("maxId", maxId)
                .setMaxResults(EXPORT_BATCH_SIZE)
                .getResultList();
    }

    private String attendeeFilterClause(String type) {
        StringBuilder clause = new StringBuilder("from Attendee attendee where attendee.meeting.id = :meetingId");
        if ("approved".equals(type) || "no-checkin".equals(type)) {
            clause.append(" and attendee.reviewStatus = :reviewStatus");
        }
        if ("no-checkin".equals(type)) {
            clause.append(" and attendee.checkinStatus = :checkinStatus");
        }
        return clause.toString();
    }

    private void applyAttendeeFilterParameters(jakarta.persistence.Query query, Long meetingId, String type) {
        query.setParameter("meetingId", meetingId);
        if ("approved".equals(type) || "no-checkin".equals(type)) {
            query.setParameter("reviewStatus", ReviewStatus.APPROVED);
        }
        if ("no-checkin".equals(type)) {
            query.setParameter("checkinStatus", CheckinStatus.NOT_CHECKED_IN);
        }
    }

    private long number(Object value) {
        return value instanceof Number numeric ? numeric.longValue() : 0L;
    }

    private List<?> attendeeRow(Attendee attendee, List<DynamicColumn> dynamicColumns, boolean masked) {
        List<Object> row = new ArrayList<>(List.of(
                attendee.getId(),
                attendee.getName(),
                masked ? SensitiveDataMasker.maskPhone(attendee.getPhone()) : attendee.getPhone(),
                nullable(attendee.getOrganization()),
                nullable(attendee.getTitle()),
                nullable(masked ? SensitiveDataMasker.maskIdentityNo(attendee.getIdentityNo()) : attendee.getIdentityNo()),
                nullable(masked ? SensitiveDataMasker.maskEmail(attendee.getEmail()) : attendee.getEmail()),
                attendee.getAttendeeType(),
                attendee.isAccommodationRequired() ? "是" : "否",
                nullable(attendee.getRoomNo()),
                nullable(attendee.getRoomRemark()),
                attendee.getReviewStatus(),
                nullable(attendee.getReviewReason()),
                nullable(attendee.getReviewRemark()),
                nullable(attendee.getReviewedBy()),
                nullable(attendee.getReviewedAt()),
                nullable(attendee.getRegisteredAt()),
                attendee.getCheckinStatus(),
                nullable(attendee.getCheckedInAt()),
                nullable(attendee.getCheckinEntrance()),
                nullable(attendee.getCheckinOperator()),
                nullable(attendee.getAttendeeCode()),
                nullable(attendee.getTags()),
                nullable(attendee.getRemark())
        ));
        Map<String, Object> snapshot = attendee.getRawDataSnapshot() == null ? Map.of() : attendee.getRawDataSnapshot();
        for (DynamicColumn column : dynamicColumns) {
            Object value = snapshot.getOrDefault(column.labelKey(), snapshot.get(column.valueKey()));
            row.add(nullable(masked
                    ? SensitiveDataMasker.maskValue(column.valueKey(), column.header(), value)
                    : value));
        }
        return row;
    }

    private boolean hasExportableFormValueKey(String key) {
        return key != null
                && !key.isBlank()
                && !TduckSubmissionFields.isMetaField(key)
                && !"source".equals(key)
                && !key.endsWith("label");
    }

    private String attendeeSheetName(String type) {
        return switch (type) {
            case "approved" -> "审核通过名单";
            case "no-checkin" -> "未签到名单";
            default -> "参会名单";
        };
    }

    private boolean currentMaskSetting() {
        Boolean masked = transactionTemplate.execute(status -> systemSettingsService.maskSensitiveEnabled());
        return masked == null || masked;
    }

    private int rowProgress(int start, int end, long processedRows, long totalRows) {
        if (totalRows <= 0) {
            return end;
        }
        double ratio = Math.min(1D, (double) processedRows / totalRows);
        return start + (int) Math.floor((end - start) * ratio);
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", ex);
        }
    }

    private Path taskOutputPath(Long meetingId, Long taskId, ExportFormat format) {
        Path meetingRoot = exportsRoot.resolve(String.valueOf(meetingId)).normalize();
        Path path = meetingRoot.resolve(taskId + "." + format.extension()).normalize();
        if (!path.startsWith(meetingRoot) || !meetingRoot.startsWith(exportsRoot)) {
            throw BusinessException.badRequest("导出文件路径不安全");
        }
        return path;
    }

    private Path resolveValidatedTaskPath(TaskSnapshot task) {
        if (task.filePath() == null || task.filePath().isBlank()) {
            throw BusinessException.notFound("导出任务没有可下载文件");
        }
        Path configured = Path.of(task.filePath());
        Path resolved = configured.isAbsolute()
                ? configured.normalize()
                : uploadRoot.resolve(configured).normalize();
        Path meetingRoot = exportsRoot.resolve(String.valueOf(task.meetingId())).normalize();
        String expectedFilename = task.taskId() + "." + ExportFormat.from(task.format()).extension();
        if (!resolved.startsWith(meetingRoot)
                || resolved.getFileName() == null
                || !expectedFilename.equals(resolved.getFileName().toString())) {
            throw BusinessException.badRequest("导出文件路径校验失败");
        }
        return resolved;
    }

    private String relativeStoredPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(exportsRoot)) {
            throw BusinessException.badRequest("导出文件路径不安全");
        }
        return uploadRoot.relativize(normalized).toString().replace('\\', '/');
    }

    private boolean deleteQuietly(Path path) {
        if (path == null) {
            return true;
        }
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ex) {
            log.warn("Failed to delete export file: {}", path, ex);
            return false;
        }
    }

    private ExportTaskResponse toResponse(ExportTask task) {
        Meeting meeting = task.getMeeting();
        return new ExportTaskResponse(
                task.getId(),
                meeting.getId(),
                meeting.getCode(),
                meeting.getName(),
                task.getExportType(),
                task.getFormat(),
                task.getStartDate(),
                task.getEndDate(),
                task.getStatus(),
                task.getFilename(),
                task.getRequestedBy(),
                task.getCompletedAt(),
                task.getFileSize(),
                task.getChecksum(),
                task.getProgress(),
                isFileAvailable(task),
                task.getGeneratedAt(),
                task.getExpiresAt(),
                task.getMessage(),
                task.getCreatedAt()
        );
    }

    private boolean isFileAvailable(ExportTask task) {
        if (task.getStatus() != ExportTaskStatus.COMPLETED
                || task.getExpiresAt() == null
                || !task.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false;
        }
        try {
            Path path = resolveValidatedTaskPath(toSnapshot(task));
            return Files.isRegularFile(path)
                    && task.getFileSize() != null
                    && Files.size(path) == task.getFileSize();
        } catch (BusinessException | IOException ex) {
            return false;
        }
    }

    private TaskSnapshot toSnapshot(ExportTask task) {
        return new TaskSnapshot(
                task.getId(),
                task.getMeeting().getId(),
                task.getExportType(),
                task.getFormat(),
                task.getStatus(),
                task.getFilename(),
                task.getFilePath(),
                task.getFileSize(),
                task.getExpiresAt()
        );
    }

    private String filename(String normalizedType, ExportFormat format) {
        return "meetingos-" + normalizedType + "." + format.extension();
    }

    private String taskFilename(String normalizedType, ExportFormat format, Long taskId) {
        return "meetingos-" + normalizedType + "-" + taskId + "." + format.extension();
    }

    private ExportFile render(String normalizedType, ExportFormat format, List<ExportTable> tables) {
        if (format == ExportFormat.XLSX) {
            return new ExportFile(filename(normalizedType, format), SpreadsheetFiles.toXlsx(tables), format.mediaType());
        }
        ExportTable table = mergeForCsv(tables);
        String csv = CsvFiles.toCsv(table.headers(), table.rows());
        return new ExportFile(filename(normalizedType, format), csv.getBytes(StandardCharsets.UTF_8), format.mediaType());
    }

    private ExportTable mergeForCsv(List<ExportTable> tables) {
        if (tables.size() == 1) {
            return tables.get(0);
        }
        List<String> headers = List.of("分组", "项目", "数值", "说明");
        List<List<?>> rows = new ArrayList<>();
        for (ExportTable table : tables) {
            for (List<?> row : table.rows()) {
                switch (table.sheetName()) {
                    case "统计概览" -> rows.add(padOrTrim(row, headers.size()));
                    case "趋势" -> {
                        Object date = valueAt(row, 0);
                        rows.add(List.of("趋势", date + " 报名新增", valueAt(row, 1), ""));
                        rows.add(List.of("趋势", date + " 签到人数", valueAt(row, 2), ""));
                    }
                    case "分布" -> rows.add(List.of(valueAt(row, 0), valueAt(row, 1), valueAt(row, 2), ""));
                    default -> rows.add(List.of(table.sheetName(), valueAt(row, 0), valueAt(row, 1), valueAt(row, 2)));
                }
            }
        }
        return new ExportTable("导出数据", headers, rows);
    }

    private List<?> padOrTrim(List<?> row, int size) {
        List<Object> values = new ArrayList<>(row);
        while (values.size() < size) {
            values.add("");
        }
        return values.subList(0, size);
    }

    private Object valueAt(List<?> row, int index) {
        return index < row.size() && row.get(index) != null ? row.get(index) : "";
    }

    private ExportTable exportSeatsTable(Long meetingId, boolean masked) {
        Map<Long, SeatAssignment> assignmentBySeat = seatAssignmentRepository.findWithAttendeeAndSeatByMeetingId(meetingId).stream()
                .collect(Collectors.toMap(assignment -> assignment.getSeat().getId(), assignment -> assignment));
        var rows = seatRepository.findByMeetingIdOrderByRowNoAscSeatNoAsc(meetingId).stream()
                .map(seat -> {
                    SeatAssignment assignment = assignmentBySeat.get(seat.getId());
                    Attendee attendee = assignment == null ? null : assignment.getAttendee();
                    return List.of(
                            seat.getAreaName(),
                            seat.getRowNo(),
                            seat.getSeatNo(),
                            seat.getSeatCode(),
                            seat.getSeatType(),
                            seat.isDisabled() ? "是" : "否",
                            seat.isLocked() ? "是" : "否",
                            attendee == null ? "" : attendee.getName(),
                            attendee == null ? "" : exportPhone(attendee.getPhone(), masked),
                            attendee == null ? "" : nullable(attendee.getOrganization()),
                            attendee == null ? "" : attendee.getAttendeeType(),
                            assignment == null ? "" : (assignment.isManualAssigned() ? "手动" : "自动"),
                            assignment == null ? "" : nullable(assignment.getAssignedBy())
                    );
                })
                .toList();
        return new ExportTable(
                "座位表",
                List.of("区域", "排号", "座号", "座位码", "座位类型", "是否禁用", "是否锁定",
                        "参会人", "手机号", "单位", "参会类型", "分配方式", "分配人"),
                rows
        );
    }

    private ExportTable exportCheckinTable(Long meetingId, boolean masked) {
        var rows = checkinRecordRepository.findWithAttendeeByMeetingIdOrderByIdAsc(meetingId).stream()
                .map(record -> checkinRow(record, masked))
                .toList();
        return new ExportTable("签到记录", List.of("记录ID", "姓名", "手机号", "签到方式", "结果", "入口", "操作人", "签到时间", "消息"), rows);
    }

    private List<?> checkinRow(CheckinRecord record, boolean masked) {
        Attendee attendee = record.getAttendee();
        return List.of(
                record.getId(),
                attendee == null ? "" : attendee.getName(),
                attendee == null ? "" : exportPhone(attendee.getPhone(), masked),
                record.getCheckinType(),
                record.getResultStatus(),
                nullable(record.getEntrance()),
                nullable(record.getOperator()),
                nullable(record.getCheckedInAt()),
                nullable(record.getMessage())
        );
    }

    private List<ExportTable> exportStatisticsTables(Long meetingId, LocalDate startDate, LocalDate endDate) {
        StatisticsOverview overview = statisticsService.overview(meetingId, startDate, endDate);
        List<List<?>> overviewRows = List.of(
                row("报名统计", "报名总人数", overview.registrationCount(), ""),
                row("报名统计", "时间范围新增", overview.rangeRegistrationCount(), ""),
                row("报名统计", "待审核", overview.pendingCount(), ""),
                row("报名统计", "已通过", overview.approvedCount(), ""),
                row("报名统计", "已拒绝", overview.rejectedCount(), ""),
                row("座位统计", "总座位", overview.totalSeatCount(), ""),
                row("座位统计", "可用座位", overview.availableSeatCount(), ""),
                row("座位统计", "已分配", overview.seatedCount(), ""),
                row("座位统计", "空余座位", overview.emptySeatCount(), ""),
                row("座位统计", "禁用座位", overview.disabledSeatCount(), ""),
                row("座位统计", "座位使用率", percent(overview.seatUsageRate()), ""),
                row("住宿统计", "房间总数", overview.roomCount(), ""),
                row("住宿统计", "需住宿人数", overview.accommodationRequiredCount(), ""),
                row("住宿统计", "需住宿已安排", overview.roomAssignedRequiredCount(), ""),
                row("住宿统计", "需住宿未安排", overview.roomUnassignedRequiredCount(), ""),
                row("晚宴统计", "已安排桌号", overview.dinnerAssignedCount(), ""),
                row("签到统计", "已签到", overview.checkedInCount(), ""),
                row("签到统计", "未签到", overview.notCheckedInCount(), ""),
                row("签到统计", "签到率", percent(overview.checkinRate()), ""),
                row("签到统计", "时间范围签到", overview.rangeCheckinCount(), "")
        );
        List<? extends List<?>> trendRows = overview.registrationTrend().keySet().stream()
                .map(date -> row(
                        date,
                        overview.registrationTrend().getOrDefault(date, 0L),
                        overview.checkinTrend().getOrDefault(date, 0L)
                ))
                .toList();
        List<List<?>> distributionRows = new ArrayList<>();
        addDistribution(distributionRows, "审核状态", overview.reviewStatusDistribution());
        addDistribution(distributionRows, "参会类型", overview.attendeeTypeDistribution());
        addDistribution(distributionRows, "单位", overview.organizationDistribution());
        addDistribution(distributionRows, "酒店", overview.hotelDistribution());
        addDistribution(distributionRows, "房型", overview.roomTypeDistribution());
        addDistribution(distributionRows, "签到入口", overview.checkinEntranceDistribution());
        addDistribution(distributionRows, "签到方式", overview.checkinTypeDistribution());
        return List.of(
                new ExportTable("统计概览", List.of("分组", "项目", "数值", "说明"), overviewRows),
                new ExportTable("趋势", List.of("日期", "报名新增", "签到人数"), trendRows),
                new ExportTable("分布", List.of("分布类型", "项目", "数量"), distributionRows)
        );
    }

    private void validateDateRange(String normalizedType, LocalDate startDate, LocalDate endDate) {
        boolean hasStart = startDate != null;
        boolean hasEnd = endDate != null;
        if (hasStart != hasEnd) {
            throw BusinessException.badRequest("自定义统计范围必须同时提供开始日期和结束日期");
        }
        if (!"statistics".equals(normalizedType) && (hasStart || hasEnd)) {
            throw BusinessException.badRequest("仅统计导出支持日期范围");
        }
        if (!hasStart) {
            return;
        }
        if (startDate.isAfter(endDate)) {
            throw BusinessException.badRequest("统计开始日期不能晚于结束日期");
        }
        if (startDate.plusDays(366).isBefore(endDate)) {
            throw BusinessException.badRequest("统计时间范围不能超过 366 天");
        }
    }

    private void ensureSupportedType(String normalizedType, String originalType) {
        switch (normalizedType) {
            case "attendees", "approved", "no-checkin", "rooms", "accommodations", "room-inventory", "dinner", "seats", "checkin", "checkin-records", "statistics" -> {
            }
            default -> throw BusinessException.badRequest("暂不支持的导出类型: " + originalType);
        }
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "attendees" : type.trim().toLowerCase(Locale.ROOT);
    }

    private List<?> row(Object... values) {
        return Arrays.asList(values);
    }

    private void addDistribution(List<List<?>> rows, String group, Map<String, Long> distribution) {
        distribution.forEach((key, value) -> rows.add(List.of(group, distributionLabel(group, key), value)));
    }

    private String distributionLabel(String group, String key) {
        return switch (group) {
            case "审核状态" -> ExportDisplayFormatter.reviewStatus(key);
            case "参会类型" -> ExportDisplayFormatter.attendeeType(key);
            case "签到方式" -> ExportDisplayFormatter.checkinType(key);
            default -> key;
        };
    }

    private String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String exportPhone(String phone, boolean masked) {
        if (phone == null) {
            return "";
        }
        return masked ? SensitiveDataMasker.maskPhone(phone) : phone;
    }

    private record TaskWork(
            Long taskId,
            Long meetingId,
            String type,
            ExportFormat format,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    private record TaskSnapshot(
            Long taskId,
            Long meetingId,
            String type,
            String format,
            ExportTaskStatus status,
            String filename,
            String filePath,
            Long fileSize,
            LocalDateTime expiresAt
    ) {
    }

    private record StreamingExport(
            String sheetName,
            List<String> headers,
            ExportRowProducer producer,
            int initialProgress
    ) {
    }

    private record CursorPage(List<List<?>> rows, long nextCursor, boolean last) {
    }

    private record ColumnPage(
            List<Map<String, Object>> snapshots,
            long nextCursor,
            int scannedRows,
            boolean last
    ) {
    }

    private record ExportRange(long maxId, long totalRows) {
    }

    private record DynamicColumn(String header, String valueKey, String labelKey) {
    }
}
