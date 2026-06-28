package com.meetingos.publicapi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PublicQueryHistoryRepository extends JpaRepository<PublicQueryHistory, Long> {

    @Query("""
            select h from PublicQueryHistory h
            where h.meeting.id = :meetingId
              and (:queryType is null or h.queryType = :queryType)
              and (:queryStatus is null or h.queryStatus = :queryStatus)
              and (:keyword is null
                   or lower(coalesce(h.queryText, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(h.attendeeName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(h.attendeePhone, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(h.resultSummary, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(h.errorMessage, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<PublicQueryHistory> search(@Param("meetingId") Long meetingId,
                                    @Param("queryType") PublicQueryType queryType,
                                    @Param("queryStatus") PublicQueryStatus queryStatus,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);

    @Query("""
            select h from PublicQueryHistory h
            where h.meeting.id = :meetingId
            order by h.createdAt desc, h.id desc
            """)
    List<PublicQueryHistory> findAllByMeetingIdForSummary(@Param("meetingId") Long meetingId);

    @Query("""
            select distinct attendee.id from PublicQueryHistory h
              join h.attendee attendee
            where h.meeting.id = :meetingId
              and h.queryType = :queryType
            """)
    List<Long> findQueriedAttendeeIds(@Param("meetingId") Long meetingId,
                                      @Param("queryType") PublicQueryType queryType);
}
