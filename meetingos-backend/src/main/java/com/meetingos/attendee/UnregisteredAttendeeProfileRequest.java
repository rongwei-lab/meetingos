package com.meetingos.attendee;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 现场未报名入座人员可维护的 TDuck 报名字段。
 *
 * 前端只提交当前会议 TDuck 字段映射中的 fieldKey（以及对应的 fieldKeylabel）。服务端会再次按
 * 会议映射白名单校验，防止客户端借由原始快照修改报名来源、TDuck 元数据或其他系统状态。
 */
public record UnregisteredAttendeeProfileRequest(
        @NotNull @Size(max = 200) Map<String, Object> formValues
) {

    public UnregisteredAttendeeProfileRequest {
        // 请求体在控制器外直接被服务测试或其他调用方使用时，也不能让可变 Map 在后续被意外改写。
        formValues = formValues == null ? null : new LinkedHashMap<>(formValues);
    }
}
