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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_invitations")
public class MeetingInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationStatus status = InvitationStatus.DRAFT;

    @Column(nullable = false)
    private long draftRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private InvitationDesignDocumentV1 draftDocument = InvitationDesignDocumentV1.empty();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_published_version_id")
    private MeetingInvitationVersion latestPublishedVersion;

    @Column(length = 64)
    private String createdBy;

    private LocalDateTime publishedAt;
    private LocalDateTime unpublishedAt;
    private LocalDateTime archivedAt;

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public InvitationStatus getStatus() { return status; }
    public void setStatus(InvitationStatus status) { this.status = status; }
    public long getDraftRevision() { return draftRevision; }
    public void setDraftRevision(long draftRevision) { this.draftRevision = draftRevision; }
    public InvitationDesignDocumentV1 getDraftDocument() { return draftDocument; }
    public void setDraftDocument(InvitationDesignDocumentV1 draftDocument) { this.draftDocument = draftDocument; }
    public MeetingInvitationVersion getLatestPublishedVersion() { return latestPublishedVersion; }
    public void setLatestPublishedVersion(MeetingInvitationVersion latestPublishedVersion) { this.latestPublishedVersion = latestPublishedVersion; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getUnpublishedAt() { return unpublishedAt; }
    public void setUnpublishedAt(LocalDateTime unpublishedAt) { this.unpublishedAt = unpublishedAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
