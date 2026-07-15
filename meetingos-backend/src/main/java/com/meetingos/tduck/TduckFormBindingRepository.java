package com.meetingos.tduck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TduckFormBindingRepository extends JpaRepository<TduckFormBinding, Long> {

    Optional<TduckFormBinding> findByMeetingId(Long meetingId);

    Optional<TduckFormBinding> findByFormKey(String formKey);

    @Query("select b.meeting.id from TduckFormBinding b where b.formKey = :formKey")
    Optional<Long> findMeetingIdByFormKey(@Param("formKey") String formKey);
}
