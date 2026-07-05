package com.meetingos.attendee;

import com.meetingos.meeting.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendeeRepository extends JpaRepository<Attendee, Long> {

    Optional<Attendee> findByMeetingAndPhone(Meeting meeting, String phone);

    Optional<Attendee> findByMeetingIdAndAttendeeCode(Long meetingId, String attendeeCode);

    List<Attendee> findByMeetingIdAndAttendeeCodeIgnoreCase(Long meetingId, String attendeeCode);

    List<Attendee> findByMeetingIdAndAttendeeCodeContainingIgnoreCase(Long meetingId, String attendeeCode);

    Optional<Attendee> findByMeetingIdAndPhone(Long meetingId, String phone);

    List<Attendee> findByMeetingIdAndPhoneContaining(Long meetingId, String phone);

    List<Attendee> findByMeetingIdAndPhoneEndingWith(Long meetingId, String phone);

    Optional<Attendee> findByMeetingIdAndTduckDataId(Long meetingId, String tduckDataId);

    Optional<Attendee> findByMeetingIdAndIdentityNo(Long meetingId, String identityNo);

    List<Attendee> findByMeetingIdAndNameIgnoreCase(Long meetingId, String name);

    List<Attendee> findByMeetingIdAndNameAndOrganization(Long meetingId, String name, String organization);

    List<Attendee> findByMeetingIdAndNameContainingIgnoreCase(Long meetingId, String name);

    List<Attendee> findByMeetingIdAndOrganizationIgnoreCase(Long meetingId, String organization);

    List<Attendee> findByMeetingIdAndOrganizationContainingIgnoreCase(Long meetingId, String organization);

    List<Attendee> findByMeetingIdAndTitleIgnoreCase(Long meetingId, String title);

    List<Attendee> findByMeetingIdAndTitleContainingIgnoreCase(Long meetingId, String title);

    List<Attendee> findByMeetingIdAndEmailIgnoreCase(Long meetingId, String email);

    List<Attendee> findByMeetingIdAndEmailContainingIgnoreCase(Long meetingId, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attendee a where a.meeting.id = :meetingId and a.attendeeCode = :attendeeCode")
    Optional<Attendee> findByMeetingIdAndAttendeeCodeForUpdate(@Param("meetingId") Long meetingId,
                                                               @Param("attendeeCode") String attendeeCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attendee a where a.meeting.id = :meetingId and a.phone = :phone")
    Optional<Attendee> findByMeetingIdAndPhoneForUpdate(@Param("meetingId") Long meetingId, @Param("phone") String phone);

    @Query("""
            select a from Attendee a
            where a.meeting.id = :meetingId
              and (:keyword is null or lower(a.name) like lower(concat('%', :keyword, '%')) or a.phone like concat('%', :keyword, '%') or lower(a.organization) like lower(concat('%', :keyword, '%')))
              and (:reviewStatus is null or a.reviewStatus = :reviewStatus)
              and (:checkinStatus is null or a.checkinStatus = :checkinStatus)
              and (:attendeeType is null or a.attendeeType = :attendeeType)
              and (:tagKeyword is null or lower(a.tags) like lower(concat('%', :tagKeyword, '%')))
              and (:hasSeat is null
                   or (:hasSeat = true and exists (select 1 from SeatAssignment sa where sa.attendee = a))
                   or (:hasSeat = false and not exists (select 1 from SeatAssignment sa where sa.attendee = a)))
            """)
    Page<Attendee> search(@Param("meetingId") Long meetingId,
                          @Param("keyword") String keyword,
                          @Param("reviewStatus") ReviewStatus reviewStatus,
                          @Param("checkinStatus") CheckinStatus checkinStatus,
                          @Param("attendeeType") AttendeeType attendeeType,
                          @Param("hasSeat") Boolean hasSeat,
                          @Param("tagKeyword") String tagKeyword,
                          Pageable pageable);

    List<Attendee> findByMeetingIdAndReviewStatus(Long meetingId, ReviewStatus reviewStatus);

    List<Attendee> findByMeetingIdOrderByIdAsc(Long meetingId);

    List<Attendee> findByMeetingIdAndRoomNoIsNotNullOrderByIdAsc(Long meetingId);

    List<Attendee> findByMeetingIdAndReviewStatusOrderByIdAsc(Long meetingId, ReviewStatus reviewStatus);

    List<Attendee> findByMeetingIdAndCheckinStatusOrderByIdAsc(Long meetingId, CheckinStatus checkinStatus);

    long countByMeetingId(Long meetingId);

    long countByMeetingIdAndReviewStatus(Long meetingId, ReviewStatus reviewStatus);

    long countByMeetingIdAndRoomNoIsNotNull(Long meetingId);

    long countByMeetingIdAndRoomNo(Long meetingId, String roomNo);

    long countByMeetingIdAndCheckinStatus(Long meetingId, CheckinStatus checkinStatus);

    @Query("""
            select a.meeting.id,
                   count(a),
                   coalesce(sum(case when a.reviewStatus = :approved then 1 else 0 end), 0),
                   coalesce(sum(case when a.roomNo is not null then 1 else 0 end), 0),
                   coalesce(sum(case when a.checkinStatus = :checkedIn then 1 else 0 end), 0)
            from Attendee a
            where a.meeting.id in :meetingIds
            group by a.meeting.id
            """)
    List<Object[]> countSummaryByMeetingIds(@Param("meetingIds") Collection<Long> meetingIds,
                                            @Param("approved") ReviewStatus approved,
                                            @Param("checkedIn") CheckinStatus checkedIn);

    @Query("""
            select count(a),
                   coalesce(sum(case when a.reviewStatus = :pending then 1 else 0 end), 0),
                   coalesce(sum(case when a.reviewStatus = :approved then 1 else 0 end), 0),
                   coalesce(sum(case when a.reviewStatus = :rejected then 1 else 0 end), 0),
                   coalesce(sum(case when a.roomNo is not null then 1 else 0 end), 0),
                   coalesce(sum(case when a.roomNo is null then 1 else 0 end), 0),
                   coalesce(sum(case when a.accommodationRequired = true then 1 else 0 end), 0),
                   coalesce(sum(case when a.accommodationRequired = true and a.roomNo is not null then 1 else 0 end), 0),
                   coalesce(sum(case when a.accommodationRequired = true and a.roomNo is null then 1 else 0 end), 0),
                   coalesce(sum(case when a.checkinStatus = :checkedIn then 1 else 0 end), 0)
            from Attendee a
            where a.meeting.id = :meetingId
            """)
    List<Object[]> countOverviewByMeetingId(@Param("meetingId") Long meetingId,
                                            @Param("pending") ReviewStatus pending,
                                            @Param("approved") ReviewStatus approved,
                                            @Param("rejected") ReviewStatus rejected,
                                            @Param("checkedIn") CheckinStatus checkedIn);

    long countByMeetingIdAndRoomNoIsNull(Long meetingId);

    long countByMeetingIdAndRegisteredAtBetween(Long meetingId, LocalDateTime start, LocalDateTime end);

    long countByMeetingIdAndAccommodationRequiredTrue(Long meetingId);

    long countByMeetingIdAndAccommodationRequiredTrueAndRoomNoIsNotNull(Long meetingId);

    long countByMeetingIdAndAccommodationRequiredTrueAndRoomNoIsNull(Long meetingId);

    boolean existsByMeetingIdAndTduckDataIdIsNotNull(Long meetingId);

    @Query("select a.reviewStatus, count(a) from Attendee a where a.meeting.id = :meetingId group by a.reviewStatus")
    List<Object[]> countByReviewStatus(@Param("meetingId") Long meetingId);

    @Query("select a.attendeeType, count(a) from Attendee a where a.meeting.id = :meetingId group by a.attendeeType")
    List<Object[]> countByAttendeeType(@Param("meetingId") Long meetingId);

    @Query("select coalesce(a.organization, '未填写'), count(a) from Attendee a where a.meeting.id = :meetingId group by coalesce(a.organization, '未填写')")
    List<Object[]> countByOrganization(@Param("meetingId") Long meetingId);

    @Query("""
            select function('date', a.registeredAt), count(a)
            from Attendee a
            where a.meeting.id = :meetingId
              and a.registeredAt >= :start
              and a.registeredAt < :end
            group by function('date', a.registeredAt)
            order by function('date', a.registeredAt)
            """)
    List<Object[]> countRegistrationsByDate(@Param("meetingId") Long meetingId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
