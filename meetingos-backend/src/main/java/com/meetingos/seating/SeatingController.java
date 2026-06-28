package com.meetingos.seating;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/seating", "/api/admin/seating"})
public class SeatingController {

    private final SeatingService seatingService;

    public SeatingController(SeatingService seatingService) {
        this.seatingService = seatingService;
    }

    @PostMapping("/layouts")
    @PreAuthorize("@meetingAccess.canManage(#request.meetingId())")
    @AuditLog(action = "SEATING_CREATE_LAYOUT", targetType = "SEAT_LAYOUT")
    public ApiResponse<List<Seat>> createLayout(@Valid @RequestBody SeatLayoutRequest request) {
        return ApiResponse.ok(seatingService.createLayout(request));
    }

    @DeleteMapping("/layouts")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "SEATING_DELETE_AREA", targetType = "SEAT_LAYOUT")
    public ApiResponse<Void> deleteArea(@RequestParam Long meetingId, @RequestParam String areaName) {
        seatingService.deleteArea(meetingId, areaName);
        return ApiResponse.ok();
    }

    @PostMapping("/auto-assign/{meetingId}")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "SEATING_AUTO_ASSIGN", targetType = "SEAT_ASSIGNMENT")
    public ApiResponse<AutoSeatingResult> autoAssign(@PathVariable Long meetingId) {
        return ApiResponse.ok(seatingService.autoAssign(meetingId));
    }

    @PostMapping("/manual-assign")
    @PreAuthorize("@meetingAccess.canManageAttendee(#request.attendeeId())")
    @AuditLog(action = "SEATING_MANUAL_ASSIGN", targetType = "SEAT_ASSIGNMENT")
    public ApiResponse<SeatAssignment> manualAssign(@Valid @RequestBody ManualAssignSeatRequest request) {
        return ApiResponse.ok(seatingService.manualAssign(request));
    }

    @PostMapping("/swap")
    @PreAuthorize("@meetingAccess.canManageAttendee(#request.firstAttendeeId()) and @meetingAccess.canManageAttendee(#request.secondAttendeeId())")
    @AuditLog(action = "SEATING_SWAP", targetType = "SEAT_ASSIGNMENT")
    public ApiResponse<Void> swap(@Valid @RequestBody SwapSeatRequest request) {
        seatingService.swap(request);
        return ApiResponse.ok();
    }

    @PostMapping("/clear/{attendeeId}")
    @PreAuthorize("@meetingAccess.canManageAttendee(#attendeeId)")
    @AuditLog(action = "SEATING_CLEAR", targetType = "SEAT_ASSIGNMENT")
    public ApiResponse<Void> clear(@PathVariable Long attendeeId) {
        seatingService.clear(attendeeId);
        return ApiResponse.ok();
    }

    @PutMapping("/seats/{seatId}/lock")
    @PreAuthorize("@meetingAccess.canManageSeat(#seatId)")
    @AuditLog(action = "SEATING_LOCK_SEAT", targetType = "SEAT")
    public ApiResponse<Seat> lock(@PathVariable Long seatId, @RequestBody SeatLockRequest request) {
        return ApiResponse.ok(seatingService.lock(seatId, request));
    }

    @PutMapping("/seats/{seatId}/type")
    @PreAuthorize("@meetingAccess.canManageSeat(#seatId)")
    @AuditLog(action = "SEATING_UPDATE_SEAT_TYPE", targetType = "SEAT")
    public ApiResponse<Seat> updateType(@PathVariable Long seatId, @Valid @RequestBody SeatTypeRequest request) {
        return ApiResponse.ok(seatingService.updateSeatType(seatId, request));
    }

    @PutMapping("/seats/{seatId}/disabled")
    @PreAuthorize("@meetingAccess.canManageSeat(#seatId)")
    @AuditLog(action = "SEATING_TOGGLE_SEAT_DISABLED", targetType = "SEAT")
    public ApiResponse<Seat> updateDisabled(@PathVariable Long seatId, @Valid @RequestBody SeatDisabledRequest request) {
        return ApiResponse.ok(seatingService.updateSeatDisabled(seatId, request));
    }

    @GetMapping("/map/{meetingId}")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<SeatView>> seatMap(@PathVariable Long meetingId) {
        return ApiResponse.ok(seatingService.seatMap(meetingId));
    }

    @GetMapping("/query")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<SeatQueryResult> query(@RequestParam Long meetingId,
                                               @RequestParam(required = false) String phone,
                                               @RequestParam(required = false) String name,
                                               @RequestParam(required = false) String phoneLast4,
                                               @RequestParam(required = false) String attendeeCode) {
        return ApiResponse.ok(seatingService.querySeat(meetingId, phone, name, phoneLast4, attendeeCode));
    }
}
