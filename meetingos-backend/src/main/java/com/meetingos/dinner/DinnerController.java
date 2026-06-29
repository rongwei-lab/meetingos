package com.meetingos.dinner;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.ImportResult;
import com.meetingos.common.SpreadsheetFiles;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/api/dinner", "/api/admin/dinner"})
public class DinnerController {

    private final DinnerService dinnerService;

    public DinnerController(DinnerService dinnerService) {
        this.dinnerService = dinnerService;
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<DinnerAssignmentResponse>> list(@RequestParam Long meetingId) {
        return ApiResponse.ok(dinnerService.list(meetingId));
    }

    @GetMapping("/tables")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<DinnerTableResponse>> listTables(@RequestParam Long meetingId) {
        return ApiResponse.ok(dinnerService.listTables(meetingId));
    }

    @GetMapping("/settings")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<DinnerSettingsResponse> settings(@RequestParam Long meetingId) {
        return ApiResponse.ok(dinnerService.settings(meetingId));
    }

    @PutMapping("/settings")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "DINNER_SETTINGS_UPDATE", targetType = "DINNER")
    public ApiResponse<DinnerSettingsResponse> updateSettings(@Valid @RequestBody DinnerSettingsRequest request) {
        return ApiResponse.ok(dinnerService.updateSettings(request));
    }

    @PostMapping("/tables")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "DINNER_TABLE_CREATE", targetType = "DINNER_TABLE")
    public ApiResponse<DinnerTableResponse> createTable(@Valid @RequestBody DinnerTableRequest request) {
        return ApiResponse.ok(dinnerService.createTable(request));
    }

    @PutMapping("/tables/{tableId}")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "DINNER_TABLE_UPDATE", targetType = "DINNER_TABLE")
    public ApiResponse<DinnerTableResponse> updateTable(@PathVariable Long tableId, @Valid @RequestBody DinnerTableRequest request) {
        return ApiResponse.ok(dinnerService.updateTable(tableId, request));
    }

    @DeleteMapping("/tables/{tableId}")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "DINNER_TABLE_DELETE", targetType = "DINNER_TABLE")
    public ApiResponse<Void> deleteTable(@RequestParam Long meetingId, @PathVariable Long tableId) {
        dinnerService.deleteTable(meetingId, tableId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/assign")
    @PreAuthorize("@meetingAccess.canManageAttendee(#request.attendeeId())")
    @AuditLog(action = "DINNER_ASSIGN", targetType = "DINNER")
    public ApiResponse<DinnerAssignmentResponse> assign(@Valid @RequestBody DinnerAssignRequest request) {
        return ApiResponse.ok(dinnerService.assign(request));
    }

    @PostMapping("/clear")
    @PreAuthorize("@meetingAccess.canManageAttendee(#attendeeId)")
    @AuditLog(action = "DINNER_ASSIGNMENT_CLEAR", targetType = "DINNER")
    public ApiResponse<Void> clear(@RequestParam Long attendeeId) {
        dinnerService.clear(attendeeId);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "DINNER_ASSIGNMENT_IMPORT", targetType = "DINNER")
    public ApiResponse<ImportResult> importAssignments(@RequestParam Long meetingId, @RequestPart MultipartFile file) {
        return ApiResponse.ok(dinnerService.importAssignments(meetingId, file));
    }

    @GetMapping("/export")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "DINNER_EXPORT", targetType = "DINNER")
    public ResponseEntity<byte[]> export(@RequestParam Long meetingId) {
        byte[] xlsx = SpreadsheetFiles.toXlsx(List.of(dinnerService.exportDinnerTable(meetingId)));
        return SpreadsheetFiles.download("meetingos-dinner.xlsx", xlsx, SpreadsheetFiles.XLSX_MEDIA_TYPE);
    }
}
