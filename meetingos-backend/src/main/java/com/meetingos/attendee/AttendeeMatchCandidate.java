package com.meetingos.attendee;

/**
 * 候选阶段只读取判断歧义所需字段，避免加载参会人的原始报名 JSON。
 */
public record AttendeeMatchCandidate(Long id, ReviewStatus reviewStatus) {
}
