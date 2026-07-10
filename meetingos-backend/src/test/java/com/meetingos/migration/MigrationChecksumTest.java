package com.meetingos.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationChecksumTest {

    private static final String MANIFEST = "flyway/migration-checksums.sha256";

    @Test
    void publishedMigrationsMustKeepTheirOriginalBytes() throws IOException {
        Map<String, String> expected = readChecksumManifest();
        Map<String, Resource> migrations = findMigrations();

        // 文件清单也参与校验：新增迁移时必须显式登记，删除或重命名历史迁移同样会失败。
        assertEquals(expected.keySet(), migrations.keySet(),
            "Flyway 迁移清单已变化。只能新增迁移；更新 checksum 前请确认没有修改已发布迁移");

        expected.forEach((filename, checksum) -> {
            Resource migration = migrations.get(filename);
            assertNotNull(migration, "缺少迁移文件: " + filename);
            assertEquals(checksum, sha256(migration),
                "已发布 Flyway 迁移发生漂移: " + filename + "。请恢复原始字节并新增迁移文件");
        });
    }

    private Map<String, String> readChecksumManifest() throws IOException {
        Resource resource = new PathMatchingResourcePatternResolver().getResource("classpath:" + MANIFEST);
        assertTrue(resource.exists(), "缺少 Flyway checksum 清单: " + MANIFEST);

        List<String> lines;
        try (InputStream input = resource.getInputStream()) {
            lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }

        Map<String, String> checksums = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            String[] parts = trimmed.split("\\s+", 2);
            assertEquals(2, parts.length, "checksum 清单格式错误: " + line);
            assertTrue(parts[0].matches("[0-9a-f]{64}"), "非法 SHA-256: " + parts[0]);
            assertTrue(parts[1].matches("V[0-9]+(?:_[0-9]+)*__.+\\.sql"), "非法迁移文件名: " + parts[1]);
            assertFalse(checksums.containsKey(parts[1]), "checksum 清单存在重复文件: " + parts[1]);
            checksums.put(parts[1], parts[0]);
        }
        assertFalse(checksums.isEmpty(), "Flyway checksum 清单不能为空");
        return checksums;
    }

    private Map<String, Resource> findMigrations() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*.sql");
        return Arrays.stream(resources)
            .sorted((left, right) -> left.getFilename().compareTo(right.getFilename()))
            .collect(Collectors.toMap(
                Resource::getFilename,
                resource -> resource,
                (left, right) -> {
                    throw new IllegalStateException("类路径中存在重名迁移文件: " + left.getFilename());
                },
                LinkedHashMap::new
            ));
    }

    private String sha256(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.readAllBytes());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算迁移 checksum: " + resource.getFilename(), exception);
        }
    }
}
