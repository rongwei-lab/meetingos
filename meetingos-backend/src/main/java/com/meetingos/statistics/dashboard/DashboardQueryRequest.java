package com.meetingos.statistics.dashboard;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record DashboardQueryRequest(
        @NotNull Long meetingId,
        @Size(max = 12) List<@Valid DashboardComponentRequest> components,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate
) {
    public DashboardQueryRequest {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
