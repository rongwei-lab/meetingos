package com.meetingos.publicapi;

public record PublicCaptchaResponse(
        boolean enabled,
        String captchaId,
        String question,
        int expiresInSeconds
) {
    public static PublicCaptchaResponse disabled() {
        return new PublicCaptchaResponse(false, null, null, 0);
    }
}
