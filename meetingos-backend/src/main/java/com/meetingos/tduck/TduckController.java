package com.meetingos.tduck;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
import java.util.List;

@RestController
public class TduckController {

    private final TduckService tduckService;

    public TduckController(TduckService tduckService) {
        this.tduckService = tduckService;
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
        return ApiResponse.ok(tduckService.batchPullSync(request));
    }

    @PostMapping({
            "/api/tduck/webhook",
            "/api/public/tduck/webhook",
            "/api/integrations/tduck/webhook"
    })
    public ApiResponse<TduckWebhookResult> webhook(@RequestHeader(name = "X-TDuck-Signature", required = false) String signature,
                                                   @RequestHeader(name = "X-MeetingOS-Webhook-Token", required = false) String headerToken,
                                                   @RequestParam(name = "token", required = false) String queryToken,
                                                   @RequestBody String rawBody) {
        return ApiResponse.ok(tduckService.handleWebhook(rawBody, signature, firstNonBlank(headerToken, queryToken)));
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
}
