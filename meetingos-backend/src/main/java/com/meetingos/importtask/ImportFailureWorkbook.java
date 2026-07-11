package com.meetingos.importtask;

import com.meetingos.common.ImportFailure;
import com.meetingos.common.TableRow;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ImportFailureWorkbook {

    private ImportFailureWorkbook() {
    }

    public static void write(Path target,
                             List<String> headers,
                             List<TableRow> sourceRows,
                             List<ImportFailure> failures) {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Map<Integer, TableRow> rowsByNumber = new LinkedHashMap<>();
        sourceRows.forEach(row -> rowsByNumber.put(row.rowNumber(), row));
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (workbook) {
            workbook.setCompressTempFiles(true);
            var sheet = workbook.createSheet("导入失败明细");
            CellStyle textStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            textStyle.setDataFormat(dataFormat.getFormat("@"));

            Row headerRow = sheet.createRow(0);
            writeText(headerRow, 0, "原始行号", textStyle);
            writeText(headerRow, 1, "失败原因", textStyle);
            for (int index = 0; index < headers.size(); index++) {
                writeText(headerRow, index + 2, headers.get(index), textStyle);
            }

            int outputRow = 1;
            for (ImportFailure failure : failures) {
                Row row = sheet.createRow(outputRow++);
                writeText(row, 0, String.valueOf(failure.rowNumber()), textStyle);
                writeText(row, 1, failure.reason(), textStyle);
                TableRow source = rowsByNumber.get(failure.rowNumber());
                for (int index = 0; index < headers.size(); index++) {
                    String normalizedHeader = TableRow.normalizeHeader(headers.get(index));
                    String value = source == null ? "" : source.rawValues().getOrDefault(normalizedHeader, "");
                    writeText(row, index + 2, value, textStyle);
                }
            }
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 48 * 256);
            for (int index = 0; index < headers.size(); index++) {
                sheet.setColumnWidth(index + 2, 20 * 256);
            }
            try (OutputStream output = Files.newOutputStream(temporary)) {
                workbook.write(output);
            }
            moveAtomically(temporary, target);
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 保留原异常。
            }
            throw new IllegalStateException("失败明细文件生成失败", ex);
        }
    }

    private static void writeText(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellStyle(style);
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        cell.setCellValue(text);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
