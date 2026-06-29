package com.meetingos.dinner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.meetingos.attendee.Attendee;
import com.meetingos.common.BaseEntity;
import com.meetingos.meeting.Meeting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "dinner_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dinner_assignment_attendee", columnNames = {"meeting_id", "attendee_id"})
})
public class DinnerAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendee_id", nullable = false)
    @JsonIgnore
    private Attendee attendee;

    @Column(nullable = false, length = 80)
    private String tableNo;

    @Column(length = 160)
    private String venue;

    @Column(length = 120)
    private String dinnerTime;

    @Column(length = 500)
    private String remark;

    @Column(length = 64)
    private String assignedBy;

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

    public String getTableNo() {
        return tableNo;
    }

    public void setTableNo(String tableNo) {
        this.tableNo = tableNo;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getDinnerTime() {
        return dinnerTime;
    }

    public void setDinnerTime(String dinnerTime) {
        this.dinnerTime = dinnerTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }
}
