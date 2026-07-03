package com.meetingos.tduck;

import jakarta.validation.constraints.NotNull;

public record TduckRegistrationStatusRequest(
        @NotNull(message = "报名入口开关不能为空")
        Boolean enabled
) {
}
