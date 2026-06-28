package com.meetingos.seating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatLayoutRepository extends JpaRepository<SeatLayout, Long> {

    List<SeatLayout> findByMeetingId(Long meetingId);

    List<SeatLayout> findByMeetingIdAndAreaNameOrderByIdAsc(Long meetingId, String areaName);
}
