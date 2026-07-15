package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.meetingos.invitation.InvitationApiModels.AssetResponse;

@Service
public class InvitationAssetService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif"
    );
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path uploadRoot;
    private final MeetingRepository meetingRepository;
    private final InvitationAssetRepository assetRepository;

    public InvitationAssetService(@Value("${meetingos.app.upload-dir:uploads}") String uploadDir,
                                  MeetingRepository meetingRepository,
                                  InvitationAssetRepository assetRepository) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.meetingRepository = meetingRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public java.util.List<AssetResponse> list(Long meetingId) {
        ensureMeeting(meetingId);
        return assetRepository.findByMeetingIdOrderByCreatedAtDesc(meetingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AssetResponse upload(Long meetingId, MultipartFile file) {
        Meeting meeting = ensureMeeting(meetingId);
        validateFile(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String datePath = LocalDate.now().format(DATE_PATH);
        String storageName = UUID.randomUUID() + EXTENSIONS.get(contentType);
        Path directory = uploadRoot.resolve("images/invitations").resolve(String.valueOf(meetingId)).resolve(datePath).normalize();
        Path target = directory.resolve(storageName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw BusinessException.badRequest("素材保存路径非法");
        }
        try {
            Files.createDirectories(directory);
            file.transferTo(target);
            validateMagic(target, contentType);
            BufferedImage image = ImageIO.read(target.toFile());

            InvitationAsset asset = new InvitationAsset();
            asset.setMeeting(meeting);
            asset.setOriginalName(normalizeOriginalName(file.getOriginalFilename()));
            asset.setStorageName(storageName);
            asset.setPublicUrl("/uploads/images/invitations/" + meetingId + "/" + datePath + "/" + storageName);
            asset.setContentType(contentType);
            asset.setSizeBytes(file.getSize());
            asset.setWidthPx(image == null ? null : image.getWidth());
            asset.setHeightPx(image == null ? null : image.getHeight());
            asset.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
            return toResponse(assetRepository.save(asset));
        } catch (IOException ex) {
            deleteQuietly(target);
            throw new IllegalStateException("邀请函素材保存失败", ex);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请上传图片素材");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BusinessException.badRequest("邀请函图片不能超过 10MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw BusinessException.badRequest("仅支持 JPG、PNG、WebP 或 GIF 图片");
        }
    }

    private void validateMagic(Path path, String contentType) throws IOException {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = Files.newInputStream(path)) {
            length = input.read(header);
        }
        boolean valid = switch (contentType) {
            case "image/jpeg" -> length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8;
            case "image/png" -> length >= 8 && Arrays.equals(Arrays.copyOf(header, 8),
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/gif" -> length >= 6 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("GIF8");
            case "image/webp" -> length >= 12
                    && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
        if (!valid) {
            throw BusinessException.badRequest("图片内容与文件类型不匹配");
        }
    }

    private Meeting ensureMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
    }

    private AssetResponse toResponse(InvitationAsset asset) {
        return new AssetResponse(asset.getId(), asset.getOriginalName(), asset.getPublicUrl(), asset.getContentType(),
                asset.getSizeBytes(), asset.getWidthPx(), asset.getHeightPx(), asset.getCreatedAt());
    }

    private String normalizeOriginalName(String value) {
        String name = StringUtils.cleanPath(value == null ? "image" : value).replace("..", "");
        if (!StringUtils.hasText(name)) {
            return "image";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 数据库写入失败后的文件清理由后续运维兜底，不覆盖原始异常。
        }
    }
}
