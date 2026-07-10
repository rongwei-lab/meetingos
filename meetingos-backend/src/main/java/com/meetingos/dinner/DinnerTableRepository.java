package com.meetingos.dinner;

import com.meetingos.meeting.Meeting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DinnerTableRepository extends JpaRepository<DinnerTable, Long> {

    List<DinnerTable> findByMeetingIdOrderByTableNoAsc(Long meetingId);

    Optional<DinnerTable> findByMeetingIdAndTableNo(Long meetingId, String tableNo);

    @Query("select dinnerTable.meeting.id from DinnerTable dinnerTable where dinnerTable.id = :tableId")
    Optional<Long> findMeetingIdById(@Param("tableId") Long tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dinnerTable from DinnerTable dinnerTable where dinnerTable.id = :tableId")
    Optional<DinnerTable> findByIdForUpdate(@Param("tableId") Long tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select dinnerTable
            from DinnerTable dinnerTable
            where dinnerTable.meeting.id = :meetingId
              and dinnerTable.tableNo in :tableNos
            order by dinnerTable.tableNo asc
            """)
    List<DinnerTable> findByMeetingIdAndTableNoInForUpdate(@Param("meetingId") Long meetingId,
                                                           @Param("tableNos") Collection<String> tableNos);

    /*
     * 所有晚宴写操作先锁会议行，统一 meeting -> attendee/assignment -> table 的锁序。
     * 这会牺牲同一会议内不同桌的并行写吞吐，但消除了重命名、清除和批量导入之间的反向锁序；
     * 同时解决历史导入自动建桌时目标行尚不存在、无法直接锁住空查询的问题。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from Meeting meeting where meeting.id = :meetingId")
    Optional<Meeting> findMeetingByIdForUpdate(@Param("meetingId") Long meetingId);

    boolean existsByMeetingIdAndTableNo(Long meetingId, String tableNo);

    long countByMeetingId(Long meetingId);
}
