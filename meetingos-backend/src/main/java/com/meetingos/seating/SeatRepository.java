package com.meetingos.seating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByMeetingIdOrderByRowNoAscSeatNoAsc(Long meetingId);

    List<Seat> findByMeetingIdOrderByLayoutSortOrderAscAreaNameAscRowNoAscSeatNoAsc(Long meetingId);

    List<Seat> findByMeetingIdAndDisabledFalseOrderByRowNoAscSeatNoAsc(Long meetingId);

    List<Seat> findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(Long meetingId, String areaName);

    Optional<Seat> findByMeetingIdAndSeatCode(Long meetingId, String seatCode);

    List<Seat> findByIdIn(Collection<Long> ids);

    long countByMeetingId(Long meetingId);

    long countByMeetingIdAndDisabledTrue(Long meetingId);
}
