package com.meetingos.common;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 流式读取会务 XLSX 导入模板，避免把完整 Workbook 对象图加载进堆内存。
 */
public final class TableFileReader {

    public static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ROWS = 10_000;
    public static final int MAX_COLUMNS = 80;
    public static final int MAX_CELL_LENGTH = 2_000;
    private static final int MAX_HEADER_LENGTH = 200;
    private static final long MAX_DECODED_CHARACTERS = 20L * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024;

    private TableFileReader() {
    }

    public static List<TableRow> read(MultipartFile file) {
        return read(file, TableFileSchema.permissive()).rows();
    }

    public static TableData read(MultipartFile file, TableFileSchema schema) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传 .xlsx 模板文件");
        }
        validateFile(file.getOriginalFilename(), file.getSize());
        try (InputStream input = file.getInputStream(); OPCPackage pkg = OPCPackage.open(input)) {
            return readPackage(pkg, schema);
        } catch (Exception ex) {
            throw parseFailure(ex);
        }
    }

    public static TableData read(Path path, TableFileSchema schema) {
        try {
            validateFile(path.getFileName().toString(), Files.size(path));
            try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
                return readPackage(pkg, schema);
            }
        } catch (Exception ex) {
            throw parseFailure(ex);
        }
    }

    private static void validateFile(String originalFilename, long size) {
        if (size <= 0) {
            throw BusinessException.badRequest("请上传 .xlsx 模板文件");
        }
        if (size > MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("导入文件不能超过 10MB");
        }
        String filename = originalFilename == null ? "" : originalFilename.trim().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) {
            throw BusinessException.badRequest("仅支持 .xlsx 文件");
        }
    }

    private static TableData readPackage(OPCPackage pkg, TableFileSchema schema) throws Exception {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_BYTES);
        XSSFReader reader = new XSSFReader(pkg);
        Iterator<InputStream> sheets = reader.getSheetsData();
        if (!sheets.hasNext()) {
            throw BusinessException.badRequest("导入文件没有工作表");
        }

        StylesTable styles = reader.getStylesTable();
        SharedStrings sharedStrings = new ReadOnlySharedStringsTable(pkg);
        StreamingSheetHandler sheetHandler = new StreamingSheetHandler(schema == null ? TableFileSchema.permissive() : schema);
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new XSSFSheetXMLHandler(
                styles,
                null,
                sharedStrings,
                sheetHandler,
                new DataFormatter(Locale.ROOT),
                false
        ));
        try (InputStream firstSheet = sheets.next()) {
            parser.parse(new InputSource(firstSheet));
        }
        return sheetHandler.result();
    }

    private static BusinessException parseFailure(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            current = current.getCause();
        }
        return BusinessException.badRequest("导入文件解析失败，请检查模板格式");
    }

    private static final class StreamingSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final TableFileSchema schema;
        private final List<TableRow> rows = new ArrayList<>();
        private final Map<Integer, String> currentCells = new TreeMap<>();
        private List<String> headers;
        private List<String> normalizedHeaders;
        private int currentRowNumber;
        private int headerRowIndex = -1;
        private int nextColumnIndex;
        private long decodedCharacters;

        private StreamingSheetHandler(TableFileSchema schema) {
            this.schema = schema;
        }

        @Override
        public void startRow(int rowNum) {
            currentCells.clear();
            currentRowNumber = rowNum + 1;
            nextColumnIndex = 0;
            if (headerRowIndex >= 0 && rowNum - headerRowIndex > MAX_ROWS) {
                throw BusinessException.badRequest("导入文件行数不能超过 " + MAX_ROWS);
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (headers == null) {
                headerRowIndex = rowNum;
                readHeaders();
                return;
            }
            readDataRow();
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            int columnIndex = cellReference == null
                    ? nextColumnIndex
                    : new CellReference(cellReference).getCol();
            nextColumnIndex = columnIndex + 1;
            if (columnIndex >= MAX_COLUMNS) {
                throw BusinessException.badRequest("导入文件列数不能超过 " + MAX_COLUMNS);
            }
            String value = formattedValue == null ? "" : formattedValue.trim();
            int maxLength = headers == null ? MAX_HEADER_LENGTH : MAX_CELL_LENGTH;
            if (value.length() > maxLength) {
                String subject = headers == null ? "表头" : "第 " + currentRowNumber + " 行单元格";
                throw BusinessException.badRequest(subject + "长度不能超过 " + maxLength + " 个字符");
            }
            decodedCharacters += value.length();
            if (decodedCharacters > MAX_DECODED_CHARACTERS) {
                throw BusinessException.badRequest("导入文件解码后的文本内容过大");
            }
            currentCells.put(columnIndex, value);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // 页眉页脚不属于导入数据。
        }

        private void readHeaders() {
            if (currentCells.isEmpty()) {
                throw BusinessException.badRequest("导入文件缺少表头");
            }
            int columnCount = currentCells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            headers = new ArrayList<>(columnCount);
            normalizedHeaders = new ArrayList<>(columnCount);
            Set<String> seen = new LinkedHashSet<>();
            for (int index = 0; index < columnCount; index++) {
                String header = currentCells.getOrDefault(index, "");
                String normalized = TableRow.normalizeHeader(header);
                if (!normalized.isBlank() && !seen.add(normalized)) {
                    throw BusinessException.badRequest("导入文件存在重复表头: " + header);
                }
                headers.add(header);
                normalizedHeaders.add(normalized);
            }
            if (seen.isEmpty()) {
                throw BusinessException.badRequest("导入文件缺少有效表头");
            }
            schema.validateHeaders(normalizedHeaders);
        }

        private void readDataRow() {
            boolean hasValue = currentCells.values().stream().anyMatch(value -> value != null && !value.isBlank());
            if (!hasValue) {
                return;
            }
            currentCells.forEach((columnIndex, value) -> {
                if (columnIndex >= normalizedHeaders.size() && value != null && !value.isBlank()) {
                    throw BusinessException.badRequest("第 " + currentRowNumber + " 行存在没有表头的数据列");
                }
            });
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < normalizedHeaders.size(); index++) {
                String header = normalizedHeaders.get(index);
                if (header.isBlank()) {
                    continue;
                }
                String value = currentCells.getOrDefault(index, "");
                schema.validateTextValue(header, value, currentRowNumber);
                values.put(header, value);
            }
            rows.add(new TableRow(currentRowNumber, values));
            if (rows.size() > MAX_ROWS) {
                throw BusinessException.badRequest("导入文件行数不能超过 " + MAX_ROWS);
            }
        }

        private TableData result() {
            if (headers == null) {
                throw BusinessException.badRequest("导入文件缺少表头");
            }
            return new TableData(List.copyOf(headers), List.copyOf(normalizedHeaders), List.copyOf(rows));
        }
    }
}
