package com.meetingos.checkin;

public record CheckinCodePayload(
        Long meetingId,
        String attendeeCode
) {
}
