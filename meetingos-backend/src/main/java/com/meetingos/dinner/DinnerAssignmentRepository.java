package com.meetingos.dinner;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DinnerAssignmentRepository extends JpaRepository<DinnerAssignment, Long> {

    @Query("select attendee.meeting.id from Attendee attendee where attendee.id = :attendeeId")
    Optional<Long> findMeetingIdByAttendeeId(@Param("attendeeId") Long attendeeId);

    List<DinnerAssignment> findByMeetingIdOrderByTableNoAscIdAsc(Long meetingId);

    List<DinnerAssignment> findByMeetingIdAndTableNo(Long meetingId, String tableNo);

    @Query("""
            select assignment
            from DinnerAssignment assignment
            join fetch assignment.attendee
            where assignment.meeting.id = :meetingId
            order by assignment.tableNo asc, assignment.id asc
            """)
    List<DinnerAssignment> findWithAttendeeByMeetingIdOrderByTableNoAscIdAsc(@Param("meetingId") Long meetingId);

    @Query("""
            select assignment.attendee.id from DinnerAssignment assignment
            where assignment.meeting.id = :meetingId
              and assignment.attendee.id in :attendeeIds
            """)
    List<Long> findAssignedAttendeeIdsByMeetingIdAndAttendeeIdIn(@Param("meetingId") Long meetingId,
                                                                 @Param("attendeeIds") Collection<Long> attendeeIds);

    Optional<DinnerAssignment> findByMeetingIdAndAttendeeId(Long meetingId, Long attendeeId);

    Optional<DinnerAssignment> findByAttendeeId(Long attendeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from DinnerAssignment assignment
            join fetch assignment.attendee
            where assignment.attendee.id = :attendeeId
            """)
    Optional<DinnerAssignment> findByAttendeeIdForUpdate(@Param("attendeeId") Long attendeeId);

    boolean existsByAttendeeId(Long attendeeId);

    long countByMeetingIdAndTableNo(Long meetingId, String tableNo);

    @Query("""
            select assignment.tableNo, count(assignment)
            from DinnerAssignment assignment
            where assignment.meeting.id = :meetingId
            group by assignment.tableNo
            """)
    List<Object[]> countByMeetingIdGroupByTableNo(@Param("meetingId") Long meetingId);

    void deleteByAttendeeId(Long attendeeId);

    long countByMeetingId(Long meetingId);

    @Query("""
            select assignment.meeting.id, count(assignment)
            from DinnerAssignment assignment
            where assignment.meeting.id in :meetingIds
            group by assignment.meeting.id
            """)
    List<Object[]> countByMeetingIds(@Param("meetingIds") Collection<Long> meetingIds);
}
