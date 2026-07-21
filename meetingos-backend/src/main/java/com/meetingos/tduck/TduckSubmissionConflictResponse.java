package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.common.SensitiveDataMasker;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record TduckSubmissionConflictResponse(
        Long id,
        Long meetingId,
        Long attendeeId,
        String currentTduckDataId,
        String incomingTduckDataId,
        TduckSubmissionStatus status,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        TduckSubmissionParticipantResponse existing,
        TduckSubmissionParticipantResponse incoming
) {

    public static TduckSubmissionConflictResponse from(TduckSubmission submission) {
        Attendee attendee = submission.getAttendee();
        TduckSubmissionParticipantResponse existing = new TduckSubmissionParticipantResponse(
                attendee.getName(),
                SensitiveDataMasker.maskPhone(attendee.getPhone()),
                attendee.getOrganization(),
                attendee.getTitle(),
                SensitiveDataMasker.maskIdentityNo(attendee.getIdentityNo()),
                SensitiveDataMasker.maskEmail(attendee.getEmail()),
                attendee.getAttendeeType(),
                attendee.isAccommodationRequired(),
                attendee.getRemark(),
                maskedBusinessFormValues(attendee.getRawDataSnapshot())
        );
        TduckSubmissionParticipantResponse incoming = new TduckSubmissionParticipantResponse(
                submission.getName(),
                SensitiveDataMasker.maskPhone(submission.getPhone()),
                submission.getOrganization(),
                submission.getTitle(),
                SensitiveDataMasker.maskIdentityNo(submission.getIdentityNo()),
                SensitiveDataMasker.maskEmail(submission.getEmail()),
                submission.getAttendeeType(),
                submission.isAccommodationRequired(),
                submission.getRemark(),
                maskedBusinessFormValues(submission.getRawDataSnapshot())
        );
        return new TduckSubmissionConflictResponse(
                submission.getId(),
                submission.getMeeting().getId(),
                attendee.getId(),
                attendee.getTduckDataId(),
                submission.getTduckDataId(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getCreatedAt(),
                existing,
                incoming
        );
    }

    private static Map<String, Object> maskedBusinessFormValues(Map<String, Object> rawValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return values;
        }
        rawValues.forEach((key, value) -> {
            if (!TduckSubmissionFields.isMetaField(key)) {
                values.put(key, value);
            }
        });
        return SensitiveDataMasker.maskMap(values);
    }
}
