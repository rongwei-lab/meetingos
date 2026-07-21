package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.common.BaseEntity;
import com.meetingos.common.JsonMapConverter;
import com.meetingos.meeting.Meeting;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "tduck_submissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tduck_submission_meeting_data", columnNames = {"meeting_id", "tduck_data_id"}),
        @UniqueConstraint(name = "uk_tduck_submission_active_attendee", columnNames = {"meeting_id", "active_attendee_id"})
})
public class TduckSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private Meeting meeting;

    /*
     * PENDING_CONFLICT 也会关联到同手机号的既有参会人，方便管理员在同一界面比较并处理。
     * 它不代表这条 TDuck 数据已经被采用。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendee_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private Attendee attendee;

    @Column(nullable = false, length = 120)
    private String formKey;

    @Column(nullable = false, length = 120)
    private String tduckDataId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TduckSubmissionStatus status;

    /*
     * MySQL 不支持部分唯一索引。ACTIVE 时写入 attendeeId，其余状态为 NULL，
     * 利用唯一键允许多个 NULL 的语义，从数据库保证一个参会人只有一个当前来源。
     */
    @Column(name = "active_attendee_id")
    private Long activeAttendeeId;

    private LocalDateTime submittedAt;

    private LocalDateTime resolvedAt;

    @Column(length = 64)
    private String resolvedBy;

    @Column(length = 500)
    private String resolutionNote;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 160)
    private String organization;

    @Column(length = 120)
    private String title;

    @Column(length = 80)
    private String identityNo;

    @Column(length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttendeeType attendeeType = AttendeeType.NORMAL;

    @Column(nullable = false)
    private boolean accommodationRequired;

    @Column(length = 500)
    private String remark;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> rawDataSnapshot = new LinkedHashMap<>();

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public Attendee getAttendee() {
        return attendee;
    }

    public void setAttendee(Attendee attendee) {
        this.attendee = attendee;
        if (status == TduckSubmissionStatus.ACTIVE) {
            this.activeAttendeeId = attendee == null ? null : attendee.getId();
        }
    }

    public String getFormKey() {
        return formKey;
    }

    public void setFormKey(String formKey) {
        this.formKey = formKey;
    }

    public String getTduckDataId() {
        return tduckDataId;
    }

    public void setTduckDataId(String tduckDataId) {
        this.tduckDataId = tduckDataId;
    }

    public TduckSubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(TduckSubmissionStatus status) {
        this.status = status;
        this.activeAttendeeId = status == TduckSubmissionStatus.ACTIVE && attendee != null ? attendee.getId() : null;
    }

    public Long getActiveAttendeeId() {
        return activeAttendeeId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIdentityNo() {
        return identityNo;
    }

    public void setIdentityNo(String identityNo) {
        this.identityNo = identityNo;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public AttendeeType getAttendeeType() {
        return attendeeType;
    }

    public void setAttendeeType(AttendeeType attendeeType) {
        this.attendeeType = attendeeType;
    }

    public boolean isAccommodationRequired() {
        return accommodationRequired;
    }

    public void setAccommodationRequired(boolean accommodationRequired) {
        this.accommodationRequired = accommodationRequired;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Map<String, Object> getRawDataSnapshot() {
        return rawDataSnapshot;
    }

    public void setRawDataSnapshot(Map<String, Object> rawDataSnapshot) {
        this.rawDataSnapshot = rawDataSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawDataSnapshot);
    }
}
