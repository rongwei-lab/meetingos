package com.meetingos.permission;

import com.meetingos.common.BaseEntity;
import com.meetingos.meeting.Meeting;
import com.meetingos.security.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "meeting_user_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_meeting_user_permission",
                columnNames = {"meeting_id", "user_id"}
        )
)
public class MeetingUserPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_role", nullable = false, length = 32)
    private MeetingPermissionRole permissionRole;

    @Column(length = 64)
    private String grantedBy;

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public MeetingPermissionRole getPermissionRole() {
        return permissionRole;
    }

    public void setPermissionRole(MeetingPermissionRole permissionRole) {
        this.permissionRole = permissionRole;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }
}
