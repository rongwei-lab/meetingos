package com.meetingos.permission;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions/meeting-users")
public class MeetingUserPermissionController {

    private final MeetingUserPermissionService permissionService;

    public MeetingUserPermissionController(MeetingUserPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    public ApiResponse<List<MeetingUserPermissionResponse>> list(@RequestParam(required = false) Long meetingId,
                                                                  @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(permissionService.list(meetingId, userId));
    }

    @PostMapping
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    @AuditLog(action = "MEETING_PERMISSION_GRANT", targetType = "MEETING_USER_PERMISSION")
    public ApiResponse<MeetingUserPermissionResponse> grant(@Valid @RequestBody MeetingUserPermissionRequest request) {
        return ApiResponse.ok(permissionService.grant(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    @AuditLog(action = "MEETING_PERMISSION_DELETE", targetType = "MEETING_USER_PERMISSION")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResponse.ok();
    }
}
