package com.meetingos.tduck;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TduckFormBindingRepository extends JpaRepository<TduckFormBinding, Long> {

    Optional<TduckFormBinding> findByMeetingId(Long meetingId);

    Optional<TduckFormBinding> findByFormKey(String formKey);
}
