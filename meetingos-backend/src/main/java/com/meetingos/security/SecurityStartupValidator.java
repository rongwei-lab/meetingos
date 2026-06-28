package com.meetingos.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("!test")
public class SecurityStartupValidator implements ApplicationRunner {

    private final SecurityProperties securityProperties;
    private final TduckProperties tduckProperties;

    public SecurityStartupValidator(SecurityProperties securityProperties, TduckProperties tduckProperties) {
        this.securityProperties = securityProperties;
        this.tduckProperties = tduckProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireStrongSecret("JWT_SECRET", securityProperties.jwtSecret(), "change-this-secret-to-at-least-32-bytes-before-production");
        requireStrongSecret("TDUCK_WEBHOOK_TOKEN", tduckProperties.webhookSecret(), "change-this-webhook-secret");
    }

    private void requireStrongSecret(String name, String value, String defaultValue) {
        String secret = value == null ? "" : value.trim();
        String normalized = secret.toLowerCase(Locale.ROOT);
        if (secret.length() < 32
                || normalized.equals(defaultValue.toLowerCase(Locale.ROOT))
                || normalized.startsWith("change_me")
                || normalized.startsWith("change-this")
                || normalized.startsWith("local-demo")
                || normalized.equals("123456")
                || normalized.equals("admin123")) {
            throw new IllegalStateException(name + " 必须设置为至少 32 位的非默认随机密钥");
        }
    }
}
