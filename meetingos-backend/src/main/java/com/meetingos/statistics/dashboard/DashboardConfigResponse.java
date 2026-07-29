package com.meetingos.statistics.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardConfigResponse(
        Long meetingId,
        List<DashboardComponentRequest> components,
        LocalDateTime updatedAt,
        boolean canManage
) {
    public DashboardConfigResponse withCanManage(boolean canManage) {
        return new DashboardConfigResponse(meetingId, components, updatedAt, canManage);
    }
}
