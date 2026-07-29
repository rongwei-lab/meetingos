package com.meetingos.publicapi;

import com.meetingos.checkin.CheckinResultStatus;

import java.time.LocalDateTime;

/**
 * 公开自助签到结果。单位来自已完成身份核验的参会人记录，仅用于签到结果页确认身份，
 * 与后台签到接口的返回模型保持隔离，避免扩大后台接口的响应范围。
 */
public record PublicCheckinResponse(
        CheckinResultStatus status,
        Long attendeeId,
        String attendeeName,
        String organization,
        String phone,
        LocalDateTime checkedInAt,
        String entrance,
        String operator,
        String message
) {
}
