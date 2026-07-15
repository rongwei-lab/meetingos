package com.meetingos.invitation;

import com.meetingos.common.BaseEntity;
import com.meetingos.meeting.Meeting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitation_share_links")
public class InvitationShareLink extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_id", nullable = false)
    private MeetingInvitation invitation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private MeetingInvitationVersion currentVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationChannelType channelType;
    @Column(nullable = false, length = 120)
    private String channelName;
    @Column(nullable = false, unique = true, length = 96)
    private String shareToken;
    @Column(nullable = false)
    private boolean enabled = true;
    private LocalDateTime expiresAt;
    @Column(length = 64)
    private String createdBy;

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }
    public MeetingInvitation getInvitation() { return invitation; }
    public void setInvitation(MeetingInvitation invitation) { this.invitation = invitation; }
    public MeetingInvitationVersion getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(MeetingInvitationVersion currentVersion) { this.currentVersion = currentVersion; }
    public InvitationChannelType getChannelType() { return channelType; }
    public void setChannelType(InvitationChannelType channelType) { this.channelType = channelType; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
