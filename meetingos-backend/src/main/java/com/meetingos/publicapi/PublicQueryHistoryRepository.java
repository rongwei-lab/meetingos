package com.meetingos.publicapi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query(value = """
            select ranked.group_key,
                   ranked.group_type,
                   ranked.attendee_id,
                   ranked.attendee_name,
                   ranked.attendee_phone,
                   ranked.failure_reason,
                   ranked.total_count,
                   ranked.success_count,
                   ranked.failed_count,
                   ranked.created_at,
                   ranked.latest_result
            from (
                select base.*,
                       count(*) over (partition by base.group_key) as total_count,
                       sum(case when base.query_status = 'SUCCESS' then 1 else 0 end) over (partition by base.group_key) as success_count,
                       sum(case when base.query_status <> 'SUCCESS' then 1 else 0 end) over (partition by base.group_key) as failed_count,
                       row_number() over (partition by base.group_key order by base.created_at desc, base.history_id desc) as row_num
                from (
                    select h.id as history_id,
                           case
                               when h.attendee_id is not null then concat('ATTENDEE:', h.attendee_id)
                               else concat('UNRECOGNIZED:', coalesce(nullif(trim(h.error_message), ''), '未知失败'))
                           end as group_key,
                           case
                               when h.attendee_id is not null then 'ATTENDEE'
                               else 'UNRECOGNIZED'
                           end as group_type,
                           h.attendee_id as attendee_id,
                           case
                               when h.attendee_id is not null then coalesce(nullif(trim(h.attendee_name), ''), a.name)
                               else null
                           end as attendee_name,
                           case
                               when h.attendee_id is not null then coalesce(nullif(trim(h.attendee_phone), ''), a.phone)
                               else null
                           end as attendee_phone,
                           case
                               when h.attendee_id is null then coalesce(nullif(trim(h.error_message), ''), '未知失败')
                               else null
                           end as failure_reason,
                           h.query_status as query_status,
                           h.created_at as created_at,
                           case
                               when h.query_status = 'SUCCESS' then h.result_summary
                               else h.error_message
                           end as latest_result
                    from public_query_history h
                    left join attendees a on a.id = h.attendee_id
                    where h.meeting_id = :meetingId
                ) base
            ) ranked
            where ranked.row_num = 1
            order by ranked.created_at desc, ranked.history_id desc
            """, nativeQuery = true)
    List<Object[]> findSummaryAggregatesByMeetingId(@Param("meetingId") Long meetingId);

    @Query("""
            select distinct attendee.id from PublicQueryHistory h
              join h.attendee attendee
            where h.meeting.id = :meetingId
              and h.queryType = :queryType
            """)
    List<Long> findQueriedAttendeeIds(@Param("meetingId") Long meetingId,
                                      @Param("queryType") PublicQueryType queryType);

    @Query("""
            select distinct attendee.id from PublicQueryHistory h
              join h.attendee attendee
            where h.meeting.id = :meetingId
              and h.queryType = :queryType
              and attendee.id in :attendeeIds
            """)
    List<Long> findQueriedAttendeeIds(@Param("meetingId") Long meetingId,
                                      @Param("queryType") PublicQueryType queryType,
                                      @Param("attendeeIds") Collection<Long> attendeeIds);

    @Query("""
            select count(h) > 0 from PublicQueryHistory h
            where h.attendee.id = :attendeeId
              and h.queryType in :queryTypes
            """)
    boolean existsByAttendeeIdAndQueryTypeIn(@Param("attendeeId") Long attendeeId,
                                             @Param("queryTypes") Collection<PublicQueryType> queryTypes);
}
