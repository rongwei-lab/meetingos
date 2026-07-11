package com.meetingos.common;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableFileReaderTest {

    @Test
    void streamsRowsAndKeepsOriginalRowNumbers() {
        TableData data = TableFileReader.read(workbook(
                List.of("姓名", "手机号"),
                List.of("张三", "13800000000"),
                List.of("", ""),
                List.of("李四", "13900000000")
        ), attendeeSchema());

        assertThat(data.headers()).containsExactly("姓名", "手机号");
        assertThat(data.rows()).extracting(TableRow::rowNumber).containsExactly(2, 4);
        assertThat(data.rows().get(1).get("姓名")).isEqualTo("李四");
    }

    @Test
    void rejectsHeadersThatCollideAfterNormalization() {
        assertThatThrownBy(() -> TableFileReader.read(workbook(
                List.of("手机号", "手机 号"),
                List.of("13800000000", "13900000000")
        ), TableFileSchema.permissive()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复表头");
    }

    @Test
    void rejectsMissingRequiredHeaderBeforeProcessingRows() {
        assertThatThrownBy(() -> TableFileReader.read(workbook(
                List.of("姓名", "单位"),
                List.of("张三", "示例公司")
        ), attendeeSchema()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号");
    }

    @Test
    void rejectsScientificNotationInTextIdentifierColumn() {
        assertThatThrownBy(() -> TableFileReader.read(workbook(
                List.of("姓名", "手机号"),
                List.of("张三", "1.3800000000E+10")
        ), attendeeSchema()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("科学计数法");
    }

    private TableFileSchema attendeeSchema() {
        var name = TableFileSchema.column("姓名", "姓名", "name");
        var phone = TableFileSchema.column("手机号", "手机号", "phone");
        return new TableFileSchema(
                List.of(
                        TableFileSchema.require("姓名", TableFileSchema.option(name)),
                        TableFileSchema.require("手机号", TableFileSchema.option(phone))
                ),
                List.of(phone)
        );
    }

    @SafeVarargs
    private MockMultipartFile workbook(List<String> headers, List<String>... rows) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入模板");
            var headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                headerRow.createCell(index).setCellValue(headers.get(index));
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows[rowIndex].size(); column++) {
                    row.createCell(column).setCellValue(rows[rowIndex].get(column));
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
