package com.meetingos.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InvitationEventRepository extends JpaRepository<InvitationEvent, Long> {
    Optional<InvitationEvent> findByShareLinkIdAndClientEventId(Long shareLinkId, String clientEventId);
    Optional<InvitationEvent> findByClickPublicId(String clickPublicId);

    @Query("""
            select coalesce(sum(case when e.eventType = :pageView then 1 else 0 end), 0),
                   count(distinct case when e.eventType = :pageView then e.visitorHash else null end),
                   coalesce(sum(case when e.eventType = :ctaClick then 1 else 0 end), 0),
                   count(distinct case when e.eventType = :ctaClick then e.visitorHash else null end)
            from InvitationEvent e
            where e.meeting.id = :meetingId
              and (:invitationId is null or e.invitation.id = :invitationId)
              and e.occurredAt between :from and :to
            """)
    List<Object[]> summarize(@Param("meetingId") Long meetingId,
                             @Param("invitationId") Long invitationId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("pageView") InvitationEventType pageView,
                             @Param("ctaClick") InvitationEventType ctaClick);

    @Query("""
            select function('date', e.occurredAt),
                   coalesce(sum(case when e.eventType = :pageView then 1 else 0 end), 0),
                   count(distinct case when e.eventType = :pageView then e.visitorHash else null end),
                   coalesce(sum(case when e.eventType = :ctaClick then 1 else 0 end), 0)
            from InvitationEvent e
            where e.meeting.id = :meetingId
              and (:invitationId is null or e.invitation.id = :invitationId)
              and e.occurredAt between :from and :to
            group by function('date', e.occurredAt)
            order by function('date', e.occurredAt)
            """)
    List<Object[]> summarizeDaily(@Param("meetingId") Long meetingId,
                                  @Param("invitationId") Long invitationId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("pageView") InvitationEventType pageView,
                                  @Param("ctaClick") InvitationEventType ctaClick);

    @Query("""
            select e.shareLink.id,
                   coalesce(sum(case when e.eventType = :pageView then 1 else 0 end), 0),
                   count(distinct case when e.eventType = :pageView then e.visitorHash else null end),
                   coalesce(sum(case when e.eventType = :ctaClick then 1 else 0 end), 0)
            from InvitationEvent e
            where e.meeting.id = :meetingId
              and (:invitationId is null or e.invitation.id = :invitationId)
              and e.occurredAt between :from and :to
            group by e.shareLink.id
            """)
    List<Object[]> summarizeByChannel(@Param("meetingId") Long meetingId,
                                      @Param("invitationId") Long invitationId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      @Param("pageView") InvitationEventType pageView,
                                      @Param("ctaClick") InvitationEventType ctaClick);

    @Modifying
    @Query("delete from InvitationEvent e where e.meeting.id = :meetingId")
    int deleteAllByMeetingId(@Param("meetingId") Long meetingId);
}
