package com.meetingos.attendee;

import com.meetingos.common.BusinessException;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ExportTable;
import com.meetingos.common.ImportFailure;
import com.meetingos.common.ImportProgressListener;
import com.meetingos.common.ImportResult;
import com.meetingos.common.ImportRowTransactionRunner;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.TableFileReader;
import com.meetingos.common.TableRow;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.publicapi.PublicQueryType;
import com.meetingos.room.AssignRoomRequest;
import com.meetingos.room.RoomService;
import com.meetingos.security.SecurityUtils;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatAssignment;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import com.meetingos.tduck.TduckSubmissionFields;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AttendeeService {

    private static final int IMPORT_BATCH_SIZE = 50;

    private static final Set<String> KNOWN_IMPORT_HEADERS = Set.of(
            "姓名", "name", "参会人", "attendeename",
            "手机号", "手机", "电话", "phone", "mobile", "tel",
            "单位", "公司", "机构", "organization", "company",
            "职务", "职位", "title", "position",
            "证件号", "身份证号", "身份证", "identityno", "idcard", "idno",
            "邮箱", "电子邮箱", "email",
            "参会类型", "类型", "attendeetype", "type",
            "是否住宿", "住宿", "accommodationrequired", "needroom", "roomrequired",
            "房间号", "房号", "roomno", "room",
            "房间备注", "住宿备注", "roomremark",
            "备注", "remark", "note", "标签", "tags",
            "审核状态", "reviewstatus", "auditstatus"
    );

    private static final List<PublicQueryType> REJECTION_LOCK_QUERY_TYPES = List.of(
            PublicQueryType.SEAT,
            PublicQueryType.ROOM,
            PublicQueryType.DINNER
    );

    private final AttendeeRepository attendeeRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccessService meetingAccessService;
    private final SystemSettingsService systemSettingsService;
    private final TduckFieldMappingRepository tduckFieldMappingRepository;
    private final PublicQueryHistoryRepository publicQueryHistoryRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final DinnerAssignmentRepository dinnerAssignmentRepository;
    private final RoomService roomService;
    private final ImportRowTransactionRunner importRowTransactionRunner;
    private final AttendeeCodeGenerator attendeeCodeGenerator;
    private final AttendeeAttributionStateSynchronizer attributionStateSynchronizer;

    public AttendeeService(AttendeeRepository attendeeRepository,
                           MeetingRepository meetingRepository,
                           MeetingAccessService meetingAccessService,
                           SystemSettingsService systemSettingsService,
                           TduckFieldMappingRepository tduckFieldMappingRepository,
                           PublicQueryHistoryRepository publicQueryHistoryRepository,
                           SeatAssignmentRepository seatAssignmentRepository,
                           DinnerAssignmentRepository dinnerAssignmentRepository,
                           RoomService roomService) {
        this(
                attendeeRepository,
                meetingRepository,
                meetingAccessService,
                systemSettingsService,
                tduckFieldMappingRepository,
                publicQueryHistoryRepository,
                seatAssignmentRepository,
                dinnerAssignmentRepository,
                roomService,
                ImportRowTransactionRunner.direct(),
                new AttendeeCodeGenerator(attendeeRepository),
                AttendeeAttributionStateSynchronizer.NOOP
        );
    }

    public AttendeeService(AttendeeRepository attendeeRepository,
                           MeetingRepository meetingRepository,
                           MeetingAccessService meetingAccessService,
                           SystemSettingsService systemSettingsService,
                           TduckFieldMappingRepository tduckFieldMappingRepository,
                           PublicQueryHistoryRepository publicQueryHistoryRepository,
                           SeatAssignmentRepository seatAssignmentRepository,
                           DinnerAssignmentRepository dinnerAssignmentRepository,
                           RoomService roomService,
                           ImportRowTransactionRunner importRowTransactionRunner,
                           AttendeeCodeGenerator attendeeCodeGenerator) {
        this(
                attendeeRepository,
                meetingRepository,
                meetingAccessService,
                systemSettingsService,
                tduckFieldMappingRepository,
                publicQueryHistoryRepository,
                seatAssignmentRepository,
                dinnerAssignmentRepository,
                roomService,
                importRowTransactionRunner,
                attendeeCodeGenerator,
                AttendeeAttributionStateSynchronizer.NOOP
        );
    }

    @Autowired
    public AttendeeService(AttendeeRepository attendeeRepository,
                           MeetingRepository meetingRepository,
                           MeetingAccessService meetingAccessService,
                           SystemSettingsService systemSettingsService,
                           TduckFieldMappingRepository tduckFieldMappingRepository,
                           PublicQueryHistoryRepository publicQueryHistoryRepository,
                           SeatAssignmentRepository seatAssignmentRepository,
                           DinnerAssignmentRepository dinnerAssignmentRepository,
                           RoomService roomService,
                           ImportRowTransactionRunner importRowTransactionRunner,
                           AttendeeCodeGenerator attendeeCodeGenerator,
                           AttendeeAttributionStateSynchronizer attributionStateSynchronizer) {
        this.attendeeRepository = attendeeRepository;
        this.meetingRepository = meetingRepository;
        this.meetingAccessService = meetingAccessService;
        this.systemSettingsService = systemSettingsService;
        this.tduckFieldMappingRepository = tduckFieldMappingRepository;
        this.publicQueryHistoryRepository = publicQueryHistoryRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.dinnerAssignmentRepository = dinnerAssignmentRepository;
        this.roomService = roomService;
        this.importRowTransactionRunner = importRowTransactionRunner;
        this.attendeeCodeGenerator = attendeeCodeGenerator;
        this.attributionStateSynchronizer = attributionStateSynchronizer;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Attendee create(AttendeeRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        meetingAccessService.requireManage(meeting.getId());
        String normalizedPhone = normalizePhone(request.phone());
        attendeeRepository.findByMeetingAndPhone(meeting, normalizedPhone)
                .ifPresent(existing -> {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                });
        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        applyRequest(attendee, request);
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setAttendeeCode(generateAttendeeCode(meeting));
        Attendee saved = attendeeRepository.saveAndFlush(attendee);
        return synchronizeRoomRequest(saved, request, "ATTENDEE_CREATE");
    }

    @Transactional(readOnly = true)
    public PageResponse<Attendee> search(Long meetingId, String keyword, ReviewStatus reviewStatus,
                                         CheckinStatus checkinStatus, AttendeeType attendeeType,
                                         Boolean hasSeat, String tags, int page, int size) {
        return PageResponse.from(attendeeRepository.search(
                meetingId,
                blankToNull(keyword),
                reviewStatus,
                checkinStatus,
                attendeeType,
                hasSeat,
                blankToNull(tags),
                PageRequests.of(page, size)
        ));
    }

    @Transactional(readOnly = true)
    public Attendee detail(Long attendeeId) {
        return attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Attendee update(Long attendeeId, AttendeeRequest request) {
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        if (!attendee.getMeeting().getId().equals(meeting.getId())) {
            throw BusinessException.badRequest("不能通过更新接口变更参会人所属会议");
        }
        String normalizedPhone = normalizePhone(request.phone());
        attendeeRepository.findByMeetingAndPhone(meeting, normalizedPhone)
                .filter(existing -> !existing.getId().equals(attendee.getId()))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                });
        synchronizeRoomRequest(attendee, request, "ATTENDEE_UPDATE");
        applyRequest(attendee, request);
        return attendee;
    }

    @Transactional
    public Attendee review(Long attendeeId, ReviewRequest request) {
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        applyReview(attendee, request.reviewStatus(), request.reason(), request.remark());
        synchronizeAttributionState(attendee, request.reviewStatus() == ReviewStatus.APPROVED);
        return attendee;
    }

    @Transactional
    public int batchReview(ReviewRequest request) {
        if (request.attendeeIds() == null || request.attendeeIds().isEmpty()) {
            throw BusinessException.badRequest("批量审核必须提供 attendeeIds");
        }
        /*
         * 审核会更新 Attendee 整行。按 ID 固定顺序逐个加锁，既避免旧 roomNo 回写覆盖并发分房，
         * 也避免两个批量请求以相反顺序拿 attendee 锁形成死锁。
         */
        List<Attendee> attendees = request.attendeeIds().stream()
                .distinct()
                .sorted()
                .map(attendeeRepository::findByIdForUpdate)
                .flatMap(Optional::stream)
                .toList();
        attendees.forEach(attendee -> meetingAccessService.requireManage(attendee.getMeeting().getId()));
        int updated = 0;
        for (Attendee attendee : attendees) {
            if (applyReview(attendee, request.reviewStatus(), request.reason(), request.remark())) {
                updated++;
            }
            synchronizeAttributionState(attendee, request.reviewStatus() == ReviewStatus.APPROVED);
        }
        return updated;
    }

    @Transactional
    public Attendee updateTags(Long attendeeId, String tags) {
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        attendee.setTags(tags);
        return attendee;
    }

    @Transactional
    public Attendee updateRemark(Long attendeeId, String remark) {
        /*
         * 备注与完整资料更新共用 attendees 行。这里使用悲观写锁，避免备注保存与审核、
         * 分房或完整资料编辑并发时发生后提交事务覆盖先提交结果。
         */
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        attendee.setRemark(trimToNull(remark));
        return attendee;
    }

    @Transactional
    public Attendee cancel(Long attendeeId, String reason) {
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        attendee.setReviewStatus(ReviewStatus.CANCELLED);
        attendee.setReviewReason(reason == null || reason.isBlank() ? "管理员取消参会" : reason);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy(SecurityUtils.currentUsernameOrSystem());
        synchronizeAttributionState(attendee, false);
        return attendee;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Attendee assignRoom(Long attendeeId, String roomNo, String roomRemark) {
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        return synchronizeRoomRequest(
                attendee,
                roomNo == null || roomNo.isBlank() ? Boolean.FALSE : Boolean.TRUE,
                roomNo,
                roomRemark,
                "ATTENDEE_ASSIGN_ROOM"
        );
    }

    @Transactional(readOnly = true)
    public Attendee querySeatOrRoom(Long meetingId, String phone) {
        return attendeeRepository.findByMeetingIdAndPhone(meetingId, phone)
                .orElseThrow(() -> BusinessException.notFound("未找到参会人"));
    }

    public ImportResult importFile(Long meetingId, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        meetingAccessService.requireManage(meeting.getId());
        List<TableRow> rows = TableFileReader.read(file);
        return importRows(meetingId, rows, ImportProgressListener.NOOP);
    }

    public ImportResult importRows(Long meetingId, List<TableRow> rows, ImportProgressListener progressListener) {
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        Map<String, String> extensionFieldKeys = extensionFieldKeys(meetingId);
        List<ImportFailure> failures = new ArrayList<>();
        int imported = 0;

        for (int start = 0; start < rows.size(); start += IMPORT_BATCH_SIZE) {
            List<TableRow> batch = rows.subList(start, Math.min(start + IMPORT_BATCH_SIZE, rows.size()));
            if (importRowTransactionRunner.supportsBatchTransactions() && batch.size() > 1) {
                try {
                    importRowTransactionRunner.execute(() -> {
                        for (TableRow row : batch) {
                            importAttendeeRowWithinTransaction(meetingId, row, extensionFieldKeys);
                        }
                        return null;
                    });
                    imported += batch.size();
                    progressListener.onProgress(imported + failures.size(), imported, failures.size());
                    continue;
                } catch (RuntimeException ignored) {
                    // 整批已经回滚，下面逐行短事务重试，以保留原有失败行定位和部分成功语义。
                }
            }

            for (TableRow row : batch) {
                try {
                    importAttendeeRowWithCodeRetry(meetingId, row, extensionFieldKeys);
                    imported++;
                } catch (BusinessException ex) {
                    failures.add(new ImportFailure(row.rowNumber(), ex.getMessage()));
                } catch (RuntimeException ex) {
                    failures.add(new ImportFailure(row.rowNumber(), "数据格式不正确，请检查该行字段"));
                }
                progressListener.onProgress(imported + failures.size(), imported, failures.size());
            }
        }
        return ImportResult.of(rows.size(), imported, failures, "参会人");
    }

    private void importAttendeeRowWithCodeRetry(Long meetingId,
                                                TableRow row,
                                                Map<String, String> extensionFieldKeys) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                importRowTransactionRunner.execute(() -> {
                    importAttendeeRowWithinTransaction(meetingId, row, extensionFieldKeys);
                    return null;
                });
                return;
            } catch (DataIntegrityViolationException ex) {
                if (isAttendeeCodeCollision(ex) && attempt < 3) {
                    continue;
                }
                if (isAttendeeCodeCollision(ex)) {
                    throw BusinessException.conflict("参会码生成冲突，请重新提交导入");
                }
                if (constraintMessageContains(ex, "meeting_phone")) {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                }
                throw ex;
            }
        }
    }

    private void importAttendeeRowWithinTransaction(Long meetingId,
                                                    TableRow row,
                                                    Map<String, String> extensionFieldKeys) {
        Meeting managedMeeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        Attendee attendee = fromRow(managedMeeting, row, extensionFieldKeys);
        String requestedRoomNo = row.get("房间号", "房号", "roomNo", "room");
        Attendee saved = attendeeRepository.saveAndFlush(attendee);
        synchronizeRoomRequest(
                saved,
                attendee.isAccommodationRequired(),
                requestedRoomNo,
                attendee.getRoomRemark(),
                "ATTENDEE_IMPORT"
        );
        attendeeRepository.flush();
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long meetingId, String type) {
        ExportTable table = exportTable(meetingId, type);
        return CsvFiles.toCsv(table.headers(), table.rows());
    }

    @Transactional(readOnly = true)
    public ExportTable exportTable(Long meetingId, String type) {
        // 导出类型保持和通用导出接口一致，便于前端在专用按钮和“数据导出”页之间复用。
        String normalizedType = normalizeExportType(type);
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<Attendee> attendees = switch (normalizedType) {
            case "approved" -> attendeeRepository.findByMeetingIdAndReviewStatusOrderByIdAsc(meetingId, ReviewStatus.APPROVED);
            case "no-checkin" -> attendeeRepository.findByMeetingIdAndReviewStatusAndCheckinStatusOrderByIdAsc(
                    meetingId,
                    ReviewStatus.APPROVED,
                    CheckinStatus.NOT_CHECKED_IN
            );
            default -> attendeeRepository.findByMeetingIdOrderByIdAsc(meetingId);
        };
        List<AttendeeExportData.DynamicColumn> formColumns = formValueColumns(meetingId, attendees);
        Map<Long, AttendeeExportData.RelatedData> relatedData = exportRelatedData(meeting, attendees);
        boolean masked = systemSettingsService.maskSensitiveEnabled();
        var rows = attendees.stream()
                .map(attendee -> AttendeeExportData.row(attendee, relatedData.get(attendee.getId()), formColumns, masked))
                .toList();
        return new ExportTable(attendeeSheetName(normalizedType), AttendeeExportData.headers(formColumns), rows);
    }

    private void applyRequest(Attendee attendee, AttendeeRequest request) {
        attendee.setName(request.name());
        attendee.setPhone(normalizePhone(request.phone()));
        attendee.setOrganization(request.organization());
        attendee.setTitle(request.title());
        attendee.setIdentityNo(request.identityNo());
        attendee.setEmail(request.email());
        attendee.setAttendeeType(request.attendeeType() == null ? AttendeeType.NORMAL : request.attendeeType());
        attendee.setRemark(request.remark());
        attendee.setTags(request.tags());
    }

    private Attendee synchronizeRoomRequest(Attendee attendee, AttendeeRequest request, String source) {
        return synchronizeRoomRequest(
                attendee,
                request.accommodationRequired(),
                request.roomNo(),
                request.roomRemark(),
                source
        );
    }

    private Attendee synchronizeRoomRequest(Attendee attendee,
                                            Boolean accommodationRequired,
                                            String requestedRoomNo,
                                            String roomRemark,
                                            String source) {
        String targetRoomNo = blankToNull(requestedRoomNo);
        if (targetRoomNo != null) {
            // 指定房号即视为实际占用，统一由 RoomService 完成 attendee -> room 行锁和容量校验。
            return roomService.assignWithinTransaction(
                    new AssignRoomRequest(attendee.getId(), targetRoomNo, roomRemark),
                    source
            );
        }
        if (attendee.getRoomNo() != null && !attendee.getRoomNo().isBlank()) {
            roomService.clearAssignmentWithinTransaction(attendee.getId(), source + "_CLEAR");
        }
        // “需要住宿但尚未排房”仍是合法业务状态，不占用任何房间容量。
        attendee.setAccommodationRequired(Boolean.TRUE.equals(accommodationRequired));
        attendee.setRoomNo(null);
        attendee.setRoomRemark(roomRemark);
        return attendee;
    }

    private boolean applyReview(Attendee attendee, ReviewStatus status, String reason, String remark) {
        if (status == ReviewStatus.PENDING || status == ReviewStatus.CANCELLED) {
            throw BusinessException.badRequest("普通审核只支持通过或拒绝");
        }
        if (attendee.getReviewStatus() == status) {
            // 重复点击同一个审核结果时按幂等处理，不再刷新审核人/审核时间，避免制造多条无意义操作。
            return false;
        }
        if (!canApplyReview(attendee.getReviewStatus(), status)) {
            /*
             * TDuck 报名默认通过，因此普通审核入口允许“已通过 -> 已拒绝”。
             * 但已拒绝/已取消属于终态，不能再通过同一入口随意改回，避免误操作恢复无效报名。
             */
            throw BusinessException.badRequest("当前审核状态不允许执行该操作");
        }
        assertRejectable(attendee, status);
        attendee.setReviewStatus(status);
        attendee.setReviewReason(reason);
        attendee.setReviewRemark(remark);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy(SecurityUtils.currentUsernameOrSystem());
        return true;
    }

    private void synchronizeAttributionState(Attendee attendee, boolean active) {
        attributionStateSynchronizer.synchronize(attendee.getMeeting().getId(), attendee.getId(), active);
    }

    private boolean canApplyReview(ReviewStatus currentStatus, ReviewStatus targetStatus) {
        if (currentStatus == ReviewStatus.PENDING) {
            return targetStatus == ReviewStatus.APPROVED || targetStatus == ReviewStatus.REJECTED;
        }
        return currentStatus == ReviewStatus.APPROVED && targetStatus == ReviewStatus.REJECTED;
    }

    private void assertRejectable(Attendee attendee, ReviewStatus targetStatus) {
        if (targetStatus != ReviewStatus.REJECTED || attendee.getId() == null) {
            return;
        }
        /*
         * 已分配座位、住宿或晚宴说明后台已经把参会人纳入正式接待资源。
         * 此时直接拒绝会产生“资源仍占用但报名已拒绝”的脏状态，要求管理员先清空对应分配再审核。
         */
        if (seatAssignmentRepository.existsByAttendeeId(attendee.getId())) {
            throw BusinessException.badRequest("该参会人已分配座位，不能拒绝");
        }
        if (attendee.getRoomNo() != null && !attendee.getRoomNo().isBlank()) {
            throw BusinessException.badRequest("该参会人已分配住宿，不能拒绝");
        }
        if (dinnerAssignmentRepository.existsByAttendeeId(attendee.getId())) {
            throw BusinessException.badRequest("该参会人已分配晚宴，不能拒绝");
        }
        /*
         * 一旦参会人已经查过座位、住宿或晚宴结果，说明对方已按“通过报名”的状态进入公开查询链路。
         * 此时再拒绝会让已看到的信息和后台状态冲突，因此在后端统一拦截，防止绕过前端按钮直接调用审核接口。
         */
        if (publicQueryHistoryRepository.existsByAttendeeIdAndQueryTypeIn(attendee.getId(), REJECTION_LOCK_QUERY_TYPES)) {
            throw BusinessException.badRequest("该参会人已有查询记录，不能拒绝");
        }
    }

    private String generateAttendeeCode(Meeting meeting) {
        return attendeeCodeGenerator.generate(meeting);
    }

    private Attendee fromRow(Meeting meeting, TableRow row, Map<String, String> extensionFieldKeys) {
        String name = required(row, "姓名", "name", "参会人", "attendeename");
        String phone = normalizePhone(required(row, "手机号", "手机", "电话", "phone", "mobile", "tel"));
        attendeeRepository.findByMeetingAndPhone(meeting, phone)
                .ifPresent(existing -> {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                });

        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        attendee.setName(requireMaxLength(name, 120, "姓名"));
        attendee.setPhone(phone);
        attendee.setOrganization(optionalMaxLength(row.get("单位", "公司", "机构", "organization", "company"), 160, "单位"));
        attendee.setTitle(optionalMaxLength(row.get("职务", "职位", "title", "position"), 120, "职务"));
        attendee.setIdentityNo(optionalMaxLength(row.get("证件号", "身份证号", "身份证", "identityNo", "idcard", "idno"), 80, "证件号"));
        attendee.setEmail(optionalMaxLength(row.get("邮箱", "电子邮箱", "email"), 160, "邮箱"));
        attendee.setAttendeeType(parseAttendeeType(row.get("参会类型", "类型", "attendeeType", "type")));
        attendee.setAccommodationRequired(parseBoolean(row.get("是否住宿", "住宿", "accommodationRequired", "needRoom", "roomRequired")));
        attendee.setRoomRemark(optionalMaxLength(row.get("房间备注", "住宿备注", "roomRemark"), 160, "房间备注"));
        attendee.setRemark(optionalMaxLength(row.get("备注", "remark", "note"), 500, "备注"));
        attendee.setTags(optionalMaxLength(row.get("标签", "tags"), 300, "标签"));
        attendee.setReviewStatus(parseReviewStatus(row.get("审核状态", "reviewStatus", "auditStatus")));
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setAttendeeCode(generateAttendeeCode(meeting));
        if (attendee.getReviewStatus() != ReviewStatus.PENDING) {
            attendee.setReviewedAt(LocalDateTime.now());
            attendee.setReviewedBy(SecurityUtils.currentUsernameOrSystem());
        }
        Map<String, Object> snapshot = extensionFields(row, extensionFieldKeys);
        snapshot.put("source", "admin-import");
        attendee.setRawDataSnapshot(snapshot);
        return attendee;
    }

    private String required(TableRow row, String... aliases) {
        String value = row.get(aliases);
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(String.join("/", aliases) + "不能为空");
        }
        return value.trim();
    }

    private AttendeeType parseAttendeeType(String value) {
        if (value == null || value.isBlank()) {
            return AttendeeType.NORMAL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VIP", "贵宾" -> AttendeeType.VIP;
            case "GUEST", "嘉宾" -> AttendeeType.GUEST;
            case "MEDIA", "媒体" -> AttendeeType.MEDIA;
            case "STAFF", "工作人员", "员工" -> AttendeeType.STAFF;
            case "NORMAL", "普通", "普通参会人" -> AttendeeType.NORMAL;
            default -> {
                try {
                    yield AttendeeType.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    throw BusinessException.badRequest("参会类型不支持: " + value);
                }
            }
        };
    }

    private ReviewStatus parseReviewStatus(String value) {
        if (value == null || value.isBlank()) {
            return ReviewStatus.PENDING;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "PASS", "通过", "已通过" -> ReviewStatus.APPROVED;
            case "REJECTED", "拒绝", "已拒绝" -> ReviewStatus.REJECTED;
            case "CANCELLED", "CANCELED", "取消", "已取消" -> ReviewStatus.CANCELLED;
            case "PENDING", "待审核", "待审" -> ReviewStatus.PENDING;
            default -> {
                try {
                    yield ReviewStatus.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    throw BusinessException.badRequest("审核状态不支持: " + value);
                }
            }
        };
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y", "是", "需要", "住宿" -> true;
            case "false", "0", "no", "n", "否", "不需要", "不住宿" -> false;
            default -> throw BusinessException.badRequest("是否住宿仅支持是/否、需要/不需要或 true/false");
        };
    }

    private Map<String, Object> extensionFields(TableRow row, Map<String, String> extensionFieldKeys) {
        Map<String, Object> extension = new LinkedHashMap<>();
        row.rawValues().forEach((key, value) -> {
            String storageKey = extensionFieldKeys.get(key);
            if (!KNOWN_IMPORT_HEADERS.contains(key) && storageKey != null) {
                extension.put(storageKey, value);
            }
        });
        return extension;
    }

    private Map<String, String> extensionFieldKeys(Long meetingId) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (TduckFieldMapping mapping : tduckFieldMappingRepository.findByMeetingIdOrderByIdAsc(meetingId)) {
            String fieldKey = mapping.getTduckFieldKey();
            if (fieldKey == null || fieldKey.isBlank()) {
                continue;
            }
            keys.putIfAbsent(TableRow.normalizeHeader(fieldKey), fieldKey);
            keys.putIfAbsent(TableRow.normalizeHeader(fieldKey + "label"), fieldKey + "label");
            String fieldLabel = mapping.getTduckFieldLabel();
            if (fieldLabel != null && !fieldLabel.isBlank()) {
                keys.putIfAbsent(TableRow.normalizeHeader(fieldLabel), fieldKey);
            }
        }
        return keys;
    }

    private String requireMaxLength(String value, int maxLength, String label) {
        if (value.length() > maxLength) {
            throw BusinessException.badRequest(label + "不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private String optionalMaxLength(String value, int maxLength, String label) {
        return value == null ? null : requireMaxLength(value, maxLength, label);
    }

    private boolean isAttendeeCodeCollision(DataIntegrityViolationException ex) {
        return constraintMessageContains(ex, "attendee_code");
    }

    private boolean constraintMessageContains(DataIntegrityViolationException ex, String fragment) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(fragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeExportType(String type) {
        return type == null ? "attendees" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String attendeeSheetName(String normalizedType) {
        return switch (normalizedType) {
            case "approved" -> "审核通过名单";
            case "no-checkin" -> "未签到名单";
            default -> "参会名单";
        };
    }

    private Map<Long, AttendeeExportData.RelatedData> exportRelatedData(Meeting meeting, List<Attendee> attendees) {
        if (attendees.isEmpty()) {
            return Map.of();
        }
        List<Long> attendeeIds = attendees.stream().map(Attendee::getId).toList();
        Map<Long, SeatAssignment> seatAssignments = new LinkedHashMap<>();
        for (SeatAssignment assignment : seatAssignmentRepository
                .findWithAttendeeAndSeatByMeetingIdAndAttendeeIdIn(meeting.getId(), attendeeIds)) {
            seatAssignments.putIfAbsent(assignment.getAttendee().getId(), assignment);
        }
        Set<Long> seatQueried = Set.copyOf(publicQueryHistoryRepository
                .findQueriedAttendeeIds(meeting.getId(), PublicQueryType.SEAT, attendeeIds));
        Set<Long> roomQueried = Set.copyOf(publicQueryHistoryRepository
                .findQueriedAttendeeIds(meeting.getId(), PublicQueryType.ROOM, attendeeIds));

        Map<Long, AttendeeExportData.RelatedData> result = new LinkedHashMap<>();
        for (Attendee attendee : attendees) {
            result.put(attendee.getId(), new AttendeeExportData.RelatedData(
                    seatAssignments.get(attendee.getId()),
                    seatQueried.contains(attendee.getId()),
                    roomQueried.contains(attendee.getId())
            ));
        }
        return result;
    }

    private List<AttendeeExportData.DynamicColumn> formValueColumns(Long meetingId, List<Attendee> attendees) {
        Map<String, String> fieldLabels = tduckFieldLabels(meetingId);
        Map<String, AttendeeExportData.DynamicColumn> columns = new LinkedHashMap<>();
        fieldLabels.forEach((key, label) -> {
            if (hasExportableFormValueKey(key)) {
                columns.putIfAbsent(key, new AttendeeExportData.DynamicColumn(label, key, key + "label"));
            }
        });
        for (Attendee attendee : attendees) {
            Map<String, Object> snapshot = attendee.getRawDataSnapshot();
            if (snapshot == null || snapshot.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String key = entry.getKey();
                if (!hasExportableFormValueKey(key)) {
                    continue;
                }
                /*
                 * 动态报名字段属于参会人主数据的一部分，但字段结构来自 TDuck 表单，不能建固定列。
                 * CSV/XLSX 按首次出现顺序展开；如果已经同步过 TDuck 字段，表头使用填写页中文标签，
                 * 避免导出文件出现 input/radio 这类组件内部 key。
                 */
                columns.putIfAbsent(key, new AttendeeExportData.DynamicColumn(fieldLabels.getOrDefault(key, key), key, key + "label"));
            }
        }
        return new ArrayList<>(columns.values());
    }

    private Map<String, String> tduckFieldLabels(Long meetingId) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (meetingId == null) {
            return labels;
        }
        for (TduckFieldMapping mapping : tduckFieldMappingRepository.findByMeetingId(meetingId)) {
            String key = mapping.getTduckFieldKey();
            String label = mapping.getTduckFieldLabel();
            if (key != null && !key.isBlank() && label != null && !label.isBlank()) {
                labels.putIfAbsent(key, label.trim());
            }
        }
        return labels;
    }

    private boolean hasExportableFormValueKey(String key) {
        return key != null
                && !key.isBlank()
                && !TduckSubmissionFields.isMetaField(key)
                && !"source".equals(key)
                && !key.endsWith("label");
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw BusinessException.badRequest("手机号不能为空");
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("^\\+?\\d{6,20}$")) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
