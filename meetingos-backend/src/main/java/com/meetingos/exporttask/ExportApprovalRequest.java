package com.meetingos.exporttask;

import jakarta.validation.constraints.Size;

public record ExportApprovalRequest(
        boolean approved,
        @Size(max = 500) String message
) {
}
