package com.meetingos.invitation;

import com.meetingos.meeting.Meeting;

import java.time.LocalDateTime;

public record InvitationMeetingSnapshot(
        Long id,
        String code,
        String name,
        String organizer,
        String coOrganizer,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime registrationStartTime,
        LocalDateTime registrationEndTime,
        String location,
        String venueName,
        String description,
        String agenda,
        String coverImageUrl,
        String contactName,
        String contactPhone
) {
    public static InvitationMeetingSnapshot from(Meeting meeting) {
        return new InvitationMeetingSnapshot(
                meeting.getId(), meeting.getCode(), meeting.getName(), meeting.getOrganizer(), meeting.getCoOrganizer(),
                meeting.getStartTime(), meeting.getEndTime(), meeting.getRegistrationStartTime(), meeting.getRegistrationEndTime(),
                meeting.getLocation(), meeting.getVenueName(), meeting.getDescription(), meeting.getAgenda(),
                meeting.getCoverImageUrl(), meeting.getContactName(), meeting.getContactPhone()
        );
    }
}
