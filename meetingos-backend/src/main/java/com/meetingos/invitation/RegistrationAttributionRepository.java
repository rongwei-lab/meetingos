package com.meetingos.invitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegistrationAttributionRepository extends JpaRepository<RegistrationAttribution, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from RegistrationAttribution a
            where a.meeting.id = :meetingId and a.tduckDataId = :dataId
            """)
    Optional<RegistrationAttribution> findForUpdate(@Param("meetingId") Long meetingId,
                                                     @Param("dataId") String dataId);

    @Modifying
    @Query("""
            update RegistrationAttribution a set a.active = :active
            where a.meeting.id = :meetingId and a.attendee.id = :attendeeId
            """)
    int updateActiveByMeetingIdAndAttendeeId(@Param("meetingId") Long meetingId,
                                              @Param("attendeeId") Long attendeeId,
                                              @Param("active") boolean active);

    List<RegistrationAttribution> findByMeetingIdAndAttributedAtBetweenOrderByAttributedAtAsc(
            Long meetingId, LocalDateTime from, LocalDateTime to);

    List<RegistrationAttribution> findByInvitationIdOrderByAttributedAtDesc(Long invitationId);
}
