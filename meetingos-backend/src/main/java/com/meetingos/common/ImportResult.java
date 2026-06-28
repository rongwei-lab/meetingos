package com.meetingos.common;

import java.util.List;

/**
 * 通用同步导入结果。MVP 使用同步导入，保留失败行明细，方便现场快速修正模板。
 */
public record ImportResult(
        int totalRows,
        int importedRows,
        int skippedRows,
        String message,
        List<ImportFailure> failedRows
) {
    public static ImportResult of(int totalRows, int importedRows, List<ImportFailure> failedRows, String subject) {
        return new ImportResult(
                totalRows,
                importedRows,
                failedRows.size(),
                subject + "导入完成：成功 " + importedRows + " 行，失败 " + failedRows.size() + " 行",
                failedRows
        );
    }
}
