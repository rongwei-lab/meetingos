package com.meetingos.tduck;

public record TduckPhonePrecheckResponse(
        boolean available,
        String code,
        String message
) {

    public static TduckPhonePrecheckResponse allowed() {
        return new TduckPhonePrecheckResponse(true, "AVAILABLE", "手机号可以报名");
    }

    public static TduckPhonePrecheckResponse duplicate() {
        return new TduckPhonePrecheckResponse(false, "DUPLICATE", "该手机号已报名，请勿重复提交");
    }

    public static TduckPhonePrecheckResponse invalidPhone() {
        return new TduckPhonePrecheckResponse(false, "INVALID_PHONE", "请输入正确的手机号");
    }

    public static TduckPhonePrecheckResponse unavailable(String message) {
        return new TduckPhonePrecheckResponse(false, "UNAVAILABLE", message);
    }

    public static TduckPhonePrecheckResponse invalidToken() {
        return new TduckPhonePrecheckResponse(false, "INVALID_TOKEN", "报名校验已过期，请刷新页面后重试");
    }
}
