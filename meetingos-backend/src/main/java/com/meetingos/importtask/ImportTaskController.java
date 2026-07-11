package com.meetingos.importtask;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.PageResponse;
import com.meetingos.operationlog.AuditLog;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping({"/api/imports", "/api/admin/imports"})
public class ImportTaskController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ImportTaskService service;

    public ImportTaskController(ImportTaskService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@importTaskAccess.canCreate(#meetingId, #type)")
    @AuditLog(action = "IMPORT_TASK_CREATE", targetType = "IMPORT_TASK")
    public ApiResponse<ImportTaskResponse> create(@RequestParam Long meetingId,
                                                  @RequestParam String type,
                                                  @RequestParam(defaultValue = "true") boolean preflight,
                                                  @RequestParam String idempotencyKey,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.create(meetingId, type, preflight, idempotencyKey, file));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@importTaskAccess.canAccess(#taskId, #meetingId)")
    public ApiResponse<ImportTaskResponse> detail(@PathVariable Long taskId, @RequestParam Long meetingId) {
        return ApiResponse.ok(service.detail(taskId, meetingId));
    }

    @GetMapping
    @PreAuthorize("@importTaskAccess.canList(#meetingId)")
    public ApiResponse<PageResponse<ImportTaskResponse>> list(@RequestParam Long meetingId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(meetingId, page, size));
    }

    @PostMapping("/{taskId}/commit")
    @PreAuthorize("@importTaskAccess.canAccess(#taskId, #meetingId)")
    @AuditLog(action = "IMPORT_TASK_COMMIT", targetType = "IMPORT_TASK")
    public ApiResponse<ImportTaskResponse> commit(@PathVariable Long taskId, @RequestParam Long meetingId) {
        return ApiResponse.ok(service.commit(taskId, meetingId));
    }

    @GetMapping("/{taskId}/failures")
    @PreAuthorize("@importTaskAccess.canAccess(#taskId, #meetingId)")
    @AuditLog(action = "IMPORT_FAILURE_DOWNLOAD", targetType = "IMPORT_TASK")
    public ResponseEntity<FileSystemResource> failures(@PathVariable Long taskId, @RequestParam Long meetingId) {
        ImportFailureFile file = service.failureFile(taskId, meetingId);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new FileSystemResource(file.path()));
    }
}
