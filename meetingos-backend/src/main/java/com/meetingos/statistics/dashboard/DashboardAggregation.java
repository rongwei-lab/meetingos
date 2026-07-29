package com.meetingos.statistics.dashboard;

/**
 * 看板允许的受控聚合方式。服务端只执行这些固定操作，不能把前端输入拼接为 SQL。
 */
public enum DashboardAggregation {
    COUNT,
    FILLED_COUNT,
    DISTINCT_COUNT,
    DISTRIBUTION,
    SUM,
    AVERAGE,
    MIN,
    MAX
}
