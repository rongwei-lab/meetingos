package com.meetingos.publicapi;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PublicQueryRequest(
        Long meetingId,
        String meetingCode,
        @NotNull Map<PublicQueryField, String> values,
        String captchaId,
        String captchaAnswer,
        String checkinSource
) {
    public PublicQueryRequest(Long meetingId, String meetingCode, Map<PublicQueryField, String> values) {
        this(meetingId, meetingCode, values, null, null, null);
    }

    public PublicQueryRequest(Long meetingId,
                              String meetingCode,
                              Map<PublicQueryField, String> values,
                              String captchaId,
                              String captchaAnswer) {
        this(meetingId, meetingCode, values, captchaId, captchaAnswer, null);
    }
}
