package com.meetingos.seating;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SeatingService {

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatLayoutRepository layoutRepository;
    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository assignmentRepository;

    public SeatingService(MeetingRepository meetingRepository,
                          AttendeeRepository attendeeRepository,
                          SeatLayoutRepository layoutRepository,
                          SeatRepository seatRepository,
                          SeatAssignmentRepository assignmentRepository) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.layoutRepository = layoutRepository;
        this.seatRepository = seatRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public List<Seat> createLayout(SeatLayoutRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        String areaName = normalizeAreaName(request.areaName());
        String lookupAreaName = normalizeOriginalAreaName(request.originalAreaName(), areaName);
        boolean renamed = !lookupAreaName.equals(areaName);
        List<SeatLayout> existingLayouts = layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(request.meetingId(), lookupAreaName);
        List<Seat> existingSeats = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(request.meetingId(), lookupAreaName);
        if (renamed) {
            ensureAreaCanBeRenamed(request.meetingId(), areaName, existingLayouts, existingSeats);
        }

        SeatLayout layout = existingLayouts
                .stream()
                .findFirst()
                .orElseGet(SeatLayout::new);
        layout.setMeeting(meeting);
        applyLayoutRequest(layout, request, areaName);
        layoutRepository.save(layout);

        boolean overrideDisabledSeats = request.disabledSeatCodes() != null;
        Set<String> disabledCodes = overrideDisabledSeats ? new HashSet<>(request.disabledSeatCodes()) : Set.of();
        SeatType requestedSeatType = request.defaultSeatType() == null ? SeatType.NORMAL : request.defaultSeatType();
        Map<String, Seat> existingByPosition = existingSeats.stream()
                .collect(Collectors.toMap(this::positionKey, seat -> seat));
        List<Seat> seatsToDelete = seatsOutsideLayout(existingSeats, request.rowCount(), request.seatsPerRow());
        ensureSeatsCanBeRemoved(seatsToDelete);

        // 管理员调整座位数量时，同一区域按“创建或更新”处理：
        // 1. 已存在且仍在新范围内的座位保留 ID、锁定、分配状态，避免丢失手动排座结果。
        // 2. 默认座位类型是区域级配置，管理员在布局弹窗切换后应覆盖该区域当前范围内座位类型。
        // 3. 新增出来的位置补建座位。
        // 4. 缩小范围时只删除未锁定且未分配的座位，防止误删业务结果。
        List<Seat> seatsToSave = new ArrayList<>();
        for (int row = 1; row <= request.rowCount(); row++) {
            for (int no = 1; no <= request.seatsPerRow(); no++) {
                String seatCode = areaName + "-" + row + "-" + no;
                Seat seat = existingByPosition.get(row + "-" + no);
                if (seat == null) {
                    seat = new Seat();
                    seat.setMeeting(meeting);
                    seat.setRowNo(row);
                    seat.setSeatNo(no);
                    seat.setDisabled(disabledCodes.contains(seatCode));
                }
                seat.setLayout(layout);
                // 区域改名时复用原座位 ID，保证已有手动排座/签到关系继续指向同一个座位。
                seat.setAreaName(areaName);
                seat.setSeatCode(seatCode);
                seat.setSeatType(requestedSeatType);
                if (overrideDisabledSeats) {
                    seat.setDisabled(disabledCodes.contains(seatCode));
                }
                seatsToSave.add(seat);
            }
        }
        if (!seatsToDelete.isEmpty()) {
            seatRepository.deleteAll(seatsToDelete);
        }
        seatRepository.saveAll(seatsToSave);
        return seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(request.meetingId(), areaName);
    }

    @Transactional
    public void deleteArea(Long meetingId, String areaName) {
        if (areaName == null || areaName.isBlank()) {
            throw BusinessException.badRequest("区域名称不能为空");
        }
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));

        String normalizedAreaName = areaName.trim();
        List<Seat> areaSeats = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(meetingId, normalizedAreaName);
        if (areaSeats.isEmpty() && layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(meetingId, normalizedAreaName).isEmpty()) {
            throw BusinessException.notFound("区域不存在");
        }

        // 删除区域只按“是否已有人员入座”拦截；空区域即使曾设置过锁定/禁用座位，也允许整体删除。
        // 前端会提前提示，但后端仍作为最后防线，防止并发操作或绕过 UI 导致误删座位分配结果。
        ensureAreaHasNoAssignments(areaSeats);
        if (!areaSeats.isEmpty()) {
            seatRepository.deleteAll(areaSeats);
        }
        layoutRepository.deleteAll(layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(meetingId, normalizedAreaName));
    }

    @Transactional
    public AutoSeatingResult autoAssign(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<Attendee> approved = attendeeRepository.findByMeetingIdAndReviewStatus(meetingId, ReviewStatus.APPROVED);
        Set<Long> alreadyAssignedAttendeeIds = assignmentRepository.findByMeetingId(meetingId).stream()
                .map(assignment -> assignment.getAttendee().getId())
                .collect(Collectors.toSet());

        List<Attendee> candidates = approved.stream()
                .filter(attendee -> !alreadyAssignedAttendeeIds.contains(attendee.getId()))
                .sorted(attendeeComparator())
                .toList();
        if (candidates.isEmpty()) {
            return new AutoSeatingResult(0, 0, List.of("没有需要自动排座的已审核参会人"));
        }

        Map<Long, SeatAssignment> assignedBySeat = assignmentRepository.findByMeetingId(meetingId).stream()
                .collect(Collectors.toMap(assignment -> assignment.getSeat().getId(), assignment -> assignment));
        List<Seat> availableSeats = seatRepository.findByMeetingIdAndDisabledFalseOrderByRowNoAscSeatNoAsc(meetingId).stream()
                .filter(seat -> !seat.isLocked())
                .filter(seat -> !assignedBySeat.containsKey(seat.getId()))
                .toList();

        List<String> warnings = new ArrayList<>();
        int assigned = 0;

        // 自动排座先按参会类型分组，再按单位分组。这样能满足“VIP/嘉宾优先”以及“同单位尽量相邻”的 MVP 规则。
        for (Map.Entry<AttendeeType, List<Attendee>> typeEntry : groupByType(candidates).entrySet()) {
            Map<String, List<Attendee>> byOrg = typeEntry.getValue().stream()
                    .collect(Collectors.groupingBy(
                            attendee -> attendee.getOrganization() == null ? "" : attendee.getOrganization(),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            for (List<Attendee> group : byOrg.values()) {
                List<Seat> reserved = reserveSeatsForGroup(typeEntry.getKey(), group.size(), availableSeats);
                if (reserved.isEmpty()) {
                    warnings.add("座位不足，未能安排: " + group.stream().map(Attendee::getName).collect(Collectors.joining(",")));
                    continue;
                }
                int assignable = Math.min(group.size(), reserved.size());
                for (int i = 0; i < assignable; i++) {
                    saveAssignment(meeting, group.get(i), reserved.get(i), false);
                    assigned++;
                }
                availableSeats = removeSeats(availableSeats, reserved.subList(0, assignable));
                if (assignable < group.size()) {
                    warnings.add("座位不足，部分同单位人员未排座: " + group.get(0).getOrganization());
                }
            }
        }
        return new AutoSeatingResult(assigned, candidates.size() - assigned, warnings);
    }

    @Transactional
    public SeatAssignment manualAssign(ManualAssignSeatRequest request) {
        Attendee attendee = attendeeRepository.findById(request.attendeeId())
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        Seat seat = seatRepository.findById(request.seatId())
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        ensureAssignable(attendee, seat);
        assignmentRepository.findByAttendeeId(attendee.getId()).ifPresent(assignmentRepository::delete);
        assignmentRepository.findBySeatId(seat.getId()).ifPresent(existing -> {
            throw BusinessException.conflict("座位已被分配，请先清空或交换座位");
        });
        return saveAssignment(attendee.getMeeting(), attendee, seat, true);
    }

    @Transactional
    public void swap(SwapSeatRequest request) {
        if (Objects.equals(request.firstAttendeeId(), request.secondAttendeeId())) {
            throw BusinessException.badRequest("不能和同一个参会人交换座位");
        }
        SeatAssignment first = assignmentRepository.findByAttendeeId(request.firstAttendeeId())
                .orElseThrow(() -> BusinessException.notFound("第一个参会人未分配座位"));
        SeatAssignment second = assignmentRepository.findByAttendeeId(request.secondAttendeeId())
                .orElseThrow(() -> BusinessException.notFound("第二个参会人未分配座位"));
        if (!Objects.equals(first.getMeeting().getId(), second.getMeeting().getId())) {
            throw BusinessException.badRequest("不能跨会议交换座位");
        }
        Meeting meeting = first.getMeeting();
        Attendee firstAttendee = first.getAttendee();
        Attendee secondAttendee = second.getAttendee();
        Seat firstSeat = first.getSeat();
        Seat secondSeat = second.getSeat();

        /*
         * seat_assignments 同时对 attendee_id 和 seat_id 建了唯一索引。
         * 如果直接把两条记录的 seat_id 对调，MySQL 会在第一条 update 刷新时看到
         * “目标座位仍被另一条记录占用”的中间状态，进而抛 Duplicate entry。
         * 这里先删除并 flush 两条旧分配，显式释放唯一键，再按互换结果重新创建。
         */
        assignmentRepository.deleteAll(List.of(first, second));
        assignmentRepository.flush();
        saveAssignment(meeting, firstAttendee, secondSeat, true);
        saveAssignment(meeting, secondAttendee, firstSeat, true);
    }

    @Transactional
    public void clear(Long attendeeId) {
        assignmentRepository.deleteByAttendeeId(attendeeId);
    }

    @Transactional
    public Seat lock(Long seatId, SeatLockRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        seat.setLocked(request.locked());
        return seat;
    }

    @Transactional
    public Seat updateSeatType(Long seatId, SeatTypeRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        if (request.seatType() == SeatType.DISABLED) {
            ensureSeatCanBeDisabled(seat);
        }
        seat.setSeatType(request.seatType());
        seat.setDisabled(request.seatType() == SeatType.DISABLED);
        return seat;
    }

    @Transactional
    public Seat updateSeatDisabled(Long seatId, SeatDisabledRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        if (request.disabled()) {
            ensureSeatCanBeDisabled(seat);
        }
        // 停用状态独立于座位类型保存，启用时保留原 VIP/嘉宾/普通座类型，避免恢复后排座策略丢失。
        seat.setDisabled(request.disabled());
        if (!request.disabled() && seat.getSeatType() == SeatType.DISABLED) {
            seat.setSeatType(SeatType.NORMAL);
        }
        if (request.disabled()) {
            seat.setLocked(false);
        }
        return seat;
    }

    @Transactional(readOnly = true)
    public List<SeatView> seatMap(Long meetingId) {
        Map<Long, SeatAssignment> assignmentBySeatId = assignmentRepository.findByMeetingId(meetingId).stream()
                .collect(Collectors.toMap(assignment -> assignment.getSeat().getId(), assignment -> assignment));
        return seatRepository.findByMeetingIdOrderByLayoutSortOrderAscAreaNameAscRowNoAscSeatNoAsc(meetingId).stream()
                .map(seat -> toView(seat, assignmentBySeatId.get(seat.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SeatQueryResult querySeat(Long meetingId, String phone, String name, String phoneLast4, String attendeeCode) {
        Attendee attendee;
        if (attendeeCode != null && !attendeeCode.isBlank()) {
            attendee = attendeeRepository.findByMeetingIdAndAttendeeCode(meetingId, attendeeCode.trim())
                    .orElseThrow(() -> BusinessException.notFound("未找到参会人"));
        } else if (phone != null && !phone.isBlank()) {
            attendee = attendeeRepository.findByMeetingIdAndPhone(meetingId, phone)
                    .orElseThrow(() -> BusinessException.notFound("未找到参会人"));
        } else {
            throw BusinessException.badRequest("请提供手机号或参会码");
        }
        if (name != null && !name.isBlank() && !Objects.equals(attendee.getName(), name)) {
            throw BusinessException.notFound("姓名与手机号不匹配");
        }
        if (phoneLast4 != null && !phoneLast4.isBlank() && !attendee.getPhone().endsWith(phoneLast4)) {
            throw BusinessException.notFound("手机号后四位不匹配");
        }
        if (attendee.getReviewStatus() != ReviewStatus.APPROVED) {
            throw BusinessException.badRequest("报名尚未审核通过，暂不能查询座位");
        }
        SeatAssignment assignment = assignmentRepository.findByAttendeeId(attendee.getId())
                .orElseThrow(() -> BusinessException.notFound("尚未排座"));
        Seat seat = assignment.getSeat();
        return new SeatQueryResult(
                attendee.getId(),
                attendee.getName(),
                attendee.getPhone(),
                attendee.getOrganization(),
                attendee.getTitle(),
                attendee.getEmail(),
                attendee.getMeeting().getName(),
                seat.getAreaName(),
                seat.getRowNo(),
                seat.getSeatNo(),
                seat.getSeatCode()
        );
    }

    private SeatAssignment saveAssignment(Meeting meeting, Attendee attendee, Seat seat, boolean manual) {
        SeatAssignment assignment = new SeatAssignment();
        assignment.setMeeting(meeting);
        assignment.setAttendee(attendee);
        assignment.setSeat(seat);
        assignment.setManualAssigned(manual);
        assignment.setAssignedBy(SecurityUtils.currentUsernameOrSystem());
        return assignmentRepository.save(assignment);
    }

    private void ensureAssignable(Attendee attendee, Seat seat) {
        if (!Objects.equals(attendee.getMeeting().getId(), seat.getMeeting().getId())) {
            throw BusinessException.badRequest("参会人与座位不属于同一会议");
        }
        if (seat.isDisabled() || seat.getSeatType() == SeatType.DISABLED) {
            throw BusinessException.badRequest("禁用座位不能分配");
        }
    }

    private void ensureSeatCanBeDisabled(Seat seat) {
        // 禁用座位会从座位图和自动排座中排除；若已有分配关系，必须先解除，避免出现“人还占座但座位不可见”的业务脏状态。
        if (assignmentRepository.findBySeatId(seat.getId()).isPresent()) {
            throw BusinessException.conflict("该座位已有人员入座，请先解除座位分配后再停用");
        }
    }

    private Map<AttendeeType, List<Attendee>> groupByType(List<Attendee> attendees) {
        Map<AttendeeType, List<Attendee>> result = new LinkedHashMap<>();
        for (AttendeeType type : List.of(AttendeeType.VIP, AttendeeType.GUEST, AttendeeType.MEDIA, AttendeeType.STAFF, AttendeeType.NORMAL)) {
            List<Attendee> typed = attendees.stream().filter(attendee -> attendee.getAttendeeType() == type).toList();
            if (!typed.isEmpty()) {
                result.put(type, typed);
            }
        }
        return result;
    }

    private List<Seat> reserveSeatsForGroup(AttendeeType attendeeType, int groupSize, List<Seat> availableSeats) {
        List<SeatType> preferredTypes = preferredSeatTypes(attendeeType);
        for (SeatType type : preferredTypes) {
            List<Seat> typedSeats = availableSeats.stream()
                    .filter(seat -> seat.getSeatType() == type)
                    .sorted(Comparator.comparingInt(Seat::getRowNo).thenComparingInt(Seat::getSeatNo))
                    .toList();
            Optional<List<Seat>> contiguous = findContiguousSeats(typedSeats, groupSize);
            if (contiguous.isPresent()) {
                return contiguous.get();
            }
        }

        // 连续座位不足时降级为顺序座位，保证尽量提高排座完成率；未覆盖管理员手动座位和锁定/禁用座位。
        return availableSeats.stream()
                .filter(seat -> preferredTypes.contains(seat.getSeatType()))
                .sorted(Comparator.comparingInt(Seat::getRowNo).thenComparingInt(Seat::getSeatNo))
                .limit(groupSize)
                .toList();
    }

    private Optional<List<Seat>> findContiguousSeats(List<Seat> seats, int size) {
        Map<Integer, List<Seat>> byRow = seats.stream()
                .collect(Collectors.groupingBy(Seat::getRowNo, LinkedHashMap::new, Collectors.toList()));
        for (List<Seat> rowSeats : byRow.values()) {
            List<Seat> sorted = rowSeats.stream().sorted(Comparator.comparingInt(Seat::getSeatNo)).toList();
            for (int start = 0; start <= sorted.size() - size; start++) {
                List<Seat> window = sorted.subList(start, start + size);
                boolean continuous = true;
                for (int i = 1; i < window.size(); i++) {
                    if (window.get(i).getSeatNo() != window.get(i - 1).getSeatNo() + 1) {
                        continuous = false;
                        break;
                    }
                }
                if (continuous) {
                    return Optional.of(new ArrayList<>(window));
                }
            }
        }
        return Optional.empty();
    }

    private List<SeatType> preferredSeatTypes(AttendeeType attendeeType) {
        return switch (attendeeType) {
            case VIP -> List.of(SeatType.VIP, SeatType.GUEST, SeatType.NORMAL);
            case GUEST -> List.of(SeatType.GUEST, SeatType.VIP, SeatType.NORMAL);
            case MEDIA -> List.of(SeatType.MEDIA, SeatType.NORMAL);
            case STAFF -> List.of(SeatType.STAFF, SeatType.NORMAL);
            case NORMAL -> List.of(SeatType.NORMAL);
        };
    }

    private Comparator<Attendee> attendeeComparator() {
        Map<AttendeeType, Integer> priority = Map.of(
                AttendeeType.VIP, 0,
                AttendeeType.GUEST, 1,
                AttendeeType.MEDIA, 2,
                AttendeeType.STAFF, 3,
                AttendeeType.NORMAL, 4
        );
        return Comparator
                .comparing((Attendee attendee) -> priority.getOrDefault(attendee.getAttendeeType(), 99))
                .thenComparing(attendee -> attendee.getReviewedAt() == null ? attendee.getRegisteredAt() : attendee.getReviewedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private List<Seat> removeSeats(List<Seat> availableSeats, List<Seat> usedSeats) {
        Set<Long> usedIds = usedSeats.stream().map(Seat::getId).collect(Collectors.toSet());
        return availableSeats.stream().filter(seat -> !usedIds.contains(seat.getId())).toList();
    }

    private String normalizeAreaName(String areaName) {
        if (areaName == null || areaName.isBlank()) {
            throw BusinessException.badRequest("区域名称不能为空");
        }
        return areaName.trim();
    }

    private String normalizeOriginalAreaName(String originalAreaName, String fallbackAreaName) {
        if (originalAreaName == null || originalAreaName.isBlank()) {
            return fallbackAreaName;
        }
        return originalAreaName.trim();
    }

    private void ensureAreaCanBeRenamed(Long meetingId, String targetAreaName, List<SeatLayout> sourceLayouts, List<Seat> sourceSeats) {
        if (sourceLayouts.isEmpty() && sourceSeats.isEmpty()) {
            throw BusinessException.notFound("原区域不存在");
        }
        boolean targetLayoutExists = !layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(meetingId, targetAreaName).isEmpty();
        boolean targetSeatExists = !seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(meetingId, targetAreaName).isEmpty();
        if (targetLayoutExists || targetSeatExists) {
            throw BusinessException.conflict("区域名称已存在");
        }
    }

    private void applyLayoutRequest(SeatLayout layout, SeatLayoutRequest request, String areaName) {
        layout.setVenueName(request.venueName());
        layout.setAreaName(areaName);
        layout.setRowCount(request.rowCount());
        layout.setSeatsPerRow(request.seatsPerRow());
        layout.setSortOrder(request.sortOrder() == null ? nextSortOrder(layout) : Math.max(0, request.sortOrder()));
        layout.setNumberingRule(request.numberingRule());
        layout.setAisleSettings(request.aisleSettings());
        layout.setStageDirection(request.stageDirection());
    }

    private int nextSortOrder(SeatLayout layout) {
        if (layout.getId() != null) {
            return layout.getSortOrder();
        }
        return layoutRepository.findByMeetingId(layout.getMeeting().getId()).stream()
                .mapToInt(SeatLayout::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private String positionKey(Seat seat) {
        return seat.getRowNo() + "-" + seat.getSeatNo();
    }

    private List<Seat> seatsOutsideLayout(List<Seat> seats, int rowCount, int seatsPerRow) {
        return seats.stream()
                .filter(seat -> seat.getRowNo() > rowCount || seat.getSeatNo() > seatsPerRow)
                .toList();
    }

    private void ensureSeatsCanBeRemoved(List<Seat> seatsToDelete) {
        ensureSeatsCanBeRemoved(seatsToDelete, "缩小座位数量会删除已分配座位，请先清空这些座位后再调整");
    }

    private void ensureSeatsCanBeRemoved(List<Seat> seatsToDelete, String assignedSeatMessage) {
        if (seatsToDelete.isEmpty()) {
            return;
        }
        Set<Long> seatIds = seatsToDelete.stream().map(Seat::getId).collect(Collectors.toSet());
        if (assignmentRepository.existsBySeatIdIn(seatIds)) {
            throw BusinessException.conflict(assignedSeatMessage);
        }
        if (seatsToDelete.stream().anyMatch(Seat::isLocked)) {
            throw BusinessException.conflict("缩小座位数量会删除锁定座位，请先解除锁定后再调整");
        }
    }

    private void ensureAreaHasNoAssignments(List<Seat> areaSeats) {
        if (areaSeats.isEmpty()) {
            return;
        }
        Set<Long> seatIds = areaSeats.stream().map(Seat::getId).collect(Collectors.toSet());
        if (assignmentRepository.existsBySeatIdIn(seatIds)) {
            throw BusinessException.conflict("该区域已有人员入座，请先解除座位分配后再删除");
        }
    }

    private SeatView toView(Seat seat, SeatAssignment assignment) {
        if (seat.isDisabled() || seat.getSeatType() == SeatType.DISABLED) {
            return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                    seat.getSeatCode(), seat.getSeatType(), SeatStatus.DISABLED, seat.isLocked(), null, null, null);
        }
        if (assignment == null) {
            return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                    seat.getSeatCode(), seat.getSeatType(), SeatStatus.EMPTY, seat.isLocked(), null, null, null);
        }
        Attendee attendee = assignment.getAttendee();
        SeatStatus status = attendee.getCheckinStatus().name().equals("CHECKED_IN") ? SeatStatus.CHECKED_IN : SeatStatus.ASSIGNED;
        return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                seat.getSeatCode(), seat.getSeatType(), status, seat.isLocked(),
                attendee.getId(), attendee.getName(), attendee.getTags());
    }

    private int areaSortOrder(Seat seat) {
        return seat.getLayout() == null ? 0 : seat.getLayout().getSortOrder();
    }
}
