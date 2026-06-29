package com.meetingos.dinner;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DinnerTableRequest(
        @NotNull Long meetingId,
        @NotBlank String tableNo,
        @NotNull @Min(1) Integer capacity,
        String remark
) {
}
