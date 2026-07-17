package com.meetingos.invitation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationTrackingTokenServiceTest {
    private final InvitationTrackingTokenService service = new InvitationTrackingTokenService(new InvitationProperties(
            "test-invitation-tracking-secret-that-is-long-enough", 180, "test-amap-key"
    ));

    @Test
    void createsCompactUrlSafeTokenAndVerifiesClaims() {
        String token = service.create("abcdefghijklmnopqrstuvwx", 42L, "form-key-2026", Instant.now().plusSeconds(3600));

        assertThat(token).hasSizeLessThan(255).matches("[A-Za-z0-9._~-]+");
        InvitationTrackingTokenService.TrackingClaims claims = service.verify(token).orElseThrow();
        assertThat(claims.meetingId()).isEqualTo(42L);
        assertThat(claims.clickPublicId()).isEqualTo("abcdefghijklmnopqrstuvwx");
        assertThat(service.matchesFormKey(claims, "form-key-2026")).isTrue();
        assertThat(service.matchesFormKey(claims, "another-form")).isFalse();
    }

    @Test
    void rejectsTamperedExpiredAndOversizedTokens() {
        String valid = service.create("abcdefghijklmnopqrstuvwx", 42L, "form-key", Instant.now().plusSeconds(3600));
        String tampered = valid.substring(0, valid.length() - 1) + (valid.endsWith("A") ? "B" : "A");
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int lastIndex = alphabet.indexOf(valid.charAt(valid.length() - 1));
        char nonCanonicalAlias = alphabet.charAt((lastIndex & ~3) | ((lastIndex + 1) & 3));
        String equivalentButNonCanonical = valid.substring(0, valid.length() - 1) + nonCanonicalAlias;
        String expired = service.create("abcdefghijklmnopqrstuvwx", 42L, "form-key", Instant.now().minusSeconds(1));

        assertThat(service.verify(tampered)).isEmpty();
        assertThat(service.verify(equivalentButNonCanonical)).isEmpty();
        assertThat(service.verify(expired)).isEmpty();
        assertThat(service.verify("a".repeat(256))).isEmpty();
    }
}
