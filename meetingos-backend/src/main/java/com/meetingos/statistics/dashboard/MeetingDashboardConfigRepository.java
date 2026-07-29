package com.meetingos.statistics.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingDashboardConfigRepository extends JpaRepository<MeetingDashboardConfig, Long> {

    Optional<MeetingDashboardConfig> findByMeetingId(Long meetingId);
}
