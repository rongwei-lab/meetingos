package com.meetingos.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingInvitationVersionRepository extends JpaRepository<MeetingInvitationVersion, Long> {
    @Query("select coalesce(max(v.versionNo), 0) from MeetingInvitationVersion v where v.invitation.id = :invitationId")
    int findLatestVersionNo(@Param("invitationId") Long invitationId);
}
