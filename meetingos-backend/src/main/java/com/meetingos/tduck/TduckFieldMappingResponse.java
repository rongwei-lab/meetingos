package com.meetingos.tduck;

/**
 * 参会人列表和报名配置页使用的 TDuck 字段展示模型。
 *
 * <p>注意：这里返回的是“TDuck 表单完整字段清单”，不只是已经映射到 MeetingOS 标准字段的记录。
 * 自定义字段（如评分、数字组件）不会写入 tduck_field_mappings 表，但仍需要在参会人列表/详情中按
 * TDuck 表单顺序展示，避免管理端字段和 TDuck 原表字段不一致。</p>
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
        return new TduckFieldMappingResponse(
                mapping.getId(),
                mapping.getAttendeeField(),
                mapping.getTduckFieldKey(),
                mapping.getTduckFieldLabel(),
                null,
                mapping.isRequiredMapping(),
                mapping.isRequiredMapping(),
                MappingStatus.VALID
        );
    }

    public static TduckFieldMappingResponse fromField(TduckFormFieldOption field, TduckFieldMapping mapping) {
        boolean mapped = mapping != null;
        return new TduckFieldMappingResponse(
                mapped ? mapping.getId() : null,
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
