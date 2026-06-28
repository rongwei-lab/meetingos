package com.meetingos.tduck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TduckSyncLogRepository extends JpaRepository<TduckSyncLog, Long> {

    Optional<TduckSyncLog> findByEventId(String eventId);

    Optional<TduckSyncLog> findTopByEventIdAndStatusOrderByIdDesc(String eventId, SyncStatus status);

    Page<TduckSyncLog> findByMeetingId(Long meetingId, Pageable pageable);

    boolean existsByMeetingIdAndTduckDataIdIsNotNull(Long meetingId);
}
