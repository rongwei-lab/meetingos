package com.meetingos.tduck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TduckSubmissionRepository extends JpaRepository<TduckSubmission, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TduckSubmission s where s.meeting.id = :meetingId and s.tduckDataId = :dataId")
    Optional<TduckSubmission> findByMeetingIdAndTduckDataIdForUpdate(@Param("meetingId") Long meetingId,
                                                                       @Param("dataId") String dataId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TduckSubmission s where s.id = :id")
    Optional<TduckSubmission> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TduckSubmission s where s.attendee.id = :attendeeId and s.status = :status order by s.id asc")
    List<TduckSubmission> findByAttendeeIdAndStatusForUpdate(@Param("attendeeId") Long attendeeId,
                                                               @Param("status") TduckSubmissionStatus status);

    long countByAttendeeIdAndStatus(Long attendeeId, TduckSubmissionStatus status);

    Page<TduckSubmission> findByMeetingIdAndStatus(Long meetingId, TduckSubmissionStatus status, Pageable pageable);
}
