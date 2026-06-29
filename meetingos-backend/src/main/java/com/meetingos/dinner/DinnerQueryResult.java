package com.meetingos.dinner;

public record DinnerQueryResult(
        Long attendeeId,
        String attendeeName,
        String attendeePhone,
        String organization,
        String title,
        String email,
        String meetingName,
        String tableNo,
        String venue,
        String dinnerTime,
        String remark
) {
}
