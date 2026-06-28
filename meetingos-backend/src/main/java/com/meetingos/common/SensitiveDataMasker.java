package com.meetingos.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        String suffix = trimmed.substring(trimmed.length() - 4);
        return "****" + suffix;
    }

    public static String maskIdentityNo(String identityNo) {
        if (identityNo == null || identityNo.isBlank()) {
            return identityNo;
        }
        String trimmed = identityNo.trim();
        if (trimmed.length() <= 6) {
            return "******";
        }
        return trimmed.substring(0, Math.min(3, trimmed.length())) + "******" + trimmed.substring(trimmed.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String prefix = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String visible = prefix.length() <= 1 ? prefix : prefix.substring(0, 1);
        return visible + "***" + domain;
    }

    public static Map<String, Object> maskMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        source.forEach((key, value) -> masked.put(key, maskValue(key, value)));
        return masked;
    }

    @SuppressWarnings("unchecked")
    public static Object maskValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> result = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) -> result.put(String.valueOf(nestedKey), maskValue(String.valueOf(nestedKey), nestedValue)));
            return result;
        }
        if (!(value instanceof String text)) {
            return value;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        // 日志、签到快照、TDuck 原始载荷里的敏感字段命名不完全一致，按常见关键字兜底清洗。
        if (normalizedKey.contains("phone") || normalizedKey.contains("mobile") || normalizedKey.contains("tel") || normalizedKey.contains("手机号")) {
            return maskPhone(text);
        }
        if (normalizedKey.contains("identity") || normalizedKey.contains("idcard") || normalizedKey.contains("id_no")
                || normalizedKey.contains("idno") || normalizedKey.contains("证件") || normalizedKey.contains("身份证")) {
            return maskIdentityNo(text);
        }
        if (normalizedKey.contains("email") || normalizedKey.contains("mail") || normalizedKey.contains("邮箱")) {
            return maskEmail(text);
        }
        if (normalizedKey.contains("token") || normalizedKey.contains("password") || normalizedKey.contains("secret")
                || normalizedKey.contains("authorization") || normalizedKey.contains("签名") || normalizedKey.contains("密钥")) {
            return "***";
        }
        /*
         * TDuck 原始报名字段经常是 input178xxx 这类动态 key，单靠字段名无法识别敏感信息。
         * 返回给前端前按值形态做最后兜底：手机号/邮箱看起来像敏感值时也脱敏；数据库快照仍保留原值。
         */
        if (looksLikeEmail(text)) {
            return maskEmail(text);
        }
        if (looksLikePhone(text)) {
            return maskPhone(text);
        }
        return text;
    }

    private static boolean looksLikeEmail(String value) {
        return value != null && value.matches("^[^\\s@]{1,64}@[^\\s@]{1,255}\\.[^\\s@]{2,}$");
    }

    private static boolean looksLikePhone(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.replaceAll("[\\s-]", "");
        return normalized.matches("^\\+?\\d{10,20}$");
    }
}
