package com.meetingos.statistics.dashboard;

public final class DashboardLimits {

    public static final int MIN_TOP_LIMIT = 1;
    public static final int DEFAULT_TOP_LIMIT = 10;

    // 单场会议约千人时，100 项足够覆盖长尾字段，同时避免图表和响应体失控。
    public static final int MAX_TOP_LIMIT = 100;

    private DashboardLimits() {
    }
}
