package com.meetingos.publicapi;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.dinner.DinnerQueryResult;
import com.meetingos.dinner.DinnerService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.PublicQueryPageContent;
import com.meetingos.room.Room;
import com.meetingos.room.RoomQueryResult;
import com.meetingos.room.RoomRepository;
import com.meetingos.seating.Seat;
import com.meetingos.seating.SeatAssignment;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import com.meetingos.seating.SeatQueryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PublicQueryService {

    private static final List<PublicQueryField> DEFAULT_FIELDS = List.of(PublicQueryField.PHONE, PublicQueryField.NAME);
    private static final List<PublicQueryResultField> DEFAULT_RESULT_FIELDS = List.of(
            PublicQueryResultField.ATTENDEE_NAME,
            PublicQueryResultField.SEAT_AREA,
            PublicQueryResultField.SEAT_ROW,
            PublicQueryResultField.SEAT_NO,
            PublicQueryResultField.SEAT_CODE,
            PublicQueryResultField.HOTEL_NAME,
            PublicQueryResultField.ROOM_TYPE,
            PublicQueryResultField.ROOM_NO,
            PublicQueryResultField.ROOM_REMARK,
            PublicQueryResultField.CHECKIN_TIPS,
            PublicQueryResultField.DINNER_TABLE_NO,
            PublicQueryResultField.DINNER_VENUE,
            PublicQueryResultField.DINNER_TIME,
            PublicQueryResultField.DINNER_REMARK
    );
    private static final List<PublicQueryResultField> DEFAULT_HIGHLIGHT_FIELDS = List.of(
            PublicQueryResultField.SEAT_CODE,
            PublicQueryResultField.ROOM_NO,
            PublicQueryResultField.DINNER_TABLE_NO
    );
    private static final List<PublicQueryResultField> DEFAULT_SEAT_RESULT_FIELDS = List.of(
            PublicQueryResultField.ATTENDEE_NAME,
            PublicQueryResultField.SEAT_AREA,
            PublicQueryResultField.SEAT_ROW,
            PublicQueryResultField.SEAT_NO,
            PublicQueryResultField.SEAT_CODE
    );
    private static final List<PublicQueryResultField> DEFAULT_ROOM_RESULT_FIELDS = List.of(
            PublicQueryResultField.ATTENDEE_NAME,
            PublicQueryResultField.HOTEL_NAME,
            PublicQueryResultField.ROOM_TYPE,
            PublicQueryResultField.ROOM_NO,
            PublicQueryResultField.ROOM_REMARK,
            PublicQueryResultField.CHECKIN_TIPS
    );
    private static final List<PublicQueryResultField> DEFAULT_DINNER_RESULT_FIELDS = List.of(
            PublicQueryResultField.ATTENDEE_NAME,
            PublicQueryResultField.DINNER_TABLE_NO,
            PublicQueryResultField.DINNER_VENUE,
            PublicQueryResultField.DINNER_TIME,
            PublicQueryResultField.DINNER_REMARK
    );
    private static final List<PublicQueryResultField> DEFAULT_SEAT_HIGHLIGHT_FIELDS = List.of(PublicQueryResultField.SEAT_CODE);
    private static final List<PublicQueryResultField> DEFAULT_ROOM_HIGHLIGHT_FIELDS = List.of(PublicQueryResultField.ROOM_NO);
    private static final List<PublicQueryResultField> DEFAULT_DINNER_HIGHLIGHT_FIELDS = List.of(PublicQueryResultField.DINNER_TABLE_NO);
    private static final String DEFAULT_SEAT_NOTICE = "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。";
    private static final String DEFAULT_ROOM_NOTICE = "凭会议证件及身份证办理入住，接待台位于酒店大堂。";
    private static final String DEFAULT_DINNER_NOTICE = "请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。";
    private static final List<String> PUBLIC_PAGE_CONTENT_KEYS = List.of("entry", "combined", "seat", "room", "dinner", "checkin");
    private static final int ADMIN_QUERY_LIMIT = 20;

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatAssignmentRepository assignmentRepository;
    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final DinnerService dinnerService;
    private final PublicQueryHistoryService historyService;
    private final PublicCaptchaService captchaService;

    public PublicQueryService(MeetingRepository meetingRepository,
                              AttendeeRepository attendeeRepository,
                              SeatAssignmentRepository assignmentRepository,
                              SeatRepository seatRepository,
                              RoomRepository roomRepository,
                              DinnerService dinnerService,
                              PublicQueryHistoryService historyService,
                              PublicCaptchaService captchaService) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
        this.dinnerService = dinnerService;
        this.historyService = historyService;
        this.captchaService = captchaService;
    }

    @Transactional(readOnly = true)
    public MeetingQuerySettingsResponse settings(Long meetingId, String meetingCode) {
        return toSettingsResponse(resolveMeeting(meetingId, meetingCode));
    }

    @Transactional(readOnly = true)
    public PublicAttendeeMatch resolvePublicAttendee(PublicQueryRequest request) {
        Meeting meeting = resolveMeeting(request.meetingId(), request.meetingCode());
        ensureQueryWindowOpen(meeting);
        ensureCheckinSourceOpen(meeting, request);
        Attendee attendee = resolveSinglePublicAttendee(meeting, request.values());
        return new PublicAttendeeMatch(meeting, attendee);
    }

    @Transactional(readOnly = true)
    public Attendee identifySingleMatchedAttendee(PublicQueryRequest request) {
        Meeting meeting = resolveMeeting(request.meetingId(), request.meetingCode());
        return identifySingleMatchedAttendee(meeting, request.values());
    }

    @Transactional(readOnly = true)
    public void recordPublicCheckin(PublicQueryRequest request,
                                    Attendee attendee,
                                    String resultSummary,
                                    String errorMessage,
                                    String clientIp,
                                    String userAgent) {
        Meeting meeting = resolveMeeting(request.meetingId(), request.meetingCode());
        if (errorMessage == null || errorMessage.isBlank()) {
            historyService.recordSuccess(meeting, PublicQueryType.CHECKIN, request.values(), attendee, resultSummary, clientIp, userAgent);
            return;
        }
        historyService.recordFailure(meeting, PublicQueryType.CHECKIN, request.values(), attendee, errorMessage, clientIp, userAgent);
    }

    @Transactional
    public MeetingQuerySettingsResponse updateSettings(Long meetingId, MeetingQuerySettingsRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<PublicQueryField> fields = normalizeFields(request.fields());
        meeting.setSeatQueryEnabled(request.seatQueryEnabled());
        meeting.setRoomQueryEnabled(request.roomQueryEnabled());
        meeting.setDinnerQueryEnabled(request.dinnerQueryEnabled());
        meeting.setCheckinEnabled(request.checkinEnabled());
        meeting.setSelfCheckinEnabled(request.selfCheckinEnabled());
        meeting.setPublicQueryPaused(request.queryPaused());
        meeting.setPublicQueryFields(serializeFields(fields));
        meeting.setPublicQueryMatchMode((request.matchMode() == null ? PublicQueryMatchMode.FUZZY : request.matchMode()).name());
        meeting.setPublicQueryTitle(limit(blankToNull(request.pageTitle()), 160));
        meeting.setPublicQueryDescription(limit(blankToNull(request.pageDescription()), 500));
        meeting.setPublicQueryAnnouncement(blankToNull(request.announcement()));
        meeting.setPublicQueryPublisher(limit(blankToNull(request.publisher()), 160));
        meeting.setPublicQueryStartTime(request.startTime());
        meeting.setPublicQueryEndTime(request.endTime());
        meeting.setPublicQueryButtonText(defaultText(request.buttonText(), "立即查询", 40));
        meeting.setPublicQueryEmptyMessage(defaultText(request.emptyMessage(), "未查询到结果，请核对信息后重试", 255));
        meeting.setPublicQuerySeatNotice(blankToNull(request.seatNotice()));
        meeting.setPublicQueryRoomNotice(blankToNull(request.roomNotice()));
        meeting.setPublicQueryDinnerNotice(blankToNull(request.dinnerNotice()));
        /*
         * 页面文案已经从“全局一套”升级成“按公开入口配置”。旧字段继续保存用于兼容旧前端和默认值，
         * 新配置集中放到 JSON 列里，避免每新增一个公开页就继续扩表。
         */
        if (request.pageContents() != null) {
            meeting.setPublicQueryPageContent(normalizePageContents(request.pageContents()));
        }
        meeting.setPublicQueryThemeColor(normalizeThemeColor(request.themeColor()));
        meeting.setPublicQueryLogoUrl(limit(blankToNull(request.logoUrl()), 500));
        meeting.setPublicQueryBackgroundUrl(limit(blankToNull(request.backgroundUrl()), 500));
        meeting.setPublicQueryResultLayout(normalizeLayout(request.resultLayout()));
        List<PublicQueryResultField> sharedVisible = normalizeResultFields(request.visibleResultFields(), DEFAULT_RESULT_FIELDS);
        List<PublicQueryResultField> sharedMasked = normalizeResultFields(request.maskedResultFields(), List.of());
        List<PublicQueryResultField> sharedHighlight = normalizeResultFields(request.highlightResultFields(), DEFAULT_HIGHLIGHT_FIELDS);
        List<PublicQueryResultField> seatVisible = normalizeResultFields(request.seatVisibleResultFields(), filterResultFields(sharedVisible, PublicQueryType.SEAT, DEFAULT_SEAT_RESULT_FIELDS));
        List<PublicQueryResultField> seatMasked = normalizeResultFields(request.seatMaskedResultFields(), filterResultFields(sharedMasked, PublicQueryType.SEAT, List.of()));
        List<PublicQueryResultField> seatHighlight = normalizeResultFields(request.seatHighlightResultFields(), filterResultFields(sharedHighlight, PublicQueryType.SEAT, DEFAULT_SEAT_HIGHLIGHT_FIELDS));
        List<PublicQueryResultField> roomVisible = normalizeResultFields(request.roomVisibleResultFields(), filterResultFields(sharedVisible, PublicQueryType.ROOM, DEFAULT_ROOM_RESULT_FIELDS));
        List<PublicQueryResultField> roomMasked = normalizeResultFields(request.roomMaskedResultFields(), filterResultFields(sharedMasked, PublicQueryType.ROOM, List.of()));
        List<PublicQueryResultField> roomHighlight = normalizeResultFields(request.roomHighlightResultFields(), filterResultFields(sharedHighlight, PublicQueryType.ROOM, DEFAULT_ROOM_HIGHLIGHT_FIELDS));
        List<PublicQueryResultField> dinnerVisible = normalizeResultFields(request.dinnerVisibleResultFields(), filterResultFields(sharedVisible, PublicQueryType.DINNER, DEFAULT_DINNER_RESULT_FIELDS));
        List<PublicQueryResultField> dinnerMasked = normalizeResultFields(request.dinnerMaskedResultFields(), filterResultFields(sharedMasked, PublicQueryType.DINNER, List.of()));
        List<PublicQueryResultField> dinnerHighlight = normalizeResultFields(request.dinnerHighlightResultFields(), filterResultFields(sharedHighlight, PublicQueryType.DINNER, DEFAULT_DINNER_HIGHLIGHT_FIELDS));
        meeting.setPublicQuerySeatVisibleResultFields(serializeResultFields(seatVisible));
        meeting.setPublicQuerySeatMaskedResultFields(serializeResultFields(seatMasked));
        meeting.setPublicQuerySeatHighlightResultFields(serializeResultFields(seatHighlight));
        meeting.setPublicQueryRoomVisibleResultFields(serializeResultFields(roomVisible));
        meeting.setPublicQueryRoomMaskedResultFields(serializeResultFields(roomMasked));
        meeting.setPublicQueryRoomHighlightResultFields(serializeResultFields(roomHighlight));
        meeting.setPublicQueryDinnerVisibleResultFields(serializeResultFields(dinnerVisible));
        meeting.setPublicQueryDinnerMaskedResultFields(serializeResultFields(dinnerMasked));
        meeting.setPublicQueryDinnerHighlightResultFields(serializeResultFields(dinnerHighlight));
        meeting.setPublicQueryVisibleResultFields(serializeResultFields(mergeResultFields(seatVisible, roomVisible, dinnerVisible)));
        meeting.setPublicQueryMaskedResultFields(serializeResultFields(mergeResultFields(seatMasked, roomMasked, dinnerMasked)));
        meeting.setPublicQueryHighlightResultFields(serializeResultFields(mergeResultFields(seatHighlight, roomHighlight, dinnerHighlight)));
        return toSettingsResponse(meeting);
    }

    @Transactional(readOnly = true)
    public SeatQueryResult publicSeatQuery(PublicQueryRequest request) {
        return publicSeatQuery(request, null, null);
    }

    @Transactional(readOnly = true)
    public SeatQueryResult publicSeatQuery(PublicQueryRequest request, String clientIp, String userAgent) {
        Meeting meeting = resolveMeeting(request.meetingId(), request.meetingCode());
        Attendee attendee = null;
        try {
            ensureQueryWindowOpen(meeting);
            if (!meeting.isSeatQueryEnabled()) {
                throw BusinessException.badRequest("座位查询尚未开放");
            }
            attendee = resolveSinglePublicAttendee(meeting, request.values());
            SeatAssignment assignment = assignmentRepository.findByAttendeeId(attendee.getId())
                    .orElseThrow(() -> BusinessException.notFound("尚未排座"));
            Seat seat = assignment.getSeat();
            SeatQueryResult rawResult = new SeatQueryResult(
                    attendee.getId(),
                    attendee.getName(),
                    attendee.getPhone(),
                    attendee.getOrganization(),
                    attendee.getTitle(),
                    attendee.getEmail(),
                    meeting.getName(),
                    seat.getAreaName(),
                    seat.getRowNo(),
                    seat.getSeatNo(),
                    seat.getSeatCode(),
                    buildPublicSeatMap(meeting.getId(), seat)
            );
            historyService.recordSuccess(meeting, PublicQueryType.SEAT, request.values(), attendee, seatSummary(rawResult), clientIp, userAgent);
            return filterSeatResult(meeting, rawResult);
        } catch (RuntimeException ex) {
            historyService.recordFailure(meeting, PublicQueryType.SEAT, request.values(), firstNonNull(attendee, identifySingleMatchedAttendee(meeting, request.values())), ex.getMessage(), clientIp, userAgent);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public RoomQueryResult publicRoomQuery(PublicQueryRequest request) {
        return publicRoomQuery(request, null, null);
    }

    @Transactional(readOnly = true)
    public RoomQueryResult publicRoomQuery(PublicQueryRequest request, String clientIp, String userAgent) {
        Meeting meeting = resolveMeeting(request.meetingId(), request.meetingCode());
        Attendee attendee = null;
        try {
            ensureQueryWindowOpen(meeting);
            if (!meeting.isRoomQueryEnabled()) {
                throw BusinessException.badRequest("住宿查询尚未开放");
            }
            attendee = resolveSinglePublicAttendee(meeting, request.values());
            RoomQueryResult rawResult = toRoomResult(meeting, attendee);
            historyService.recordSuccess(meeting, PublicQueryType.ROOM, request.values(), attendee, roomSummary(rawResult), clientIp, userAgent);
            return filterRoomResult(meeting, rawResult);
        } catch (RuntimeException ex) {
            historyService.recordFailure(meeting, PublicQueryType.ROOM, request.values(), firstNonNull(attendee, identifySingleMatchedAttendee(meeting, request.values())), ex.getMessage(), clientIp, userAgent);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DinnerQueryResult publicDinnerQuery(PublicQueryRequest request) {
        return publicDinnerQuery(request, null, null);
    }

    @Transactional(readOnly = true)
    public DinnerQueryResult publicDinnerQuery(PublicQueryRequest request, String clientIp, String userAgent) {
        Meeting meeting = resolveMeeting(request.meetingId(), request.meetingCode());
        Attendee attendee = null;
        try {
            ensureQueryWindowOpen(meeting);
            if (!meeting.isDinnerQueryEnabled()) {
                throw BusinessException.badRequest("晚宴查询尚未开放");
            }
            attendee = resolveSinglePublicAttendee(meeting, request.values());
            DinnerQueryResult rawResult = dinnerService.query(meeting.getId(), attendee);
            historyService.recordSuccess(meeting, PublicQueryType.DINNER, request.values(), attendee, dinnerSummary(rawResult), clientIp, userAgent);
            return filterDinnerResult(meeting, rawResult);
        } catch (RuntimeException ex) {
            historyService.recordFailure(meeting, PublicQueryType.DINNER, request.values(), firstNonNull(attendee, identifySingleMatchedAttendee(meeting, request.values())), ex.getMessage(), clientIp, userAgent);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AdminQueryCandidate> adminSeatCandidates(Long meetingId, AdminQueryRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        return matchAttendees(meeting, request.values()).stream()
                .limit(ADMIN_QUERY_LIMIT)
                .map(this::toCandidate)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminQueryCandidate> adminRoomCandidates(Long meetingId, AdminQueryRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        return matchAttendees(meeting, request.values()).stream()
                .limit(ADMIN_QUERY_LIMIT)
                .map(this::toCandidate)
                .toList();
    }

    private Attendee resolveSinglePublicAttendee(Meeting meeting, Map<PublicQueryField, String> values) {
        List<Attendee> matches = matchAttendees(meeting, values);
        if (matches.isEmpty()) {
            throw BusinessException.notFound("未找到匹配报名记录");
        }
        if (matches.size() > 1) {
            throw BusinessException.badRequest("匹配到多条报名记录，请补充更精确的查询信息");
        }
        Attendee attendee = matches.get(0);
        if (attendee.getReviewStatus() != ReviewStatus.APPROVED) {
            throw BusinessException.badRequest("报名尚未审核通过，暂不能查询");
        }
        return attendee;
    }

    private Attendee identifySingleMatchedAttendee(Meeting meeting, Map<PublicQueryField, String> values) {
        try {
            List<Attendee> matches = matchAttendees(meeting, values);
            return matches.size() == 1 ? matches.get(0) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<Attendee> matchAttendees(Meeting meeting, Map<PublicQueryField, String> values) {
        List<PublicQueryField> fields = parseFields(meeting);
        PublicQueryMatchMode mode = parseMatchMode(meeting);
        requireConfiguredValues(fields, values);
        return attendeeRepository.findByMeetingIdOrderByIdAsc(meeting.getId()).stream()
                .filter(attendee -> fields.stream().allMatch(field -> matches(attendee, field, values.get(field), mode)))
                .toList();
    }

    private void ensureQueryWindowOpen(Meeting meeting) {
        if (meeting.isPublicQueryPaused()) {
            throw BusinessException.badRequest("查询已暂停，请稍后再试");
        }
        LocalDateTime now = LocalDateTime.now();
        if (meeting.getPublicQueryStartTime() != null && now.isBefore(meeting.getPublicQueryStartTime())) {
            throw BusinessException.badRequest("查询尚未开始");
        }
        if (meeting.getPublicQueryEndTime() != null && now.isAfter(meeting.getPublicQueryEndTime())) {
            throw BusinessException.badRequest("查询已结束");
        }
    }

    private void requireConfiguredValues(List<PublicQueryField> fields, Map<PublicQueryField, String> values) {
        if (values == null) {
            throw BusinessException.badRequest("请填写查询信息");
        }
        for (PublicQueryField field : fields) {
            String value = values.get(field);
            if (value == null || value.isBlank()) {
                throw BusinessException.badRequest("请填写" + field.label());
            }
        }
    }

    private boolean matches(Attendee attendee, PublicQueryField field, String rawNeedle, PublicQueryMatchMode mode) {
        String haystack = valueOf(attendee, field);
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        String needle = normalizeForMatch(field, rawNeedle);
        String normalizedHaystack = normalizeForMatch(field, haystack);
        if (mode == PublicQueryMatchMode.EXACT) {
            // PHONE_LAST4 是“拿输入值和完整手机号后缀比较”，不是要求参会人手机号等于 4 位数字。
            if (field == PublicQueryField.PHONE_LAST4) {
                return normalizedHaystack.endsWith(needle);
            }
            return Objects.equals(normalizedHaystack, needle);
        }
        return normalizedHaystack.contains(needle);
    }

    private String valueOf(Attendee attendee, PublicQueryField field) {
        return switch (field) {
            case NAME -> attendee.getName();
            case PHONE, PHONE_LAST4 -> attendee.getPhone();
            case ATTENDEE_CODE -> attendee.getAttendeeCode();
            case ORGANIZATION -> attendee.getOrganization();
            case TITLE -> attendee.getTitle();
            case EMAIL -> attendee.getEmail();
        };
    }

    private String normalizeForMatch(PublicQueryField field, String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (field == PublicQueryField.PHONE || field == PublicQueryField.PHONE_LAST4) {
            normalized = normalized.replaceAll("[\\s-]", "");
        }
        return normalized;
    }

    private RoomQueryResult toRoomResult(Meeting meeting, Attendee attendee) {
        if (attendee.getRoomNo() == null || attendee.getRoomNo().isBlank()) {
            throw BusinessException.notFound("尚未安排房间");
        }
        Room room = roomRepository.findByMeetingIdAndRoomNo(meeting.getId(), attendee.getRoomNo()).orElse(null);
        return new RoomQueryResult(
                attendee.getId(),
                attendee.getName(),
                attendee.getPhone(),
                attendee.getOrganization(),
                attendee.getTitle(),
                attendee.getEmail(),
                meeting.getName(),
                attendee.getRoomNo(),
                attendee.getRoomRemark(),
                room == null ? null : room.getHotelName(),
                room == null ? null : room.getRoomType(),
                room == null ? null : room.getRemark()
        );
    }

    private SeatQueryResult filterSeatResult(Meeting meeting, SeatQueryResult result) {
        List<PublicQueryResultField> visible = parseResultFields(
                meeting.getPublicQuerySeatVisibleResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryVisibleResultFields(), DEFAULT_RESULT_FIELDS), PublicQueryType.SEAT, DEFAULT_SEAT_RESULT_FIELDS)
        );
        List<PublicQueryResultField> masked = parseResultFields(
                meeting.getPublicQuerySeatMaskedResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryMaskedResultFields(), List.of()), PublicQueryType.SEAT, List.of())
        );
        return new SeatQueryResult(
                result.attendeeId(),
                visibleOrNull(PublicQueryResultField.ATTENDEE_NAME, visible, masked, result.attendeeName()),
                visibleOrNull(PublicQueryResultField.ATTENDEE_PHONE, visible, masked, result.attendeePhone()),
                visibleOrNull(PublicQueryResultField.ORGANIZATION, visible, masked, result.organization()),
                visibleOrNull(PublicQueryResultField.TITLE, visible, masked, result.title()),
                visibleOrNull(PublicQueryResultField.EMAIL, visible, masked, result.email()),
                visibleOrNull(PublicQueryResultField.MEETING_NAME, visible, masked, result.meetingName()),
                visibleOrNull(PublicQueryResultField.SEAT_AREA, visible, masked, result.areaName()),
                visible.contains(PublicQueryResultField.SEAT_ROW) ? result.rowNo() : 0,
                visible.contains(PublicQueryResultField.SEAT_NO) ? result.seatNo() : 0,
                visibleOrNull(PublicQueryResultField.SEAT_CODE, visible, masked, result.seatCode()),
                shouldExposeSeatMap(visible, masked) ? result.seatMap() : null
        );
    }

    private SeatQueryResult.PublicSeatMap buildPublicSeatMap(Long meetingId, Seat currentSeat) {
        List<Seat> areaSeats = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(meetingId, currentSeat.getAreaName());
        int rowCount = areaSeats.stream().mapToInt(Seat::getRowNo).max().orElse(0);
        int seatsPerRow = areaSeats.stream().mapToInt(Seat::getSeatNo).max().orElse(0);
        if (rowCount <= 0 || seatsPerRow <= 0) {
            return null;
        }
        return new SeatQueryResult.PublicSeatMap(
                currentSeat.getAreaName(),
                rowCount,
                seatsPerRow,
                currentSeat.getRowNo(),
                currentSeat.getSeatNo()
        );
    }

    private boolean shouldExposeSeatMap(List<PublicQueryResultField> visible, List<PublicQueryResultField> masked) {
        return visible.contains(PublicQueryResultField.SEAT_AREA)
                && visible.contains(PublicQueryResultField.SEAT_ROW)
                && visible.contains(PublicQueryResultField.SEAT_NO)
                && !masked.contains(PublicQueryResultField.SEAT_AREA)
                && !masked.contains(PublicQueryResultField.SEAT_ROW)
                && !masked.contains(PublicQueryResultField.SEAT_NO);
    }

    private RoomQueryResult filterRoomResult(Meeting meeting, RoomQueryResult result) {
        List<PublicQueryResultField> visible = parseResultFields(
                meeting.getPublicQueryRoomVisibleResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryVisibleResultFields(), DEFAULT_RESULT_FIELDS), PublicQueryType.ROOM, DEFAULT_ROOM_RESULT_FIELDS)
        );
        List<PublicQueryResultField> masked = parseResultFields(
                meeting.getPublicQueryRoomMaskedResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryMaskedResultFields(), List.of()), PublicQueryType.ROOM, List.of())
        );
        return new RoomQueryResult(
                result.attendeeId(),
                visibleOrNull(PublicQueryResultField.ATTENDEE_NAME, visible, masked, result.attendeeName()),
                visibleOrNull(PublicQueryResultField.ATTENDEE_PHONE, visible, masked, result.attendeePhone()),
                visibleOrNull(PublicQueryResultField.ORGANIZATION, visible, masked, result.organization()),
                visibleOrNull(PublicQueryResultField.TITLE, visible, masked, result.title()),
                visibleOrNull(PublicQueryResultField.EMAIL, visible, masked, result.email()),
                visibleOrNull(PublicQueryResultField.MEETING_NAME, visible, masked, result.meetingName()),
                visibleOrNull(PublicQueryResultField.ROOM_NO, visible, masked, result.roomNo()),
                visibleOrNull(PublicQueryResultField.ROOM_REMARK, visible, masked, result.roomRemark()),
                visibleOrNull(PublicQueryResultField.HOTEL_NAME, visible, masked, result.hotelName()),
                visibleOrNull(PublicQueryResultField.ROOM_TYPE, visible, masked, result.roomType()),
                visibleOrNull(PublicQueryResultField.CHECKIN_TIPS, visible, masked, result.checkinTips())
        );
    }

    private DinnerQueryResult filterDinnerResult(Meeting meeting, DinnerQueryResult result) {
        List<PublicQueryResultField> visible = parseResultFields(
                meeting.getPublicQueryDinnerVisibleResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryVisibleResultFields(), DEFAULT_RESULT_FIELDS), PublicQueryType.DINNER, DEFAULT_DINNER_RESULT_FIELDS)
        );
        List<PublicQueryResultField> masked = parseResultFields(
                meeting.getPublicQueryDinnerMaskedResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryMaskedResultFields(), List.of()), PublicQueryType.DINNER, List.of())
        );
        return new DinnerQueryResult(
                result.attendeeId(),
                visibleOrNull(PublicQueryResultField.ATTENDEE_NAME, visible, masked, result.attendeeName()),
                visibleOrNull(PublicQueryResultField.ATTENDEE_PHONE, visible, masked, result.attendeePhone()),
                visibleOrNull(PublicQueryResultField.ORGANIZATION, visible, masked, result.organization()),
                visibleOrNull(PublicQueryResultField.TITLE, visible, masked, result.title()),
                visibleOrNull(PublicQueryResultField.EMAIL, visible, masked, result.email()),
                visibleOrNull(PublicQueryResultField.MEETING_NAME, visible, masked, result.meetingName()),
                visibleOrNull(PublicQueryResultField.DINNER_TABLE_NO, visible, masked, result.tableNo()),
                visibleOrNull(PublicQueryResultField.DINNER_VENUE, visible, masked, result.venue()),
                visibleOrNull(PublicQueryResultField.DINNER_TIME, visible, masked, result.dinnerTime()),
                visibleOrNull(PublicQueryResultField.DINNER_REMARK, visible, masked, result.remark())
        );
    }

    private String visibleOrNull(PublicQueryResultField field,
                                 List<PublicQueryResultField> visible,
                                 List<PublicQueryResultField> masked,
                                 String value) {
        if (!visible.contains(field)) {
            return null;
        }
        if (!masked.contains(field)) {
            return value;
        }
        return switch (field) {
            case ATTENDEE_PHONE -> SensitiveDataMasker.maskPhone(value);
            case EMAIL -> SensitiveDataMasker.maskEmail(value);
            default -> value == null || value.isBlank() ? value : "***";
        };
    }

    private String seatSummary(SeatQueryResult result) {
        return result.areaName() + " " + result.rowNo() + "排" + result.seatNo() + "座（" + result.seatCode() + "）";
    }

    private String roomSummary(RoomQueryResult result) {
        String hotel = result.hotelName() == null || result.hotelName().isBlank() ? "" : result.hotelName() + " ";
        String roomType = result.roomType() == null || result.roomType().isBlank() ? "" : " · " + result.roomType();
        return hotel + result.roomNo() + roomType;
    }

    private String dinnerSummary(DinnerQueryResult result) {
        String venue = result.venue() == null || result.venue().isBlank() ? "" : result.venue() + " ";
        return venue + result.tableNo();
    }

    private Attendee firstNonNull(Attendee primary, Attendee fallback) {
        return primary == null ? fallback : primary;
    }

    private AdminQueryCandidate toCandidate(Attendee attendee) {
        SeatAssignment assignment = assignmentRepository.findByAttendeeId(attendee.getId()).orElse(null);
        String seatLabel = null;
        if (assignment != null) {
            Seat seat = assignment.getSeat();
            seatLabel = seat.getAreaName() + " " + seat.getRowNo() + "排" + seat.getSeatNo() + "座";
        }
        return new AdminQueryCandidate(
                attendee.getId(),
                attendee.getName(),
                attendee.getPhone(),
                attendee.getOrganization(),
                attendee.getTitle(),
                attendee.getReviewStatus(),
                seatLabel,
                attendee.getRoomNo(),
                attendee.getRoomRemark()
        );
    }

    private Meeting resolveMeeting(Long meetingId, String meetingCode) {
        if (meetingId != null) {
            return meetingRepository.findById(meetingId)
                    .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        }
        if (meetingCode != null && !meetingCode.isBlank()) {
            return meetingRepository.findByCode(meetingCode.trim())
                    .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        }
        throw BusinessException.badRequest("请提供会议 ID 或会议编号");
    }

    private void ensureCheckinSourceOpen(Meeting meeting, PublicQueryRequest request) {
        String source = request.checkinSource() == null ? "SELF" : request.checkinSource().trim().toUpperCase(Locale.ROOT);
        /*
         * /public/checkin 被两个前端入口复用：
         * 1. SELF：独立自助签到页，适合现场扫码；
         * 2. RESULT：座位/住宿/晚宴查询成功后的“立即签到”按钮。
         * 两个入口由后台独立开关控制，避免关闭独立页时误伤查询结果按钮，反之亦然。
         */
        if ("RESULT".equals(source)) {
            if (!meeting.isCheckinEnabled()) {
                throw BusinessException.badRequest("查询后签到按钮尚未开放");
            }
            return;
        }
        if (!"SELF".equals(source)) {
            throw BusinessException.badRequest("签到来源不支持");
        }
        if (!meeting.isSelfCheckinEnabled()) {
            throw BusinessException.badRequest("签到独立页尚未开放");
        }
    }

    private MeetingQuerySettingsResponse toSettingsResponse(Meeting meeting) {
        List<PublicQueryResultField> seatVisible = parseResultFields(
                meeting.getPublicQuerySeatVisibleResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryVisibleResultFields(), DEFAULT_RESULT_FIELDS), PublicQueryType.SEAT, DEFAULT_SEAT_RESULT_FIELDS)
        );
        List<PublicQueryResultField> seatMasked = parseResultFields(
                meeting.getPublicQuerySeatMaskedResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryMaskedResultFields(), List.of()), PublicQueryType.SEAT, List.of())
        );
        List<PublicQueryResultField> seatHighlight = parseResultFields(
                meeting.getPublicQuerySeatHighlightResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryHighlightResultFields(), DEFAULT_HIGHLIGHT_FIELDS), PublicQueryType.SEAT, DEFAULT_SEAT_HIGHLIGHT_FIELDS)
        );
        List<PublicQueryResultField> roomVisible = parseResultFields(
                meeting.getPublicQueryRoomVisibleResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryVisibleResultFields(), DEFAULT_RESULT_FIELDS), PublicQueryType.ROOM, DEFAULT_ROOM_RESULT_FIELDS)
        );
        List<PublicQueryResultField> roomMasked = parseResultFields(
                meeting.getPublicQueryRoomMaskedResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryMaskedResultFields(), List.of()), PublicQueryType.ROOM, List.of())
        );
        List<PublicQueryResultField> roomHighlight = parseResultFields(
                meeting.getPublicQueryRoomHighlightResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryHighlightResultFields(), DEFAULT_HIGHLIGHT_FIELDS), PublicQueryType.ROOM, DEFAULT_ROOM_HIGHLIGHT_FIELDS)
        );
        List<PublicQueryResultField> dinnerVisible = parseResultFields(
                meeting.getPublicQueryDinnerVisibleResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryVisibleResultFields(), DEFAULT_RESULT_FIELDS), PublicQueryType.DINNER, DEFAULT_DINNER_RESULT_FIELDS)
        );
        List<PublicQueryResultField> dinnerMasked = parseResultFields(
                meeting.getPublicQueryDinnerMaskedResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryMaskedResultFields(), List.of()), PublicQueryType.DINNER, List.of())
        );
        List<PublicQueryResultField> dinnerHighlight = parseResultFields(
                meeting.getPublicQueryDinnerHighlightResultFields(),
                filterResultFields(parseResultFields(meeting.getPublicQueryHighlightResultFields(), DEFAULT_HIGHLIGHT_FIELDS), PublicQueryType.DINNER, DEFAULT_DINNER_HIGHLIGHT_FIELDS)
        );
        return new MeetingQuerySettingsResponse(
                meeting.getId(),
                meeting.getCode(),
                meeting.getName(),
                meeting.isSeatQueryEnabled(),
                meeting.isRoomQueryEnabled(),
                meeting.isDinnerQueryEnabled(),
                meeting.isCheckinEnabled(),
                meeting.isSelfCheckinEnabled(),
                meeting.isPublicQueryPaused(),
                parseFields(meeting),
                parseMatchMode(meeting),
                firstNonBlank(meeting.getPublicQueryTitle(), meeting.getName() + "查询"),
                meeting.getPublicQueryDescription(),
                meeting.getPublicQueryAnnouncement(),
                meeting.getPublicQueryPublisher(),
                meeting.getPublicQueryStartTime(),
                meeting.getPublicQueryEndTime(),
                firstNonBlank(meeting.getPublicQueryButtonText(), "立即查询"),
                firstNonBlank(meeting.getPublicQueryEmptyMessage(), "未查询到结果，请核对信息后重试"),
                firstNonBlank(meeting.getPublicQuerySeatNotice(), DEFAULT_SEAT_NOTICE),
                firstNonBlank(meeting.getPublicQueryRoomNotice(), DEFAULT_ROOM_NOTICE),
                firstNonBlank(meeting.getPublicQueryDinnerNotice(), DEFAULT_DINNER_NOTICE),
                resolvePageContents(meeting),
                firstNonBlank(meeting.getPublicQueryThemeColor(), "#2563eb"),
                meeting.getPublicQueryLogoUrl(),
                meeting.getPublicQueryBackgroundUrl(),
                firstNonBlank(meeting.getPublicQueryResultLayout(), "CARD"),
                mergeResultFields(seatVisible, roomVisible, dinnerVisible),
                mergeResultFields(seatMasked, roomMasked, dinnerMasked),
                mergeResultFields(seatHighlight, roomHighlight, dinnerHighlight),
                seatVisible,
                seatMasked,
                seatHighlight,
                roomVisible,
                roomMasked,
                roomHighlight,
                dinnerVisible,
                dinnerMasked,
                dinnerHighlight,
                captchaService.isEnabled(),
                Arrays.stream(PublicQueryField.values())
                        .map(field -> new QueryFieldOption(field, field.label()))
                        .toList(),
                Arrays.stream(PublicQueryResultField.values())
                        .map(field -> new QueryResultFieldOption(field, field.label()))
                        .toList()
        );
    }

    private Map<String, PublicQueryPageContent> resolvePageContents(Meeting meeting) {
        Map<String, PublicQueryPageContent> defaults = defaultPageContents(meeting);
        Map<String, PublicQueryPageContent> stored = meeting.getPublicQueryPageContent();
        if (stored == null || stored.isEmpty()) {
            return defaults;
        }

        Map<String, PublicQueryPageContent> merged = new LinkedHashMap<>();
        for (String key : PUBLIC_PAGE_CONTENT_KEYS) {
            merged.put(key, mergePageContent(stored.get(key), defaults.get(key)));
        }
        return merged;
    }

    private Map<String, PublicQueryPageContent> normalizePageContents(Map<String, PublicQueryPageContent> rawContents) {
        Map<String, PublicQueryPageContent> normalized = new LinkedHashMap<>();
        for (String key : PUBLIC_PAGE_CONTENT_KEYS) {
            PublicQueryPageContent raw = rawContents.get(key);
            if (raw != null) {
                normalized.put(key, cleanPageContent(raw));
            }
        }
        return normalized;
    }

    private Map<String, PublicQueryPageContent> defaultPageContents(Meeting meeting) {
        String title = firstNonBlank(meeting.getPublicQueryTitle(), meeting.getName() + "查询");
        String description = meeting.getPublicQueryDescription();
        String announcement = meeting.getPublicQueryAnnouncement();
        String publisher = meeting.getPublicQueryPublisher();
        String buttonText = firstNonBlank(meeting.getPublicQueryButtonText(), "立即查询");
        String emptyMessage = firstNonBlank(meeting.getPublicQueryEmptyMessage(), "未查询到结果，请核对信息后重试");

        Map<String, PublicQueryPageContent> defaults = new LinkedHashMap<>();
        defaults.put("entry", new PublicQueryPageContent(title, description, announcement, publisher, null, null, null));
        defaults.put("combined", new PublicQueryPageContent(title, description, announcement, publisher, buttonText, emptyMessage, null));
        defaults.put("seat", new PublicQueryPageContent(
                title,
                description,
                announcement,
                publisher,
                buttonText,
                emptyMessage,
                firstNonBlank(meeting.getPublicQuerySeatNotice(), DEFAULT_SEAT_NOTICE)
        ));
        defaults.put("room", new PublicQueryPageContent(
                title,
                description,
                announcement,
                publisher,
                buttonText,
                emptyMessage,
                firstNonBlank(meeting.getPublicQueryRoomNotice(), DEFAULT_ROOM_NOTICE)
        ));
        defaults.put("dinner", new PublicQueryPageContent(
                title,
                description,
                announcement,
                publisher,
                buttonText,
                emptyMessage,
                firstNonBlank(meeting.getPublicQueryDinnerNotice(), DEFAULT_DINNER_NOTICE)
        ));
        defaults.put("checkin", new PublicQueryPageContent(title, description, announcement, publisher, "自助签到", null, null));
        return defaults;
    }

    private PublicQueryPageContent mergePageContent(PublicQueryPageContent stored, PublicQueryPageContent fallback) {
        if (stored == null) {
            return fallback;
        }
        return new PublicQueryPageContent(
                stored.title() == null ? fallback.title() : stored.title(),
                stored.description() == null ? fallback.description() : stored.description(),
                stored.announcement() == null ? fallback.announcement() : stored.announcement(),
                stored.publisher() == null ? fallback.publisher() : stored.publisher(),
                stored.buttonText() == null ? fallback.buttonText() : stored.buttonText(),
                stored.emptyMessage() == null ? fallback.emptyMessage() : stored.emptyMessage(),
                stored.notice() == null ? fallback.notice() : stored.notice()
        );
    }

    private PublicQueryPageContent cleanPageContent(PublicQueryPageContent raw) {
        return new PublicQueryPageContent(
                limitOptional(raw.title(), 160),
                limitOptional(raw.description(), 500),
                limitOptional(raw.announcement(), 2000),
                limitOptional(raw.publisher(), 160),
                limitOptional(raw.buttonText(), 40),
                limitOptional(raw.emptyMessage(), 255),
                limitOptional(raw.notice(), 2000)
        );
    }

    private String limitOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return limit(value.trim(), maxLength);
    }

    private List<PublicQueryField> parseFields(Meeting meeting) {
        String raw = meeting.getPublicQueryFields();
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FIELDS;
        }
        Set<PublicQueryField> fields = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            try {
                fields.add(PublicQueryField.valueOf(token.trim()));
            } catch (IllegalArgumentException ignored) {
                // 历史脏配置不能让公开查询整页不可用，忽略未知枚举并回退默认字段。
            }
        }
        return fields.isEmpty() ? DEFAULT_FIELDS : List.copyOf(fields);
    }

    private List<PublicQueryField> normalizeFields(List<PublicQueryField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw BusinessException.badRequest("请至少选择一个查询字段");
        }
        EnumSet<PublicQueryField> allowed = EnumSet.allOf(PublicQueryField.class);
        List<PublicQueryField> normalized = fields.stream()
                .filter(Objects::nonNull)
                .filter(allowed::contains)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw BusinessException.badRequest("请至少选择一个有效查询字段");
        }
        return normalized;
    }

    private String serializeFields(List<PublicQueryField> fields) {
        return fields.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private List<PublicQueryResultField> parseResultFields(String raw, List<PublicQueryResultField> fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Set<PublicQueryResultField> fields = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            try {
                fields.add(PublicQueryResultField.valueOf(token.trim()));
            } catch (IllegalArgumentException ignored) {
                // 结果字段也允许灰度迁移，未知值忽略，避免公开页配置整体不可用。
            }
        }
        return fields.isEmpty() ? fallback : List.copyOf(fields);
    }

    private List<PublicQueryResultField> normalizeResultFields(List<PublicQueryResultField> fields, List<PublicQueryResultField> fallback) {
        if (fields == null) {
            return fallback;
        }
        EnumSet<PublicQueryResultField> allowed = EnumSet.allOf(PublicQueryResultField.class);
        List<PublicQueryResultField> normalized = fields.stream()
                .filter(Objects::nonNull)
                .filter(allowed::contains)
                .distinct()
                .toList();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String serializeResultFields(List<PublicQueryResultField> fields) {
        return fields.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private List<PublicQueryResultField> mergeResultFields(List<PublicQueryResultField> primary, List<PublicQueryResultField> secondary, List<PublicQueryResultField> tertiary) {
        Set<PublicQueryResultField> merged = new LinkedHashSet<>();
        merged.addAll(primary);
        merged.addAll(secondary);
        merged.addAll(tertiary);
        return List.copyOf(merged);
    }

    private List<PublicQueryResultField> filterResultFields(List<PublicQueryResultField> fields,
                                                            PublicQueryType type,
                                                            List<PublicQueryResultField> fallback) {
        Set<PublicQueryResultField> allowed = switch (type) {
            case SEAT -> seatResultFields();
            case ROOM -> roomResultFields();
            case DINNER -> dinnerResultFields();
            case CHECKIN -> Set.of();
        };
        List<PublicQueryResultField> filtered = fields.stream()
                .filter(allowed::contains)
                .toList();
        if (!filtered.isEmpty()) {
            return filtered;
        }
        return fallback;
    }

    private Set<PublicQueryResultField> seatResultFields() {
        return Set.of(
                PublicQueryResultField.ATTENDEE_NAME,
                PublicQueryResultField.ATTENDEE_PHONE,
                PublicQueryResultField.ORGANIZATION,
                PublicQueryResultField.TITLE,
                PublicQueryResultField.EMAIL,
                PublicQueryResultField.MEETING_NAME,
                PublicQueryResultField.SEAT_AREA,
                PublicQueryResultField.SEAT_ROW,
                PublicQueryResultField.SEAT_NO,
                PublicQueryResultField.SEAT_CODE
        );
    }

    private Set<PublicQueryResultField> roomResultFields() {
        return Set.of(
                PublicQueryResultField.ATTENDEE_NAME,
                PublicQueryResultField.ATTENDEE_PHONE,
                PublicQueryResultField.ORGANIZATION,
                PublicQueryResultField.TITLE,
                PublicQueryResultField.EMAIL,
                PublicQueryResultField.MEETING_NAME,
                PublicQueryResultField.HOTEL_NAME,
                PublicQueryResultField.ROOM_TYPE,
                PublicQueryResultField.ROOM_NO,
                PublicQueryResultField.ROOM_REMARK,
                PublicQueryResultField.CHECKIN_TIPS
        );
    }

    private Set<PublicQueryResultField> dinnerResultFields() {
        return Set.of(
                PublicQueryResultField.ATTENDEE_NAME,
                PublicQueryResultField.ATTENDEE_PHONE,
                PublicQueryResultField.ORGANIZATION,
                PublicQueryResultField.TITLE,
                PublicQueryResultField.EMAIL,
                PublicQueryResultField.MEETING_NAME,
                PublicQueryResultField.DINNER_TABLE_NO,
                PublicQueryResultField.DINNER_VENUE,
                PublicQueryResultField.DINNER_TIME,
                PublicQueryResultField.DINNER_REMARK
        );
    }

    private PublicQueryMatchMode parseMatchMode(Meeting meeting) {
        try {
            return PublicQueryMatchMode.valueOf(meeting.getPublicQueryMatchMode());
        } catch (RuntimeException ex) {
            return PublicQueryMatchMode.FUZZY;
        }
    }

    private String blankToNull(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isBlank() ? null : text;
    }

    private String firstNonBlank(String primary, String fallback) {
        String text = blankToNull(primary);
        return text == null ? fallback : text;
    }

    private String defaultText(String value, String fallback, int maxLength) {
        return limit(firstNonBlank(value, fallback), maxLength);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeThemeColor(String value) {
        String color = blankToNull(value);
        if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
            return "#2563eb";
        }
        return color;
    }

    private String normalizeLayout(String value) {
        String layout = blankToNull(value);
        if (layout == null) {
            return "CARD";
        }
        String normalized = layout.toUpperCase(Locale.ROOT);
        return Set.of("CARD", "LIST", "COMPACT").contains(normalized) ? normalized : "CARD";
    }
}
