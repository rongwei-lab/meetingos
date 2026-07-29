package com.meetingos.publicapi;

import java.time.LocalDateTime;

/**
 * 自助签到关闭自动签到时的身份核验结果。ready 仅表示可以确认签到，不表示已经写入签到状态。
 */
public record PublicCheckinPreviewResponse(
        boolean ready,
        Long attendeeId,
        String attendeeName,
        String organization,
        String phone,
        LocalDateTime checkedInAt,
        String message
) {
}
