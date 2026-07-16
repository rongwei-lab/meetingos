package com.meetingos.operationlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    /**
     * 返回当前可见日志的最大 ID，用于把一次分页浏览冻结在同一批数据上。
     * 空表返回 0，前端仍可在响应头中保存这个有效快照。
     */
    @Query("select coalesce(max(log.id), 0) from OperationLog log")
    long findMaxId();

    Page<OperationLog> findByIdLessThanEqual(long snapshotId, Pageable pageable);

    Page<OperationLog> findByActionContainingIgnoreCaseAndIdLessThanEqual(
            String action,
            long snapshotId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OperationLog log where log.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
