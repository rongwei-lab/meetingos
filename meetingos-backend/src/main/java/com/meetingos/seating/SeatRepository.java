package com.meetingos.seating;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByMeetingIdOrderByRowNoAscSeatNoAsc(Long meetingId);

    @EntityGraph(attributePaths = "layout")
    List<Seat> findByMeetingIdOrderByLayoutSortOrderAscAreaNameAscRowNoAscSeatNoAsc(Long meetingId);

    List<Seat> findByMeetingIdAndDisabledFalseOrderByRowNoAscSeatNoAsc(Long meetingId);

    List<Seat> findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(Long meetingId, String areaName);

    Optional<Seat> findByMeetingIdAndSeatCode(Long meetingId, String seatCode);

    List<Seat> findByIdIn(Collection<Long> ids);

    long countByMeetingId(Long meetingId);

    long countByMeetingIdAndDisabledTrue(Long meetingId);

    @Query("""
            select count(seat),
                   coalesce(sum(case when seat.disabled = true then 1 else 0 end), 0)
            from Seat seat
            where seat.meeting.id = :meetingId
            """)
    List<Object[]> countOverviewByMeetingId(@Param("meetingId") Long meetingId);

    @Query("""
            select coalesce(max(seat.rowNo), 0),
                   coalesce(max(seat.seatNo), 0)
            from Seat seat
            where seat.meeting.id = :meetingId
              and seat.areaName = :areaName
            """)
    List<Object[]> findAreaBoundsByMeetingIdAndAreaName(@Param("meetingId") Long meetingId,
                                                        @Param("areaName") String areaName);
}
