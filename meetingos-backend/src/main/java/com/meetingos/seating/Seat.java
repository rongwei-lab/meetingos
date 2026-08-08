package com.meetingos.seating;

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
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_seat_meeting_area_row_no", columnNames = {"meeting_id", "area_name", "row_no", "seat_no"})
})
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id")
    @JsonIgnore
    private SeatLayout layout;

    @Column(nullable = false, length = 80)
    private String areaName;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Column(nullable = false, length = 80)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SeatType seatType = SeatType.NORMAL;

    @Column(nullable = false)
    private boolean disabled;

    /**
     * 座位图中的结构留白（例如扇形会场两侧的空格）。
     *
     * 留白仍需保留在网格里，才能维持每排的横向坐标；但它不是可用座位，管理端也不应把它
     * 统计成禁用座位。该状态与人工停用的 disabled 分开保存，避免后续维护布局时误隐藏真实座位。
     */
    @Column(nullable = false)
    private boolean structuralBlank;

    /**
     * 座位图中的过道。过道需要占据网格坐标以保持现场座位图的版式，
     * 但不能作为座位被分配、锁定或改成其他座位类型。
     */
    @Column(nullable = false)
    private boolean aisle;

    @Column(nullable = false)
    private boolean locked;

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public SeatLayout getLayout() {
        return layout;
    }

    public void setLayout(SeatLayout layout) {
        this.layout = layout;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public int getRowNo() {
        return rowNo;
    }

    public void setRowNo(int rowNo) {
        this.rowNo = rowNo;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(int seatNo) {
        this.seatNo = seatNo;
    }

    public String getSeatCode() {
        return seatCode;
    }

    public void setSeatCode(String seatCode) {
        this.seatCode = seatCode;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isStructuralBlank() {
        return structuralBlank;
    }

    public void setStructuralBlank(boolean structuralBlank) {
        this.structuralBlank = structuralBlank;
    }

    public boolean isAisle() {
        return aisle;
    }

    public void setAisle(boolean aisle) {
        this.aisle = aisle;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
