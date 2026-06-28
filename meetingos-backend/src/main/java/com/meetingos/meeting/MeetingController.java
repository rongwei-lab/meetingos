package com.meetingos.meeting;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.operationlog.AuditLog;
import com.meetingos.publicapi.AdminQueryCandidate;
import com.meetingos.publicapi.AdminQueryRequest;
import com.meetingos.publicapi.MeetingQuerySettingsRequest;
import com.meetingos.publicapi.MeetingQuerySettingsResponse;
import com.meetingos.publicapi.PublicQueryService;
import com.meetingos.publicapi.PublicQueryHistoryResponse;
import com.meetingos.publicapi.PublicQueryHistoryService;
import com.meetingos.publicapi.PublicQueryStatus;
import com.meetingos.publicapi.PublicQuerySummaryResponse;
import com.meetingos.publicapi.PublicQueryType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/meetings", "/api/admin/meetings"})
public class MeetingController {

    private final MeetingService meetingService;
    private final PublicQueryService publicQueryService;
    private final PublicQueryHistoryService publicQueryHistoryService;

    public MeetingController(MeetingService meetingService,
                             PublicQueryService publicQueryService,
                             PublicQueryHistoryService publicQueryHistoryService) {
        this.meetingService = meetingService;
        this.publicQueryService = publicQueryService;
        this.publicQueryHistoryService = publicQueryHistoryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEETING_ADMIN')")
    @AuditLog(action = "MEETING_CREATE", targetType = "MEETING")
    public ApiResponse<Meeting> create(@Valid @RequestBody MeetingRequest request) {
        return ApiResponse.ok(meetingService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_UPDATE", targetType = "MEETING")
    public ApiResponse<Meeting> update(@PathVariable Long id, @Valid @RequestBody MeetingRequest request) {
        return ApiResponse.ok(meetingService.update(id, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEETING_ADMIN','CHECKIN_STAFF','ROOM_STAFF','VIEWER')")
    public ApiResponse<PageResponse<MeetingSummary>> list(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) MeetingStatus status,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(meetingService.list(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@meetingAccess.canView(#id)")
    public ApiResponse<MeetingDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.detail(id));
    }

    @GetMapping("/{id}/query-settings")
    @PreAuthorize("@meetingAccess.canView(#id)")
    public ApiResponse<MeetingQuerySettingsResponse> querySettings(@PathVariable Long id) {
        return ApiResponse.ok(publicQueryService.settings(id, null));
    }

    @PutMapping("/{id}/query-settings")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_QUERY_SETTINGS_UPDATE", targetType = "MEETING")
    public ApiResponse<MeetingQuerySettingsResponse> updateQuerySettings(@PathVariable Long id,
                                                                         @Valid @RequestBody MeetingQuerySettingsRequest request) {
        return ApiResponse.ok(publicQueryService.updateSettings(id, request));
    }

    @PostMapping("/{id}/queries/seat")
    @PreAuthorize("@meetingAccess.canView(#id)")
    public ApiResponse<java.util.List<AdminQueryCandidate>> querySeatCandidates(@PathVariable Long id,
                                                                                @Valid @RequestBody AdminQueryRequest request) {
        return ApiResponse.ok(publicQueryService.adminSeatCandidates(id, request));
    }

    @PostMapping("/{id}/queries/room")
    @PreAuthorize("@meetingAccess.canView(#id)")
    public ApiResponse<java.util.List<AdminQueryCandidate>> queryRoomCandidates(@PathVariable Long id,
                                                                                @Valid @RequestBody AdminQueryRequest request) {
        return ApiResponse.ok(publicQueryService.adminRoomCandidates(id, request));
    }

    @GetMapping("/{id}/query-history")
    @PreAuthorize("@meetingAccess.canView(#id)")
    public ApiResponse<PageResponse<PublicQueryHistoryResponse>> queryHistory(@PathVariable Long id,
                                                                              @RequestParam(required = false) PublicQueryType type,
                                                                              @RequestParam(required = false) PublicQueryStatus status,
                                                                              @RequestParam(required = false) String keyword,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(publicQueryHistoryService.history(id, type, status, keyword, page, size));
    }

    @GetMapping("/{id}/query-history/summary")
    @PreAuthorize("@meetingAccess.canView(#id)")
    public ApiResponse<java.util.List<PublicQuerySummaryResponse>> queryHistorySummary(@PathVariable Long id) {
        return ApiResponse.ok(publicQueryHistoryService.summary(id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_STATUS_CHANGE", targetType = "MEETING")
    public ApiResponse<Meeting> status(@PathVariable Long id, @Valid @RequestBody MeetingStatusRequest request) {
        return ApiResponse.ok(meetingService.transition(id, request.status()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_PUBLISH", targetType = "MEETING")
    public ApiResponse<Meeting> publish(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.transition(id, MeetingStatus.REGISTRATION_OPEN));
    }

    @PostMapping("/{id}/close-registration")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_CLOSE_REGISTRATION", targetType = "MEETING")
    public ApiResponse<Meeting> closeRegistration(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.transition(id, MeetingStatus.REGISTRATION_CLOSED));
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_READY", targetType = "MEETING")
    public ApiResponse<Meeting> ready(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.transition(id, MeetingStatus.READY));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_START", targetType = "MEETING")
    public ApiResponse<Meeting> start(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.transition(id, MeetingStatus.IN_PROGRESS));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_END", targetType = "MEETING")
    public ApiResponse<Meeting> end(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.transition(id, MeetingStatus.ENDED));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_ARCHIVE", targetType = "MEETING")
    public ApiResponse<Meeting> archive(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.archive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@meetingAccess.canManage(#id)")
    @AuditLog(action = "MEETING_DELETE_ARCHIVE", targetType = "MEETING")
    public ApiResponse<Meeting> delete(@PathVariable Long id) {
        return ApiResponse.ok(meetingService.archive(id));
    }
}
