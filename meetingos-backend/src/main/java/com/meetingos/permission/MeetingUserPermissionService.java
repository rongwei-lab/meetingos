package com.meetingos.permission;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.AppUser;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MeetingUserPermissionService {

    private final MeetingUserPermissionRepository permissionRepository;
    private final MeetingRepository meetingRepository;
    private final AppUserRepository userRepository;

    public MeetingUserPermissionService(MeetingUserPermissionRepository permissionRepository,
                                        MeetingRepository meetingRepository,
                                        AppUserRepository userRepository) {
        this.permissionRepository = permissionRepository;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<MeetingUserPermissionResponse> list(Long meetingId, Long userId) {
        List<MeetingUserPermission> permissions;
        if (meetingId != null && userId != null) {
            permissions = permissionRepository.findByMeetingIdAndUserIdOrderByIdAsc(meetingId, userId);
        } else if (meetingId != null) {
            permissions = permissionRepository.findByMeetingIdOrderByIdAsc(meetingId);
        } else if (userId != null) {
            permissions = permissionRepository.findByUserIdOrderByIdAsc(userId);
        } else {
            permissions = permissionRepository.findAll();
        }
        return permissions.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MeetingUserPermissionResponse grant(MeetingUserPermissionRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        AppUser user = userRepository.findById(request.userId())
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        MeetingUserPermission permission = permissionRepository
                .findByMeetingIdAndUserId(request.meetingId(), request.userId())
                .orElseGet(MeetingUserPermission::new);
        permission.setMeeting(meeting);
        permission.setUser(user);
        permission.setPermissionRole(request.role());
        // 授权接口可能由集成测试或后台任务调用，没有登录上下文时记录 system，避免审计字段为空。
        permission.setGrantedBy(SecurityUtils.currentUsernameOrSystem());
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw BusinessException.notFound("会议授权不存在");
        }
        permissionRepository.deleteById(id);
    }

    private MeetingUserPermissionResponse toResponse(MeetingUserPermission permission) {
        Meeting meeting = permission.getMeeting();
        AppUser user = permission.getUser();
        return new MeetingUserPermissionResponse(
                permission.getId(),
                meeting.getId(),
                meeting.getCode(),
                meeting.getName(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                permission.getPermissionRole(),
                permission.getGrantedBy(),
                permission.getCreatedAt()
        );
    }
}
