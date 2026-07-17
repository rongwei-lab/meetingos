package com.meetingos.publicapi;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record PublicQueryRequest(
        @Size(max = 4096, message = "公开查询令牌长度不能超过 4096")
        String publicQueryToken,
        @NotNull
        @Size(min = 1, max = 8, message = "查询字段数量必须在 1 到 8 个之间")
        Map<PublicQueryField, @Size(max = 200, message = "单个查询字段长度不能超过 200") String> values,
        @Size(max = 128, message = "验证码标识长度不能超过 128")
        String captchaId,
        @Size(max = 32, message = "验证码答案长度不能超过 32")
        String captchaAnswer,
        @Size(max = 120, message = "签到来源长度不能超过 120")
        String checkinSource,
        @Size(max = 120, message = "签到页面长度不能超过 120")
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
