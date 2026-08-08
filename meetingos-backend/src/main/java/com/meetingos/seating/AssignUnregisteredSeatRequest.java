package com.meetingos.seating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 现场未报名人员的座位录入请求。服务层会创建带来源标记的 Attendee，再建立常规座位分配。
 */
public record AssignUnregisteredSeatRequest(
        @NotNull Long seatId,
        @NotBlank @Size(max = 120) String attendeeName,
        @Size(max = 40) String phone
) {
}
