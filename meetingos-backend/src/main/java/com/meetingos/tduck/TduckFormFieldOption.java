package com.meetingos.tduck;

public record TduckFormFieldOption(
        String fieldKey,
        String label,
        String type,
        boolean required
) {
}
