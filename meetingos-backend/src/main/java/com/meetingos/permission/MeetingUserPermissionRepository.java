package com.meetingos.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MeetingUserPermissionRepository extends JpaRepository<MeetingUserPermission, Long> {

    List<MeetingUserPermission> findByMeetingIdOrderByIdAsc(Long meetingId);

    List<MeetingUserPermission> findByUserIdOrderByIdAsc(Long userId);

    Set<MeetingUserPermission> findByUserId(Long userId);

    List<MeetingUserPermission> findByMeetingIdAndUserIdOrderByIdAsc(Long meetingId, Long userId);

    Optional<MeetingUserPermission> findByMeetingIdAndUserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndUserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndUserIdAndPermissionRole(Long meetingId, Long userId, MeetingPermissionRole permissionRole);
}
