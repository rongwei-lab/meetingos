package com.meetingos.tduck;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateFieldMappingsRequest(
        @NotEmpty List<@Valid FieldMappingItem> mappings
) {
}
