package com.meetingos.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EnvExampleConfigurationTest {

    @Test
    void envExampleKeepsJwtSecretPlaceholderForDeploymentGuard() throws IOException {
        Path envExample = locateEnvExample();
        assertThat(envExample)
                .as(".env.example should be available when running repository integration tests")
                .exists();

        Optional<String> jwtSecret = Files.readAllLines(envExample).stream()
                .filter(line -> line.startsWith("JWT_SECRET="))
                .map(line -> line.substring("JWT_SECRET=".length()))
                .findFirst();

        assertThat(jwtSecret)
                .as(".env.example must define JWT_SECRET for Docker Compose runtime")
                .isPresent();

        /*
         * 示例文件应保留明显占位符，避免被误认为可直接用于生产。
         * 真正的强随机值由 deploy/scripts/check-env.sh 在部署前强制校验。
         */
        assertThat(jwtSecret.orElseThrow())
                .as(".env.example JWT_SECRET must be an obvious placeholder")
                .startsWith("CHANGE_ME");
    }

    private Path locateEnvExample() {
        return List.of(Path.of(".env.example"), Path.of("..", ".env.example")).stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(Path.of("..", ".env.example"));
    }
}
