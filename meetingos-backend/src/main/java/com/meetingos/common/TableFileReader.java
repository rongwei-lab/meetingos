package com.meetingos.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 读取会务导入模板。
 *
 * <p>导入模板统一使用 .xlsx，避免 CSV 在中文、长数字、手机号和证件号场景下出现编码或格式丢失。</p>
 */
public final class TableFileReader {

    private static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_COLUMNS = 80;
    private static final int MAX_CELL_LENGTH = 2_000;

    private TableFileReader() {
    }

    public static List<TableRow> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传 .xlsx 模板文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("导入文件不能超过 10MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".xlsx") || filename.isBlank()) {
                return readWorkbook(file);
            }
            throw BusinessException.badRequest("仅支持 .xlsx 文件");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("导入文件解析失败，请检查模板格式");
        }
    }

    private static List<TableRow> readWorkbook(MultipartFile file) throws IOException {
        ZipSecureFile.setMinInflateRatio(0.01);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw BusinessException.badRequest("导入文件没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() - sheet.getFirstRowNum() > MAX_ROWS) {
                throw BusinessException.badRequest("导入文件行数不能超过 " + MAX_ROWS);
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw BusinessException.badRequest("导入文件缺少表头");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = headersFromExcel(headerRow, formatter);
            List<TableRow> rows = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isBlank()) {
                        continue;
                    }
                    Cell cell = row == null ? null : row.getCell(c);
                    String value = formatter.formatCellValue(cell).trim();
                    if (value.length() > MAX_CELL_LENGTH) {
                        throw BusinessException.badRequest("第 " + (i + 1) + " 行存在超长单元格");
                    }
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    values.put(header, value);
                }
                if (hasValue) {
                    rows.add(new TableRow(i + 1, values));
                }
            }
            return rows;
        }
    }

    private static List<String> headersFromExcel(Row headerRow, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        int columnCount = headerRow.getLastCellNum();
        if (columnCount > MAX_COLUMNS) {
            throw BusinessException.badRequest("导入文件列数不能超过 " + MAX_COLUMNS);
        }
        for (int c = 0; c < columnCount; c++) {
            headers.add(TableRow.normalizeHeader(formatter.formatCellValue(headerRow.getCell(c))));
        }
        ensureHeaders(headers);
        return headers;
    }

    private static void ensureHeaders(List<String> headers) {
        boolean hasHeader = headers.stream().anyMatch(header -> !header.isBlank());
        if (!hasHeader) {
            throw BusinessException.badRequest("导入文件缺少有效表头");
        }
    }
}
