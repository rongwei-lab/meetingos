package com.meetingos.asset;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/assets")
public class AssetController {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );
    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path uploadRoot;

    public AssetController(@Value("${meetingos.app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEETING_ADMIN')")
    public ApiResponse<AssetUploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传图片文件");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw BusinessException.badRequest("图片大小不能超过 5MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw BusinessException.badRequest("仅支持 JPG、PNG、WebP 或 GIF 图片");
        }

        String extension = extensionOf(file.getOriginalFilename(), contentType);
        String datePath = LocalDate.now().format(DATE_PATH_FORMAT);
        String filename = UUID.randomUUID() + extension;
        Path targetDir = uploadRoot.resolve("images").resolve(datePath).normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw BusinessException.badRequest("文件路径非法");
        }

        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("图片保存失败", ex);
        }

        String url = "/uploads/images/" + datePath + "/" + filename;
        return ApiResponse.ok(new AssetUploadResponse(url, filename, file.getSize(), contentType));
    }

    private String normalizeContentType(String contentType) {
        return (contentType == null ? "" : contentType).toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String originalFilename, String contentType) {
        String normalizedName = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename).toLowerCase(Locale.ROOT);
        String extension = normalizedName.contains(".") ? normalizedName.substring(normalizedName.lastIndexOf(".")) : "";
        if (Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(extension)) {
            return ".jpeg".equals(extension) ? ".jpg" : extension;
        }
        return EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(contentType, ".bin");
    }
}
