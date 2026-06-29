package com.meetingos.dinner;

public record DinnerTableResponse(
        Long id,
        Long meetingId,
        String tableNo,
        Integer capacity,
        Long occupiedCount,
        Long remainingCount,
        Long overCapacityCount,
        String venue,
        String dinnerTime,
        String remark,
        String createdBy
) {
}
