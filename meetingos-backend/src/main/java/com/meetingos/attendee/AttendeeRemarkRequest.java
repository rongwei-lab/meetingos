package com.meetingos.attendee;

import jakarta.validation.constraints.Size;

/**
 * 参会人备注的专用更新请求。
 *
 * 使用独立请求避免前端为了修改备注而回传可能已脱敏的完整参会人对象，进而误写手机号、
 * 证件号等敏感字段。{@code null} 或空字符串均允许，用于清空已有备注。
 */
public record AttendeeRemarkRequest(
        @Size(max = 500, message = "备注不能超过500个字符") String remark
) {
}
