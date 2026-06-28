package com.meetingos.asset;

public record AssetUploadResponse(
        String url,
        String filename,
        long size,
        String contentType
) {
}
