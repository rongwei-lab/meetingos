package com.meetingos.tduck;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.operationlog.AuditLog;
import com.meetingos.operationlog.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TduckController {

    private static final Logger log = LoggerFactory.getLogger(TduckController.class);

    private final TduckService tduckService;
    private final TduckPullSyncTaskService pullSyncTaskService;
    private final OperationLogService operationLogService;

    public TduckController(TduckService tduckService,
                           TduckPullSyncTaskService pullSyncTaskService,
                           OperationLogService operationLogService) {
        this.tduckService = tduckService;
        this.pullSyncTaskService = pullSyncTaskService;
        this.operationLogService = operationLogService;
    }

    @PostMapping({"/api/tduck/bindings", "/api/admin/tduck/bindings"})
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "TDUCK_BIND_FORM", targetType = "TDUCK_FORM")
    public ApiResponse<TduckBindingResponse> bind(@Valid @RequestBody BindFormRequest request) {
        return ApiResponse.ok(tduckService.bindForm(request));
    }

    @GetMapping({"/api/tduck/bindings/{meetingId}", "/api/admin/tduck/bindings/{meetingId}"})
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<TduckBindingResponse> binding(@PathVariable Long meetingId) {
        return ApiResponse.ok(tduckService.binding(meetingId));
    }

    @PutMapping({"/api/tduck/bindings/{meetingId}/registration-enabled", "/api/admin/tduck/bindings/{meetingId}/registration-enabled"})
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "TDUCK_REGISTRATION_TOGGLE", targetType = "TDUCK_FORM")
    public ApiResponse<TduckBindingResponse> updateRegistrationEnabled(@PathVariable Long meetingId,
                                                                        @Valid @RequestBody TduckRegistrationStatusRequest request) {
        return ApiResponse.ok(tduckService.updateRegistrationEnabled(meetingId, request));
    }

    @GetMapping({"/api/tduck/forms", "/api/admin/tduck/forms"})
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEETING_ADMIN')")
    public ApiResponse<List<TduckFormOption>> forms(@RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tduckService.formOptions(keyword, page, size));
    }

    @PostMapping({"/api/tduck/forms", "/api/admin/tduck/forms"})
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "TDUCK_CREATE_FORM", targetType = "TDUCK_FORM")
    public ApiResponse<TduckFormOption> createForm(@Valid @RequestBody CreateTduckFormRequest request) {
        return ApiResponse.ok(tduckService.createBlankForm(request));
    }

    @GetMapping({"/api/tduck/browser-session", "/api/admin/tduck/browser-session"})
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    public ApiResponse<TduckBrowserSessionResponse> browserSession(@RequestParam Long meetingId) {
        return ApiResponse.ok(tduckService.browserSession(meetingId));
    }

    @PutMapping({"/api/tduck/mappings/{meetingId}", "/api/admin/tduck/mappings/{meetingId}"})
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "TDUCK_UPDATE_FIELD_MAPPING", targetType = "TDUCK_MAPPING")
    public ApiResponse<List<TduckFieldMapping>> updateMappings(@PathVariable Long meetingId,
                                                                @Valid @RequestBody UpdateFieldMappingsRequest request) {
        return ApiResponse.ok(tduckService.updateMappings(meetingId, request));
    }

    @GetMapping({"/api/tduck/mappings/{meetingId}", "/api/admin/tduck/mappings/{meetingId}"})
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<TduckFieldMappingResponse>> mappings(@PathVariable Long meetingId) {
        return ApiResponse.ok(tduckService.mappings(meetingId));
    }

    @PostMapping({"/api/tduck/mappings/{meetingId}/sync", "/api/admin/tduck/mappings/{meetingId}/sync"})
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "TDUCK_SYNC_FIELD_MAPPING", targetType = "TDUCK_MAPPING")
    public ApiResponse<TduckFieldSyncResponse> syncMappings(@PathVariable Long meetingId) {
        return ApiResponse.ok(tduckService.syncFields(meetingId));
    }

    @GetMapping({"/api/tduck/sync-logs", "/api/admin/tduck/sync-logs"})
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<PageResponse<TduckSyncLogResponse>> syncLogs(@RequestParam Long meetingId,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        PageResponse<TduckSyncLog> result = tduckService.syncLogs(meetingId, page, size);
        return ApiResponse.ok(new PageResponse<>(
                result.content().stream().map(TduckSyncLogResponse::masked).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        ));
    }

    @GetMapping({"/api/tduck/sync-logs/{syncLogId}", "/api/admin/tduck/sync-logs/{syncLogId}"})
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<TduckSyncLogDetailResponse> syncLogDetail(@PathVariable Long syncLogId,
                                                                  @RequestParam Long meetingId) {
        return ApiResponse.ok(tduckService.syncLogDetail(meetingId, syncLogId));
    }

    @GetMapping({"/api/tduck/sync-conflicts", "/api/admin/tduck/sync-conflicts"})
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<PageResponse<TduckSubmissionConflictResponse>> submissionConflicts(@RequestParam Long meetingId,
                                                                                            @RequestParam(defaultValue = "0") int page,
                                                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tduckService.submissionConflicts(meetingId, page, size));
    }

    @GetMapping({"/api/tduck/sync-conflicts/{submissionId}", "/api/admin/tduck/sync-conflicts/{submissionId}"})
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<TduckSubmissionConflictResponse> submissionConflict(@PathVariable Long submissionId,
                                                                             @RequestParam Long meetingId) {
        return ApiResponse.ok(tduckService.submissionConflict(meetingId, submissionId));
    }

    @PostMapping({"/api/tduck/sync-conflicts/{submissionId}/resolve", "/api/admin/tduck/sync-conflicts/{submissionId}/resolve"})
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "TDUCK_DUPLICATE_CONFLICT_RESOLVE", targetType = "TDUCK_DATA")
    public ApiResponse<TduckSubmissionConflictResponse> resolveSubmissionConflict(@PathVariable Long submissionId,
                                                                                    @Valid @RequestBody TduckSubmissionResolveRequest request) {
        return ApiResponse.ok(tduckService.resolveSubmissionConflict(submissionId, request));
    }

    @PostMapping({"/api/tduck/sync/manual", "/api/admin/tduck/sync/manual"})
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "TDUCK_MANUAL_SYNC", targetType = "TDUCK_DATA")
    public ApiResponse<TduckWebhookResult> manualSync(@Valid @RequestBody TduckManualSyncRequest request) {
        return ApiResponse.ok(tduckService.manualSync(request));
    }

    @PostMapping({"/api/tduck/sync/pull", "/api/admin/tduck/sync/pull"})
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "TDUCK_PULL_SYNC", targetType = "TDUCK_DATA")
    public ApiResponse<TduckWebhookResult> pullSync(@Valid @RequestBody TduckPullSyncRequest request) {
        return ApiResponse.ok(tduckService.pullSync(request));
    }

    @PostMapping({"/api/tduck/sync/pull/all", "/api/admin/tduck/sync/pull/all"})
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "TDUCK_BATCH_PULL_SYNC", targetType = "TDUCK_DATA")
    public ApiResponse<TduckBatchPullSyncResponse> batchPullSync(@Valid @RequestBody TduckBatchPullSyncRequest request) {
        return ApiResponse.ok(pullSyncTaskService.create(request));
    }

    @GetMapping({
            "/api/tduck/sync/pull/tasks/{taskId}",
            "/api/admin/tduck/sync/pull/tasks/{taskId}"
    })
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    public ApiResponse<TduckBatchPullSyncResponse> batchPullSyncTask(@PathVariable Long taskId,
                                                                     @RequestParam Long meetingId) {
        return ApiResponse.ok(pullSyncTaskService.detail(meetingId, taskId));
    }

    @PostMapping({
            "/api/tduck/webhook",
            "/api/public/tduck/webhook",
            "/api/integrations/tduck/webhook"
    })
    public ApiResponse<TduckWebhookResult> webhook(@RequestHeader(name = "X-TDuck-Signature", required = false) String signature,
                                                   @RequestHeader(name = "X-MeetingOS-Webhook-Token", required = false) String headerToken,
                                                   @RequestParam(name = "token", required = false) String queryToken,
                                                   @RequestBody String rawBody,
                                                   HttpServletRequest request) {
        TduckWebhookResult result;
        try {
            result = tduckService.handleWebhook(rawBody, signature, firstNonBlank(headerToken, queryToken));
        } catch (VerifiedWebhookParseException ex) {
            // 只有服务层在验签成功后才会抛出该异常，因此可以安全记录固定失败摘要。
            recordWebhookFailure(request, ex.getSyncLogId(), "TDUCK_WEBHOOK_PARSE_FAILED");
            throw ex;
        }
        recordWebhookSync(request, result);
        return ApiResponse.ok(result);
    }

    @GetMapping("/r/{meetingCode}")
    public void redirectToTduck(@PathVariable String meetingCode, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.sendRedirect(tduckService.resolveRedirectUrl(meetingCode));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private void recordWebhookSync(HttpServletRequest request, TduckWebhookResult result) {
        if (result.status() == SyncStatus.DUPLICATE_IGNORED) {
            // TDuck 可能重试同一事件；重复请求不产生状态变更，也不应放大操作日志。
            return;
        }
        try {
            /*
             * Webhook 的原始体、签名和 query token 都可能包含凭据或报名信息。
             * 不能使用 @AuditLog 直接汇总方法参数，只记录已验签同步结果中的安全标识。
             * 未通过验签的请求会在 TduckService 拒绝，此处不额外记日志，避免被公网探测刷量。
             */
            boolean success = result.status() == SyncStatus.SUCCESS;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("syncLogId", result.syncLogId());
            details.put("attendeeId", result.attendeeId());
            details.put("status", result.status().name());
            if (!success) {
                details.put("errorCode", "TDUCK_SYNC_FAILED");
            }
            recordWebhookAudit(request, result.syncLogId(), success,
                    success ? "TDuck Webhook 同步成功" : "TDuck Webhook 同步失败", details);
        } catch (RuntimeException ex) {
            // Webhook 同步已经完成，操作日志异常不能影响 TDuck 的应答与重试语义。
            log.warn("TDuck webhook audit log recording failed", ex);
        }
    }

    private void recordWebhookFailure(HttpServletRequest request, Long syncLogId, String errorCode) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("syncLogId", syncLogId);
            details.put("attendeeId", null);
            details.put("status", SyncStatus.FAILED.name());
            details.put("errorCode", errorCode);
            recordWebhookAudit(request, syncLogId, false, "TDuck Webhook 同步失败", details);
        } catch (RuntimeException ex) {
            // 解析失败已经有 TDuck 同步日志，补充审计失败也不能改变原有 400 应答。
            log.warn("TDuck webhook parse audit log recording failed", ex);
        }
    }

    private void recordWebhookAudit(HttpServletRequest request, Long syncLogId, boolean success,
                                    String message, Map<String, Object> details) {
        operationLogService.record(
                "TDUCK_WEBHOOK_SYNC",
                "TDUCK_SYNC",
                syncLogId == null ? null : syncLogId.toString(),
                success,
                message,
                request,
                details
        );
    }
}
