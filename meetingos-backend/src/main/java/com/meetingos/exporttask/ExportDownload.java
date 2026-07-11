package com.meetingos.exporttask;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record ExportDownload(
        String filename,
        Resource resource,
        long contentLength,
        MediaType contentType
) {
}
