package com.meetingos.statistics.dashboard;

import java.util.List;

/**
 * 前端编辑器只能使用这里返回的字段和能力集合，避免任意属性名进入统计执行层。
 */
public record DashboardFieldDescriptor(
        String key,
        String label,
        String category,
        DashboardFieldSource source,
        DashboardFieldDataType dataType,
        boolean sensitive,
        long coverageCount,
        List<DashboardAggregation> allowedAggregations,
        List<DashboardVisualization> allowedVisualizations,
        List<DashboardFilterOperator> allowedOperators,
        List<DashboardFieldOption> options
) {
}
