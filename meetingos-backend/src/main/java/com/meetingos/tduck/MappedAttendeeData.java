package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeType;

public record MappedAttendeeData(
        String name,
        String phone,
        String organization,
        String title,
        String identityNo,
        String email,
        AttendeeType attendeeType,
        boolean accommodationRequired,
        String roomNo,
        String roomRemark,
        String remark
) {
}
