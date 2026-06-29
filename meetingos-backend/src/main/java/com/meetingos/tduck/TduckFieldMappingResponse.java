package com.meetingos.tduck;

/**
 * 参会人列表和报名配置页使用的 TDuck 字段展示模型。
 *
 * <p>注意：这里返回的是“TDuck 表单完整字段清单”，不只是已经映射到 MeetingOS 标准字段的记录。
 * 自定义字段（如评分、数字组件）可能只以 attendeeField=null 的“明确不映射”状态写入映射表，
 * 用来避免后续同步时被自动识别覆盖；页面仍按 TDuck 表单顺序展示完整字段清单。</p>
 */
public record TduckFieldMappingResponse(
        Long id,
        AttendeeField attendeeField,
        String tduckFieldKey,
        String tduckFieldLabel,
        String tduckFieldType,
        boolean tduckRequired,
        boolean requiredMapping,
        MappingStatus status
) {

    public static TduckFieldMappingResponse saved(TduckFieldMapping mapping) {
        boolean mapped = mapping.getAttendeeField() != null;
        return new TduckFieldMappingResponse(
                mapping.getId(),
                mapping.getAttendeeField(),
                mapping.getTduckFieldKey(),
                mapping.getTduckFieldLabel(),
                null,
                mapped && mapping.isRequiredMapping(),
                mapped && mapping.isRequiredMapping(),
                mapped ? MappingStatus.VALID : MappingStatus.NOT_CONFIGURED
        );
    }

    public static TduckFieldMappingResponse fromField(TduckFormFieldOption field, TduckFieldMapping mapping) {
        boolean mapped = mapping != null && mapping.getAttendeeField() != null;
        return new TduckFieldMappingResponse(
                mapping == null ? null : mapping.getId(),
                mapped ? mapping.getAttendeeField() : null,
                field.fieldKey(),
                field.label(),
                field.type(),
                field.required(),
                mapped && mapping.isRequiredMapping(),
                mapped ? MappingStatus.VALID : MappingStatus.NOT_CONFIGURED
        );
    }
}
