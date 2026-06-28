package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeType;
import com.meetingos.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TduckFieldMapper {

    public MappedAttendeeData map(List<TduckFieldMapping> mappings, Map<String, Object> data) {
        if (mappings == null || mappings.isEmpty()) {
            throw BusinessException.badRequest("字段映射不存在");
        }

        Map<AttendeeField, Object> values = new EnumMap<>(AttendeeField.class);
        for (TduckFieldMapping mapping : mappings) {
            Object value = mappedValue(data, mapping.getTduckFieldKey());
            if (mapping.isRequiredMapping() && isBlank(value)) {
                throw BusinessException.badRequest("必填映射字段缺失: " + mapping.getAttendeeField());
            }
            values.put(mapping.getAttendeeField(), value);
        }

        String name = asString(values.get(AttendeeField.NAME));
        String phone = normalizePhone(asString(values.get(AttendeeField.PHONE)));
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("姓名不能为空");
        }
        if (phone == null || phone.isBlank()) {
            throw BusinessException.badRequest("手机号不能为空");
        }

        return new MappedAttendeeData(
                name,
                phone,
                asString(values.get(AttendeeField.ORGANIZATION)),
                asString(values.get(AttendeeField.TITLE)),
                asString(values.get(AttendeeField.IDENTITY_NO)),
                asString(values.get(AttendeeField.EMAIL)),
                parseAttendeeType(asString(values.get(AttendeeField.ATTENDEE_TYPE))),
                parseBoolean(values.get(AttendeeField.ACCOMMODATION_REQUIRED)),
                asString(values.get(AttendeeField.ROOM_NO)),
                asString(values.get(AttendeeField.ROOM_REMARK)),
                asString(values.get(AttendeeField.REMARK))
        );
    }

    private Object mappedValue(Map<String, Object> data, String fieldKey) {
        if (data == null || fieldKey == null) {
            return null;
        }
        /*
         * TDuck 的单选、多选、下拉字段通常会同时返回内部枚举值和展示标签：
         *   radio_xxx=1, radio_xxxlabel=个人参观
         * 如果映射字段指向原组件 key，优先使用 label，避免把 1/2/3 写入参会类型、
         * 是否住宿等业务字段，也让原始详情和列表展示更接近填写人看到的内容。
         */
        Object labelValue = data.get(fieldKey + "label");
        if (!isBlank(labelValue)) {
            return labelValue;
        }
        return data.get(fieldKey);
    }

    private AttendeeType parseAttendeeType(String value) {
        if (value == null || value.isBlank()) {
            return AttendeeType.NORMAL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VIP", "贵宾" -> AttendeeType.VIP;
            case "GUEST", "嘉宾" -> AttendeeType.GUEST;
            case "MEDIA", "媒体" -> AttendeeType.MEDIA;
            case "STAFF", "工作人员" -> AttendeeType.STAFF;
            default -> AttendeeType.NORMAL;
        };
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = asString(value);
        if (text == null) {
            return false;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1", "需要", "是" -> true;
            default -> false;
        };
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("^\\+?\\d{6,20}$")) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        return normalized;
    }

    private boolean isBlank(Object value) {
        return asString(value) == null || asString(value).isBlank();
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
