package com.meetingos.invitation;

import com.meetingos.attendee.Attendee;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration_attributions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_registration_attr_tduck", columnNames = {"meeting_id", "tduck_data_id"})
})
public class RegistrationAttribution extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private MeetingInvitation invitation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private MeetingInvitationVersion version;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_link_id")
    private InvitationShareLink shareLink;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "click_event_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private InvitationEvent clickEvent;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Attendee attendee;
    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_status", nullable = false, length = 32)
    private RegistrationAttributionStatus attributionStatus;
    @Column(nullable = false, length = 120)
    private String formKey;
    @Column(nullable = false, length = 120)
    private String tduckDataId;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private LocalDateTime attributedAt;

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }
    public MeetingInvitation getInvitation() { return invitation; }
    public void setInvitation(MeetingInvitation invitation) { this.invitation = invitation; }
    public MeetingInvitationVersion getVersion() { return version; }
    public void setVersion(MeetingInvitationVersion version) { this.version = version; }
    public InvitationShareLink getShareLink() { return shareLink; }
    public void setShareLink(InvitationShareLink shareLink) { this.shareLink = shareLink; }
    public InvitationEvent getClickEvent() { return clickEvent; }
    public void setClickEvent(InvitationEvent clickEvent) { this.clickEvent = clickEvent; }
    public Attendee getAttendee() { return attendee; }
    public void setAttendee(Attendee attendee) { this.attendee = attendee; }
    public RegistrationAttributionStatus getAttributionStatus() { return attributionStatus; }
    public void setAttributionStatus(RegistrationAttributionStatus attributionStatus) { this.attributionStatus = attributionStatus; }
    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }
    public String getTduckDataId() { return tduckDataId; }
    public void setTduckDataId(String tduckDataId) { this.tduckDataId = tduckDataId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getAttributedAt() { return attributedAt; }
    public void setAttributedAt(LocalDateTime attributedAt) { this.attributedAt = attributedAt; }
}
