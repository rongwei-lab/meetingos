package com.meetingos.statistics.dashboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardConfigRequest(
        @NotNull Long meetingId,
        @Size(max = 12) List<@Valid DashboardComponentRequest> components
) {
    public DashboardConfigRequest {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
