package com.meetingos.exporttask;

import org.springframework.http.MediaType;

public record ExportFile(
        String filename,
        byte[] content,
        MediaType contentType
) {
}
