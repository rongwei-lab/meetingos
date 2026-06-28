package com.meetingos.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(dbData);
            if (node == null || node.isNull()) {
                return new LinkedHashMap<>();
            }
            if (node.isObject()) {
                return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
            }
            if (node.isTextual()) {
                // H2 的 JSON 类型在 MySQL 兼容模式下可能把 "{}" 作为 JSON 字符串返回。
                // 二次解析可同时兼容测试库和生产库已有的字符串化历史数据。
                String nestedJson = node.asText();
                if (nestedJson == null || nestedJson.isBlank()) {
                    return new LinkedHashMap<>();
                }
                return OBJECT_MAPPER.readValue(nestedJson, MAP_TYPE);
            }
            throw new IllegalArgumentException("JSON 内容必须是对象");
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 反序列化失败", ex);
        }
    }
}
