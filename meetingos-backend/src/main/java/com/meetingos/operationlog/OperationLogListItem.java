package com.meetingos.operationlog;

import java.time.LocalDateTime;

/**
 * 管理端操作日志列表的最小响应模型。
 * 审计实体仍保存 clientIp 和 details 供服务端排查使用，但列表接口不能把这些敏感上下文返回给浏览器。
 */
public record OperationLogListItem(
        Long id,
        LocalDateTime createdAt,
        String actor,
        String action,
        OperationLogCategory category,
        String targetType,
        String targetId,
        boolean success,
        String message
) {
    public static OperationLogListItem from(OperationLog log) {
        return new OperationLogListItem(
                log.getId(),
                log.getCreatedAt(),
                log.getActor(),
                log.getAction(),
                log.getCategory(),
                log.getTargetType(),
                log.getTargetId(),
                log.isSuccess(),
                log.getMessage()
        );
    }
}
