package com.meetingos.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * XLSX 输出工具。
 *
 * <p>所有单元格都按文本写入：一方面能保留手机号、证件号、参会码等长数字/混合编码，
 * 另一方面能避免 Excel 把用户输入误当公式执行。</p>
 */
public final class SpreadsheetFiles {

    public static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private SpreadsheetFiles() {
    }

    public static byte[] toXlsx(List<ExportTable> tables) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);
            for (ExportTable table : tables) {
                writeSheet(workbook, table, headerStyle, textStyle);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw BusinessException.badRequest("导出 XLSX 失败，请稍后重试");
        }
    }

    public static ResponseEntity<byte[]> download(String filename, byte[] content, MediaType contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename(filename) + "\"")
                .contentType(contentType)
                .body(content);
    }

    private static void writeSheet(Workbook workbook, ExportTable table, CellStyle headerStyle, CellStyle textStyle) {
        Sheet sheet = workbook.createSheet(safeSheetName(table.sheetName()));
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < table.headers().size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellStyle(headerStyle);
            cell.setCellValue(table.headers().get(i));
        }
        for (int r = 0; r < table.rows().size(); r++) {
            Row row = sheet.createRow(r + 1);
            List<?> values = table.rows().get(r);
            for (int c = 0; c < table.headers().size(); c++) {
                Object value = c < values.size() ? values.get(c) : "";
                Cell cell = row.createCell(c);
                cell.setCellStyle(textStyle);
                cell.setCellValue(protectFormula(value == null ? "" : String.valueOf(value)));
            }
        }
        applyColumnWidths(sheet, table);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = textStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle textStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("@"));
        return style;
    }

    private static void applyColumnWidths(Sheet sheet, ExportTable table) {
        for (int c = 0; c < table.headers().size(); c++) {
            int max = visualLength(table.headers().get(c));
            for (List<?> row : table.rows()) {
                Object value = c < row.size() ? row.get(c) : "";
                max = Math.max(max, visualLength(value == null ? "" : String.valueOf(value)));
            }
            int width = Math.min(42, Math.max(10, max + 2));
            sheet.setColumnWidth(c, width * 256);
        }
    }

    private static int visualLength(String value) {
        int length = 0;
        for (char ch : value.toCharArray()) {
            length += ch > 255 ? 2 : 1;
        }
        return length;
    }

    private static String protectFormula(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    private static String safeSheetName(String value) {
        String text = value == null || value.isBlank() ? "导出数据" : value.trim();
        text = text.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return text.length() > 31 ? text.substring(0, 31) : text;
    }

    private static String safeFilename(String filename) {
        String text = filename == null || filename.isBlank() ? "meetingos-export.xlsx" : filename.trim();
        return text.replaceAll("[\\r\\n\"]", "_").toLowerCase(Locale.ROOT);
    }
}
