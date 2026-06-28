package com.meetingos.attendee;

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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "attendees", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendee_meeting_phone", columnNames = {"meeting_id", "phone"})
})
public class Attendee extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

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

    @Column(length = 80)
    private String roomNo;

    @Column(length = 160)
    private String roomRemark;

    @Column(length = 500)
    private String remark;

    @Column(length = 300)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(length = 500)
    private String reviewReason;

    @Column(length = 500)
    private String reviewRemark;

    private LocalDateTime reviewedAt;

    @Column(length = 64)
    private String reviewedBy;

    private LocalDateTime registeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckinStatus checkinStatus = CheckinStatus.NOT_CHECKED_IN;

    private LocalDateTime checkedInAt;

    @Column(length = 80)
    private String checkinEntrance;

    @Column(length = 64)
    private String checkinOperator;

    @Column(nullable = false, unique = true, length = 80)
    private String attendeeCode;

    @Column(length = 80)
    private String tduckDataId;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> rawDataSnapshot = new LinkedHashMap<>();

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
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

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public String getRoomRemark() {
        return roomRemark;
    }

    public void setRoomRemark(String roomRemark) {
        this.roomRemark = roomRemark;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public String getReviewRemark() {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public CheckinStatus getCheckinStatus() {
        return checkinStatus;
    }

    public void setCheckinStatus(CheckinStatus checkinStatus) {
        this.checkinStatus = checkinStatus;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public String getCheckinEntrance() {
        return checkinEntrance;
    }

    public void setCheckinEntrance(String checkinEntrance) {
        this.checkinEntrance = checkinEntrance;
    }

    public String getCheckinOperator() {
        return checkinOperator;
    }

    public void setCheckinOperator(String checkinOperator) {
        this.checkinOperator = checkinOperator;
    }

    public String getAttendeeCode() {
        return attendeeCode;
    }

    public void setAttendeeCode(String attendeeCode) {
        this.attendeeCode = attendeeCode;
    }

    public String getTduckDataId() {
        return tduckDataId;
    }

    public void setTduckDataId(String tduckDataId) {
        this.tduckDataId = tduckDataId;
    }

    public Map<String, Object> getRawDataSnapshot() {
        return rawDataSnapshot;
    }

    public void setRawDataSnapshot(Map<String, Object> rawDataSnapshot) {
        this.rawDataSnapshot = rawDataSnapshot;
    }
}
