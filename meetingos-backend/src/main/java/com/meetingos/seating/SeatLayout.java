package com.meetingos.seating;

import com.meetingos.common.BaseEntity;
import com.meetingos.meeting.Meeting;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat_layouts")
public class SeatLayout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @Column(nullable = false, length = 160)
    private String venueName;

    @Column(nullable = false, length = 80)
    private String areaName;

    @Column(nullable = false)
    private int rowCount;

    @Column(nullable = false)
    private int seatsPerRow;

    @Column(nullable = false)
    private int sortOrder;

    @Column(length = 160)
    private String numberingRule;

    // 80 座一排时可配置多个过道位置，规范化后的配置可能超过旧的 160 字符限制。
    @Column(length = 512)
    private String aisleSettings;

    @Column(length = 80)
    private String stageDirection;

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getSeatsPerRow() {
        return seatsPerRow;
    }

    public void setSeatsPerRow(int seatsPerRow) {
        this.seatsPerRow = seatsPerRow;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getNumberingRule() {
        return numberingRule;
    }

    public void setNumberingRule(String numberingRule) {
        this.numberingRule = numberingRule;
    }

    public String getAisleSettings() {
        return aisleSettings;
    }

    public void setAisleSettings(String aisleSettings) {
        this.aisleSettings = aisleSettings;
    }

    public String getStageDirection() {
        return stageDirection;
    }

    public void setStageDirection(String stageDirection) {
        this.stageDirection = stageDirection;
    }
}
