package com.meetingos.dinner;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "dinner_tables", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dinner_table_meeting_no", columnNames = {"meeting_id", "table_no"})
})
public class DinnerTable extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @Column(name = "table_no", nullable = false, length = 80)
    private String tableNo;

    @Column(nullable = false)
    private Integer capacity = 10;

    @Column(length = 160)
    private String venue;

    @Column(length = 120)
    private String dinnerTime;

    @Column(length = 500)
    private String remark;

    @Column(length = 64)
    private String createdBy;

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public String getTableNo() {
        return tableNo;
    }

    public void setTableNo(String tableNo) {
        this.tableNo = tableNo;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
