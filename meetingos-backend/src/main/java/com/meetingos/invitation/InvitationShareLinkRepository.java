package com.meetingos.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvitationShareLinkRepository extends JpaRepository<InvitationShareLink, Long> {
    List<InvitationShareLink> findByInvitationIdOrderByCreatedAtDesc(Long invitationId);
    List<InvitationShareLink> findByMeetingIdOrderByCreatedAtAsc(Long meetingId);
    int countByInvitationId(Long invitationId);
    Optional<InvitationShareLink> findByIdAndInvitationId(Long id, Long invitationId);

    @Query("""
            select l from InvitationShareLink l
              join fetch l.meeting
              join fetch l.invitation i
              left join fetch l.currentVersion
            where l.shareToken = :token
            """)
    Optional<InvitationShareLink> findPublicLink(@Param("token") String token);

    @Modifying
    @Query("update InvitationShareLink l set l.currentVersion = :version where l.invitation.id = :invitationId")
    int pointAllToVersion(@Param("invitationId") Long invitationId,
                          @Param("version") MeetingInvitationVersion version);

    @Modifying
    @Query("update InvitationShareLink l set l.enabled = false where l.invitation.id = :invitationId")
    int disableAll(@Param("invitationId") Long invitationId);
}
