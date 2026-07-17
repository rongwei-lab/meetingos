package com.meetingos.tduck;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TduckPullSyncTaskRepository extends JpaRepository<TduckPullSyncTask, Long> {

    Optional<TduckPullSyncTask> findByIdAndMeetingId(Long id, Long meetingId);

    Optional<TduckPullSyncTask> findByMeetingIdAndIdempotencyKey(Long meetingId, String idempotencyKey);

    Optional<TduckPullSyncTask> findFirstByMeetingIdAndStatusInOrderByCreatedAtDesc(
            Long meetingId,
            Collection<TduckPullSyncTaskStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from TduckPullSyncTask task where task.id = :id")
    Optional<TduckPullSyncTask> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select task.id from TduckPullSyncTask task
            where task.status = :pending
               or (task.status = :running and (task.leaseUntil is null or task.leaseUntil < :now))
            order by task.updatedAt asc
            """)
    List<Long> findRecoverableTaskIds(@Param("pending") TduckPullSyncTaskStatus pending,
                                      @Param("running") TduckPullSyncTaskStatus running,
                                      @Param("now") LocalDateTime now,
                                      org.springframework.data.domain.Pageable pageable);
}
