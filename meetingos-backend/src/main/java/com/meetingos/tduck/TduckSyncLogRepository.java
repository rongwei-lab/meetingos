package com.meetingos.tduck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TduckSyncLogRepository extends JpaRepository<TduckSyncLog, Long> {

    List<TduckSyncLog> findAllByEventId(String eventId);

    /** 使用与数据库唯一约束相同的规范化键查询，兼容 V4 迁移前 event_id 带空格的历史日志。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TduckSyncLog> findBySuccessEventId(String successEventId);

    Page<TduckSyncLog> findByMeetingId(Long meetingId, Pageable pageable);

    Optional<TduckSyncLog> findByIdAndMeetingId(Long id, Long meetingId);

    boolean existsByMeetingIdAndTduckDataIdIsNotNull(Long meetingId);
}
