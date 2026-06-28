package com.meetingos.tduck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BindFormRequest(
        @NotNull Long meetingId,
        @NotBlank String formKey,
        String formName,
        String registrationUrl,
        TduckBindingStatus bindingStatus,
        WebhookStatus webhookStatus,
        Boolean replaceExisting,
        String confirmMeetingCode,
        String changeReason
) {
}
