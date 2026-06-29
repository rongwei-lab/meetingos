package com.meetingos.dinner;

public record DinnerAssignmentResponse(
        Long id,
        Long meetingId,
        Long attendeeId,
        String attendeeName,
        String attendeePhone,
        String organization,
        String title,
        String attendeeType,
        String tableNo,
        String venue,
        String dinnerTime,
        String remark,
        String assignedBy
) {
}
