package com.meetingos.exporttask;

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

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {

    Page<ExportTask> findByMeeting_IdOrderByCreatedAtDesc(Long meetingId, Pageable pageable);

    Page<ExportTask> findByMeeting_IdAndExportTypeInOrderByCreatedAtDesc(Long meetingId,
                                                                         Collection<String> exportTypes,
                                                                         Pageable pageable);

    Optional<ExportTask> findByIdAndMeeting_Id(Long id, Long meetingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ExportTask task where task.id = :id")
    Optional<ExportTask> findByIdForUpdate(@Param("id") Long id);

    boolean existsByMeeting_IdAndExportTypeAndStatusIn(Long meetingId,
                                                       String exportType,
                                                       Collection<ExportTaskStatus> statuses);

    List<ExportTask> findByStatusInAndExpiresAtLessThanEqual(Collection<ExportTaskStatus> statuses,
                                                             LocalDateTime expiresAt);

    @Query("""
            select task.id
            from ExportTask task
            where task.status = :status and task.updatedAt < :updatedAt
            order by task.updatedAt asc, task.id asc
            """)
    List<Long> findRecoverableIds(@Param("status") ExportTaskStatus status,
                                  @Param("updatedAt") LocalDateTime updatedAt,
                                  Pageable pageable);

    Optional<ExportTask> findFirstByMeeting_IdAndExportTypeAndStatusOrderByCreatedAtDesc(Long meetingId,
                                                                                         String exportType,
                                                                                         ExportTaskStatus status);
}
