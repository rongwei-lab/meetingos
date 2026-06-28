package com.meetingos.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    List<NotificationRecord> findByMeeting_IdOrderByCreatedAtDesc(Long meetingId);

    List<NotificationRecord> findAllByOrderByCreatedAtDesc();
}
