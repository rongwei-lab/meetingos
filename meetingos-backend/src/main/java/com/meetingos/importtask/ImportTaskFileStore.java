package com.meetingos.importtask;

import com.meetingos.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Component
public class ImportTaskFileStore {

    private final Path importsRoot;

    public ImportTaskFileStore(@Value("${meetingos.app.upload-dir:uploads}") String uploadDir) {
        this.importsRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("imports").normalize();
    }

    public StoredInput saveInput(MultipartFile file) {
        String originalFilename = sanitizeOriginalFilename(file == null ? null : file.getOriginalFilename());
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw BusinessException.badRequest("仅支持 .xlsx 文件");
        }
        Path relative = Path.of("input", UUID.randomUUID() + ".xlsx");
        Path target = resolve(relative.toString());
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new StoredInput(originalFilename, relative.toString(), target);
        } catch (IOException ex) {
            deleteQuietly(relative.toString());
            throw new IllegalStateException("导入文件保存失败", ex);
        }
    }

    public String failurePath(Long taskId) {
        return Path.of("failures", taskId + "-" + UUID.randomUUID() + ".xlsx").toString();
    }

    public Path createFailureTarget(String storedPath) {
        Path target = resolve(storedPath);
        try {
            Files.createDirectories(target.getParent());
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("失败明细目录创建失败", ex);
        }
    }

    public Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw BusinessException.notFound("任务文件不存在或已清理");
        }
        Path relative = Path.of(storedPath);
        if (relative.isAbsolute()) {
            throw BusinessException.badRequest("任务文件路径非法");
        }
        Path resolved = importsRoot.resolve(relative).normalize();
        if (!resolved.startsWith(importsRoot)) {
            throw BusinessException.badRequest("任务文件路径非法");
        }
        return resolved;
    }

    public boolean exists(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return false;
        }
        try {
            return Files.isRegularFile(resolve(storedPath));
        } catch (BusinessException ex) {
            return false;
        }
    }

    public void deleteQuietly(String storedPath) {
        delete(storedPath);
    }

    public boolean delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return true;
        }
        try {
            Files.deleteIfExists(resolve(storedPath));
            return true;
        } catch (IOException | BusinessException ignored) {
            // 清理失败保留任务记录，下一轮定时清理可以继续重试。
            return false;
        }
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (cleaned.contains("/")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1);
        }
        if (cleaned.contains("\\")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('\\') + 1);
        }
        if (cleaned.isBlank()) {
            cleaned = "import.xlsx";
        }
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }

    public record StoredInput(String originalFilename, String storedPath, Path absolutePath) {
    }
}
