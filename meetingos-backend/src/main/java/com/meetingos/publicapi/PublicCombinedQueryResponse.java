package com.meetingos.publicapi;

/**
 * 综合查询只匹配一次身份，各业务结果仍独立成功或失败，保持原页面的部分成功体验。
 */
public record PublicCombinedQueryResponse(
        Object seat,
        String seatError,
        Object room,
        String roomError,
        Object dinner,
        String dinnerError
) {
}
