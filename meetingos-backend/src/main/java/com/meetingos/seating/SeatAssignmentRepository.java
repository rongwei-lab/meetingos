package com.meetingos.seating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    Optional<SeatAssignment> findByAttendeeId(Long attendeeId);

    Optional<SeatAssignment> findBySeatId(Long seatId);

    List<SeatAssignment> findByMeetingId(Long meetingId);

    long countByMeetingId(Long meetingId);

    void deleteByAttendeeId(Long attendeeId);

    Set<SeatAssignment> findBySeatIdIn(Collection<Long> seatIds);

    boolean existsBySeatIdIn(Collection<Long> seatIds);
}
