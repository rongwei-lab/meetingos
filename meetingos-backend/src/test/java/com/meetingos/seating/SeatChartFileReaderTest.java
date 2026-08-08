package com.meetingos.seating;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SeatChartFileReaderTest {

    @Test
    void preservesActualAndPaddedBlankCoordinatesInTheSeatGrid() {
        SeatChartFileReader.SeatChart chart = SeatChartFileReader.read(workbookWithBlankSeats());

        assertThat(chart.rowCount()).isEqualTo(2);
        assertThat(chart.seatsPerRow()).isEqualTo(4);
        assertThat(chart.cells()).hasSize(8);
        assertThat(chart.cells())
                .filteredOn(cell -> cell.name().isBlank())
                .extracting(cell -> cell.rowNo() + "-" + cell.seatNo())
                // 1-1、1-4 和 2-2 是 Excel 中实际存在的空白格；2-4 是短行补齐的位置。
                .containsExactly("1-1", "1-3", "1-4", "2-2", "2-4");
        assertThat(chart.blankSeatCount()).isEqualTo(5);
    }

    @Test
    void recognizesAisleColumnAndIgnoresRepeatedRightRowLabel() {
        SeatChartFileReader.SeatChart chart = SeatChartFileReader.read(workbookWithAisleAndRightRowLabels());

        assertThat(chart.rowCount()).isEqualTo(2);
        assertThat(chart.seatsPerRow()).isEqualTo(3);
        assertThat(chart.cells()).hasSize(6);
        assertThat(chart.aisleCount()).isEqualTo(2);
        assertThat(chart.blankSeatCount()).isZero();
        assertThat(chart.cells())
                .filteredOn(SeatChartFileReader.SeatChartCell::aisle)
                .extracting(cell -> cell.rowNo() + "-" + cell.seatNo())
                .containsExactly("1-2", "2-2");
        assertThat(chart.cells())
                .filteredOn(cell -> !cell.aisle())
                .extracting(SeatChartFileReader.SeatChartCell::name)
                .containsExactly("张三", "李四", "王五", "钱七");
    }

    private MockMultipartFile workbookWithBlankSeats() {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("座位图");
            var blankSeatStyle = workbook.createCellStyle();
            blankSeatStyle.setBorderBottom(BorderStyle.THIN);

            var firstRow = sheet.createRow(0);
            firstRow.createCell(0).setCellValue("第1排");
            blankCell(firstRow, 1, blankSeatStyle);
            firstRow.createCell(2).setCellValue("张三");
            // 该单元格位于最后一位姓名右侧，应作为实际存在的禁用空座保留。
            blankCell(firstRow, 4, blankSeatStyle);

            var secondRow = sheet.createRow(1);
            secondRow.createCell(0).setCellValue("第2排");
            secondRow.createCell(1).setCellValue("李四");
            blankCell(secondRow, 2, blankSeatStyle);
            secondRow.createCell(3).setCellValue("王五");

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "seat-chart.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MockMultipartFile workbookWithAisleAndRightRowLabels() {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("座位图");

            var firstRow = sheet.createRow(0);
            firstRow.createCell(0).setCellValue("第1排");
            firstRow.createCell(1).setCellValue("张三");
            firstRow.createCell(2).setCellValue("通道");
            firstRow.createCell(3).setCellValue("李四");
            firstRow.createCell(4).setCellValue("第1排");

            var secondRow = sheet.createRow(1);
            secondRow.createCell(0).setCellValue("第2排");
            secondRow.createCell(1).setCellValue("王五");
            // 同一列后续行即使有内容，也必须被首行“通道”标记扩展成整列过道。
            secondRow.createCell(2).setCellValue("不应分配");
            secondRow.createCell(3).setCellValue("钱七");
            secondRow.createCell(4).setCellValue("第2排");

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "seat-chart-aisle.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void blankCell(org.apache.poi.ss.usermodel.Row row,
                           int columnIndex,
                           org.apache.poi.ss.usermodel.CellStyle style) {
        var cell = row.createCell(columnIndex);
        cell.setBlank();
        cell.setCellStyle(style);
    }
}
