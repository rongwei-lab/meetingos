package com.meetingos.tduck;

import java.util.Map;

public record TduckBrowserSessionResponse(
        String token,
        Map<String, Object> userInfo
) {
}
