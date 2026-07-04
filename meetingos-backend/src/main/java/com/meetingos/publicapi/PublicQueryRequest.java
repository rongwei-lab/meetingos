package com.meetingos.publicapi;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PublicQueryRequest(
        String publicQueryToken,
        @NotNull Map<PublicQueryField, String> values,
        String captchaId,
        String captchaAnswer,
        String checkinSource,
        String checkinPage
) {
    public PublicQueryRequest(String publicQueryToken, Map<PublicQueryField, String> values) {
        this(publicQueryToken, values, null, null, null, null);
    }

    public PublicQueryRequest(String publicQueryToken,
                              Map<PublicQueryField, String> values,
                              String captchaId,
                              String captchaAnswer) {
        this(publicQueryToken, values, captchaId, captchaAnswer, null, null);
    }
}
