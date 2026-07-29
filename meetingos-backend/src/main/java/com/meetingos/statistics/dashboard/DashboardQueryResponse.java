package com.meetingos.statistics.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardQueryResponse(
        List<DashboardComponentResult> components,
        LocalDateTime generatedAt
) {
}
