package com.meetingos.common;

import java.util.List;

/**
 * XLSX 首个工作表的流式读取结果。
 *
 * @param headers           原始展示表头，供失败明细文件复用
 * @param normalizedHeaders 归一化表头，供 schema 和业务字段匹配
 * @param rows              非空数据行
 */
public record TableData(
        List<String> headers,
        List<String> normalizedHeaders,
        List<TableRow> rows
) {
}
