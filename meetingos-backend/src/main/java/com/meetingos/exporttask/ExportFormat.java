package com.meetingos.exporttask;

import com.meetingos.common.BusinessException;
import com.meetingos.common.SpreadsheetFiles;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public enum ExportFormat {
    CSV("csv", new MediaType("text", "csv", StandardCharsets.UTF_8)),
    XLSX("xlsx", SpreadsheetFiles.XLSX_MEDIA_TYPE);

    private final String extension;
    private final MediaType mediaType;

    ExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public static ExportFormat from(String value) {
        String normalized = value == null || value.isBlank() ? "xlsx" : value.trim().toLowerCase(Locale.ROOT);
        for (ExportFormat format : values()) {
            if (format.extension.equals(normalized)) {
                return format;
            }
        }
        throw BusinessException.badRequest("暂不支持的导出格式: " + value);
    }
}
