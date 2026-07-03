package com.meetingos.exporttask;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.SpreadsheetFiles;
import com.meetingos.operationlog.AuditLog;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/exports", "/api/admin/exports"})
public class ExportTaskController {

    private final ExportTaskService exportTaskService;

    public ExportTaskController(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "EXPORT_CREATE", targetType = "EXPORT_TASK")
    public ApiResponse<ExportTaskResponse> create(@RequestParam Long meetingId, @RequestParam String type) {
        return ApiResponse.ok(exportTaskService.create(meetingId, type));
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    public ApiResponse<List<ExportTaskResponse>> list(@RequestParam Long meetingId) {
        return ApiResponse.ok(exportTaskService.list(meetingId));
    }

    @GetMapping("/download")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "EXPORT_DOWNLOAD", targetType = "EXPORT_TASK")
    public ResponseEntity<byte[]> download(
            @RequestParam Long meetingId,
            @RequestParam(defaultValue = "attendees") String type,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ExportFile file = exportTaskService.build(meetingId, type, format, startDate, endDate);
        return SpreadsheetFiles.download(file.filename(), file.content(), file.contentType());
    }
}
