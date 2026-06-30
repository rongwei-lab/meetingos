package com.meetingos.attendee;

import com.meetingos.common.SensitiveDataMasker;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record AttendeeResponse(
        Long id,
        Long meetingId,
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
        String seatLabel,
        String remark,
        String tags,
        ReviewStatus reviewStatus,
        String reviewReason,
        String reviewRemark,
        LocalDateTime reviewedAt,
        String reviewedBy,
        LocalDateTime registeredAt,
        CheckinStatus checkinStatus,
        LocalDateTime checkedInAt,
        String checkinEntrance,
        String checkinOperator,
        String attendeeCode,
        String tduckDataId,
        boolean seatAssigned,
        boolean roomAssigned,
        boolean dinnerAssigned,
        boolean seatQueried,
        boolean roomQueried,
        boolean dinnerQueried,
        Map<String, Object> formValues
) {

    public static AttendeeResponse masked(Attendee attendee) {
        return from(attendee, true, Set.of(), Set.of(), Set.of());
    }

    public static AttendeeResponse raw(Attendee attendee) {
        return from(attendee, false, Set.of(), Set.of(), Set.of());
    }

    public static AttendeeResponse masked(Attendee attendee, Set<Long> seatQueriedAttendeeIds, Set<Long> roomQueriedAttendeeIds) {
        return from(attendee, true, seatQueriedAttendeeIds, roomQueriedAttendeeIds, Set.of());
    }

    public static AttendeeResponse raw(Attendee attendee, Set<Long> seatQueriedAttendeeIds, Set<Long> roomQueriedAttendeeIds) {
        return from(attendee, false, seatQueriedAttendeeIds, roomQueriedAttendeeIds, Set.of());
    }

    public static AttendeeResponse masked(Attendee attendee, String seatLabel, Set<Long> seatQueriedAttendeeIds, Set<Long> roomQueriedAttendeeIds) {
        return from(attendee, true, seatLabel, false, seatQueriedAttendeeIds, roomQueriedAttendeeIds, Set.of());
    }

    public static AttendeeResponse raw(Attendee attendee, String seatLabel, Set<Long> seatQueriedAttendeeIds, Set<Long> roomQueriedAttendeeIds) {
        return from(attendee, false, seatLabel, false, seatQueriedAttendeeIds, roomQueriedAttendeeIds, Set.of());
    }

    public static AttendeeResponse masked(Attendee attendee,
                                          String seatLabel,
                                          boolean dinnerAssigned,
                                          Set<Long> seatQueriedAttendeeIds,
                                          Set<Long> roomQueriedAttendeeIds,
                                          Set<Long> dinnerQueriedAttendeeIds) {
        return from(attendee, true, seatLabel, dinnerAssigned, seatQueriedAttendeeIds, roomQueriedAttendeeIds, dinnerQueriedAttendeeIds);
    }

    public static AttendeeResponse raw(Attendee attendee,
                                       String seatLabel,
                                       boolean dinnerAssigned,
                                       Set<Long> seatQueriedAttendeeIds,
                                       Set<Long> roomQueriedAttendeeIds,
                                       Set<Long> dinnerQueriedAttendeeIds) {
        return from(attendee, false, seatLabel, dinnerAssigned, seatQueriedAttendeeIds, roomQueriedAttendeeIds, dinnerQueriedAttendeeIds);
    }

    private static AttendeeResponse from(Attendee attendee,
                                         boolean masked,
                                         Set<Long> seatQueriedAttendeeIds,
                                         Set<Long> roomQueriedAttendeeIds,
                                         Set<Long> dinnerQueriedAttendeeIds) {
        return from(attendee, masked, null, false, seatQueriedAttendeeIds, roomQueriedAttendeeIds, dinnerQueriedAttendeeIds);
    }

    private static AttendeeResponse from(Attendee attendee,
                                         boolean masked,
                                         String seatLabel,
                                         boolean dinnerAssigned,
                                         Set<Long> seatQueriedAttendeeIds,
                                         Set<Long> roomQueriedAttendeeIds,
                                         Set<Long> dinnerQueriedAttendeeIds) {
        String phone = attendee.getPhone();
        String identityNo = attendee.getIdentityNo();
        String email = attendee.getEmail();
        Map<String, Object> formValues = attendee.getRawDataSnapshot() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(attendee.getRawDataSnapshot());
        if (masked) {
            phone = SensitiveDataMasker.maskPhone(phone);
            identityNo = SensitiveDataMasker.maskIdentityNo(identityNo);
            email = SensitiveDataMasker.maskEmail(email);
            formValues = SensitiveDataMasker.maskMap(formValues);
        }
        Long meetingId = attendee.getMeeting() == null ? null : attendee.getMeeting().getId();
        return new AttendeeResponse(
                attendee.getId(),
                meetingId,
                attendee.getName(),
                phone,
                attendee.getOrganization(),
                attendee.getTitle(),
                identityNo,
                email,
                attendee.getAttendeeType(),
                attendee.isAccommodationRequired(),
                attendee.getRoomNo(),
                attendee.getRoomRemark(),
                seatLabel,
                attendee.getRemark(),
                attendee.getTags(),
                attendee.getReviewStatus(),
                attendee.getReviewReason(),
                attendee.getReviewRemark(),
                attendee.getReviewedAt(),
                attendee.getReviewedBy(),
                attendee.getRegisteredAt(),
                attendee.getCheckinStatus(),
                attendee.getCheckedInAt(),
                attendee.getCheckinEntrance(),
                attendee.getCheckinOperator(),
                attendee.getAttendeeCode(),
                attendee.getTduckDataId(),
                seatLabel != null && !seatLabel.isBlank(),
                attendee.getRoomNo() != null && !attendee.getRoomNo().isBlank(),
                dinnerAssigned,
                containsId(seatQueriedAttendeeIds, attendee.getId()),
                containsId(roomQueriedAttendeeIds, attendee.getId()),
                containsId(dinnerQueriedAttendeeIds, attendee.getId()),
                formValues
        );
    }

    private static boolean containsId(Set<Long> ids, Long id) {
        return id != null && ids != null && ids.contains(id);
    }
}
