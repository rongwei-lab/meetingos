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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return dinnerAssignmentRepository.findByMeetingIdOrderByTableNoAscIdAsc(meetingId).stream()
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
        return dinnerTableRepository.findByMeetingIdOrderByTableNoAsc(meetingId).stream()
                .map(this::toTableResponse)
                .toList();
    }

    @Transactional
    public DinnerTableResponse createTable(DinnerTableRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
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

    @Transactional
    public DinnerTableResponse updateTable(Long tableId, DinnerTableRequest request) {
        DinnerTable table = dinnerTableRepository.findById(tableId)
                .orElseThrow(() -> BusinessException.notFound("桌号不存在"));
        if (!table.getMeeting().getId().equals(request.meetingId())) {
            throw BusinessException.badRequest("桌号不属于当前会议");
        }
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
            dinnerAssignmentRepository.findByMeetingIdOrderByTableNoAscIdAsc(request.meetingId()).stream()
                    .filter(assignment -> oldTableNo.equals(assignment.getTableNo()))
                    .forEach(assignment -> assignment.setTableNo(newTableNo));
        }
        return toTableResponse(saved);
    }

    @Transactional
    public void deleteTable(Long meetingId, Long tableId) {
        DinnerTable table = dinnerTableRepository.findById(tableId)
                .orElseThrow(() -> BusinessException.notFound("桌号不存在"));
        if (!table.getMeeting().getId().equals(meetingId)) {
            throw BusinessException.badRequest("桌号不属于当前会议");
        }
        long occupied = dinnerAssignmentRepository.countByMeetingIdAndTableNo(meetingId, table.getTableNo());
        if (occupied > 0) {
            throw BusinessException.badRequest("该桌已有就餐人员，不能删除");
        }
        dinnerTableRepository.delete(table);
    }

    @Transactional
    public DinnerAssignmentResponse assign(DinnerAssignRequest request) {
        Attendee attendee = attendeeRepository.findById(request.attendeeId())
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        DinnerAssignment assignment = upsertAssignment(attendee, request.tableNo(), request.remark(), "MANUAL");
        return toResponse(assignment, attendee.getMeeting());
    }

    @Transactional
    public void clear(Long attendeeId) {
        DinnerAssignment assignment = dinnerAssignmentRepository.findByAttendeeId(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("该参会人尚未安排晚宴桌号"));
        Attendee attendee = assignment.getAttendee();
        String oldTableNo = assignment.getTableNo();
        dinnerAssignmentRepository.delete(assignment);
        recordDinnerChange(attendee, oldTableNo, null, "MANUAL_CLEAR", null);
    }

    @Transactional
    public ImportResult importAssignments(Long meetingId, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<TableRow> rows = TableFileReader.read(file);
        List<ImportFailure> failures = new ArrayList<>();
        int imported = 0;

        for (TableRow row : rows) {
            try {
                Attendee attendee = resolveAttendee(meetingId, row);
                String tableNo = required(row, "桌号", "餐桌号", "桌位", "tableNo", "table");
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
        var rows = dinnerAssignmentRepository.findByMeetingIdOrderByTableNoAscIdAsc(meetingId).stream()
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
                .findByMeetingIdAndAttendeeId(attendee.getMeeting().getId(), attendee.getId())
                .orElseGet(DinnerAssignment::new);
        String oldTableNo = assignment.getTableNo();
        DinnerTable table = ensureTable(attendee.getMeeting(), normalizedTableNo);
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
                assignment.getMeeting().getId(),
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
        long occupied = dinnerAssignmentRepository.countByMeetingIdAndTableNo(table.getMeeting().getId(), table.getTableNo());
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

    private DinnerTable ensureTable(Meeting meeting, String tableNo) {
        return dinnerTableRepository.findByMeetingIdAndTableNo(meeting.getId(), tableNo)
                .orElseGet(() -> {
                    /*
                     * 导入旧格式分配表时可能只有“桌号”没有提前建桌。
                     * 这里自动补一张默认 10 人桌，保证老流程继续可用；管理员可随后在桌号管理里调整容量。
                     */
                    DinnerTable table = new DinnerTable();
                    table.setMeeting(meeting);
                    table.setTableNo(tableNo);
                    table.setCapacity(10);
                    table.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
                    return dinnerTableRepository.save(table);
                });
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
