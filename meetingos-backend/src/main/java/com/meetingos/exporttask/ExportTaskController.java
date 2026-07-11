package com.meetingos.exporttask;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.common.SpreadsheetFiles;
import com.meetingos.operationlog.AuditLog;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/exports", "/api/admin/exports"})
public class ExportTaskController {

    private final ExportTaskService exportTaskService;

    public ExportTaskController(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    @AuditLog(action = "EXPORT_CREATE", targetType = "EXPORT_TASK")
    public ApiResponse<ExportTaskResponse> create(
            @RequestParam Long meetingId,
            @RequestParam String type,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.ok(exportTaskService.create(meetingId, type, format, startDate, endDate));
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<PageResponse<ExportTaskResponse>> list(
            @RequestParam Long meetingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(exportTaskService.list(meetingId, page, size));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<ExportTaskResponse> get(@PathVariable Long taskId, @RequestParam Long meetingId) {
        return ApiResponse.ok(exportTaskService.get(meetingId, taskId));
    }

    @GetMapping("/{taskId}/download")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    @AuditLog(action = "EXPORT_DOWNLOAD", targetType = "EXPORT_TASK")
    public ResponseEntity<Resource> downloadTask(@PathVariable Long taskId, @RequestParam Long meetingId) {
        ExportDownload download = exportTaskService.openDownload(meetingId, taskId);
        return SpreadsheetFiles.download(
                download.filename(),
                download.resource(),
                download.contentLength(),
                download.contentType()
        );
    }

    @GetMapping("/download")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    @AuditLog(action = "EXPORT_DOWNLOAD", targetType = "EXPORT_TASK")
    public ResponseEntity<byte[]> download(
            @RequestParam Long meetingId,
            @RequestParam(defaultValue = "attendees") String type,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ExportFile file = exportTaskService.build(meetingId, type, format, startDate, endDate);
        return SpreadsheetFiles.download(file.filename(), file.content(), file.contentType());
    }
}
