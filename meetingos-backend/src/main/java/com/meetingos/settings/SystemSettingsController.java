package com.meetingos.settings;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class SystemSettingsController {

    private final SystemSettingsService settingsService;

    public SystemSettingsController(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    public ApiResponse<SystemSettingsResponse> current() {
        return ApiResponse.ok(settingsService.current());
    }

    @PutMapping
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    @AuditLog(action = "SYSTEM_SETTINGS_UPDATE", targetType = "SYSTEM_SETTINGS")
    public ApiResponse<SystemSettingsResponse> update(@Valid @RequestBody SystemSettingsRequest request) {
        return ApiResponse.ok(settingsService.update(request));
    }
}
