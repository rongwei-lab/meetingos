package com.meetingos.common;

import java.util.List;

/**
 * 导出用的结构化表格数据。
 *
 * <p>业务服务只负责产出表头和行数据，CSV/XLSX 的编码、文件格式和安全转义统一交给渲染工具处理，
 * 避免为了新增一种文件格式去反解析已经生成好的 CSV。</p>
 */
public record ExportTable(
        String sheetName,
        List<String> headers,
        List<? extends List<?>> rows
) {
}
