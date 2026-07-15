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

    List<InvitationEvent> findByMeetingIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long meetingId, LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("delete from InvitationEvent e where e.meeting.id = :meetingId")
    int deleteAllByMeetingId(@Param("meetingId") Long meetingId);
}
