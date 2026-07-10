package com.meetingos.room;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByMeetingId(Long meetingId);

    List<Room> findByMeetingIdOrderByRoomNoAsc(Long meetingId);

    Optional<Room> findByMeetingIdAndRoomNo(Long meetingId, String roomNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select room
            from Room room
            where room.meeting.id = :meetingId
              and room.roomNo in :roomNos
            order by room.roomNo asc
            """)
    List<Room> findByMeetingIdAndRoomNoInForUpdate(@Param("meetingId") Long meetingId,
                                                   @Param("roomNos") Collection<String> roomNos);

    List<Room> findByMeetingIdAndRoomNoIn(Long meetingId, Collection<String> roomNos);

    long countByMeetingId(Long meetingId);

    @Query("select coalesce(r.hotelName, '未填写'), count(r) from Room r where r.meeting.id = :meetingId group by coalesce(r.hotelName, '未填写')")
    List<Object[]> countByHotelName(@Param("meetingId") Long meetingId);

    @Query("select coalesce(r.roomType, '未填写'), count(r) from Room r where r.meeting.id = :meetingId group by coalesce(r.roomType, '未填写')")
    List<Object[]> countByRoomType(@Param("meetingId") Long meetingId);
}
