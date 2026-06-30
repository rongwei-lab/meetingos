package com.meetingos.attendee;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ImportResult;
import com.meetingos.common.PageResponse;
import com.meetingos.exporttask.ExportTaskService;
import com.meetingos.operationlog.AuditLog;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.publicapi.PublicQueryType;
import com.meetingos.seating.Seat;
import com.meetingos.seating.SeatAssignment;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.dinner.DinnerAssignmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/attendees", "/api/admin/attendees"})
public class AttendeeController {

    private final AttendeeService attendeeService;
    private final SystemSettingsService systemSettingsService;
    private final ExportTaskService exportTaskService;
    private final PublicQueryHistoryRepository publicQueryHistoryRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final DinnerAssignmentRepository dinnerAssignmentRepository;

    public AttendeeController(AttendeeService attendeeService,
                              SystemSettingsService systemSettingsService,
                              ExportTaskService exportTaskService,
                              PublicQueryHistoryRepository publicQueryHistoryRepository,
                              SeatAssignmentRepository seatAssignmentRepository,
                              DinnerAssignmentRepository dinnerAssignmentRepository) {
        this.attendeeService = attendeeService;
        this.systemSettingsService = systemSettingsService;
        this.exportTaskService = exportTaskService;
        this.publicQueryHistoryRepository = publicQueryHistoryRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.dinnerAssignmentRepository = dinnerAssignmentRepository;
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "ATTENDEE_CREATE", targetType = "ATTENDEE")
    public ApiResponse<AttendeeResponse> create(@Valid @RequestBody AttendeeRequest request) {
        return ApiResponse.ok(AttendeeResponse.raw(attendeeService.create(request)));
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<PageResponse<AttendeeResponse>> search(@RequestParam Long meetingId,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) ReviewStatus reviewStatus,
                                                              @RequestParam(required = false) CheckinStatus checkinStatus,
                                                              @RequestParam(required = false) AttendeeType attendeeType,
                                                              @RequestParam(required = false) Boolean hasSeat,
                                                              @RequestParam(required = false) String tags,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        PageResponse<Attendee> result = attendeeService.search(meetingId, keyword, reviewStatus, checkinStatus, attendeeType, hasSeat, tags, page, size);
        boolean masked = systemSettingsService.maskSensitiveEnabled();
        /*
         * 参会人列表需要快速判断该人员是否已经在公开查询页查过座位/住宿。
         * 查询历史可能包含失败记录；只要日志能关联到该参会人，就说明对方已经尝试查询过对应入口。
         */
        Set<Long> seatQueriedAttendeeIds = Set.copyOf(publicQueryHistoryRepository.findQueriedAttendeeIds(meetingId, PublicQueryType.SEAT));
        Set<Long> roomQueriedAttendeeIds = Set.copyOf(publicQueryHistoryRepository.findQueriedAttendeeIds(meetingId, PublicQueryType.ROOM));
        Set<Long> dinnerQueriedAttendeeIds = Set.copyOf(publicQueryHistoryRepository.findQueriedAttendeeIds(meetingId, PublicQueryType.DINNER));
        var seatAssignments = seatAssignmentRepository.findWithAttendeeAndSeatByMeetingId(meetingId);
        Set<Long> seatAssignedAttendeeIds = seatAssignments.stream()
                .filter(assignment -> assignment.getAttendee() != null)
                .map(assignment -> assignment.getAttendee().getId())
                .collect(Collectors.toSet());
        Set<Long> dinnerAssignedAttendeeIds = dinnerAssignmentRepository.findByMeetingIdOrderByTableNoAscIdAsc(meetingId).stream()
                .filter(assignment -> assignment.getAttendee() != null)
                .map(assignment -> assignment.getAttendee().getId())
                .collect(Collectors.toSet());
        Map<Long, String> seatLabelByAttendeeId = seatAssignments.stream()
                .filter(assignment -> assignment.getAttendee() != null
                        && assignment.getSeat() != null
                        && assignment.getSeat().getAreaName() != null
                        && !assignment.getSeat().getAreaName().isBlank())
                .collect(Collectors.toMap(
                        assignment -> assignment.getAttendee().getId(),
                        this::seatLabel,
                        (existing, replacement) -> existing
                ));
        return ApiResponse.ok(new PageResponse<>(
                result.content().stream()
                        .map(attendee -> response(
                                attendee,
                                seatLabelByAttendeeId.get(attendee.getId()),
                                seatAssignedAttendeeIds.contains(attendee.getId()),
                                dinnerAssignedAttendeeIds.contains(attendee.getId()),
                                masked,
                                seatQueriedAttendeeIds,
                                roomQueriedAttendeeIds,
                                dinnerQueriedAttendeeIds
                        ))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@meetingAccess.canViewAttendee(#id)")
    public ApiResponse<AttendeeResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(response(attendeeService.detail(id), systemSettingsService.maskSensitiveEnabled()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "ATTENDEE_UPDATE", targetType = "ATTENDEE")
    public ApiResponse<AttendeeResponse> update(@PathVariable Long id, @Valid @RequestBody AttendeeRequest request) {
        return ApiResponse.ok(AttendeeResponse.raw(attendeeService.update(id, request)));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("@meetingAccess.canManageAttendee(#id)")
    @AuditLog(action = "ATTENDEE_REVIEW", targetType = "ATTENDEE")
    public ApiResponse<AttendeeResponse> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(AttendeeResponse.raw(attendeeService.review(id, request)));
    }

    @PutMapping("/batch-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEETING_ADMIN')")
    @AuditLog(action = "ATTENDEE_BATCH_REVIEW", targetType = "ATTENDEE")
    public ApiResponse<Integer> batchReview(@Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(attendeeService.batchReview(request));
    }

    @PutMapping("/{id}/tags")
    @PreAuthorize("@meetingAccess.canManageAttendee(#id)")
    @AuditLog(action = "ATTENDEE_TAGS_UPDATE", targetType = "ATTENDEE")
    public ApiResponse<AttendeeResponse> updateTags(@PathVariable Long id, @RequestBody AttendeeTagsRequest request) {
        return ApiResponse.ok(AttendeeResponse.raw(attendeeService.updateTags(id, request.tags())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@meetingAccess.canManageAttendee(#id)")
    @AuditLog(action = "ATTENDEE_CANCEL", targetType = "ATTENDEE")
    public ApiResponse<AttendeeResponse> cancel(@PathVariable Long id,
                                                @RequestParam(required = false) String reason) {
        return ApiResponse.ok(AttendeeResponse.raw(attendeeService.cancel(id, reason)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "ATTENDEE_IMPORT", targetType = "ATTENDEE")
    public ApiResponse<ImportResult> importExcel(@RequestParam Long meetingId, @RequestPart MultipartFile file) {
        return ApiResponse.ok(attendeeService.importFile(meetingId, file));
    }

    @GetMapping("/export")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "ATTENDEE_EXPORT", targetType = "ATTENDEE")
    public ResponseEntity<byte[]> export(@RequestParam Long meetingId,
                                         @RequestParam(defaultValue = "attendees") String type) {
        exportTaskService.assertDownloadAllowed(meetingId, type);
        return CsvFiles.download("meetingos-attendees.csv", attendeeService.exportCsv(meetingId, type));
    }

    private AttendeeResponse response(Attendee attendee, boolean masked) {
        return masked ? AttendeeResponse.masked(attendee) : AttendeeResponse.raw(attendee);
    }

    private AttendeeResponse response(Attendee attendee, boolean masked, Set<Long> seatQueriedAttendeeIds, Set<Long> roomQueriedAttendeeIds) {
        return masked
                ? AttendeeResponse.masked(attendee, seatQueriedAttendeeIds, roomQueriedAttendeeIds)
                : AttendeeResponse.raw(attendee, seatQueriedAttendeeIds, roomQueriedAttendeeIds);
    }

    private AttendeeResponse response(Attendee attendee,
                                      String seatLabel,
                                      boolean seatAssigned,
                                      boolean dinnerAssigned,
                                      boolean masked,
                                      Set<Long> seatQueriedAttendeeIds,
                                      Set<Long> roomQueriedAttendeeIds,
                                      Set<Long> dinnerQueriedAttendeeIds) {
        /*
         * seatAssigned 与 seatLabel 在正常数据下等价；这里在极端旧数据缺少座位展示字段时，
         * 仍然用兜底标签告诉前端“已有座位分配”，避免误放开拒绝操作。
         */
        String effectiveSeatLabel = seatAssigned && (seatLabel == null || seatLabel.isBlank()) ? "已分配座位" : seatLabel;
        return masked
                ? AttendeeResponse.masked(attendee, effectiveSeatLabel, dinnerAssigned, seatQueriedAttendeeIds, roomQueriedAttendeeIds, dinnerQueriedAttendeeIds)
                : AttendeeResponse.raw(attendee, effectiveSeatLabel, dinnerAssigned, seatQueriedAttendeeIds, roomQueriedAttendeeIds, dinnerQueriedAttendeeIds);
    }

    private String seatLabel(SeatAssignment assignment) {
        Seat seat = assignment.getSeat();
        return seat.getAreaName() + " " + seat.getRowNo() + "排" + seat.getSeatNo() + "座";
    }
}
