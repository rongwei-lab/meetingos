package com.meetingos.seating;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SeatChartImportService {

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatLayoutRepository layoutRepository;
    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository assignmentRepository;
    private final SeatingService seatingService;

    public SeatChartImportService(MeetingRepository meetingRepository,
                                  AttendeeRepository attendeeRepository,
                                  SeatLayoutRepository layoutRepository,
                                  SeatRepository seatRepository,
                                  SeatAssignmentRepository assignmentRepository,
                                  SeatingService seatingService) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.layoutRepository = layoutRepository;
        this.seatRepository = seatRepository;
        this.assignmentRepository = assignmentRepository;
        this.seatingService = seatingService;
    }

    /**
     * 会议现场导入必须是全有或全无：先解析和匹配，再在同一事务中生成布局、写入禁用空座、过道和分配。
     * 已有布局时直接拒绝，避免错误文件覆盖人工维护过的座位。
     */
    @Transactional
    public SeatChartImportResult importChart(Long meetingId,
                                             String venueName,
                                             String areaName,
                                             MultipartFile file) {
        SeatChartFileReader.SeatChart chart = SeatChartFileReader.read(file);
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        if (seatRepository.countByMeetingId(meetingId) > 0
                || !layoutRepository.findByMeetingId(meetingId).isEmpty()
                || assignmentRepository.countByMeetingId(meetingId) > 0) {
            throw BusinessException.conflict("当前会议已有座位图或分配记录，不能直接覆盖导入");
        }

        String effectiveVenueName = firstNonBlank(venueName, meeting.getVenueName(), "主会场");
        String effectiveAreaName = firstNonBlank(areaName, "主会场");
        // 无论是姓名之间的实际空白格，还是较短行按最大列数补齐的位置，都会生成座位并停用。
        // 不使用 structuralBlank，以便管理端仍把它们作为可见的座位位置展示；通道/过道标记则单独
        // 保存为 aisle，使客户端能以非座位样式渲染且统计时不把它误算为禁用座位。
        Set<String> disabledSeatCodes = chart.cells().stream()
                .filter(cell -> !cell.aisle() && cell.name().isBlank())
                .map(cell -> seatCode(effectiveAreaName, cell.rowNo(), cell.seatNo()))
                .collect(Collectors.toSet());
        Set<String> aisleSeatCodes = chart.cells().stream()
                .filter(SeatChartFileReader.SeatChartCell::aisle)
                .map(cell -> seatCode(effectiveAreaName, cell.rowNo(), cell.seatNo()))
                .collect(Collectors.toSet());
        List<Seat> seats = seatingService.createLayout(new SeatLayoutRequest(
                meetingId,
                effectiveVenueName,
                effectiveAreaName,
                null,
                chart.rowCount(),
                chart.seatsPerRow(),
                "row-seat",
                "",
                "北",
                0,
                SeatType.NORMAL,
                List.copyOf(disabledSeatCodes),
                List.of(),
                List.copyOf(aisleSeatCodes)
        ));
        Map<String, Seat> seatByCode = seats.stream().collect(Collectors.toMap(Seat::getSeatCode, seat -> seat));

        Map<String, Long> chartNameCounts = chart.cells().stream()
                .filter(cell -> !cell.aisle() && !cell.name().isBlank())
                .collect(Collectors.groupingBy(
                        cell -> normalizeName(cell.name()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<String, List<Attendee>> attendeesByName = attendeeRepository
                .findByMeetingIdAndReviewStatus(meetingId, ReviewStatus.APPROVED)
                .stream()
                .collect(Collectors.groupingBy(
                        attendee -> normalizeName(attendee.getName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<SeatAssignment> assignments = new ArrayList<>();
        List<SeatChartImportIssue> issues = new ArrayList<>();
        for (SeatChartFileReader.SeatChartCell cell : chart.cells()) {
            if (cell.aisle() || cell.name().isBlank()) {
                continue;
            }
            String normalizedName = normalizeName(cell.name());
            if (chartNameCounts.getOrDefault(normalizedName, 0L) != 1) {
                issues.add(new SeatChartImportIssue(cell.rowNo(), cell.seatNo(), cell.name(), "姓名在座位表中重复，未自动分配"));
                continue;
            }
            List<Attendee> candidates = attendeesByName.getOrDefault(normalizedName, List.of());
            if (candidates.isEmpty()) {
                issues.add(new SeatChartImportIssue(cell.rowNo(), cell.seatNo(), cell.name(), "未找到同名的已审核参会人"));
                continue;
            }
            if (candidates.size() > 1) {
                issues.add(new SeatChartImportIssue(cell.rowNo(), cell.seatNo(), cell.name(), "系统中存在多个同名已审核参会人，未自动分配"));
                continue;
            }
            Seat seat = seatByCode.get(seatCode(effectiveAreaName, cell.rowNo(), cell.seatNo()));
            if (seat == null || seat.isStructuralBlank() || seat.isAisle()) {
                throw BusinessException.badRequest("座位图坐标不完整，无法导入");
            }
            assignments.add(newAssignment(meeting, candidates.get(0), seat));
        }
        assignmentRepository.saveAll(assignments);

        return new SeatChartImportResult(
                chart.rowCount(),
                chart.seatsPerRow(),
                chart.cells().size() - chart.aisleCount(),
                0,
                chart.aisleCount(),
                chart.namedSeatCount(),
                assignments.size(),
                issues.size(),
                List.copyOf(issues)
        );
    }

    private SeatAssignment newAssignment(Meeting meeting, Attendee attendee, Seat seat) {
        SeatAssignment assignment = new SeatAssignment();
        assignment.setMeeting(meeting);
        assignment.setAttendee(attendee);
        assignment.setSeat(seat);
        assignment.setManualAssigned(true);
        assignment.setAssignedBy(SecurityUtils.currentUsernameOrSystem());
        return assignment;
    }

    private String seatCode(String areaName, int rowNo, int seatNo) {
        return areaName + "-" + rowNo + "-" + seatNo;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "主会场";
    }
}
