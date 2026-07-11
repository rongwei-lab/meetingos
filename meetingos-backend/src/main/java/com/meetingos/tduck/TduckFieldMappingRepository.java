package com.meetingos.tduck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TduckFieldMappingRepository extends JpaRepository<TduckFieldMapping, Long> {

    List<TduckFieldMapping> findByMeetingId(Long meetingId);

    List<TduckFieldMapping> findByMeetingIdOrderByIdAsc(Long meetingId);

    Optional<TduckFieldMapping> findByMeetingIdAndAttendeeField(Long meetingId, AttendeeField attendeeField);

    void deleteByMeetingId(Long meetingId);
}
