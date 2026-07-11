package com.meetingos.common;

@FunctionalInterface
public interface ImportProgressListener {

    ImportProgressListener NOOP = (processedRows, importedRows, failedRows) -> {
    };

    void onProgress(int processedRows, int importedRows, int failedRows);
}
