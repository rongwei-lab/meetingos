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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitation_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invitation_event_client", columnNames = {"share_link_id", "client_event_id"}),
        @UniqueConstraint(name = "uk_invitation_event_click", columnNames = "click_public_id")
})
public class InvitationEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_id", nullable = false)
    private MeetingInvitation invitation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private MeetingInvitationVersion version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_link_id", nullable = false)
    private InvitationShareLink shareLink;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationEventType eventType;
    @Column(nullable = false, length = 80)
    private String clientEventId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String visitorHash;
    @Column(nullable = false)
    private LocalDateTime occurredAt;
    @Column(length = 80)
    private String clickPublicId;
    private LocalDateTime clickExpiresAt;
    @Column(length = 24)
    private String deviceType;
    @Column(length = 255)
    private String referrerHost;

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }
    public MeetingInvitation getInvitation() { return invitation; }
    public void setInvitation(MeetingInvitation invitation) { this.invitation = invitation; }
    public MeetingInvitationVersion getVersion() { return version; }
    public void setVersion(MeetingInvitationVersion version) { this.version = version; }
    public InvitationShareLink getShareLink() { return shareLink; }
    public void setShareLink(InvitationShareLink shareLink) { this.shareLink = shareLink; }
    public InvitationEventType getEventType() { return eventType; }
    public void setEventType(InvitationEventType eventType) { this.eventType = eventType; }
    public String getClientEventId() { return clientEventId; }
    public void setClientEventId(String clientEventId) { this.clientEventId = clientEventId; }
    public String getVisitorHash() { return visitorHash; }
    public void setVisitorHash(String visitorHash) { this.visitorHash = visitorHash; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getClickPublicId() { return clickPublicId; }
    public void setClickPublicId(String clickPublicId) { this.clickPublicId = clickPublicId; }
    public LocalDateTime getClickExpiresAt() { return clickExpiresAt; }
    public void setClickExpiresAt(LocalDateTime clickExpiresAt) { this.clickExpiresAt = clickExpiresAt; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getReferrerHost() { return referrerHost; }
    public void setReferrerHost(String referrerHost) { this.referrerHost = referrerHost; }
}
