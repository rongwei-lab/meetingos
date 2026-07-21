package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeType;

import java.util.LinkedHashMap;
import java.util.Map;

public record TduckSubmissionParticipantResponse(
        String name,
        String phone,
        String organization,
        String title,
        String identityNo,
        String email,
        AttendeeType attendeeType,
        boolean accommodationRequired,
        String remark,
        Map<String, Object> formValues
) {

    public TduckSubmissionParticipantResponse {
        // 保留 TDuck 表单原值，管理员核对重复报名时需要看到与填写页一致的完整数据。
        formValues = formValues == null ? Map.of() : new LinkedHashMap<>(formValues);
    }
}
