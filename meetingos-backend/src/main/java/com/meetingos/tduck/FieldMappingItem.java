package com.meetingos.tduck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FieldMappingItem(
        @NotNull AttendeeField attendeeField,
        @NotBlank String tduckFieldKey,
        String tduckFieldLabel,
        boolean requiredMapping
) {
}
