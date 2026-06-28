package com.meetingos.checkin;

import com.meetingos.attendee.Attendee;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "checkin_records")
public class CheckinRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id")
    @JsonIgnore
    private Attendee attendee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckinType checkinType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckinResultStatus resultStatus;

    @Column(length = 80)
    private String entrance;

    @Column(length = 64)
    private String operator;

    private LocalDateTime checkedInAt;

    @Column(length = 500)
    private String message;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> requestSnapshot = new LinkedHashMap<>();

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
    }

    public CheckinType getCheckinType() {
        return checkinType;
    }

    public void setCheckinType(CheckinType checkinType) {
        this.checkinType = checkinType;
    }

    public CheckinResultStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(CheckinResultStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getEntrance() {
        return entrance;
    }

    public void setEntrance(String entrance) {
        this.entrance = entrance;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getRequestSnapshot() {
        return requestSnapshot;
    }

    public void setRequestSnapshot(Map<String, Object> requestSnapshot) {
        this.requestSnapshot = requestSnapshot;
    }
}
