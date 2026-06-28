package com.meetingos.publicapi;

import com.meetingos.meeting.MeetingStatus;

public record RegistrationInfo(
        Long meetingId,
        String meetingCode,
        String meetingName,
        MeetingStatus meetingStatus,
        boolean open,
        String message,
        String formName,
        String formKey,
        String registrationUrl
) {
}
