package com.meetingos.tduck;

public record TduckFormOption(
        String formKey,
        String formName,
        String status,
        boolean published,
        int itemCount,
        String registrationUrl
) {
}
