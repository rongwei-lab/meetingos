package com.meetingos.tduck;

import java.util.Map;

public record TduckWebhookRequest(
        String eventId,
        String eventType,
        String formKey,
        String dataId,
        String extValue,
        Map<String, Object> data,
        Map<String, Object> raw
) {

    /**
     * 保留旧的内部构造方式，手工同步和既有集成调用未提供来源信息时按未归因处理。
     */
    public TduckWebhookRequest(String eventId,
                               String eventType,
                               String formKey,
                               String dataId,
                               Map<String, Object> data,
                               Map<String, Object> raw) {
        this(eventId, eventType, formKey, dataId, null, data, raw);
    }
}
