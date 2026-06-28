package com.meetingos.statistics;

import java.util.Map;

public record StatisticsOverview(
        long registrationCount,
        long pendingCount,
        long approvedCount,
        long rejectedCount,
        long todayRegistrationCount,
        long totalSeatCount,
        long availableSeatCount,
        long seatedCount,
        long emptySeatCount,
        long disabledSeatCount,
        double seatUsageRate,
        long roomCount,
        long accommodationRequiredCount,
        long roomAssignedRequiredCount,
        long roomUnassignedRequiredCount,
        long roomAssignedCount,
        long roomUnassignedCount,
        long checkedInCount,
        long notCheckedInCount,
        double checkinRate,
        long rangeRegistrationCount,
        long rangeCheckinCount,
        Map<String, Long> registrationTrend,
        Map<String, Long> checkinTrend,
        Map<String, Long> reviewStatusDistribution,
        Map<String, Long> attendeeTypeDistribution,
        Map<String, Long> organizationDistribution,
        Map<String, Long> hotelDistribution,
        Map<String, Long> roomTypeDistribution,
        Map<String, Long> checkinEntranceDistribution,
        Map<String, Long> checkinTypeDistribution
) {
}
