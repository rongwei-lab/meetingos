package com.meetingos.tduck;

/**
 * TDuck 填写页使用的手机号预检查配置。
 *
 * <p>令牌只允许短期使用，且绑定会议与表单。未配置手机号映射时返回 disabled，
 * 让旧表单继续走 Webhook 兜底，不因为预检查能力缺失而阻断报名。</p>
 */
public record TduckPrecheckTokenResponse(
        boolean enabled,
        String token,
        String phoneFieldKey,
        int expiresInSeconds,
        String message
) {

    public static TduckPrecheckTokenResponse active(String token,
                                                    String phoneFieldKey,
                                                    int expiresInSeconds) {
        return new TduckPrecheckTokenResponse(
                true,
                token,
                phoneFieldKey,
                expiresInSeconds,
                "手机号重复校验已启用"
        );
    }

    public static TduckPrecheckTokenResponse disabled(String message) {
        return new TduckPrecheckTokenResponse(false, null, null, 0, message);
    }
}
