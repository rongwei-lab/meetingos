package com.meetingos.publicapi;

import com.meetingos.attendee.Attendee;
import com.meetingos.meeting.Meeting;

public record PublicAttendeeMatch(
        Meeting meeting,
        Attendee attendee
) {
}
