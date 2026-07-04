package com.meetingos.meeting;

/**
 * 公开入口的页面文案配置。
 *
 * <p>页面内容按 entry/combined/seat/room/dinner/checkin 等入口分别存储在 meetings.public_query_page_content JSON 中。
 * 这样后续新增入口时不需要继续给 meetings 表扩大量文案列，同时保留旧全局字段作为兼容默认值。</p>
 */
public record PublicQueryPageContent(
        String title,
        String description,
        String announcement,
        String publisher,
        String buttonText,
        String emptyMessage,
        String notice,
        Boolean checkinEnabled
) {
}
