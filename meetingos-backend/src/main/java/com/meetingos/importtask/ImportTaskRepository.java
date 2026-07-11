package com.meetingos.importtask;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportTaskRepository extends JpaRepository<ImportTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImportTask> findByMeetingIdAndImportTypeAndIdempotencyKey(Long meetingId,
                                                                       String importType,
                                                                       String idempotencyKey);

    Optional<ImportTask> findFirstByMeetingIdAndImportTypeAndStatusInOrderByCreatedAtDesc(
            Long meetingId,
            String importType,
            Collection<ImportTaskStatus> statuses
    );

    boolean existsByMeetingIdAndImportTypeAndStatusInAndIdNot(Long meetingId,
                                                               String importType,
                                                               Collection<ImportTaskStatus> statuses,
                                                               Long id);

    Optional<ImportTask> findByIdAndMeetingId(Long id, Long meetingId);

    Page<ImportTask> findByMeetingIdOrderByCreatedAtDesc(Long meetingId, Pageable pageable);

    Page<ImportTask> findByMeetingIdAndImportTypeInOrderByCreatedAtDesc(Long meetingId,
                                                                        Collection<String> importTypes,
                                                                        Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ImportTask task where task.id = :id and task.meeting.id = :meetingId")
    Optional<ImportTask> findByIdAndMeetingIdForUpdate(@Param("id") Long id, @Param("meetingId") Long meetingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ImportTask task where task.id = :id")
    Optional<ImportTask> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select task from ImportTask task
            where task.status in :statuses
              and task.expiresAt < :expiresAt
              and (task.inputFilePath is not null or task.failureFilePath is not null)
            """)
    List<ImportTask> findExpiredWithFiles(@Param("statuses") Collection<ImportTaskStatus> statuses,
                                          @Param("expiresAt") LocalDateTime expiresAt);

    List<ImportTask> findByStatusInAndUpdatedAtBefore(Collection<ImportTaskStatus> statuses, LocalDateTime updatedAt);

    List<ImportTask> findTop100ByStatusInAndStartedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<ImportTaskStatus> statuses,
            LocalDateTime updatedAt
    );
}
