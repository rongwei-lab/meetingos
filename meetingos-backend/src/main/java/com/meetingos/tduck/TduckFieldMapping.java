package com.meetingos.tduck;

import com.meetingos.common.BaseEntity;
import com.meetingos.meeting.Meeting;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "tduck_field_mappings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mapping_meeting_target", columnNames = {"meeting_id", "attendee_field"}),
        @UniqueConstraint(name = "uk_mapping_meeting_tduck_field", columnNames = {"meeting_id", "tduck_field_key"})
})
public class TduckFieldMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendee_field", length = 48)
    private AttendeeField attendeeField;

    @Column(nullable = false, length = 120)
    private String tduckFieldKey;

    @Column(length = 160)
    private String tduckFieldLabel;

    @Column(nullable = false)
    private boolean requiredMapping;

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public AttendeeField getAttendeeField() {
        return attendeeField;
    }

    public void setAttendeeField(AttendeeField attendeeField) {
        this.attendeeField = attendeeField;
    }

    public String getTduckFieldKey() {
        return tduckFieldKey;
    }

    public void setTduckFieldKey(String tduckFieldKey) {
        this.tduckFieldKey = tduckFieldKey;
    }

    public String getTduckFieldLabel() {
        return tduckFieldLabel;
    }

    public void setTduckFieldLabel(String tduckFieldLabel) {
        this.tduckFieldLabel = tduckFieldLabel;
    }

    public boolean isRequiredMapping() {
        return requiredMapping;
    }

    public void setRequiredMapping(boolean requiredMapping) {
        this.requiredMapping = requiredMapping;
    }
}
