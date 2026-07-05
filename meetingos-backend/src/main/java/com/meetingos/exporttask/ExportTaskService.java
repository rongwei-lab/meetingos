package com.meetingos.exporttask;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.checkin.CheckinRecord;
import com.meetingos.checkin.CheckinRecordRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ExportDisplayFormatter;
import com.meetingos.common.ExportTable;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.common.SpreadsheetFiles;
import com.meetingos.dinner.DinnerService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.room.RoomService;
import com.meetingos.security.SecurityUtils;
import com.meetingos.seating.SeatAssignment;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.statistics.StatisticsOverview;
import com.meetingos.statistics.StatisticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExportTaskService {

    private final AttendeeService attendeeService;
    private final RoomService roomService;
    private final DinnerService dinnerService;
    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final CheckinRecordRepository checkinRecordRepository;
    private final MeetingRepository meetingRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final SystemSettingsService systemSettingsService;
    private final StatisticsService statisticsService;

    public ExportTaskService(AttendeeService attendeeService,
                             RoomService roomService,
                             DinnerService dinnerService,
                             SeatRepository seatRepository,
                             SeatAssignmentRepository seatAssignmentRepository,
                             CheckinRecordRepository checkinRecordRepository,
                             MeetingRepository meetingRepository,
                             ExportTaskRepository exportTaskRepository,
                             SystemSettingsService systemSettingsService,
                             StatisticsService statisticsService) {
        this.attendeeService = attendeeService;
        this.roomService = roomService;
        this.dinnerService = dinnerService;
        this.seatRepository = seatRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.checkinRecordRepository = checkinRecordRepository;
        this.meetingRepository = meetingRepository;
        this.exportTaskRepository = exportTaskRepository;
        this.systemSettingsService = systemSettingsService;
        this.statisticsService = statisticsService;
    }

    @Transactional
    public ExportTaskResponse create(Long meetingId, String type) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        String normalizedType = normalizeType(type);
        ensureSupportedType(normalizedType, type);

        ExportTask task = new ExportTask();
        task.setMeeting(meeting);
        task.setExportType(normalizedType);
        task.setFilename(filename(normalizedType, ExportFormat.CSV));
        task.setRequestedBy(SecurityUtils.currentUsernameOrSystem());
        task.setStatus(ExportTaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setMessage("导出任务已生成，可直接下载");
        return toResponse(exportTaskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<ExportTaskResponse> list(Long meetingId) {
        if (meetingId == null) {
            throw BusinessException.badRequest("meetingId 不能为空");
        }
        return exportTaskRepository.findByMeetingIdOrderByCreatedAtDesc(meetingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExportFile build(Long meetingId, String type) {
        return build(meetingId, type, "csv", null, null);
    }

    @Transactional(readOnly = true)
    public ExportFile build(Long meetingId, String type, String format, LocalDate startDate, LocalDate endDate) {
        String normalizedType = normalizeType(type);
        ensureSupportedType(normalizedType, type);
        ExportFormat exportFormat = ExportFormat.from(format);
        List<ExportTable> tables = switch (normalizedType) {
            case "attendees" -> List.of(attendeeService.exportTable(meetingId, "attendees"));
            case "approved" -> List.of(attendeeService.exportTable(meetingId, "approved"));
            case "no-checkin" -> List.of(attendeeService.exportTable(meetingId, "no-checkin"));
            case "rooms", "accommodations" -> List.of(roomService.exportRoomsTable(meetingId));
            case "room-inventory" -> List.of(roomService.exportRoomInventoryTable(meetingId));
            case "dinner" -> List.of(dinnerService.exportDinnerTable(meetingId));
            case "seats" -> List.of(exportSeatsTable(meetingId));
            case "checkin", "checkin-records" -> List.of(exportCheckinTable(meetingId));
            case "statistics" -> exportStatisticsTables(meetingId, startDate, endDate);
            default -> throw BusinessException.badRequest("暂不支持的导出类型: " + type);
        };
        return render(normalizedType, exportFormat, tables);
    }

    @Transactional(readOnly = true)
    public void assertDownloadAllowed(Long meetingId, String type) {
        String normalizedType = normalizeType(type);
        ensureSupportedType(normalizedType, type);
        ensureDownloadAllowed(meetingId, normalizedType);
    }

    private void ensureDownloadAllowed(Long meetingId, String normalizedType) {
        /*
         * 无审批版：任务表仅记录导出请求，不再作为下载门禁。保留方法入口是为了兼容
         * 参会人/住宿等旧专用下载接口，后续若恢复审批也能集中调整策略。
         */
    }

    private void ensureSupportedType(String normalizedType, String originalType) {
        switch (normalizedType) {
            case "attendees", "approved", "no-checkin", "rooms", "accommodations", "room-inventory", "dinner", "seats", "checkin", "checkin-records", "statistics" -> {
            }
            default -> throw BusinessException.badRequest("暂不支持的导出类型: " + originalType);
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
                task.getStatus(),
                task.getFilename(),
                task.getRequestedBy(),
                task.getCompletedAt(),
                task.getMessage(),
                task.getCreatedAt()
        );
    }

    private String filename(String normalizedType) {
        return filename(normalizedType, ExportFormat.CSV);
    }

    private String filename(String normalizedType, ExportFormat format) {
        return "meetingos-" + normalizedType + "." + format.extension();
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
                List<Object> values = new ArrayList<>();
                values.add(table.sheetName());
                values.addAll(row);
                while (values.size() < headers.size()) {
                    values.add("");
                }
                rows.add(values.subList(0, headers.size()));
            }
        }
        return new ExportTable("导出数据", headers, rows);
    }

    private ExportTable exportSeatsTable(Long meetingId) {
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
                            attendee == null ? "" : exportPhone(attendee.getPhone()),
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

    private ExportTable exportCheckinTable(Long meetingId) {
        var rows = checkinRecordRepository.findWithAttendeeByMeetingIdOrderByIdAsc(meetingId).stream()
                .map(this::checkinRow)
                .toList();
        return new ExportTable("签到记录", List.of("记录ID", "姓名", "手机号", "签到方式", "结果", "入口", "操作人", "签到时间", "消息"), rows);
    }

    private List<?> checkinRow(CheckinRecord record) {
        Attendee attendee = record.getAttendee();
        return List.of(
                record.getId(),
                attendee == null ? "" : attendee.getName(),
                attendee == null ? "" : exportPhone(attendee.getPhone()),
                record.getCheckinType(),
                record.getResultStatus(),
                nullable(record.getEntrance()),
                nullable(record.getOperator()),
                nullable(record.getCheckedInAt()),
                nullable(record.getMessage())
        );
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "attendees" : type.trim().toLowerCase(Locale.ROOT);
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

    private String exportPhone(String phone) {
        if (phone == null) {
            return "";
        }
        return systemSettingsService.maskSensitiveEnabled() ? SensitiveDataMasker.maskPhone(phone) : phone;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
