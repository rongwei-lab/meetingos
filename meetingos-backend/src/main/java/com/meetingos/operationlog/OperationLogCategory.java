package com.meetingos.operationlog;

import java.util.Locale;

/**
 * 操作日志的展示分类不落库，而是由历史 action 实时推导。
 * 这样既不会改写已有审计数据，也能让新旧日志遵循同一套筛选规则。
 */
public enum OperationLogCategory {
    ACCOUNT_PERMISSION("账户与权限"),
    MEETING_CONFIGURATION("会议与配置"),
    ATTENDEE_CHECKIN("参会人与签到"),
    VENUE_SEATING("场地与排座"),
    REGISTRATION_TDUCK("报名与 TDuck"),
    INVITATION("邀请函"),
    IMPORT_EXPORT("导入导出"),
    SYSTEM("系统");

    private final String label;

    OperationLogCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 导入、导出必须优先于业务模块分类，避免例如 ATTENDEE_IMPORT 被误归入参会人。
     */
    public static OperationLogCategory fromAction(String action) {
        String normalized = normalize(action);
        if (containsImportOrExport(normalized)) {
            return IMPORT_EXPORT;
        }
        if (startsWithAny(normalized, "AUTH_", "USER_", "MEETING_PERMISSION_")) {
            return ACCOUNT_PERMISSION;
        }
        if ((normalized.startsWith("MEETING_") && !normalized.startsWith("MEETING_PERMISSION_"))
                || normalized.startsWith("NOTIFICATION_")) {
            return MEETING_CONFIGURATION;
        }
        if (normalized.startsWith("ATTENDEE_") || normalized.contains("CHECKIN")) {
            return ATTENDEE_CHECKIN;
        }
        if (startsWithAny(normalized, "ROOM_", "SEATING_", "DINNER_")) {
            return VENUE_SEATING;
        }
        if (normalized.startsWith("TDUCK_")) {
            return REGISTRATION_TDUCK;
        }
        if (normalized.startsWith("INVITATION_")) {
            return INVITATION;
        }
        return SYSTEM;
    }

    static boolean containsImportOrExport(String action) {
        return action.contains("IMPORT") || action.contains("EXPORT");
    }

    static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
    }
}
