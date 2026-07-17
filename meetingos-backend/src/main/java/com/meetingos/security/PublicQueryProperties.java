package com.meetingos.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingos.public-query")
public class PublicQueryProperties {

    /**
     * 单个 IP 每分钟允许访问公开查询接口的次数。MVP 使用本机内存限流，生产多副本可替换为 Redis。
     */
    private int rateLimitPerMinute = 30;

    /**
     * 是否开启公开查询验证码。默认关闭以兼容历史公开链接，生产面向公网时建议开启。
     */
    private boolean captchaEnabled = false;

    /**
     * 验证码有效期。内存验证码只适合单实例部署，多副本生产环境应迁移到 Redis。
     */
    private int captchaTtlSeconds = 300;

    /**
     * 是否信任反向代理写入的 X-Forwarded-For / X-Real-IP。公网直连后端时必须关闭，
     * 否则攻击者可以伪造请求头绕过公开查询限流。
     */
    private boolean trustForwardHeaders = false;

    /** 公开查询历史保留天数，防止审计表无限增长。 */
    private int historyRetentionDays = 180;

    /** 单个清理事务删除的最大行数。 */
    private int historyCleanupBatchSize = 1000;

    /** 单次调度允许处理的最大批次数，避免清理任务长期占用数据库。 */
    private int historyCleanupMaxBatches = 10;

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        this.captchaEnabled = captchaEnabled;
    }

    public int getCaptchaTtlSeconds() {
        return captchaTtlSeconds;
    }

    public void setCaptchaTtlSeconds(int captchaTtlSeconds) {
        this.captchaTtlSeconds = captchaTtlSeconds;
    }

    public boolean isTrustForwardHeaders() {
        return trustForwardHeaders;
    }

    public void setTrustForwardHeaders(boolean trustForwardHeaders) {
        this.trustForwardHeaders = trustForwardHeaders;
    }

    public int getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    public void setHistoryRetentionDays(int historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
    }

    public int getHistoryCleanupBatchSize() {
        return historyCleanupBatchSize;
    }

    public void setHistoryCleanupBatchSize(int historyCleanupBatchSize) {
        this.historyCleanupBatchSize = historyCleanupBatchSize;
    }

    public int getHistoryCleanupMaxBatches() {
        return historyCleanupMaxBatches;
    }

    public void setHistoryCleanupMaxBatches(int historyCleanupMaxBatches) {
        this.historyCleanupMaxBatches = historyCleanupMaxBatches;
    }
}
