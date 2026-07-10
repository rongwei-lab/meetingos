package com.meetingos.dinner;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ExportTable;
import com.meetingos.common.ImportFailure;
import com.meetingos.common.ImportResult;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.common.TableFileReader;
import com.meetingos.common.TableRow;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.security.SecurityUtils;
import com.meetingos.settings.SystemSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DinnerService {

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final DinnerAssignmentRepository dinnerAssignmentRepository;
    private final DinnerTableRepository dinnerTableRepository;
    private final OperationLogService operationLogService;
    private final SystemSettingsService systemSettingsService;

    public DinnerService(MeetingRepository meetingRepository,
                         AttendeeRepository attendeeRepository,
                         DinnerAssignmentRepository dinnerAssignmentRepository,
                         DinnerTableRepository dinnerTableRepository,
                         OperationLogService operationLogService,
                         SystemSettingsService systemSettingsService) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.dinnerAssignmentRepository = dinnerAssignmentRepository;
        this.dinnerTableRepository = dinnerTableRepository;
        this.operationLogService = operationLogService;
        this.systemSettingsService = systemSettingsService;
    }

    @Transactional(readOnly = true)
    public List<DinnerAssignmentResponse> list(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        return dinnerAssignmentRepository.findWithAttendeeByMeetingIdOrderByTableNoAscIdAsc(meetingId).stream()
                .map(assignment -> toResponse(assignment, meeting))
                .toList();
    }

    @Transactional(readOnly = true)
    public DinnerSettingsResponse settings(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        return toSettingsResponse(meeting);
    }

    @Transactional
    public DinnerSettingsResponse updateSettings(DinnerSettingsRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        /*
         * 晚宴地点和时间描述的是整场晚宴，不跟随桌号或个人分配。
         * 统一存在会议上后，导入、手动安排、桌号容量调整都不会意外覆盖全局信息。
         */
        meeting.setDinnerVenue(limit(blankToNull(request.venue()), 160));
        meeting.setDinnerTime(limit(blankToNull(request.dinnerTime()), 120));
        return toSettingsResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<DinnerTableResponse> listTables(Long meetingId) {
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        Map<String, Long> occupiedByTableNo = dinnerAssignmentRepository.countByMeetingIdGroupByTableNo(meetingId).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> row[1] instanceof Number number ? number.longValue() : 0L));
        return dinnerTableRepository.findByMeetingIdOrderByTableNoAsc(meetingId).stream()
                .map(table -> toTableResponse(table, occupiedByTableNo.getOrDefault(table.getTableNo(), 0L)))
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DinnerTableResponse createTable(DinnerTableRequest request) {
        // 新桌尚无可锁餐桌行，统一锁会议父行后再检查并创建，避免把“空查询”误当成互斥锁。
        Meeting meeting = lockMeetingForDinnerChange(request.meetingId());
        String tableNo = requiredText(request.tableNo(), "桌号不能为空", 80);
        if (dinnerTableRepository.existsByMeetingIdAndTableNo(meeting.getId(), tableNo)) {
            throw BusinessException.conflict("桌号已存在: " + tableNo);
        }
        long occupied = dinnerAssignmentRepository.countByMeetingIdAndTableNo(meeting.getId(), tableNo);
        int capacity = normalizedCapacity(request.capacity());
        if (occupied > capacity) {
            throw BusinessException.badRequest("当前桌已安排 " + occupied + " 人，容量不能小于已安排人数");
        }
        DinnerTable table = new DinnerTable();
        table.setMeeting(meeting);
        applyTable(table, request, tableNo);
        table.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
        return toTableResponse(dinnerTableRepository.save(table));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DinnerTableResponse updateTable(Long tableId, DinnerTableRequest request) {
        Long meetingId = dinnerTableRepository.findMeetingIdById(tableId)
                .orElseThrow(() -> BusinessException.notFound("桌号不存在"));
        if (!meetingId.equals(request.meetingId())) {
            throw BusinessException.badRequest("桌号不属于当前会议");
        }
        // 先按 meeting -> table 的固定顺序加锁，既保护容量调整，也与缺桌创建/重命名保持一致锁序。
        lockMeetingForDinnerChange(request.meetingId());
        DinnerTable table = lockDinnerTable(tableId);
        String oldTableNo = table.getTableNo();
        String newTableNo = requiredText(request.tableNo(), "桌号不能为空", 80);
        dinnerTableRepository.findByMeetingIdAndTableNo(request.meetingId(), newTableNo)
                .filter(existing -> !existing.getId().equals(tableId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("桌号已存在: " + newTableNo);
                });
        long occupied = dinnerAssignmentRepository.countByMeetingIdAndTableNo(request.meetingId(), oldTableNo);
        int capacity = normalizedCapacity(request.capacity());
        if (occupied > capacity) {
            throw BusinessException.badRequest("当前桌已安排 " + occupied + " 人，容量不能小于已安排人数");
        }
        if (!oldTableNo.equals(newTableNo) && dinnerAssignmentRepository.countByMeetingIdAndTableNo(request.meetingId(), newTableNo) > 0) {
            throw BusinessException.conflict("目标桌号已有晚宴安排，请先调整该桌人员");
        }
        applyTable(table, request, newTableNo);
        DinnerTable saved = dinnerTableRepository.save(table);
        if (!oldTableNo.equals(newTableNo)) {
            /*
             * 晚宴安排用桌号字符串保存，允许历史导入的自由桌号继续存在。
             * 管理端主动重命名桌号时同步已安排记录，避免统计被拆成“旧桌号”和“新桌号”两组。
             */
            dinnerAssignmentRepository.findByMeetingIdAndTableNo(request.meetingId(), oldTableNo).stream()
                    .forEach(assignment -> assignment.setTableNo(newTableNo));
        }
        return toTableResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteTable(Long meetingId, Long tableId) {
        Long actualMeetingId = dinnerTableRepository.findMeetingIdById(tableId)
                .orElseThrow(() -> BusinessException.notFound("桌号不存在"));
        if (!actualMeetingId.equals(meetingId)) {
            throw BusinessException.badRequest("桌号不属于当前会议");
        }
        lockMeetingForDinnerChange(meetingId);
        DinnerTable table = lockDinnerTable(tableId);
        long occupied = dinnerAssignmentRepository.countByMeetingIdAndTableNo(meetingId, table.getTableNo());
        if (occupied > 0) {
            throw BusinessException.badRequest("该桌已有就餐人员，不能删除");
        }
        dinnerTableRepository.delete(table);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DinnerAssignmentResponse assign(DinnerAssignRequest request) {
        Long meetingId = dinnerAssignmentRepository.findMeetingIdByAttendeeId(request.attendeeId())
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        lockMeetingForDinnerChange(meetingId);
        Attendee attendee = lockAttendeeForDinnerChange(request.attendeeId());
        DinnerAssignment assignment = upsertAssignment(attendee, request.tableNo(), request.remark(), "MANUAL");
        return toResponse(assignment, attendee.getMeeting());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clear(Long attendeeId) {
        Long meetingId = dinnerAssignmentRepository.findMeetingIdByAttendeeId(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        lockMeetingForDinnerChange(meetingId);
        Attendee attendee = lockAttendeeForDinnerChange(attendeeId);
        DinnerAssignment assignment = dinnerAssignmentRepository.findByAttendeeIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("该参会人尚未安排晚宴桌号"));
        String oldTableNo = assignment.getTableNo();
        lockExistingTableForClear(attendee.getMeeting().getId(), oldTableNo);
        dinnerAssignmentRepository.delete(assignment);
        recordDinnerChange(attendee, oldTableNo, null, "MANUAL_CLEAR", null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ImportResult importAssignments(Long meetingId, MultipartFile file) {
        // 整批导入持有会议锁，避免按文件行顺序累计多张桌锁时与另一批导入形成反向锁序。
        Meeting meeting = lockMeetingForDinnerChange(meetingId);
        List<TableRow> rows = TableFileReader.read(file);
        List<ImportFailure> failures = new ArrayList<>();
        int imported = 0;

        for (TableRow row : rows) {
            try {
                Attendee attendee = resolveAttendee(meetingId, row);
                String tableNo = required(row, "桌号", "餐桌号", "桌位", "tableNo", "table");
                attendee = lockAttendeeForDinnerChange(attendee.getId());
                String venue = row.get("晚宴地点", "用餐地点", "宴会厅", "餐厅", "venue", "dinnerVenue");
                String dinnerTime = row.get("晚宴时间", "用餐时间", "时间", "dinnerTime", "time");
                String remark = row.get("备注", "晚宴备注", "用餐备注", "remark", "dinnerRemark");
                backfillDinnerSettingsIfBlank(meeting, venue, dinnerTime);
                upsertAssignment(attendee, tableNo, remark, "IMPORT");
                imported++;
            } catch (BusinessException ex) {
                failures.add(new ImportFailure(row.rowNumber(), ex.getMessage()));
            } catch (RuntimeException ex) {
                failures.add(new ImportFailure(row.rowNumber(), "晚宴分配数据格式不正确，请检查该行字段"));
            }
        }
        return ImportResult.of(rows.size(), imported, failures, "晚宴分配");
    }

    @Transactional(readOnly = true)
    public DinnerQueryResult query(Long meetingId, Attendee attendee) {
        if (attendee.getReviewStatus() != ReviewStatus.APPROVED) {
            throw BusinessException.badRequest("报名尚未审核通过，暂不能查询晚宴桌号");
        }
        DinnerAssignment assignment = dinnerAssignmentRepository.findByMeetingIdAndAttendeeId(meetingId, attendee.getId())
                .orElseThrow(() -> BusinessException.notFound("尚未安排晚宴桌号"));
        return toQueryResult(assignment);
    }

    @Transactional(readOnly = true)
    public ExportTable exportDinnerTable(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        var rows = dinnerAssignmentRepository.findWithAttendeeByMeetingIdOrderByTableNoAscIdAsc(meetingId).stream()
                .map(assignment -> {
                    Attendee attendee = assignment.getAttendee();
                    return List.of(
                            attendee.getName(),
                            exportPhone(attendee.getPhone()),
                            nullable(attendee.getOrganization()),
                            nullable(attendee.getTitle()),
                            attendee.getAttendeeType(),
                            assignment.getTableNo(),
                            nullable(meeting.getDinnerVenue()),
                            nullable(meeting.getDinnerTime()),
                            nullable(assignment.getRemark())
                    );
                })
                .toList();
        return new ExportTable(
                "晚宴安排",
                List.of("姓名", "手机号", "单位", "职务", "参会类型", "桌号", "晚宴地点", "晚宴时间", "备注"),
                rows
        );
    }

    private DinnerAssignment upsertAssignment(Attendee attendee,
                                              String tableNo,
                                              String remark,
                                              String source) {
        String normalizedTableNo = requiredText(tableNo, "桌号不能为空", 80);
        DinnerAssignment assignment = dinnerAssignmentRepository
                .findByAttendeeIdForUpdate(attendee.getId())
                .orElseGet(DinnerAssignment::new);
        String oldTableNo = assignment.getTableNo();
        DinnerTable table = lockTablesForAssignment(attendee.getMeeting(), normalizedTableNo, oldTableNo);
        assertTableCapacity(attendee, table, oldTableNo);
        assignment.setMeeting(attendee.getMeeting());
        assignment.setAttendee(attendee);
        assignment.setTableNo(normalizedTableNo);
        assignment.setRemark(limit(blankToNull(remark), 500));
        assignment.setAssignedBy(SecurityUtils.currentUsernameOrSystem());
        DinnerAssignment saved = dinnerAssignmentRepository.save(assignment);
        recordDinnerChange(attendee, oldTableNo, normalizedTableNo, source, saved.getRemark());
        return saved;
    }

    private DinnerAssignmentResponse toResponse(DinnerAssignment assignment, Meeting meeting) {
        Attendee attendee = assignment.getAttendee();
        return new DinnerAssignmentResponse(
                assignment.getId(),
                meeting.getId(),
                attendee.getId(),
                attendee.getName(),
                attendee.getPhone(),
                attendee.getOrganization(),
                attendee.getTitle(),
                attendee.getAttendeeType().name(),
                assignment.getTableNo(),
                meeting.getDinnerVenue(),
                meeting.getDinnerTime(),
                assignment.getRemark(),
                assignment.getAssignedBy()
        );
    }

    private DinnerQueryResult toQueryResult(DinnerAssignment assignment) {
        Attendee attendee = assignment.getAttendee();
        Meeting meeting = assignment.getMeeting();
        return new DinnerQueryResult(
                attendee.getId(),
                attendee.getName(),
                attendee.getPhone(),
                attendee.getOrganization(),
                attendee.getTitle(),
                attendee.getEmail(),
                meeting.getName(),
                assignment.getTableNo(),
                meeting.getDinnerVenue(),
                meeting.getDinnerTime(),
                assignment.getRemark()
        );
    }

    private DinnerTableResponse toTableResponse(DinnerTable table) {
        return toTableResponse(table, dinnerAssignmentRepository.countByMeetingIdAndTableNo(table.getMeeting().getId(), table.getTableNo()));
    }

    private DinnerTableResponse toTableResponse(DinnerTable table, long occupied) {
        int capacity = normalizedCapacity(table.getCapacity());
        return new DinnerTableResponse(
                table.getId(),
                table.getMeeting().getId(),
                table.getTableNo(),
                capacity,
                occupied,
                Math.max(0, capacity - occupied),
                Math.max(0, occupied - capacity),
                null,
                null,
                table.getRemark(),
                table.getCreatedBy()
        );
    }

    private void applyTable(DinnerTable table, DinnerTableRequest request, String tableNo) {
        table.setTableNo(tableNo);
        table.setCapacity(normalizedCapacity(request.capacity()));
        table.setRemark(limit(blankToNull(request.remark()), 500));
    }

    private DinnerTable lockTablesForAssignment(Meeting meeting, String targetTableNo, String oldTableNo) {
        DinnerTable existing = dinnerTableRepository.findByMeetingIdAndTableNo(meeting.getId(), targetTableNo)
                .orElse(null);
        if (existing != null) {
            return lockExistingAssignmentTables(meeting.getId(), targetTableNo, oldTableNo);
        }

        /*
         * 导入旧格式分配表时可能只有“桌号”没有提前建桌。目标行不存在时，PESSIMISTIC_WRITE
         * 锁不到任何记录；改锁始终存在的会议父行并二次查询，确保并发创建者看到同一张真实桌。
         */
        Meeting lockedMeeting = lockMeetingForDinnerChange(meeting.getId());
        DinnerTable createdByAnotherTransaction = dinnerTableRepository
                .findByMeetingIdAndTableNo(meeting.getId(), targetTableNo)
                .orElse(null);
        if (createdByAnotherTransaction != null) {
            return lockExistingAssignmentTables(meeting.getId(), targetTableNo, oldTableNo);
        }

        if (oldTableNo != null && !oldTableNo.isBlank() && !oldTableNo.equals(targetTableNo)) {
            // 此分支已持有会议锁；若原桌仍存在，再锁真实原桌行，使清除/重分配与删除串行。
            dinnerTableRepository.findByMeetingIdAndTableNo(meeting.getId(), oldTableNo)
                    .flatMap(oldTable -> dinnerTableRepository.findByIdForUpdate(oldTable.getId()));
        }

        DinnerTable table = new DinnerTable();
        table.setMeeting(lockedMeeting);
        table.setTableNo(targetTableNo);
        table.setCapacity(10);
        table.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
        // 立即插入，使后续容量统计针对真实餐桌行执行；会议锁会一直持有到事务提交。
        return dinnerTableRepository.saveAndFlush(table);
    }

    private DinnerTable lockExistingAssignmentTables(Long meetingId, String targetTableNo, String oldTableNo) {
        List<String> tableNos = new ArrayList<>();
        tableNos.add(targetTableNo);
        if (oldTableNo != null && !oldTableNo.isBlank() && !oldTableNo.equals(targetTableNo)) {
            tableNos.add(oldTableNo);
        }
        // 两名参会人互换餐桌时也按桌号排序拿锁，避免目标桌与原桌形成反向锁序。
        tableNos.sort(String::compareTo);
        return dinnerTableRepository.findByMeetingIdAndTableNoInForUpdate(meetingId, tableNos).stream()
                .filter(table -> targetTableNo.equals(table.getTableNo()))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("桌号不存在"));
    }

    private void lockExistingTableForClear(Long meetingId, String tableNo) {
        if (tableNo == null || tableNo.isBlank()) {
            return;
        }
        // 历史自由桌号可能没有 dinner_tables 行；存在时锁真实行，不改变缺表也可清除的旧行为。
        dinnerTableRepository.findByMeetingIdAndTableNo(meetingId, tableNo)
                .flatMap(table -> dinnerTableRepository.findByIdForUpdate(table.getId()));
    }

    private Attendee lockAttendeeForDinnerChange(Long attendeeId) {
        return attendeeRepository.findByIdForUpdate(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
    }

    private DinnerTable lockDinnerTable(Long tableId) {
        return dinnerTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> BusinessException.notFound("桌号不存在"));
    }

    private Meeting lockMeetingForDinnerChange(Long meetingId) {
        return dinnerTableRepository.findMeetingByIdForUpdate(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
    }

    private DinnerSettingsResponse toSettingsResponse(Meeting meeting) {
        return new DinnerSettingsResponse(
                meeting.getId(),
                meeting.getDinnerVenue(),
                meeting.getDinnerTime()
        );
    }

    private void backfillDinnerSettingsIfBlank(Meeting meeting, String venue, String dinnerTime) {
        String normalizedVenue = limit(blankToNull(venue), 160);
        String normalizedDinnerTime = limit(blankToNull(dinnerTime), 120);
        if (meeting.getDinnerVenue() == null && normalizedVenue != null) {
            meeting.setDinnerVenue(normalizedVenue);
        }
        if (meeting.getDinnerTime() == null && normalizedDinnerTime != null) {
            meeting.setDinnerTime(normalizedDinnerTime);
        }
    }

    private void assertTableCapacity(Attendee attendee, DinnerTable table, String oldTableNo) {
        // 已有桌持有 PESSIMISTIC_WRITE；新桌由插入锁和会议父行锁保护，均覆盖计数与 assignment 写入。
        long occupied = dinnerAssignmentRepository.countByMeetingIdAndTableNo(attendee.getMeeting().getId(), table.getTableNo());
        boolean alreadyInThisTable = table.getTableNo().equals(oldTableNo);
        if (!alreadyInThisTable && occupied >= normalizedCapacity(table.getCapacity())) {
            throw BusinessException.badRequest("该桌已达到可就餐人数上限");
        }
    }

    private Attendee resolveAttendee(Long meetingId, TableRow row) {
        String phone = row.get("手机号", "手机", "电话", "phone", "mobile");
        if (phone != null && !phone.isBlank()) {
            return attendeeRepository.findByMeetingIdAndPhone(meetingId, phone.trim())
                    .orElseThrow(() -> BusinessException.notFound("未找到手机号对应参会人: " + phone));
        }
        String identityNo = row.get("证件号", "身份证号", "身份证", "identityNo", "idcard", "idno");
        if (identityNo != null && !identityNo.isBlank()) {
            return attendeeRepository.findByMeetingIdAndIdentityNo(meetingId, identityNo.trim())
                    .orElseThrow(() -> BusinessException.notFound("未找到证件号对应参会人: " + identityNo));
        }
        String name = row.get("姓名", "name", "参会人");
        String organization = row.get("单位", "公司", "机构", "organization", "company");
        if (name != null && !name.isBlank() && organization != null && !organization.isBlank()) {
            List<Attendee> matched = attendeeRepository.findByMeetingIdAndNameAndOrganization(meetingId, name.trim(), organization.trim());
            if (matched.size() == 1) {
                return matched.get(0);
            }
            if (matched.isEmpty()) {
                throw BusinessException.notFound("未找到姓名+单位对应参会人: " + name + "/" + organization);
            }
            throw BusinessException.conflict("姓名+单位匹配到多名参会人，请改用手机号或证件号");
        }
        throw BusinessException.badRequest("请提供手机号、证件号，或姓名+单位用于匹配参会人");
    }

    private String required(TableRow row, String... aliases) {
        String value = row.get(aliases);
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(String.join("/", aliases) + "不能为空");
        }
        return value.trim();
    }

    private String requiredText(String value, String message, int maxLength) {
        String text = blankToNull(value);
        if (text == null) {
            throw BusinessException.badRequest(message);
        }
        return limit(text, maxLength);
    }

    private int normalizedCapacity(Integer capacity) {
        return capacity == null || capacity < 1 ? 1 : capacity;
    }

    private String blankToNull(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isBlank() ? null : text;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String exportPhone(String phone) {
        if (phone == null) {
            return "";
        }
        return systemSettingsService.maskSensitiveEnabled() ? SensitiveDataMasker.maskPhone(phone) : phone;
    }

    private void recordDinnerChange(Attendee attendee, String oldTableNo, String newTableNo, String source, String remark) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("meetingId", attendee.getMeeting().getId());
        details.put("attendeeId", attendee.getId());
        details.put("attendeeName", attendee.getName());
        details.put("oldTableNo", oldTableNo);
        details.put("newTableNo", newTableNo);
        details.put("source", source);
        details.put("remark", remark);
        operationLogService.record(
                "DINNER_ASSIGNMENT_CHANGE",
                "DINNER",
                attendee.getId() == null ? null : attendee.getId().toString(),
                true,
                "晚宴桌号变更",
                null,
                details
        );
    }
}
