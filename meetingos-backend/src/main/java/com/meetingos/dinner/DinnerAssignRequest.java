package com.meetingos.dinner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DinnerAssignRequest(
        @NotNull Long attendeeId,
        @NotBlank String tableNo,
        String remark
) {
}
