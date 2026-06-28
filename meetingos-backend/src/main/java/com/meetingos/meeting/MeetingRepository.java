package com.meetingos.meeting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findByCode(String code);

    @Query("""
            select m from Meeting m
            where (:keyword is null or lower(m.name) like lower(concat('%', :keyword, '%')) or lower(m.code) like lower(concat('%', :keyword, '%')))
              and (:status is null or m.status = :status)
            """)
    Page<Meeting> search(@Param("keyword") String keyword,
                         @Param("status") MeetingStatus status,
                         Pageable pageable);

    @Query("""
            select m from Meeting m
            where m.id in :meetingIds
              and (:keyword is null or lower(m.name) like lower(concat('%', :keyword, '%')) or lower(m.code) like lower(concat('%', :keyword, '%')))
              and (:status is null or m.status = :status)
            """)
    Page<Meeting> searchAuthorized(@Param("meetingIds") java.util.Set<Long> meetingIds,
                                   @Param("keyword") String keyword,
                                   @Param("status") MeetingStatus status,
                                   Pageable pageable);
}
