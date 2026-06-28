package com.meetingos.meeting;

import jakarta.validation.constraints.NotBlank;
import com.meetingos.publicapi.PublicQueryMatchMode;

import java.time.LocalDateTime;
import java.util.List;
import com.meetingos.publicapi.PublicQueryField;

public record MeetingRequest(
        @NotBlank String code,
        @NotBlank String name,
        String organizer,
        String coOrganizer,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime registrationStartTime,
        LocalDateTime registrationEndTime,
        String location,
        String venueName,
        Integer capacity,
        String description,
        String agenda,
        String coverImageUrl,
        String contactName,
        String contactPhone,
        MeetingStatus status,
        Boolean seatQueryEnabled,
        Boolean roomQueryEnabled,
        Boolean checkinEnabled,
        List<PublicQueryField> publicQueryFields,
        PublicQueryMatchMode publicQueryMatchMode
) {
}
