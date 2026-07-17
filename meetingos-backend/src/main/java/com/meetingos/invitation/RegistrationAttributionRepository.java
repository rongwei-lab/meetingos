package com.meetingos.invitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegistrationAttributionRepository extends JpaRepository<RegistrationAttribution, Long> {
    long countByInvitationId(Long invitationId);

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

    @Query("""
            select coalesce(sum(case when a.attributionStatus = :attributed then 1 else 0 end), 0),
                   coalesce(sum(case when a.attributionStatus = :attributed and a.active = true then 1 else 0 end), 0),
                   coalesce(sum(case when a.attributionStatus = :direct then 1 else 0 end), 0),
                   coalesce(sum(case when a.attributionStatus = :invalid then 1 else 0 end), 0),
                   coalesce(sum(case when a.attributionStatus = :legacy then 1 else 0 end), 0),
                   coalesce(sum(case when a.active = true then 1 else 0 end), 0)
            from RegistrationAttribution a
            where a.meeting.id = :meetingId
              and (:invitationId is null or a.invitation.id = :invitationId)
              and a.attributedAt between :from and :to
            """)
    List<Object[]> summarize(@Param("meetingId") Long meetingId,
                             @Param("invitationId") Long invitationId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("attributed") RegistrationAttributionStatus attributed,
                             @Param("direct") RegistrationAttributionStatus direct,
                             @Param("invalid") RegistrationAttributionStatus invalid,
                             @Param("legacy") RegistrationAttributionStatus legacy);

    @Query("""
            select function('date', a.attributedAt), count(a)
            from RegistrationAttribution a
            where a.meeting.id = :meetingId
              and (:invitationId is null or a.invitation.id = :invitationId)
              and a.attributionStatus = :attributed
              and a.attributedAt between :from and :to
            group by function('date', a.attributedAt)
            order by function('date', a.attributedAt)
            """)
    List<Object[]> summarizeAttributedDaily(@Param("meetingId") Long meetingId,
                                            @Param("invitationId") Long invitationId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("attributed") RegistrationAttributionStatus attributed);

    @Query("""
            select a.shareLink.id,
                   count(a),
                   coalesce(sum(case when a.active = true then 1 else 0 end), 0)
            from RegistrationAttribution a
            where a.meeting.id = :meetingId
              and (:invitationId is null or a.invitation.id = :invitationId)
              and a.attributionStatus = :attributed
              and a.shareLink is not null
              and a.attributedAt between :from and :to
            group by a.shareLink.id
            """)
    List<Object[]> summarizeAttributedByChannel(@Param("meetingId") Long meetingId,
                                                @Param("invitationId") Long invitationId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("attributed") RegistrationAttributionStatus attributed);

    @Query("""
            select a.id, a.attributionStatus, a.tduckDataId, attendee.id,
                   link.id, link.channelName, a.active, a.attributedAt
            from RegistrationAttribution a
            left join a.attendee attendee
            left join a.shareLink link
            where a.invitation.id = :invitationId
            order by a.attributedAt desc, a.id desc
            """)
    List<Object[]> findResponseRowsByInvitationId(@Param("invitationId") Long invitationId, Pageable pageable);
}
