package com.meetingos.meeting;

import java.util.List;

public record MeetingPrintSettingsResponse(
        Long meetingId,
        String nameplateTemplateUrl,
        List<NameplateFieldSetting> nameplateFields
) {
}
