package com.meetingos.operationlog;

import com.meetingos.common.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 操作日志列表的统一筛选条件。参数解析在这里完成，避免 Controller 和 Service 对默认值产生分歧。
 */
public record OperationLogFilter(
        String action,
        OperationLogCategory category,
        String keyword,
        Boolean success,
        LocalDate fromDate,
        LocalDate toDate,
        boolean includeDraftSaves
) {
    public static final int MAX_KEYWORD_LENGTH = 100;

    public OperationLogFilter {
        action = normalize(action);
        keyword = normalize(keyword);
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw BusinessException.badRequest("keyword 不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw BusinessException.badRequest("fromDate 不能晚于 toDate");
        }
    }

    public static OperationLogFilter fromRequest(String action, String category, String keyword,
                                                 String success, String fromDate, String toDate,
                                                 String includeDraftSaves) {
        return new OperationLogFilter(
                action,
                parseCategory(category),
                keyword,
                parseOptionalBoolean(success, "success"),
                parseDate(fromDate, "fromDate"),
                parseDate(toDate, "toDate"),
                parseBooleanOrDefault(includeDraftSaves, "includeDraftSaves", false)
        );
    }

    public static OperationLogFilter legacyAction(String action) {
        return new OperationLogFilter(action, null, null, null, null, null, false);
    }

    private static OperationLogCategory parseCategory(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return OperationLogCategory.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("category 无效");
        }
    }

    private static LocalDate parseDate(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest(name + " 必须为 yyyy-MM-dd 格式");
        }
    }

    private static Boolean parseOptionalBoolean(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return parseBoolean(normalized, name);
    }

    private static boolean parseBooleanOrDefault(String value, String name, boolean defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : parseBoolean(normalized, name);
    }

    private static boolean parseBoolean(String value, String name) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw BusinessException.badRequest(name + " 必须为 true 或 false");
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
