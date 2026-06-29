package com.meetingos.dinner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DinnerTableRepository extends JpaRepository<DinnerTable, Long> {

    List<DinnerTable> findByMeetingIdOrderByTableNoAsc(Long meetingId);

    Optional<DinnerTable> findByMeetingIdAndTableNo(Long meetingId, String tableNo);

    boolean existsByMeetingIdAndTableNo(Long meetingId, String tableNo);

    long countByMeetingId(Long meetingId);
}
