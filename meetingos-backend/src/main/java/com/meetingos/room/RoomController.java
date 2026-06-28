package com.meetingos.room;

import com.meetingos.attendee.Attendee;
import com.meetingos.common.ApiResponse;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ImportResult;
import com.meetingos.exporttask.ExportTaskService;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
@RequestMapping({"/api/rooms", "/api/admin/rooms"})
public class RoomController {

    private final RoomService roomService;
    private final ExportTaskService exportTaskService;

    public RoomController(RoomService roomService,
                          ExportTaskService exportTaskService) {
        this.roomService = roomService;
        this.exportTaskService = exportTaskService;
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canManageRooms(#request.meetingId())")
    @AuditLog(action = "ROOM_CREATE", targetType = "ROOM")
    public ApiResponse<Room> create(@Valid @RequestBody RoomRequest request) {
        return ApiResponse.ok(roomService.create(request));
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<Room>> list(@RequestParam Long meetingId) {
        return ApiResponse.ok(roomService.list(meetingId));
    }

    @PostMapping("/assign")
    @PreAuthorize("@meetingAccess.canManageRoomByAttendee(#request.attendeeId())")
    @AuditLog(action = "ROOM_ASSIGN", targetType = "ROOM")
    public ApiResponse<Attendee> assign(@Valid @RequestBody AssignRoomRequest request) {
        return ApiResponse.ok(roomService.assign(request));
    }

    @PostMapping("/clear")
    @PreAuthorize("@meetingAccess.canManageRoomByAttendee(#attendeeId)")
    @AuditLog(action = "ROOM_ASSIGNMENT_CLEAR", targetType = "ROOM")
    public ApiResponse<Attendee> clearAssignment(@RequestParam Long attendeeId) {
        return ApiResponse.ok(roomService.clearAssignment(attendeeId));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@meetingAccess.canManageRooms(#meetingId)")
    @AuditLog(action = "ROOM_IMPORT", targetType = "ROOM")
    public ApiResponse<ImportResult> importRooms(@RequestParam Long meetingId, @RequestPart MultipartFile file) {
        return ApiResponse.ok(roomService.importRooms(meetingId, file));
    }

    @PostMapping(value = "/assignments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@meetingAccess.canManageRooms(#meetingId)")
    @AuditLog(action = "ROOM_ASSIGNMENT_IMPORT", targetType = "ROOM")
    public ApiResponse<ImportResult> importAssignments(@RequestParam Long meetingId, @RequestPart MultipartFile file) {
        return ApiResponse.ok(roomService.importAssignments(meetingId, file));
    }

    @GetMapping("/export")
    @PreAuthorize("@meetingAccess.canManageRooms(#meetingId)")
    @AuditLog(action = "ROOM_EXPORT", targetType = "ROOM")
    public ResponseEntity<byte[]> export(@RequestParam Long meetingId,
                                         @RequestParam(defaultValue = "assignments") String type) {
        String exportType = "inventory".equalsIgnoreCase(type) ? "room-inventory" : "rooms";
        exportTaskService.assertDownloadAllowed(meetingId, exportType);
        String csv = "inventory".equalsIgnoreCase(type)
                ? roomService.exportRoomInventoryCsv(meetingId)
                : roomService.exportRoomsCsv(meetingId);
        return CsvFiles.download("meetingos-rooms.csv", csv);
    }

    @GetMapping("/query")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<RoomQueryResult> query(@RequestParam Long meetingId, @RequestParam String phone) {
        return ApiResponse.ok(roomService.query(meetingId, phone));
    }
}
