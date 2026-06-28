package com.meetingos.checkin;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/checkin", "/api/admin/checkin"})
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canCheckin(#request.meetingId())")
    @AuditLog(action = "CHECKIN", targetType = "ATTENDEE")
    public ApiResponse<CheckinResponse> checkin(@Valid @RequestBody CheckinRequest request) {
        return ApiResponse.ok(checkinService.checkin(request));
    }

    @GetMapping("/records")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<PageResponse<CheckinRecordResponse>> records(@RequestParam Long meetingId,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(checkinService.records(meetingId, page, size));
    }
}
