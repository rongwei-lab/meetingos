package com.meetingos.tduck;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 同步日志详情只返回已写入的表单数据，不暴露 Webhook 原始载荷中的非表单信息。 */
public record TduckSyncLogDetailResponse(
        Long id,
        Long meetingId,
        String formKey,
        String eventType,
        SyncStatus status,
        String message,
        String tduckDataId,
        LocalDateTime createdAt,
        Map<String, Object> formValues
) {

    public TduckSyncLogDetailResponse {
        formValues = formValues == null ? Map.of() : new LinkedHashMap<>(formValues);
    }

    public static TduckSyncLogDetailResponse from(TduckSyncLog log) {
        Map<String, Object> payload = log.getRawPayload() == null ? Map.of() : log.getRawPayload();
        return new TduckSyncLogDetailResponse(
                log.getId(),
                log.getMeeting() == null ? null : log.getMeeting().getId(),
                log.getFormKey(),
                log.getEventType(),
                log.getStatus(),
                log.getMessage(),
                log.getTduckDataId(),
                log.getCreatedAt(),
                formValues(payload.get("data"))
        );
    }

    private static Map<String, Object> formValues(Object value) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> source)) {
            return values;
        }
        source.forEach((key, entry) -> {
            if (key != null) {
                values.put(String.valueOf(key), entry);
            }
        });
        return values;
    }
}
