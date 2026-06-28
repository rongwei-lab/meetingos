package com.meetingos.notification;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/notifications", "/api/admin/notifications"})
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<NotificationRecord>> list(@RequestParam Long meetingId) {
        return ApiResponse.ok(notificationService.list(meetingId));
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "NOTIFICATION_CREATE", targetType = "NOTIFICATION")
    public ApiResponse<NotificationRecord> create(@Valid @RequestBody NotificationRequest request) {
        return ApiResponse.ok(notificationService.create(request));
    }
}
