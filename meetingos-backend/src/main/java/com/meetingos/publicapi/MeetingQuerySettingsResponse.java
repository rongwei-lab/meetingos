package com.meetingos.publicapi;

import java.time.LocalDateTime;
import java.util.List;

public record MeetingQuerySettingsResponse(
        Long meetingId,
        String meetingCode,
        String meetingName,
        boolean seatQueryEnabled,
        boolean roomQueryEnabled,
        boolean checkinEnabled,
        boolean queryPaused,
        List<PublicQueryField> fields,
        PublicQueryMatchMode matchMode,
        String pageTitle,
        String pageDescription,
        String announcement,
        String publisher,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String buttonText,
        String emptyMessage,
        String seatNotice,
        String roomNotice,
        String themeColor,
        String logoUrl,
        String backgroundUrl,
        String resultLayout,
        List<PublicQueryResultField> visibleResultFields,
        List<PublicQueryResultField> maskedResultFields,
        List<PublicQueryResultField> highlightResultFields,
        List<PublicQueryResultField> seatVisibleResultFields,
        List<PublicQueryResultField> seatMaskedResultFields,
        List<PublicQueryResultField> seatHighlightResultFields,
        List<PublicQueryResultField> roomVisibleResultFields,
        List<PublicQueryResultField> roomMaskedResultFields,
        List<PublicQueryResultField> roomHighlightResultFields,
        boolean captchaEnabled,
        List<QueryFieldOption> fieldOptions,
        List<QueryResultFieldOption> resultFieldOptions
) {
}
