package com.meetingos.statistics.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardComponentResult(
        String id,
        String title,
        String fieldKey,
        DashboardAggregation aggregation,
        DashboardVisualization visualization,
        DashboardComponentStatus status,
        String message,
        BigDecimal value,
        long total,
        List<DashboardDistributionItem> items
) {
}
