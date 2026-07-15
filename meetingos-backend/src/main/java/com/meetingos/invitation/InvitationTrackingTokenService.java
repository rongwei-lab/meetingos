package com.meetingos.invitation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Service
public class InvitationTrackingTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String VERSION = "1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;

    public InvitationTrackingTokenService(InvitationProperties properties) {
        String secret = properties.trackingSecret();
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("INVITATION_TRACKING_SECRET 至少需要 32 字节");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String create(String clickPublicId, Long meetingId, String formKey, Instant expiresAt) {
        if (!StringUtils.hasText(clickPublicId) || meetingId == null || meetingId <= 0
                || !StringUtils.hasText(formKey) || expiresAt == null) {
            throw new IllegalArgumentException("邀请归因令牌参数不完整");
        }
        String payload = String.join(".",
                VERSION,
                clickPublicId,
                String.valueOf(meetingId),
                formKeyFingerprint(formKey),
                String.valueOf(expiresAt.getEpochSecond())
        );
        return payload + "." + encode(hmac(payload));
    }

    public Optional<TrackingClaims> verify(String token) {
        if (!StringUtils.hasText(token) || token.length() > 255
                || !token.matches("[A-Za-z0-9._~-]+")) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 6 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        String payload = String.join(".", Arrays.copyOf(parts, 5));
        if (!MessageDigest.isEqual(hmac(payload), decode(parts[5]))) {
            return Optional.empty();
        }
        try {
            long meetingId = Long.parseLong(parts[2]);
            long expiresAt = Long.parseLong(parts[4]);
            if (meetingId <= 0 || expiresAt <= Instant.now().getEpochSecond()
                    || !parts[1].matches("[A-Za-z0-9_-]{20,80}")
                    || !parts[3].matches("[A-Za-z0-9_-]{22}")) {
                return Optional.empty();
            }
            return Optional.of(new TrackingClaims(parts[1], meetingId, parts[3], Instant.ofEpochSecond(expiresAt)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public boolean matchesFormKey(TrackingClaims claims, String formKey) {
        return claims != null && StringUtils.hasText(formKey)
                && MessageDigest.isEqual(
                        claims.formKeyFingerprint().getBytes(StandardCharsets.US_ASCII),
                        formKeyFingerprint(formKey).getBytes(StandardCharsets.US_ASCII)
                );
    }

    public String newOpaqueId() {
        byte[] value = new byte[24];
        SECURE_RANDOM.nextBytes(value);
        return encode(value);
    }

    public String hashVisitor(String rawVisitorId) {
        return java.util.HexFormat.of().formatHex(hmac("visitor:" + rawVisitorId));
    }

    private String formKeyFingerprint(String formKey) {
        return encode(Arrays.copyOf(hmac("form:" + formKey.trim()), 16));
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("邀请归因签名计算失败", ex);
        }
    }

    private String encode(byte[] value) {
        return BASE64_URL.encodeToString(value);
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    public record TrackingClaims(
            String clickPublicId,
            long meetingId,
            String formKeyFingerprint,
            Instant expiresAt
    ) {
    }
}
