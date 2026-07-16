package com.meetingos.tduck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TduckWebhookParserTest {

    private final TduckWebhookParser parser = new TduckWebhookParser(new ObjectMapper());

    @Test
    void parsesExtValueFromFlattenedTduckWebhookPayload() {
        TduckWebhookRequest request = parser.parse("""
                {
                  "id": "data-1",
                  "formKey": "form-1",
                  "eventType": "form_data_add",
                  "extValue": "signed-click-token",
                  "name": "测试参会人",
                  "phone": "13800000001"
                }
                """);

        assertThat(request.dataId()).isEqualTo("data-1");
        assertThat(request.formKey()).isEqualTo("form-1");
        assertThat(request.extValue()).isEqualTo("signed-click-token");
        assertThat(request.data())
                .containsEntry("name", "测试参会人")
                .containsEntry("phone", "13800000001")
                .doesNotContainKeys("id", "formKey", "eventType", "extValue");
    }

    @Test
    void parsesExtValueFromNestedWebhookPayloadWithoutAddingItToMappedFields() {
        TduckWebhookRequest request = parser.parse("""
                {
                  "eventId": "event-1",
                  "eventType": "form_data_add",
                  "data": {
                    "id": "data-1",
                    "formKey": "form-1",
                    "extValue": "nested-click-token",
                    "originalData": {
                      "name": "测试参会人",
                      "phone": "13800000001"
                    }
                  }
                }
                """);

        assertThat(request.extValue()).isEqualTo("nested-click-token");
        assertThat(request.data())
                .containsEntry("name", "测试参会人")
                .containsEntry("phone", "13800000001")
                .doesNotContainKey("extValue");
    }

    @Test
    void rejectsJsonNullAsInvalidWebhookObject() {
        assertThatThrownBy(() -> parser.parse("null"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Webhook 请求体不是有效的 JSON 对象");
    }
}
