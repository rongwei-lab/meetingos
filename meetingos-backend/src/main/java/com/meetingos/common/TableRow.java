package com.meetingos.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 .xlsx 模板读取的一行结构化数据。列名会做轻量归一化，业务层可以同时兼容中文模板和英文字段名。
 */
public record TableRow(
        int rowNumber,
        Map<String, String> values
) {
    public String get(String... aliases) {
        for (String alias : aliases) {
            String value = values.get(normalizeHeader(alias));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public Map<String, String> rawValues() {
        return new LinkedHashMap<>(values);
    }

    static String normalizeHeader(String header) {
        return header == null
                ? ""
                : header.trim()
                        .replace("\uFEFF", "")
                        .replaceAll("[\\s_\\-（）()：:]", "")
                        .toLowerCase();
    }
}
