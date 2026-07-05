package com.meetingos.seating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    Optional<SeatAssignment> findByAttendeeId(Long attendeeId);

    Optional<SeatAssignment> findBySeatId(Long seatId);

    boolean existsByAttendeeId(Long attendeeId);

    List<SeatAssignment> findByMeetingId(Long meetingId);

    @Query("""
            select assignment
            from SeatAssignment assignment
            join fetch assignment.attendee
            join fetch assignment.seat
            where assignment.meeting.id = :meetingId
            """)
    List<SeatAssignment> findWithAttendeeAndSeatByMeetingId(@Param("meetingId") Long meetingId);

    @Query("""
            select assignment
            from SeatAssignment assignment
            join fetch assignment.attendee
            join fetch assignment.seat
            where assignment.meeting.id = :meetingId
              and assignment.attendee.id in :attendeeIds
            """)
    List<SeatAssignment> findWithAttendeeAndSeatByMeetingIdAndAttendeeIdIn(@Param("meetingId") Long meetingId,
                                                                            @Param("attendeeIds") Collection<Long> attendeeIds);

    long countByMeetingId(Long meetingId);

    @Query("""
            select assignment.meeting.id, count(assignment)
            from SeatAssignment assignment
            where assignment.meeting.id in :meetingIds
            group by assignment.meeting.id
            """)
    List<Object[]> countByMeetingIds(@Param("meetingIds") Collection<Long> meetingIds);

    void deleteByAttendeeId(Long attendeeId);

    Set<SeatAssignment> findBySeatIdIn(Collection<Long> seatIds);

    boolean existsBySeatIdIn(Collection<Long> seatIds);
}
