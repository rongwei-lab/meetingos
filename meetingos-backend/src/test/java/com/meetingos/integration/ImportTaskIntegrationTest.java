package com.meetingos.integration;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.importtask.ImportTaskResponse;
import com.meetingos.importtask.ImportTaskFileStore;
import com.meetingos.importtask.ImportTaskRepository;
import com.meetingos.importtask.ImportTaskService;
import com.meetingos.importtask.ImportTaskStatus;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "meetingos.app.upload-dir=target/test-import-task-files",
        "meetingos.import-task.retention-hours=1"
})
@ActiveProfiles("test")
@Import(TestDataConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportTaskIntegrationTest {

    @Autowired
    private ImportTaskService importTaskService;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private ImportTaskRepository importTaskRepository;

    @Autowired
    private ImportTaskFileStore importTaskFileStore;

    @Test
    void preflightsCommitsSameFileAndPublishesFailureWorkbook() throws Exception {
        MockMultipartFile file = workbook(
                List.of("姓名", "手机号", "单位"),
                List.of("任务导入成功", "13800009991", "异步任务"),
                List.of("重复手机号", "13800000001", "异步任务")
        );

        ImportTaskResponse created = importTaskService.create(1L, "attendees", true, "integration-task-1", file);
        assertThat(created.status()).isEqualTo(ImportTaskStatus.PENDING);

        ImportTaskResponse preview = awaitStatus(created.id(), ImportTaskStatus.PREVIEW_READY);
        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.importedRows()).isZero();

        ImportTaskResponse duplicateRequest = importTaskService.create(
                1L,
                "attendees",
                true,
                "integration-task-1",
                file
        );
        assertThat(duplicateRequest.id()).isEqualTo(created.id());

        importTaskService.commit(created.id(), 1L);
        ImportTaskResponse completed = awaitStatus(created.id(), ImportTaskStatus.COMPLETED);

        assertThat(completed.importedRows()).isEqualTo(1);
        assertThat(completed.failedRows()).isEqualTo(1);
        assertThat(completed.progress()).isEqualTo(100);
        assertThat(completed.failureFileAvailable()).isTrue();
        assertThat(importTaskService.failureFile(created.id(), 1L).size()).isGreaterThan(0);
        assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, "13800009991")).isPresent();
    }

    @Test
    void retriesFailedTaskWithSameIdempotencyKeyAndReplacesInputFile() throws Exception {
        MockMultipartFile invalid = workbook(
                List.of("错误表头"),
                List.of("无效数据")
        );
        ImportTaskResponse first = importTaskService.create(
                1L,
                "attendees",
                true,
                "integration-retry-1",
                invalid
        );
        awaitStatus(first.id(), ImportTaskStatus.FAILED);
        String oldInputPath = importTaskRepository.findById(first.id()).orElseThrow().getInputFilePath();
        assertThat(importTaskFileStore.exists(oldInputPath)).isTrue();

        MockMultipartFile corrected = workbook(
                List.of("姓名", "手机号", "单位"),
                List.of("任务重试成功", "13800009992", "异步任务")
        );
        ImportTaskResponse retried = importTaskService.create(
                1L,
                "attendees",
                false,
                "integration-retry-1",
                corrected
        );

        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(retried.status()).isEqualTo(ImportTaskStatus.PENDING);
        assertThat(retried.totalRows()).isZero();
        assertThat(retried.completedAt()).isNull();
        assertThat(importTaskFileStore.exists(oldInputPath)).isFalse();

        ImportTaskResponse completed = awaitStatus(retried.id(), ImportTaskStatus.COMPLETED);
        assertThat(completed.importedRows()).isEqualTo(1);
        assertThat(completed.failedRows()).isZero();
        assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, "13800009992")).isPresent();
    }

    private ImportTaskResponse awaitStatus(Long taskId, ImportTaskStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        ImportTaskResponse current;
        do {
            current = importTaskService.detail(taskId, 1L);
            if (current.status() == expected) {
                return current;
            }
            if (current.status() == ImportTaskStatus.FAILED) {
                throw new AssertionError("任务意外失败: " + current.message());
            }
            Thread.sleep(25);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("等待任务状态超时，当前状态: " + current.status());
    }

    @SafeVarargs
    private MockMultipartFile workbook(List<String> headers, List<String>... rows) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入模板");
            var header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
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
                    "attendees.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @AfterAll
    static void cleanupTaskFiles() throws Exception {
        Path root = Path.of("target/test-import-task-files");
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // 测试临时目录由下一次 clean 继续清理。
                }
            });
        }
    }
}
