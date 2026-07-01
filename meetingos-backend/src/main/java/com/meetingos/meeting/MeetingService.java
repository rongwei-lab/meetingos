package com.meetingos.meeting;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.permission.MeetingPermissionRole;
import com.meetingos.permission.MeetingUserPermission;
import com.meetingos.permission.MeetingUserPermissionRepository;
import com.meetingos.publicapi.PublicQueryField;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.SecurityUtils;
import com.meetingos.security.Role;
import com.meetingos.seating.SeatAssignmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MeetingService {

    private static final String MEETING_CODE_PREFIX = "MEET";
    private static final DateTimeFormatter MEETING_CODE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final DinnerAssignmentRepository dinnerAssignmentRepository;
    private final MeetingUserPermissionRepository permissionRepository;
    private final AppUserRepository userRepository;

    public MeetingService(MeetingRepository meetingRepository,
                          AttendeeRepository attendeeRepository,
                          SeatAssignmentRepository seatAssignmentRepository,
                          DinnerAssignmentRepository dinnerAssignmentRepository,
                          MeetingUserPermissionRepository permissionRepository,
                          AppUserRepository userRepository) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.dinnerAssignmentRepository = dinnerAssignmentRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Meeting create(MeetingRequest request) {
        Meeting meeting = new Meeting();
        meeting.setCode(generateMeetingCode());
        applyRequest(meeting, request);
        meeting.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
        Meeting saved = meetingRepository.save(meeting);
        grantCreatorManagerPermission(saved);
        return saved;
    }

    @Transactional
    public Meeting update(Long id, MeetingRequest request) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        applyRequest(meeting, request);
        return meeting;
    }

    @Transactional
    public Meeting transition(Long id, MeetingStatus targetStatus) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        ensureTransitionAllowed(meeting.getStatus(), targetStatus);
        meeting.setStatus(targetStatus);
        return meeting;
    }

    @Transactional
    public Meeting archive(Long id) {
        // MeetingOS MVP 不物理删除会议，避免参会人、签到、住宿、排座等历史数据失去归属。
        // 这里复用状态流转约束：例如 IN_PROGRESS 必须先结束再归档，防止误删正在执行的会议。
        return transition(id, MeetingStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public PageResponse<MeetingSummary> list(String keyword, MeetingStatus status, int page, int size) {
        PageRequest pageRequest = PageRequests.of(page, size);
        Page<Meeting> meetings = searchVisibleMeetings(keyword, status, pageRequest);
        Page<MeetingSummary> summaries = meetings.map(this::toSummary);
        return PageResponse.from(summaries);
    }

    @Transactional(readOnly = true)
    public MeetingDetail detail(Long id) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        MeetingSummary summary = toSummary(meeting);
        return new MeetingDetail(
                meeting,
                summary.registrationCount(),
                summary.approvedCount(),
                summary.seatedCount(),
                summary.roomAssignedCount(),
                summary.dinnerAssignedCount(),
                summary.checkedInCount()
        );
    }

    public MeetingSummary toSummary(Meeting meeting) {
        long registrationCount = attendeeRepository.countByMeetingId(meeting.getId());
        long approvedCount = attendeeRepository.countByMeetingIdAndReviewStatus(meeting.getId(), ReviewStatus.APPROVED);
        long seatedCount = seatAssignmentRepository.countByMeetingId(meeting.getId());
        long roomAssignedCount = attendeeRepository.countByMeetingIdAndRoomNoIsNotNull(meeting.getId());
        long dinnerAssignedCount = dinnerAssignmentRepository.countByMeetingId(meeting.getId());
        long checkedInCount = attendeeRepository.countByMeetingIdAndCheckinStatus(meeting.getId(), CheckinStatus.CHECKED_IN);
        return new MeetingSummary(
                meeting.getId(),
                meeting.getCode(),
                meeting.getName(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                meeting.getLocation(),
                meeting.getDinnerVenue(),
                meeting.getDinnerTime(),
                registrationCount,
                approvedCount,
                seatedCount,
                roomAssignedCount,
                dinnerAssignedCount,
                checkedInCount,
                meeting.getStatus(),
                meeting.isSeatQueryEnabled(),
                meeting.isRoomQueryEnabled(),
                meeting.isDinnerQueryEnabled(),
                meeting.isCheckinEnabled(),
                meeting.isSelfCheckinEnabled(),
                meeting.getPublicQueryFields(),
                meeting.getPublicQueryMatchMode()
        );
    }

    private void applyRequest(Meeting meeting, MeetingRequest request) {
        if (!StringUtils.hasText(meeting.getCode())) {
            meeting.setCode(generateMeetingCode());
        }
        meeting.setName(request.name());
        meeting.setOrganizer(request.organizer());
        meeting.setCoOrganizer(request.coOrganizer());
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meeting.setRegistrationStartTime(request.registrationStartTime());
        meeting.setRegistrationEndTime(request.registrationEndTime());
        meeting.setLocation(request.location());
        meeting.setVenueName(request.venueName());
        meeting.setDinnerVenue(limit(blankToNull(request.dinnerVenue()), 160));
        meeting.setDinnerTime(limit(blankToNull(request.dinnerTime()), 120));
        meeting.setCapacity(request.capacity());
        meeting.setDescription(request.description());
        meeting.setAgenda(request.agenda());
        meeting.setCoverImageUrl(request.coverImageUrl());
        meeting.setContactName(request.contactName());
        meeting.setContactPhone(request.contactPhone());
        meeting.setStatus(request.status() == null ? MeetingStatus.DRAFT : request.status());
        if (request.seatQueryEnabled() != null) {
            meeting.setSeatQueryEnabled(request.seatQueryEnabled());
        }
        if (request.roomQueryEnabled() != null) {
            meeting.setRoomQueryEnabled(request.roomQueryEnabled());
        }
        if (request.dinnerQueryEnabled() != null) {
            meeting.setDinnerQueryEnabled(request.dinnerQueryEnabled());
        }
        if (request.checkinEnabled() != null) {
            meeting.setCheckinEnabled(request.checkinEnabled());
        }
        if (request.selfCheckinEnabled() != null) {
            meeting.setSelfCheckinEnabled(request.selfCheckinEnabled());
        }
        if (request.publicQueryFields() != null && !request.publicQueryFields().isEmpty()) {
            meeting.setPublicQueryFields(request.publicQueryFields().stream()
                    .map(PublicQueryField::name)
                    .distinct()
                    .collect(Collectors.joining(",")));
        }
        if (request.publicQueryMatchMode() != null) {
            meeting.setPublicQueryMatchMode(request.publicQueryMatchMode().name());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String generateMeetingCode() {
        String datePart = LocalDate.now().format(MEETING_CODE_DATE_FORMAT);
        for (int attempts = 0; attempts < 10; attempts++) {
            String suffix = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase();
            String code = MEETING_CODE_PREFIX + datePart + "-" + suffix;
            if (meetingRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw BusinessException.conflict("会议编号生成失败，请稍后重试");
    }

    private Page<Meeting> searchVisibleMeetings(String keyword, MeetingStatus status, PageRequest pageRequest) {
        var currentUser = SecurityUtils.currentUser().orElse(null);
        if (currentUser == null || currentUser.roles().contains(Role.SUPER_ADMIN)) {
            return meetingRepository.search(blankToNull(keyword), status, pageRequest);
        }
        Set<Long> authorizedMeetingIds = permissionRepository.findByUserId(currentUser.id()).stream()
                .map(permission -> permission.getMeeting().getId())
                .collect(Collectors.toSet());
        if (authorizedMeetingIds.isEmpty()) {
            return Page.empty(pageRequest);
        }
        return meetingRepository.searchAuthorized(authorizedMeetingIds, blankToNull(keyword), status, pageRequest);
    }

    private void grantCreatorManagerPermission(Meeting meeting) {
        var currentUser = SecurityUtils.currentUser().orElse(null);
        if (currentUser == null || currentUser.roles().contains(Role.SUPER_ADMIN)) {
            return;
        }
        userRepository.findById(currentUser.id()).ifPresent(user -> {
            MeetingUserPermission permission = new MeetingUserPermission();
            permission.setMeeting(meeting);
            permission.setUser(user);
            permission.setPermissionRole(MeetingPermissionRole.MANAGER);
            permission.setGrantedBy(SecurityUtils.currentUsernameOrSystem());
            permissionRepository.save(permission);
        });
    }

    private void ensureTransitionAllowed(MeetingStatus current, MeetingStatus target) {
        if (current == target) {
            return;
        }
        boolean allowed = switch (current) {
            case DRAFT -> target == MeetingStatus.REGISTRATION_OPEN || target == MeetingStatus.ARCHIVED;
            case REGISTRATION_OPEN -> target == MeetingStatus.REGISTRATION_CLOSED || target == MeetingStatus.READY || target == MeetingStatus.ARCHIVED;
            case REGISTRATION_CLOSED -> target == MeetingStatus.READY || target == MeetingStatus.REGISTRATION_OPEN || target == MeetingStatus.ARCHIVED;
            case READY -> target == MeetingStatus.IN_PROGRESS || target == MeetingStatus.REGISTRATION_CLOSED || target == MeetingStatus.ARCHIVED;
            case IN_PROGRESS -> target == MeetingStatus.ENDED;
            case ENDED -> target == MeetingStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw BusinessException.badRequest("会议状态不能从 " + current + " 流转到 " + target);
        }
    }
}
