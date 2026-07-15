package com.meetingos.security;

import com.meetingos.invitation.InvitationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityStartupValidatorInvitationTest {

    @Test
    void rejectsDefaultInvitationTrackingSecret() {
        SecurityStartupValidator validator = new SecurityStartupValidator(
                security("strong-jwt-secret-strong-jwt-secret-123456"),
                tduck("strong-webhook-secret-strong-webhook-secret-123456"),
                new InvitationProperties("change-this-invitation-tracking-secret-at-least-32-bytes", 180, "")
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MEETINGOS_INVITATION_TRACKING_SECRET");
    }

    @Test
    void acceptsIndependentStrongSecrets() {
        SecurityStartupValidator validator = new SecurityStartupValidator(
                security("strong-jwt-secret-strong-jwt-secret-123456"),
                tduck("strong-webhook-secret-strong-webhook-secret-123456"),
                new InvitationProperties("strong-invitation-secret-strong-invitation-123456", 180, "")
        );

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    private SecurityProperties security(String secret) {
        return new SecurityProperties(secret, "meetingos-test", 60, false);
    }

    private TduckProperties tduck(String secret) {
        return new TduckProperties(
                secret, "http://meeting.test/webhook", "http://tduck.test", "http://tduck-api.test",
                "http://tduck-api.test", "admin@example.test", "password",
                Duration.ofSeconds(1), Duration.ofSeconds(1)
        );
    }
}
