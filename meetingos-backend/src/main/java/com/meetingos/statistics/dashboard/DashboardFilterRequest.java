package com.meetingos.statistics.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardFilterRequest(
        @NotBlank @Size(max = 180) String fieldKey,
        @NotNull DashboardFilterOperator operator,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> values
) {
    public DashboardFilterRequest {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
