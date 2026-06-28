package com.meetingos.attendee;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReviewRequest(
        @NotNull ReviewStatus reviewStatus,
        String reason,
        String remark,
        List<Long> attendeeIds
) {
}
