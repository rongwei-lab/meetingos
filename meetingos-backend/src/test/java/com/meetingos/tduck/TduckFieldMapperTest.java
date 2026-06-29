package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeType;
import com.meetingos.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TduckFieldMapperTest {

    private final TduckFieldMapper mapper = new TduckFieldMapper();

    @Test
    void mapsRequiredFieldsAndNormalizesPhone() {
        List<TduckFieldMapping> mappings = List.of(
                mapping(AttendeeField.NAME, "name", true),
                mapping(AttendeeField.PHONE, "phone", true),
                mapping(AttendeeField.ATTENDEE_TYPE, "type", false),
                mapping(AttendeeField.ACCOMMODATION_REQUIRED, "needRoom", false)
        );

        MappedAttendeeData mapped = mapper.map(mappings, Map.of(
                "name", "陈嘉宁",
                "phone", "138-0000-0001",
                "type", "VIP",
                "needRoom", "需要"
        ));

        assertThat(mapped.name()).isEqualTo("陈嘉宁");
        assertThat(mapped.phone()).isEqualTo("13800000001");
        assertThat(mapped.attendeeType()).isEqualTo(AttendeeType.VIP);
        assertThat(mapped.accommodationRequired()).isTrue();
    }

    @Test
    void rejectsMissingRequiredField() {
        List<TduckFieldMapping> mappings = List.of(mapping(AttendeeField.NAME, "name", true), mapping(AttendeeField.PHONE, "phone", true));

        assertThatThrownBy(() -> mapper.map(mappings, Map.of("name", "陈嘉宁")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必填映射字段缺失");
    }

    @Test
    void prefersTduckDisplayLabelForChoiceFields() {
        List<TduckFieldMapping> mappings = List.of(
                mapping(AttendeeField.NAME, "name", true),
                mapping(AttendeeField.PHONE, "phone", true),
                mapping(AttendeeField.ATTENDEE_TYPE, "attendeeType", false),
                mapping(AttendeeField.ACCOMMODATION_REQUIRED, "needRoom", false)
        );

        MappedAttendeeData mapped = mapper.map(mappings, Map.of(
                "name", "陈嘉宁",
                "phone", "13800000001",
                "attendeeType", 1,
                "attendeeTypelabel", "媒体",
                "needRoom", 2,
                "needRoomlabel", "需要"
        ));

        assertThat(mapped.attendeeType()).isEqualTo(AttendeeType.MEDIA);
        assertThat(mapped.accommodationRequired()).isTrue();
    }

    @Test
    void extensionMappingsDoNotOverwriteCoreAttendeeFields() {
        List<TduckFieldMapping> mappings = List.of(
                mapping(AttendeeField.NAME, "name", true),
                mapping(AttendeeField.PHONE, "phone", true),
                mapping(AttendeeField.DIETARY_REQUIREMENT, "diet", false),
                mapping(AttendeeField.ARRIVAL_FLIGHT_TRAIN, "arrivalNo", false),
                mapping(AttendeeField.INVOICE_TITLE, "invoiceTitle", false)
        );

        MappedAttendeeData mapped = mapper.map(mappings, Map.of(
                "name", "陈嘉宁",
                "phone", "13800000001",
                "diet", "清真餐",
                "arrivalNo", "CA1234",
                "invoiceTitle", "未来科技集团"
        ));

        /*
         * 扩展字段目前作为 TDuck 原始报名快照展示和导出，不写入 attendees 主表；
         * 这里确保新增标准字段不会误覆盖核心参会人数据。
         */
        assertThat(mapped.name()).isEqualTo("陈嘉宁");
        assertThat(mapped.phone()).isEqualTo("13800000001");
        assertThat(mapped.organization()).isNull();
        assertThat(mapped.remark()).isNull();
    }

    private TduckFieldMapping mapping(AttendeeField field, String key, boolean required) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setAttendeeField(field);
        mapping.setTduckFieldKey(key);
        mapping.setRequiredMapping(required);
        return mapping;
    }
}
