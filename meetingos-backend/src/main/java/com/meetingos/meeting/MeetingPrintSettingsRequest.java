package com.meetingos.meeting;

import jakarta.validation.constraints.Size;

import java.util.List;

public record MeetingPrintSettingsRequest(
        @Size(max = 500) String nameplateTemplateUrl,
        List<NameplateFieldSetting> nameplateFields
) {
}
