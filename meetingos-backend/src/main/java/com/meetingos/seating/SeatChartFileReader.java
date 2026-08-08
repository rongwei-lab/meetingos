package com.meetingos.seating;

import com.meetingos.common.BusinessException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取以“第 N 排”为行标签的座位图。空单元格必须保留为禁用座位，不能在解析时压缩掉，
 * 否则前几排的横向坐标会与原始 Excel 不一致。
 */
final class SeatChartFileReader {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final int MAX_SHEET_ROWS = 10_000;
    private static final Pattern ROW_LABEL = Pattern.compile("^第\\s*(\\d+)\\s*排$");
    private static final Set<String> AISLE_MARKERS = Set.of("通道", "过道");

    private SeatChartFileReader() {
    }

    static SeatChart read(MultipartFile file) {
        validateFile(file);
        ZipSecureFile.setMinInflateRatio(0.01);
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw BusinessException.badRequest("座位表没有工作表");
            }
            return readSheet(workbook.getSheetAt(0));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("座位表解析失败，请确认文件为有效的 .xlsx 座位图");
        }
    }

    private static SeatChart readSheet(Sheet sheet) {
        if (sheet.getLastRowNum() + 1 > MAX_SHEET_ROWS) {
            throw BusinessException.badRequest("座位表行数不能超过 " + MAX_SHEET_ROWS);
        }

        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Map<Integer, Map<Integer, String>> valuesByRowNo = new TreeMap<>();
        Set<Integer> aisleSeatNos = new LinkedHashSet<>();
        int rowLabelColumn = -1;
        int maxSeatNo = 0;

        for (Row spreadsheetRow : sheet) {
            List<RowLabel> labels = findRowLabels(spreadsheetRow, formatter);
            if (labels.isEmpty()) {
                continue;
            }
            RowLabel label = labels.get(0);
            if (label.rowNo() > SeatLayoutRequest.MAX_ROWS) {
                throw BusinessException.badRequest("单个区域最多支持 80 排");
            }
            if (rowLabelColumn < 0) {
                rowLabelColumn = label.columnIndex();
            } else if (rowLabelColumn != label.columnIndex()) {
                throw BusinessException.badRequest("所有排号必须位于同一列");
            }
            if (valuesByRowNo.containsKey(label.rowNo())) {
                throw BusinessException.badRequest("座位表中存在重复的第 " + label.rowNo() + " 排");
            }

            Map<Integer, String> rowValues = new LinkedHashMap<>();
            int endLabelColumn = findEndLabelColumn(labels, label);
            // getLastCellNum 包含 Excel 中实际创建的空白单元格。这样行尾的空座位不会因为
            // 没有姓名而丢失；其他较短行会在后续按全局最大列数补齐为禁用座位。部分现场座位表
            // 会在最右侧重复写同一行的“第 N 排”作为结束标记，该单元格不属于座位区域。
            int lastSeatColumn = endLabelColumn >= 0 ? endLabelColumn - 1 : spreadsheetRow.getLastCellNum() - 1;
            int lastSeatNo = Math.max(0, lastSeatColumn - label.columnIndex());
            if (lastSeatNo > SeatLayoutRequest.MAX_SEATS_PER_ROW) {
                throw BusinessException.badRequest("单排最多支持 80 个座位");
            }
            int namedSeatCount = 0;
            for (int columnIndex = label.columnIndex() + 1; columnIndex <= lastSeatColumn; columnIndex++) {
                String value = cellValue(spreadsheetRow.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter);
                int seatNo = columnIndex - label.columnIndex();
                if (AISLE_MARKERS.contains(value)) {
                    // 一个“通道/过道”标记代表整列纵向过道，其他排即使填了姓名也不参与匹配。
                    aisleSeatNos.add(seatNo);
                    continue;
                }
                if (value.isBlank()) {
                    continue;
                }
                rowValues.put(seatNo, value);
                namedSeatCount++;
            }
            if (namedSeatCount == 0) {
                throw BusinessException.badRequest("第 " + label.rowNo() + " 排没有座位数据");
            }
            valuesByRowNo.put(label.rowNo(), rowValues);
            maxSeatNo = Math.max(maxSeatNo, lastSeatNo);
        }

        if (valuesByRowNo.isEmpty()) {
            throw BusinessException.badRequest("未找到“第 N 排”格式的座位行");
        }
        int rowCount = valuesByRowNo.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int rowNo = 1; rowNo <= rowCount; rowNo++) {
            if (!valuesByRowNo.containsKey(rowNo)) {
                throw BusinessException.badRequest("座位表缺少第 " + rowNo + " 排");
            }
        }

        List<SeatChartCell> cells = new ArrayList<>(rowCount * maxSeatNo);
        for (int rowNo = 1; rowNo <= rowCount; rowNo++) {
            Map<Integer, String> rowValues = valuesByRowNo.get(rowNo);
            for (int seatNo = 1; seatNo <= maxSeatNo; seatNo++) {
                boolean aisle = aisleSeatNos.contains(seatNo);
                cells.add(new SeatChartCell(rowNo, seatNo, aisle ? "" : rowValues.getOrDefault(seatNo, ""), aisle));
            }
        }
        return new SeatChart(rowCount, maxSeatNo, List.copyOf(cells));
    }

    private static List<RowLabel> findRowLabels(Row row, DataFormatter formatter) {
        List<RowLabel> labels = new ArrayList<>();
        for (Cell cell : row) {
            String value = cellValue(cell, formatter);
            Matcher matcher = ROW_LABEL.matcher(value);
            if (matcher.matches()) {
                labels.add(new RowLabel(Integer.parseInt(matcher.group(1)), cell.getColumnIndex()));
            }
        }
        return labels;
    }

    private static int findEndLabelColumn(List<RowLabel> labels, RowLabel rowLabel) {
        if (labels.size() == 1) {
            return -1;
        }
        for (int index = 1; index < labels.size(); index++) {
            RowLabel extraLabel = labels.get(index);
            if (extraLabel.rowNo() != rowLabel.rowNo()) {
                throw BusinessException.badRequest("同一行的排号标记不一致");
            }
        }
        return labels.get(1).columnIndex();
    }

    private static String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.ERROR) {
            throw BusinessException.badRequest("座位表中不能包含错误单元格");
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传 .xlsx 座位表");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("座位表不能超过 10MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) {
            throw BusinessException.badRequest("仅支持 .xlsx 座位表");
        }
    }

    record SeatChart(int rowCount, int seatsPerRow, List<SeatChartCell> cells) {
        int namedSeatCount() {
            return (int) cells.stream().filter(cell -> !cell.name().isBlank()).count();
        }

        int blankSeatCount() {
            return (int) cells.stream().filter(cell -> !cell.aisle() && cell.name().isBlank()).count();
        }

        int aisleCount() {
            return (int) cells.stream().filter(SeatChartCell::aisle).count();
        }
    }

    record SeatChartCell(int rowNo, int seatNo, String name, boolean aisle) {
    }

    private record RowLabel(int rowNo, int columnIndex) {
    }
}
