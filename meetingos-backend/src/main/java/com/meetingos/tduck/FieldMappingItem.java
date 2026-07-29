package com.meetingos.tduck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FieldMappingItem(
        /*
         * attendeeField 允许为空，用来保存“管理员已确认该 TDuck 字段不需要映射”的状态。
         * 这类记录不会参与 Webhook 入库字段映射，但能阻止后续同步字段时再次被自动识别回填。
         */
        AttendeeField attendeeField,
        @NotBlank @Size(max = 120) String tduckFieldKey,
        @Size(max = 160) String tduckFieldLabel,
        boolean requiredMapping
) {
}
