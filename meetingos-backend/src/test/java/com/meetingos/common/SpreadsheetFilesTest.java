package com.meetingos.common;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void writesStreamingRowsDirectlyToPathAsTextCells() throws Exception {
        Path output = tempDir.resolve("streaming.xlsx");

        SpreadsheetFiles.writeStreamingXlsx(
                output,
                "参会名单",
                List.of("手机号", "备注"),
                consumer -> {
                    consumer.accept(List.of("13800000000", "=1+1"));
                    consumer.accept(List.of("13900000000", "正常文本"));
                }
        );

        assertThat(Files.size(output)).isPositive();
        try (InputStream input = Files.newInputStream(output);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheet("参会名单");
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("13800000000");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("'=1+1");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("正常文本");
        }
    }
}
