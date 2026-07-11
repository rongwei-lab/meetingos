package com.meetingos.importtask;

import com.meetingos.common.ImportFailure;
import com.meetingos.common.TableRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportFailureWorkbookTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesOriginalValuesAndProtectsFormulaLikeCells() throws Exception {
        Path target = temporaryDirectory.resolve("failures.xlsx");
        ImportFailureWorkbook.write(
                target,
                List.of("姓名", "备注"),
                List.of(new TableRow(2, Map.of("姓名", "张三", "备注", "=HYPERLINK(\"https://example.com\")"))),
                List.of(new ImportFailure(2, "示例失败"))
        );

        assertThat(Files.size(target)).isGreaterThan(0);
        try (var workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            var row = workbook.getSheet("导入失败明细").getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("示例失败");
            assertThat(row.getCell(3).getStringCellValue()).startsWith("'=");
        }
    }
}
