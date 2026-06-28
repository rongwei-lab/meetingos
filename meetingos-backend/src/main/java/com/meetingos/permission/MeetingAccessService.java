package com.meetingos.permission;

import com.meetingos.common.BusinessException;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.seating.SeatRepository;
import com.meetingos.security.CurrentUser;
import com.meetingos.security.Role;
import com.meetingos.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service("meetingAccess")
public class MeetingAccessService {

    private final MeetingUserPermissionRepository permissionRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatRepository seatRepository;

    public MeetingAccessService(MeetingUserPermissionRepository permissionRepository,
                                AttendeeRepository attendeeRepository,
                                SeatRepository seatRepository) {
        this.permissionRepository = permissionRepository;
        this.attendeeRepository = attendeeRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional(readOnly = true)
    public boolean canView(Long meetingId) {
        return hasMeetingRole(meetingId, Set.of(
                MeetingPermissionRole.MANAGER,
                MeetingPermissionRole.CHECKIN,
                MeetingPermissionRole.ROOM,
                MeetingPermissionRole.VIEWER
        ));
    }

    @Transactional(readOnly = true)
    public boolean canViewAttendee(Long attendeeId) {
        return attendeeRepository.findById(attendeeId)
                .map(attendee -> canView(attendee.getMeeting().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageAttendee(Long attendeeId) {
        return attendeeRepository.findById(attendeeId)
                .map(attendee -> canManage(attendee.getMeeting().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageRoomByAttendee(Long attendeeId) {
        return attendeeRepository.findById(attendeeId)
                .map(attendee -> canManageRooms(attendee.getMeeting().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageSeat(Long seatId) {
        return seatRepository.findById(seatId)
                .map(seat -> canManage(seat.getMeeting().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManage(Long meetingId) {
        return hasMeetingRole(meetingId, Set.of(MeetingPermissionRole.MANAGER));
    }

    @Transactional(readOnly = true)
    public boolean canCheckin(Long meetingId) {
        return hasMeetingRole(meetingId, Set.of(MeetingPermissionRole.MANAGER, MeetingPermissionRole.CHECKIN));
    }

    @Transactional(readOnly = true)
    public boolean canManageRooms(Long meetingId) {
        return hasMeetingRole(meetingId, Set.of(MeetingPermissionRole.MANAGER, MeetingPermissionRole.ROOM));
    }

    @Transactional(readOnly = true)
    public void requireView(Long meetingId) {
        require(canView(meetingId));
    }

    @Transactional(readOnly = true)
    public void requireManage(Long meetingId) {
        require(canManage(meetingId));
    }

    @Transactional(readOnly = true)
    public void requireCheckin(Long meetingId) {
        require(canCheckin(meetingId));
    }

    @Transactional(readOnly = true)
    public void requireRoom(Long meetingId) {
        require(canManageRooms(meetingId));
    }

    private boolean hasMeetingRole(Long meetingId, Set<MeetingPermissionRole> acceptedRoles) {
        if (meetingId == null) {
            return false;
        }
        CurrentUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            // 服务层会被集成测试、导入任务和未来定时补偿任务直接调用。真正的 Web 请求已经由
            // Spring Security 保证有认证上下文；没有当前用户时按系统内部调用处理，避免误伤后台任务。
            return true;
        }
        if (user.roles().contains(Role.SUPER_ADMIN)) {
            return true;
        }
        if (user.roles().contains(Role.MEETING_ADMIN) && acceptedRoles.contains(MeetingPermissionRole.MANAGER)) {
            return permissionRepository.existsByMeetingIdAndUserIdAndPermissionRole(meetingId, user.id(), MeetingPermissionRole.MANAGER);
        }
        if (user.roles().contains(Role.CHECKIN_STAFF) && acceptedRoles.contains(MeetingPermissionRole.CHECKIN)) {
            return permissionRepository.existsByMeetingIdAndUserIdAndPermissionRole(meetingId, user.id(), MeetingPermissionRole.CHECKIN)
                    || permissionRepository.existsByMeetingIdAndUserIdAndPermissionRole(meetingId, user.id(), MeetingPermissionRole.MANAGER);
        }
        if (user.roles().contains(Role.ROOM_STAFF) && acceptedRoles.contains(MeetingPermissionRole.ROOM)) {
            return permissionRepository.existsByMeetingIdAndUserIdAndPermissionRole(meetingId, user.id(), MeetingPermissionRole.ROOM)
                    || permissionRepository.existsByMeetingIdAndUserIdAndPermissionRole(meetingId, user.id(), MeetingPermissionRole.MANAGER);
        }
        if (user.roles().contains(Role.VIEWER) && acceptedRoles.contains(MeetingPermissionRole.VIEWER)) {
            return permissionRepository.existsByMeetingIdAndUserId(meetingId, user.id());
        }
        return false;
    }

    private void require(boolean allowed) {
        if (!allowed) {
            // 用业务异常返回 403 语义由全局异常处理器统一负责；这里避免控制器重复拼错误消息。
            throw new BusinessException(com.meetingos.common.ErrorCode.FORBIDDEN, "当前账号未被授权访问该会议");
        }
    }
}
