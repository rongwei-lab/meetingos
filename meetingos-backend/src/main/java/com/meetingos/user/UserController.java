package com.meetingos.user;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.operationlog.AuditLog;
import com.meetingos.security.UserStatus;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) UserStatus status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.list(keyword, status, page, size));
    }

    @PostMapping
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    @AuditLog(action = "USER_CREATE", targetType = "APP_USER")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    @AuditLog(action = "USER_UPDATE", targetType = "APP_USER")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.update(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@systemAdminAccess.isAdminAccount()")
    @AuditLog(action = "USER_RESET_PASSWORD", targetType = "APP_USER")
    public ApiResponse<UserResponse> resetPassword(@PathVariable Long id,
                                                   @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(userService.resetPassword(id, request));
    }
}
