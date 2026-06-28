package com.meetingos.tduck;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TduckController {

    private final TduckService tduckService;
    private final ObjectMapper objectMapper;

    public TduckController(TduckService tduckService, ObjectMapper objectMapper) {
        this.tduckService = tduckService;
        this.objectMapper = objectMapper;
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

    @GetMapping({"/api/tduck/forms", "/api/admin/tduck/forms"})
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEETING_ADMIN')")
    public ApiResponse<List<TduckFormOption>> forms(@RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tduckService.formOptions(keyword, page, size));
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

    @PostMapping({
            "/api/tduck/webhook",
            "/api/public/tduck/webhook",
            "/api/integrations/tduck/webhook"
    })
    public ApiResponse<TduckWebhookResult> webhook(@RequestHeader(name = "X-TDuck-Signature", required = false) String signature,
                                                   @RequestHeader(name = "X-MeetingOS-Webhook-Token", required = false) String headerToken,
                                                   @RequestParam(name = "token", required = false) String queryToken,
                                                   @RequestBody String rawBody) throws IOException {
        Map<String, Object> payload = objectMapper.readValue(rawBody, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        TduckWebhookRequest request = toWebhookRequest(payload);
        return ApiResponse.ok(tduckService.handleWebhook(request, signature, firstNonBlank(headerToken, queryToken), rawBody));
    }

    @GetMapping("/r/{meetingCode}")
    public void redirectToTduck(@PathVariable String meetingCode, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.sendRedirect(tduckService.resolveRedirectUrl(meetingCode));
    }

    @SuppressWarnings("unchecked")
    private TduckWebhookRequest toWebhookRequest(Map<String, Object> payload) {
        Map<String, Object> data = extractSubmissionData(payload);
        Object nestedData = payload.get("data");
        Map<String, Object> dataContainer = nestedData instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return new TduckWebhookRequest(
                firstNonBlank(asString(payload.get("eventId")), asString(dataContainer.get("eventId"))),
                firstNonBlank(
                        asString(payload.getOrDefault("eventType", payload.get("type"))),
                        asString(dataContainer.getOrDefault("eventType", dataContainer.get("type")))
                ),
                firstNonBlank(asString(payload.get("formKey")), asString(dataContainer.get("formKey"))),
                firstNonBlank(
                        asString(payload.get("dataId")),
                        firstNonBlank(
                                asString(payload.get("id")),
                                firstNonBlank(asString(dataContainer.get("dataId")), asString(dataContainer.get("id")))
                        )
                ),
                data,
                payload
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSubmissionData(Map<String, Object> payload) {
        Object nestedData = payload.get("data");
        if (nestedData instanceof Map<?, ?> nestedMap) {
            Object originalData = nestedMap.get("originalData");
            if (originalData instanceof Map<?, ?> originalMap) {
                return (Map<String, Object>) originalMap;
            }
            return withoutMetaFields((Map<String, Object>) nestedMap);
        }

        Object originalData = payload.get("originalData");
        if (originalData instanceof Map<?, ?> originalMap) {
            return (Map<String, Object>) originalMap;
        }

        return withoutMetaFields(payload);
    }

    private Map<String, Object> withoutMetaFields(Map<String, Object> source) {
        Map<String, Object> data = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!TduckSubmissionFields.isMetaField(key)) {
                data.put(key, value);
            }
        });
        return data;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
