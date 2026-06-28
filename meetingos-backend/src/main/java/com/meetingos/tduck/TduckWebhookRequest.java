package com.meetingos.tduck;

import java.util.Map;

public record TduckWebhookRequest(
        String eventId,
        String eventType,
        String formKey,
        String dataId,
        Map<String, Object> data,
        Map<String, Object> raw
) {
}
