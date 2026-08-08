package com.meetingos.seating;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeCodeGenerator;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SeatingService {

    private static final String AISLE_SETTINGS_V1_PREFIX = "v1:";
    private static final Pattern LEGACY_AISLE_AFTER_SEAT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:号)?\\s*座\\s*后\\s*(?:过道|通道)"
    );

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatLayoutRepository layoutRepository;
    private final SeatRepository seatRepository;
    private final SeatAssignmentRepository assignmentRepository;
    private final AttendeeCodeGenerator attendeeCodeGenerator;

    @Autowired
    public SeatingService(MeetingRepository meetingRepository,
                          AttendeeRepository attendeeRepository,
                          SeatLayoutRepository layoutRepository,
                          SeatRepository seatRepository,
                          SeatAssignmentRepository assignmentRepository,
                          AttendeeCodeGenerator attendeeCodeGenerator) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.layoutRepository = layoutRepository;
        this.seatRepository = seatRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendeeCodeGenerator = attendeeCodeGenerator;
    }

    SeatingService(MeetingRepository meetingRepository,
                   AttendeeRepository attendeeRepository,
                   SeatLayoutRepository layoutRepository,
                   SeatRepository seatRepository,
                   SeatAssignmentRepository assignmentRepository) {
        this(meetingRepository, attendeeRepository, layoutRepository, seatRepository, assignmentRepository, null);
    }

    @Transactional
    public List<Seat> createLayout(SeatLayoutRequest request) {
        validateLayoutSize(request);
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
        boolean overrideStructuralBlanks = request.structuralBlankSeatCodes() != null;
        Set<String> structuralBlankCodes = overrideStructuralBlanks ? new HashSet<>(request.structuralBlankSeatCodes()) : Set.of();
        boolean overrideAisles = request.aisleSeatCodes() != null;
        Set<String> aisleCodes = overrideAisles ? new HashSet<>(request.aisleSeatCodes()) : Set.of();
        SeatType requestedSeatType = request.defaultSeatType();
        boolean overrideSeatType = requestedSeatType != null;
        Map<String, Seat> existingByPosition = existingSeats.stream()
                .collect(Collectors.toMap(this::positionKey, seat -> seat));
        List<Seat> seatsToDelete = seatsOutsideLayout(existingSeats, request.rowCount(), request.seatsPerRow());
        ensureSeatsCanBeRemoved(seatsToDelete);
        if (overrideAisles) {
            ensureSeatsCanBecomeAisles(existingSeats, request.rowCount(), request.seatsPerRow(), areaName, aisleCodes);
        }

        // 管理员调整座位数量时，同一区域按“创建或更新”处理：
        // 1. 已存在且仍在新范围内的座位保留 ID、锁定、分配状态，避免丢失手动排座结果。
        // 2. 只有旧调用方明确提交座位类型时才批量覆盖；布局调整本身不能重置既有座位类型。
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
                    seat.setSeatType(overrideSeatType ? requestedSeatType : SeatType.NORMAL);
                }
                seat.setLayout(layout);
                // 区域改名时复用原座位 ID，保证已有手动排座/签到关系继续指向同一个座位。
                seat.setAreaName(areaName);
                seat.setSeatCode(seatCode);
                boolean wasAisle = seat.isAisle();
                boolean aisle = overrideAisles ? aisleCodes.contains(seatCode) : wasAisle;
                if (aisle) {
                    // 过道不是“被人工停用的座位”，但同样必须从所有排座路径中排除。
                    // 强制归一化其状态，避免旧布局请求的默认座位类型、锁定或结构留白残留。
                    seat.setAisle(true);
                    seat.setSeatType(SeatType.NORMAL);
                    seat.setDisabled(true);
                    seat.setLocked(false);
                    seat.setStructuralBlank(false);
                } else {
                    if (wasAisle) {
                        // 批量布局里取消过道时，若调用方未提交禁用清单，也要恢复为可用位置。
                        seat.setAisle(false);
                        seat.setStructuralBlank(false);
                        seat.setLocked(false);
                        if (!overrideDisabledSeats) {
                            seat.setDisabled(false);
                        }
                    }
                    if (overrideSeatType) {
                        seat.setSeatType(requestedSeatType);
                    }
                    if (overrideStructuralBlanks) {
                        seat.setStructuralBlank(structuralBlankCodes.contains(seatCode));
                    }
                    if (seat.isStructuralBlank()) {
                        // 结构留白不可参与自动/手动排座，也不应保留锁定状态。
                        seat.setDisabled(true);
                        seat.setLocked(false);
                    } else if (overrideDisabledSeats) {
                        seat.setDisabled(disabledCodes.contains(seatCode));
                    }
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

    private void validateLayoutSize(SeatLayoutRequest request) {
        long totalSeats = (long) request.rowCount() * request.seatsPerRow();
        if (request.rowCount() < 1
                || request.rowCount() > SeatLayoutRequest.MAX_ROWS
                || request.seatsPerRow() < 1
                || request.seatsPerRow() > SeatLayoutRequest.MAX_SEATS_PER_ROW
                || totalSeats > SeatLayoutRequest.MAX_SEATS_PER_AREA) {
            throw BusinessException.badRequest("单个区域最多支持 80 排、每排 80 座（共 6400 座）");
        }
        if (request.disabledSeatCodes() != null
                && request.disabledSeatCodes().size() > SeatLayoutRequest.MAX_SEATS_PER_AREA) {
            throw BusinessException.badRequest("禁用座位数量不能超过 6400");
        }
        if (request.structuralBlankSeatCodes() != null
                && request.structuralBlankSeatCodes().size() > SeatLayoutRequest.MAX_SEATS_PER_AREA) {
            throw BusinessException.badRequest("结构空白数量不能超过 6400");
        }
        if (request.aisleSeatCodes() != null
                && request.aisleSeatCodes().size() > SeatLayoutRequest.MAX_SEATS_PER_AREA) {
            throw BusinessException.badRequest("过道数量不能超过 6400");
        }
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
        /*
         * 已分配关系在本方法里会同时按 attendee 和 seat 维度使用。一次 fetch join 后复用，
         * 避免自动排座开始阶段重复扫描 seat_assignments，并避免懒加载 attendee/seat 产生 N+1。
         */
        List<SeatAssignment> existingAssignments = assignmentRepository.findWithAttendeeAndSeatByMeetingId(meetingId);
        Set<Long> alreadyAssignedAttendeeIds = existingAssignments.stream()
                .map(assignment -> assignment.getAttendee().getId())
                .collect(Collectors.toSet());

        List<Attendee> candidates = approved.stream()
                .filter(attendee -> !alreadyAssignedAttendeeIds.contains(attendee.getId()))
                .sorted(attendeeComparator())
                .toList();
        if (candidates.isEmpty()) {
            return new AutoSeatingResult(0, 0, List.of("没有需要自动排座的已审核参会人"));
        }

        Map<Long, SeatAssignment> assignedBySeat = existingAssignments.stream()
                .collect(Collectors.toMap(assignment -> assignment.getSeat().getId(), assignment -> assignment));
        List<Seat> availableSeats = seatRepository.findByMeetingIdAndDisabledFalseOrderByRowNoAscSeatNoAsc(meetingId).stream()
                .filter(seat -> !seat.isAisle())
                .filter(seat -> !seat.isLocked())
                .filter(seat -> !assignedBySeat.containsKey(seat.getId()))
                .toList();
        SeatAvailabilityIndex availability = new SeatAvailabilityIndex(availableSeats);

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
                List<Seat> reserved = availability.reserve(typeEntry.getKey(), group.size());
                if (reserved.isEmpty()) {
                    warnings.add("座位不足，未能安排: " + group.stream().map(Attendee::getName).collect(Collectors.joining(",")));
                    continue;
                }
                int assignable = Math.min(group.size(), reserved.size());
                for (int i = 0; i < assignable; i++) {
                    saveAssignment(meeting, group.get(i), reserved.get(i), false);
                    availability.remove(reserved.get(i));
                    assigned++;
                }
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
    public SeatAssignment assignUnregistered(AssignUnregisteredSeatRequest request) {
        Seat seat = seatRepository.findById(request.seatId())
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        ensureAssignableSeat(seat);
        assignmentRepository.findBySeatId(seat.getId()).ifPresent(existing -> {
            throw BusinessException.conflict("座位已被分配，请先清空或交换座位");
        });

        String phone = normalizeOptionalPhone(request.phone());
        if (phone != null) {
            attendeeRepository.findByMeetingIdAndPhone(seat.getMeeting().getId(), phone)
                    .ifPresent(existing -> {
                        throw BusinessException.conflict("该手机号已存在参会人，请从参会人列表分配座位");
                    });
        }

        Attendee attendee = new Attendee();
        attendee.setMeeting(seat.getMeeting());
        attendee.setName(request.attendeeName().trim());
        attendee.setPhone(phone);
        attendee.setAttendeeType(AttendeeType.NORMAL);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy(SecurityUtils.currentUsernameOrSystem());
        // 现场入座不是 TDuck 报名，不写 registeredAt/tduckDataId，避免伪造成普通报名记录。
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        attendee.setUnregistered(true);
        attendee.setAttendeeCode(requireAttendeeCodeGenerator().generate(seat.getMeeting()));
        Attendee saved = attendeeRepository.saveAndFlush(attendee);
        return saveAssignment(seat.getMeeting(), saved, seat, true);
    }

    @Transactional
    public void clearUnregistered(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        SeatAssignment assignment = assignmentRepository.findBySeatId(seat.getId())
                .orElseThrow(() -> BusinessException.notFound("该座位没有未报名人员入座"));
        if (!assignment.getAttendee().isUnregistered()) {
            throw BusinessException.badRequest("该座位分配的是已报名参会人");
        }
        assignmentRepository.delete(assignment);
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
        ensureNotAisle(firstSeat);
        ensureNotAisle(secondSeat);

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
        ensureNotStructuralBlank(seat);
        ensureNotAisle(seat);
        seat.setLocked(request.locked());
        return seat;
    }

    @Transactional
    public Seat updateSeatType(Long seatId, SeatTypeRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        ensureNotStructuralBlank(seat);
        ensureNotAisle(seat);
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
        ensureNotStructuralBlank(seat);
        ensureNotAisle(seat);
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

    @Transactional
    public Seat updateSeatAisle(Long seatId, SeatAisleRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> BusinessException.notFound("座位不存在"));
        if (request.aisle()) {
            ensureSeatHasNoAssignment(seat, "该座位已有人员入座，请先解除座位分配后再设为过道");
            // 过道占据网格位置，但不是座位。强制清除与座位概念冲突的配置，保证切换可逆。
            seat.setAisle(true);
            seat.setDisabled(true);
            seat.setLocked(false);
            seat.setStructuralBlank(false);
            seat.setSeatType(SeatType.NORMAL);
            return seat;
        }

        // 对非过道的 false 请求保持幂等，避免误将普通禁用座位或结构留白重新启用。
        if (!seat.isAisle()) {
            return seat;
        }
        ensureSeatHasNoAssignment(seat, "过道仍有关联人员，不能恢复为座位");
        seat.setAisle(false);
        seat.setDisabled(false);
        seat.setLocked(false);
        seat.setStructuralBlank(false);
        seat.setSeatType(SeatType.NORMAL);
        return seat;
    }

    @Transactional(readOnly = true)
    public List<SeatView> seatMap(Long meetingId) {
        Map<Long, SeatAssignment> assignmentBySeatId = assignmentRepository.findWithAttendeeAndSeatByMeetingId(meetingId).stream()
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
        if (phoneLast4 != null && !phoneLast4.isBlank()
                && (attendee.getPhone() == null || !attendee.getPhone().endsWith(phoneLast4))) {
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
        ensureNotAisle(seat);
        if (seat.isDisabled() || seat.getSeatType() == SeatType.DISABLED) {
            throw BusinessException.badRequest("禁用座位不能分配");
        }
        ensureNotStructuralBlank(seat);
    }

    private void ensureAssignableSeat(Seat seat) {
        ensureNotAisle(seat);
        if (seat.isDisabled() || seat.getSeatType() == SeatType.DISABLED) {
            throw BusinessException.badRequest("禁用座位不能分配");
        }
        ensureNotStructuralBlank(seat);
    }

    private void ensureNotStructuralBlank(Seat seat) {
        if (seat.isStructuralBlank()) {
            throw BusinessException.badRequest("结构空白不能修改或分配");
        }
    }

    private void ensureNotAisle(Seat seat) {
        if (seat.isAisle()) {
            throw BusinessException.badRequest("过道不能修改或分配");
        }
    }

    private void ensureSeatCanBeDisabled(Seat seat) {
        // 禁用座位会从座位图和自动排座中排除；若已有分配关系，必须先解除，避免出现“人还占座但座位不可见”的业务脏状态。
        ensureSeatHasNoAssignment(seat, "该座位已有人员入座，请先解除座位分配后再停用");
    }

    private void ensureSeatHasNoAssignment(Seat seat, String assignedSeatMessage) {
        if (assignmentRepository.findBySeatId(seat.getId()).isPresent()) {
            throw BusinessException.conflict(assignedSeatMessage);
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

    /**
     * 自动排座期间维护按类型、排号和座号组织的可变索引。单位很多且每组只有一人时，
     * 取下一个座位和删除已用座位均为对数复杂度，避免每组重新扫描、排序并复制全量列表。
     */
    private final class SeatAvailabilityIndex {
        private final Comparator<Seat> seatOrder;
        private final TreeSet<Seat> allSeats;
        private final Map<SeatType, NavigableMap<Integer, TreeSet<Seat>>> rowsByType = new EnumMap<>(SeatType.class);

        private SeatAvailabilityIndex(List<Seat> seats) {
            Map<Seat, Integer> sourceOrder = new IdentityHashMap<>();
            for (int index = 0; index < seats.size(); index++) {
                sourceOrder.put(seats.get(index), index);
            }
            this.seatOrder = Comparator.comparingInt(Seat::getRowNo)
                    .thenComparingInt(Seat::getSeatNo)
                    .thenComparingInt(sourceOrder::get);
            this.allSeats = new TreeSet<>(seatOrder);
            for (Seat seat : seats) {
                allSeats.add(seat);
                if (seat.getSeatType() == null) {
                    continue;
                }
                rowsByType.computeIfAbsent(seat.getSeatType(), ignored -> new TreeMap<>())
                        .computeIfAbsent(seat.getRowNo(), ignored -> new TreeSet<>(seatOrder))
                        .add(seat);
            }
        }

        private List<Seat> reserve(AttendeeType attendeeType, int groupSize) {
            List<SeatType> preferredTypes = preferredSeatTypes(attendeeType);
            for (SeatType type : preferredTypes) {
                List<Seat> contiguous = contiguous(type, groupSize);
                if (!contiguous.isEmpty()) {
                    return contiguous;
                }
            }

            // 连续座位不足时仍按原规则合并所有可接受类型，再按排号和座号取前 N 个。
            Set<SeatType> acceptedTypes = EnumSet.copyOf(preferredTypes);
            List<Seat> fallback = new ArrayList<>(Math.min(groupSize, allSeats.size()));
            for (Seat seat : allSeats) {
                if (acceptedTypes.contains(seat.getSeatType())) {
                    fallback.add(seat);
                    if (fallback.size() == groupSize) {
                        break;
                    }
                }
            }
            return fallback;
        }

        private List<Seat> contiguous(SeatType type, int size) {
            NavigableMap<Integer, TreeSet<Seat>> rows = rowsByType.get(type);
            if (rows == null) {
                return List.of();
            }
            for (TreeSet<Seat> rowSeats : rows.values()) {
                List<Seat> run = new ArrayList<>(Math.min(size, rowSeats.size()));
                Integer previousSeatNo = null;
                for (Seat seat : rowSeats) {
                    if (previousSeatNo == null || seat.getSeatNo() != previousSeatNo + 1) {
                        run.clear();
                    }
                    run.add(seat);
                    if (run.size() == size) {
                        return new ArrayList<>(run);
                    }
                    previousSeatNo = seat.getSeatNo();
                }
            }
            return List.of();
        }

        private void remove(Seat seat) {
            allSeats.remove(seat);
            SeatType type = seat.getSeatType();
            if (type == null) {
                return;
            }
            NavigableMap<Integer, TreeSet<Seat>> rows = rowsByType.get(type);
            if (rows == null) {
                return;
            }
            TreeSet<Seat> rowSeats = rows.get(seat.getRowNo());
            if (rowSeats != null) {
                rowSeats.remove(seat);
                if (rowSeats.isEmpty()) {
                    rows.remove(seat.getRowNo());
                }
            }
        }
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
        layout.setAisleSettings(normalizeAisleSettings(request.aisleSettings(), request.seatsPerRow()));
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

    private void ensureSeatsCanBecomeAisles(List<Seat> existingSeats,
                                             int rowCount,
                                             int seatsPerRow,
                                             String areaName,
                                             Set<String> aisleCodes) {
        Set<Long> targetSeatIds = existingSeats.stream()
                .filter(seat -> seat.getRowNo() <= rowCount && seat.getSeatNo() <= seatsPerRow)
                .filter(seat -> aisleCodes.contains(areaName + "-" + seat.getRowNo() + "-" + seat.getSeatNo()))
                .map(Seat::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!targetSeatIds.isEmpty() && assignmentRepository.existsBySeatIdIn(targetSeatIds)) {
            throw BusinessException.conflict("已分配座位不能改为过道，请先解除座位分配");
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
        List<Integer> aisleAfterSeatNos = aisleAfterSeatNos(seat);
        if (seat.isAisle()) {
            return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                    seat.getSeatCode(), seat.getSeatType(), SeatStatus.AISLE, false, false, true, aisleAfterSeatNos,
                    false, null, null, null);
        }
        if (seat.isDisabled() || seat.getSeatType() == SeatType.DISABLED) {
            return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                    seat.getSeatCode(), seat.getSeatType(), SeatStatus.DISABLED, seat.isLocked(), seat.isStructuralBlank(), false,
                    aisleAfterSeatNos, false, null, null, null);
        }
        if (assignment == null) {
            return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                    seat.getSeatCode(), seat.getSeatType(), SeatStatus.EMPTY, seat.isLocked(), seat.isStructuralBlank(), false,
                    aisleAfterSeatNos, false, null, null, null);
        }
        Attendee attendee = assignment.getAttendee();
        // “未报名入座”是人员来源，而不是签到状态。两者拆开返回，现场签到后既能保留来源标记，也能正确计入已签到。
        SeatStatus status = attendee.getCheckinStatus() == CheckinStatus.CHECKED_IN ? SeatStatus.CHECKED_IN : SeatStatus.ASSIGNED;
        return new SeatView(seat.getId(), seat.getAreaName(), areaSortOrder(seat), seat.getRowNo(), seat.getSeatNo(),
                seat.getSeatCode(), seat.getSeatType(), status, seat.isLocked(), seat.isStructuralBlank(), false,
                aisleAfterSeatNos, attendee.isUnregistered(),
                attendee.getId(), attendee.getName(), attendee.getTags());
    }

    private List<Integer> aisleAfterSeatNos(Seat seat) {
        SeatLayout layout = seat.getLayout();
        if (layout == null || layout.getSeatsPerRow() < 2) {
            return List.of();
        }
        return parseAisleAfterSeatNos(layout.getAisleSettings(), layout.getSeatsPerRow());
    }

    /**
     * 布局级过道只描述“第 N 座后”的间距，不能占用真实座位编号。
     * 兼容早期以中文文案保存的配置，读取时统一过滤非法位置，避免旧数据影响座位图渲染。
     */
    private List<Integer> parseAisleAfterSeatNos(String aisleSettings, int seatsPerRow) {
        if (aisleSettings == null || aisleSettings.isBlank() || seatsPerRow < 2) {
            return List.of();
        }

        TreeSet<Integer> aisleAfterSeatNos = new TreeSet<>();
        String normalizedSettings = aisleSettings.trim();
        if (normalizedSettings.regionMatches(true, 0, AISLE_SETTINGS_V1_PREFIX, 0, AISLE_SETTINGS_V1_PREFIX.length())) {
            for (String value : normalizedSettings.substring(AISLE_SETTINGS_V1_PREFIX.length()).split("[,，]")) {
                addAisleAfterSeatNo(aisleAfterSeatNos, value, seatsPerRow);
            }
        } else {
            Matcher matcher = LEGACY_AISLE_AFTER_SEAT_PATTERN.matcher(normalizedSettings);
            while (matcher.find()) {
                addAisleAfterSeatNo(aisleAfterSeatNos, matcher.group(1), seatsPerRow);
            }
        }
        return aisleAfterSeatNos.isEmpty() ? List.of() : List.copyOf(aisleAfterSeatNos);
    }

    private String normalizeAisleSettings(String aisleSettings, int seatsPerRow) {
        List<Integer> aisleAfterSeatNos = parseAisleAfterSeatNos(aisleSettings, seatsPerRow);
        if (aisleAfterSeatNos.isEmpty()) {
            return null;
        }
        return AISLE_SETTINGS_V1_PREFIX + aisleAfterSeatNos.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private void addAisleAfterSeatNo(TreeSet<Integer> aisleAfterSeatNos, String value, int seatsPerRow) {
        try {
            int seatNo = Integer.parseInt(value.trim());
            if (seatNo >= 1 && seatNo < seatsPerRow) {
                aisleAfterSeatNos.add(seatNo);
            }
        } catch (NumberFormatException ignored) {
            // 旧配置可能来自可编辑文本；无法识别的片段不应阻断座位图查询。
        }
    }

    private int areaSortOrder(Seat seat) {
        return seat.getLayout() == null ? 0 : seat.getLayout().getSortOrder();
    }

    private String normalizeOptionalPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("^\\+?\\d{6,20}$")) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        return normalized;
    }

    private AttendeeCodeGenerator requireAttendeeCodeGenerator() {
        if (attendeeCodeGenerator == null) {
            throw new IllegalStateException("未配置参会码生成器");
        }
        return attendeeCodeGenerator;
    }
}
