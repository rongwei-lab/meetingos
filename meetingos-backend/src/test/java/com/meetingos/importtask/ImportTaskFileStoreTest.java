package com.meetingos.importtask;

import com.meetingos.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportTaskFileStoreTest {

    @TempDir
    Path uploadDir;

    @Test
    void resolvesOnlyRelativePathsInsideImportRoot() {
        ImportTaskFileStore store = new ImportTaskFileStore(uploadDir.toString());

        assertThat(store.resolve("input/source.xlsx"))
                .isEqualTo(uploadDir.resolve("imports/input/source.xlsx").toAbsolutePath().normalize());
        assertThatThrownBy(() -> store.resolve("../exports/private.xlsx"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("任务文件路径非法");
        assertThatThrownBy(() -> store.resolve(uploadDir.resolve("private.xlsx").toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("任务文件路径非法");
    }

    @Test
    void storesSanitizedFilenameUnderGeneratedPath() throws Exception {
        ImportTaskFileStore store = new ImportTaskFileStore(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../危险目录/参会人.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );

        ImportTaskFileStore.StoredInput stored = store.saveInput(file);

        assertThat(stored.originalFilename()).isEqualTo("参会人.xlsx");
        assertThat(stored.storedPath()).startsWith("input");
        assertThat(stored.storedPath()).endsWith(".xlsx");
        assertThat(stored.absolutePath()).startsWith(uploadDir.resolve("imports").toAbsolutePath().normalize());
        assertThat(Files.readAllBytes(stored.absolutePath())).containsExactly(1, 2, 3);
    }
}
