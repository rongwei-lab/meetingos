package com.meetingos.attendee;

/**
 * 公开查询候选条件。所有字符串均由业务层按现有匹配规则预先归一化。
 */
public record AttendeePublicQueryCriteria(
        Long meetingId,
        boolean exact,
        ReviewStatus reviewStatus,
        String attendeeCode,
        String phone,
        String phoneLast4,
        String email,
        String name,
        String organization,
        String title,
        int offset,
        int limit
) {
}
