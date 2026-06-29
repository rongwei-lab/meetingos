package com.meetingos.dinner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DinnerAssignmentRepository extends JpaRepository<DinnerAssignment, Long> {

    List<DinnerAssignment> findByMeetingIdOrderByTableNoAscIdAsc(Long meetingId);

    Optional<DinnerAssignment> findByMeetingIdAndAttendeeId(Long meetingId, Long attendeeId);

    Optional<DinnerAssignment> findByAttendeeId(Long attendeeId);

    long countByMeetingIdAndTableNo(Long meetingId, String tableNo);

    void deleteByAttendeeId(Long attendeeId);

    long countByMeetingId(Long meetingId);
}
