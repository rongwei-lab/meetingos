package com.meetingos.statistics.dashboard;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.AuditLog;
import com.meetingos.permission.MeetingAccessService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可配置统计看板的管理端接口。保留 /api/statistics 前缀以兼容既有管理端反向代理，
 * 新页面统一使用 /api/admin/statistics/dashboard。
 */
@RestController
@RequestMapping({"/api/statistics/dashboard", "/api/admin/statistics/dashboard"})
public class DashboardController {

    private final DashboardService dashboardService;
    private final MeetingAccessService meetingAccessService;

    public DashboardController(DashboardService dashboardService,
                               MeetingAccessService meetingAccessService) {
        this.dashboardService = dashboardService;
        this.meetingAccessService = meetingAccessService;
    }

    @GetMapping("/fields")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    public ApiResponse<DashboardFieldCatalogResponse> fields(@RequestParam Long meetingId) {
        return ApiResponse.ok(dashboardService.fields(meetingId));
    }

    @GetMapping("/config")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<DashboardConfigResponse> config(@RequestParam Long meetingId) {
        return ApiResponse.ok(dashboardService.config(meetingId)
                .withCanManage(meetingAccessService.canManage(meetingId)));
    }

    @PutMapping("/config")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "DASHBOARD_CONFIG_UPDATE", targetType = "MEETING_DASHBOARD")
    public ApiResponse<DashboardConfigResponse> saveConfig(@Valid @RequestBody DashboardConfigRequest request) {
        // 已通过 canManage 授权，响应也显式告知前端当前会话可继续编辑。
        return ApiResponse.ok(dashboardService.saveConfig(request).withCanManage(true));
    }

    @PostMapping("/query")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    public ApiResponse<DashboardQueryResponse> query(@Valid @RequestBody DashboardQueryRequest request) {
        return ApiResponse.ok(dashboardService.query(request));
    }
}
