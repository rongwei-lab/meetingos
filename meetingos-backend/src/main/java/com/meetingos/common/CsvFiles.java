package com.meetingos.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 输出工具。统一加 UTF-8 BOM，避免中文在常见 Excel 环境中打开乱码。
 */
public final class CsvFiles {

    private CsvFiles() {
    }

    public static String toCsv(List<String> headers, List<? extends List<?>> rows) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        appendRow(builder, headers);
        for (List<?> row : rows) {
            appendRow(builder, row);
        }
        return builder.toString();
    }

    public static ResponseEntity<byte[]> download(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static void appendRow(StringBuilder builder, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(values.get(i)));
        }
        builder.append('\n');
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = protectFormula(String.valueOf(value));
        boolean shouldQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        return shouldQuote ? "\"" + escaped + "\"" : escaped;
    }

    private static String protectFormula(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            // Excel 会把这些前缀当作公式执行；导出文件面向人工打开，统一加单引号保留文本语义。
            return "'" + value;
        }
        return value;
    }
}
