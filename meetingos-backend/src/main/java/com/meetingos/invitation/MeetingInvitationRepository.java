package com.meetingos.invitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingInvitationRepository extends JpaRepository<MeetingInvitation, Long> {
    List<MeetingInvitation> findByMeetingIdOrderByUpdatedAtDesc(Long meetingId);

    Optional<MeetingInvitation> findByIdAndMeetingId(Long id, Long meetingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MeetingInvitation i where i.id = :id and i.meeting.id = :meetingId")
    Optional<MeetingInvitation> findForUpdate(@Param("id") Long id, @Param("meetingId") Long meetingId);
}
