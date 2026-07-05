package com.meetingos.checkin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {

    Page<CheckinRecord> findByMeetingId(Long meetingId, Pageable pageable);

    @Query(value = """
            select record
            from CheckinRecord record
            left join fetch record.attendee
            where record.meeting.id = :meetingId
            """,
            countQuery = """
                    select count(record)
                    from CheckinRecord record
                    where record.meeting.id = :meetingId
                    """)
    Page<CheckinRecord> findPageWithAttendeeByMeetingId(@Param("meetingId") Long meetingId, Pageable pageable);

    List<CheckinRecord> findByMeetingIdOrderByIdAsc(Long meetingId);

    @Query("""
            select record
            from CheckinRecord record
            left join fetch record.attendee
            where record.meeting.id = :meetingId
            order by record.id asc
            """)
    List<CheckinRecord> findWithAttendeeByMeetingIdOrderByIdAsc(@Param("meetingId") Long meetingId);

    @Query("select coalesce(c.entrance, '未填写'), count(c) from CheckinRecord c where c.meeting.id = :meetingId and c.resultStatus = 'SUCCESS' group by coalesce(c.entrance, '未填写')")
    List<Object[]> countSuccessByEntrance(@Param("meetingId") Long meetingId);

    @Query("select c.checkinType, count(c) from CheckinRecord c where c.meeting.id = :meetingId and c.resultStatus = 'SUCCESS' group by c.checkinType")
    List<Object[]> countSuccessByType(@Param("meetingId") Long meetingId);

    long countByMeetingIdAndResultStatusAndCheckedInAtBetween(Long meetingId,
                                                              CheckinResultStatus resultStatus,
                                                              LocalDateTime start,
                                                              LocalDateTime end);

    @Query("""
            select function('date', c.checkedInAt), count(c)
            from CheckinRecord c
            where c.meeting.id = :meetingId
              and c.resultStatus = 'SUCCESS'
              and c.checkedInAt >= :start
              and c.checkedInAt < :end
            group by function('date', c.checkedInAt)
            order by function('date', c.checkedInAt)
            """)
    List<Object[]> countSuccessByDate(@Param("meetingId") Long meetingId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);
}
