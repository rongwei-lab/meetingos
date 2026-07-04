package com.meetingos.publicapi;

import com.meetingos.meeting.PublicQueryPageContent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MeetingQuerySettingsResponse(
        Long meetingId,
        String meetingCode,
        String publicQueryToken,
        String meetingName,
        boolean seatQueryEnabled,
        boolean roomQueryEnabled,
        boolean dinnerQueryEnabled,
        boolean checkinEnabled,
        boolean selfCheckinEnabled,
        boolean queryPaused,
        List<PublicQueryField> fields,
        PublicQueryMatchMode matchMode,
        boolean allowMultipleResults,
        List<PublicQueryType> combinedQueryTypes,
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
        String dinnerNotice,
        Map<String, PublicQueryPageContent> pageContents,
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
        List<PublicQueryResultField> dinnerVisibleResultFields,
        List<PublicQueryResultField> dinnerMaskedResultFields,
        List<PublicQueryResultField> dinnerHighlightResultFields,
        boolean captchaEnabled,
        List<QueryFieldOption> fieldOptions,
        List<QueryResultFieldOption> resultFieldOptions
) {
}
