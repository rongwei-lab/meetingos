package com.meetingos.tduck;

public record TduckBindingResponse(
        String formKey,
        String formName,
        String registrationUrl,
        TduckBindingStatus bindingStatus,
        WebhookStatus webhookStatus,
        TduckBindingValidationStatus validationStatus,
        String validationMessage,
        boolean rebindingLocked,
        String rebindingLockedReason
) {
    public static TduckBindingResponse valid(TduckFormBinding binding) {
        return of(binding, TduckBindingValidationStatus.VALID, "TDuck 表单可访问且已发布");
    }

    public static TduckBindingResponse valid(TduckFormBinding binding, String registrationUrl) {
        return of(binding, TduckBindingValidationStatus.VALID, "TDuck 表单可访问且已发布", registrationUrl);
    }

    public static TduckBindingResponse of(TduckFormBinding binding,
                                          TduckBindingValidationStatus validationStatus,
                                          String validationMessage) {
        return of(binding, validationStatus, validationMessage, binding.getRegistrationUrl());
    }

    public static TduckBindingResponse of(TduckFormBinding binding,
                                          TduckBindingValidationStatus validationStatus,
                                          String validationMessage,
                                          String registrationUrl) {
        return new TduckBindingResponse(
                binding.getFormKey(),
                binding.getFormName(),
                registrationUrl,
                binding.getBindingStatus(),
                binding.getWebhookStatus(),
                validationStatus,
                validationMessage,
                false,
                null
        );
    }

    public TduckBindingResponse withRebindingLock(boolean locked, String reason) {
        return new TduckBindingResponse(
                formKey,
                formName,
                registrationUrl,
                bindingStatus,
                webhookStatus,
                validationStatus,
                validationMessage,
                locked,
                reason
        );
    }
}
