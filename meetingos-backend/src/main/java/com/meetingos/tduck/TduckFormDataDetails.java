package com.meetingos.tduck;

import java.util.Map;

record TduckFormDataDetails(
        String formKey,
        String dataId,
        String extValue,
        Map<String, Object> formData,
        Map<String, Object> raw
) {
}
