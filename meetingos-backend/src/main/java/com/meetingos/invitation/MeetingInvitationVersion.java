package com.meetingos.invitation;

import com.meetingos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "meeting_invitation_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invitation_version_no", columnNames = {"invitation_id", "version_no"})
})
public class MeetingInvitationVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_id", nullable = false)
    private MeetingInvitation invitation;

    @Column(nullable = false)
    private int versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private InvitationDesignDocumentV1 designDocument;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private InvitationMeetingSnapshot meetingSnapshot;

    @Column(length = 64)
    private String publishedBy;

    public MeetingInvitation getInvitation() { return invitation; }
    public void setInvitation(MeetingInvitation invitation) { this.invitation = invitation; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public InvitationDesignDocumentV1 getDesignDocument() { return designDocument; }
    public void setDesignDocument(InvitationDesignDocumentV1 designDocument) { this.designDocument = designDocument; }
    public InvitationMeetingSnapshot getMeetingSnapshot() { return meetingSnapshot; }
    public void setMeetingSnapshot(InvitationMeetingSnapshot meetingSnapshot) { this.meetingSnapshot = meetingSnapshot; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
}
