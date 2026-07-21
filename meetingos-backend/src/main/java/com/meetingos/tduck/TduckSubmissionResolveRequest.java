package com.meetingos.tduck;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TduckSubmissionResolveRequest(
        @NotNull Long meetingId,
        @NotNull TduckSubmissionResolution resolution,
        @Size(max = 500) String note
) {
}
