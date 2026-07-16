package com.meetingos.operationlog;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/operation-logs", "/api/admin/operation-logs"})
public class OperationLogController {

    static final String LOG_SNAPSHOT_HEADER = "X-MeetingOS-Log-Snapshot";

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PageResponse<OperationLogListItem>> list(@RequestParam(required = false) String action,
                                                                 @RequestParam(required = false) String category,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String success,
                                                                 @RequestParam(required = false) String fromDate,
                                                                 @RequestParam(required = false) String toDate,
                                                                 @RequestParam(required = false) String includeDraftSaves,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestHeader(value = LOG_SNAPSHOT_HEADER, required = false) Long snapshotId,
                                                                 HttpServletResponse response) {
        OperationLogFilter filter = OperationLogFilter.fromRequest(
                action, category, keyword, success, fromDate, toDate, includeDraftSaves
        );
        OperationLogService.SnapshotPage result = operationLogService.listFiltered(filter, page, size, snapshotId);
        response.setHeader(LOG_SNAPSHOT_HEADER, Long.toString(result.snapshotId()));
        return ApiResponse.ok(toListPage(result.page()));
    }

    /**
     * 保留供已有 Controller 单测和内部直接调用使用的兼容入口；HTTP 请求始终走完整筛选接口。
     */
    ApiResponse<PageResponse<OperationLogListItem>> list(String action, int page, int size, Long snapshotId,
                                                          HttpServletResponse response) {
        OperationLogService.SnapshotPage result = operationLogService.list(action, page, size, snapshotId);
        response.setHeader(LOG_SNAPSHOT_HEADER, Long.toString(result.snapshotId()));
        return ApiResponse.ok(toListPage(result.page()));
    }

    private PageResponse<OperationLogListItem> toListPage(PageResponse<OperationLog> page) {
        List<OperationLogListItem> content = page.content().stream()
                .map(OperationLogListItem::from)
                .toList();
        return new PageResponse<>(content, page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
