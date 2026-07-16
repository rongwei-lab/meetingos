package com.meetingos.tduck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 TDuck 不同版本的 Webhook JSON 规范化为内部请求对象。
 *
 * <p>该组件只能在 Webhook 凭据校验通过后调用，避免未认证的大请求进入 JSON 解析流程，
 * 也避免后续失败日志误保存攻击者提交的原始业务数据。</p>
 */
@Component
public class TduckWebhookParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public TduckWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TduckWebhookRequest parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw BusinessException.badRequest("Webhook 请求体不能为空");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(rawBody, MAP_TYPE);
            if (payload == null) {
                throw BusinessException.badRequest("Webhook 请求体不是有效的 JSON 对象");
            }
            return toWebhookRequest(payload);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("Webhook 请求体不是有效的 JSON 对象");
        }
    }

    @SuppressWarnings("unchecked")
    private TduckWebhookRequest toWebhookRequest(Map<String, Object> payload) {
        Map<String, Object> data = extractSubmissionData(payload);
        Object nestedData = payload.get("data");
        Map<String, Object> dataContainer = nestedData instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return new TduckWebhookRequest(
                firstNonBlank(asString(payload.get("eventId")), asString(dataContainer.get("eventId"))),
                firstNonBlank(
                        asString(payload.getOrDefault("eventType", payload.get("type"))),
                        asString(dataContainer.getOrDefault("eventType", dataContainer.get("type")))
                ),
                firstNonBlank(asString(payload.get("formKey")), asString(dataContainer.get("formKey"))),
                firstNonBlank(
                        asString(payload.get("dataId")),
                        firstNonBlank(
                                asString(payload.get("id")),
                                firstNonBlank(asString(dataContainer.get("dataId")), asString(dataContainer.get("id")))
                        )
                ),
                firstNonBlank(
                        asMetadataString(payload.get("extValue")),
                        asMetadataString(dataContainer.get("extValue"))
                ),
                data,
                payload
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSubmissionData(Map<String, Object> payload) {
        Object nestedData = payload.get("data");
        if (nestedData instanceof Map<?, ?> nestedMap) {
            Object originalData = nestedMap.get("originalData");
            if (originalData instanceof Map<?, ?> originalMap) {
                return (Map<String, Object>) originalMap;
            }
            return withoutMetaFields((Map<String, Object>) nestedMap);
        }

        Object originalData = payload.get("originalData");
        if (originalData instanceof Map<?, ?> originalMap) {
            return (Map<String, Object>) originalMap;
        }
        return withoutMetaFields(payload);
    }

    private Map<String, Object> withoutMetaFields(Map<String, Object> source) {
        Map<String, Object> data = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!TduckSubmissionFields.isMetaField(key)) {
                data.put(key, value);
            }
        });
        return data;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String asMetadataString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
