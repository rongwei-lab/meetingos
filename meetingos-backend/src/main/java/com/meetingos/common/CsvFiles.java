package com.meetingos.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.BufferedWriter;
import java.io.IOException;
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

    public static void writeCsv(Path path,
                                List<String> headers,
                                ExportRowProducer producer) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            writer.write('\uFEFF');
            writer.write(csvRow(headers));
            producer.produce(row -> writer.write(csvRow(row)));
        }
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

    private static String csvRow(List<?> values) {
        StringBuilder builder = new StringBuilder();
        appendRow(builder, values);
        return builder.toString();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = protectFormula(ExportDisplayFormatter.value(value));
        boolean shouldQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        return shouldQuote ? "\"" + escaped + "\"" : escaped;
    }

    private static String protectFormula(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int offset = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (!Character.isWhitespace(codePoint)
                    && !Character.isSpaceChar(codePoint)
                    && !Character.isISOControl(codePoint)
                    && type != Character.FORMAT) {
                break;
            }
            offset += Character.charCount(codePoint);
        }
        if (offset >= value.length()) {
            return value;
        }
        char first = value.charAt(offset);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            // Excel 可能忽略前导空白/控制字符后执行公式；单引号必须放在原值最前面才能形成文本门禁。
            return "'" + value;
        }
        return value;
    }
}
