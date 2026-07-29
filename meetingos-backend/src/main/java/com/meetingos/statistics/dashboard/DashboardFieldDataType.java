package com.meetingos.statistics.dashboard;

/**
 * 字段的统计语义，不直接等同于 TDuck 组件类型。
 * 未能安全确认数字或日期的原始字符串会保守归为 TEXT。
 */
public enum DashboardFieldDataType {
    RECORD,
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    MULTI_SELECT
}
