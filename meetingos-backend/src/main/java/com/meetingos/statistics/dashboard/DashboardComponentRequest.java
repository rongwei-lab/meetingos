package com.meetingos.statistics.dashboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardComponentRequest(
        @NotBlank @Size(max = 80) String id,
        @Size(max = 120) String title,
        @NotBlank @Size(max = 180) String fieldKey,
        @NotNull DashboardAggregation aggregation,
        @NotNull DashboardVisualization visualization,
        @Min(DashboardLimits.MIN_TOP_LIMIT) @Max(DashboardLimits.MAX_TOP_LIMIT) Integer limit,
        @Size(max = 8) List<@Valid DashboardFilterRequest> filters,
        DashboardComponentWidth width
) {
    public DashboardComponentRequest {
        filters = filters == null ? List.of() : List.copyOf(filters);
        // 历史看板 JSON 没有宽度字段时继续使用原来的双列半宽布局。
        width = width == null ? DashboardComponentWidth.HALF : width;
    }

    public DashboardComponentRequest(String id,
                                     String title,
                                     String fieldKey,
                                     DashboardAggregation aggregation,
                                     DashboardVisualization visualization,
                                     Integer limit,
                                     List<DashboardFilterRequest> filters) {
        this(id, title, fieldKey, aggregation, visualization, limit, filters, DashboardComponentWidth.HALF);
    }
}
