package com.meetingos.publicapi;

import com.meetingos.attendee.ReviewStatus;

public record AdminQueryCandidate(
        Long attendeeId,
        String attendeeName,
        String phone,
        String organization,
        String title,
        ReviewStatus reviewStatus,
        boolean unregistered,
        String seatLabel,
        String roomNo,
        String roomRemark
) {
}
