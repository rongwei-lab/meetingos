package com.meetingos.tduck;

import com.meetingos.common.BusinessException;
import com.meetingos.common.ErrorCode;

/**
 * 仅在 Webhook 凭据校验完成后，且请求体无法解析时抛出。
 * 控制器据此补写脱敏审计摘要，同时仍向 TDuck 返回原有的 400 业务错误。
 */
public class VerifiedWebhookParseException extends BusinessException {

    private final Long syncLogId;

    public VerifiedWebhookParseException(Long syncLogId) {
        super(ErrorCode.BAD_REQUEST, "Webhook 请求体解析失败");
        this.syncLogId = syncLogId;
    }

    public Long getSyncLogId() {
        return syncLogId;
    }
}
