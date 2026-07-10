package com.meetingos.tduck;

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

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "tduck_sync_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tduck_sync_success_event", columnNames = "success_event_id")
})
public class TduckSyncLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    @JsonIgnore
    private Meeting meeting;

    @Column(nullable = false, length = 120)
    private String formKey;

    @Column(length = 120)
    private String eventId;

    /** 仅 SUCCESS 日志写入，用数据库唯一约束关闭并发“先查后写”的幂等窗口。 */
    @Column(length = 120)
    private String successEventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncStatus status;

    @Column(length = 500)
    private String message;

    @Column
    private Long attendeeId;

    @Column(length = 120)
    private String tduckDataId;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public String getFormKey() {
        return formKey;
    }

    public void setFormKey(String formKey) {
        this.formKey = formKey;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getSuccessEventId() {
        return successEventId;
    }

    public void setSuccessEventId(String successEventId) {
        this.successEventId = successEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getAttendeeId() {
        return attendeeId;
    }

    public void setAttendeeId(Long attendeeId) {
        this.attendeeId = attendeeId;
    }

    public String getTduckDataId() {
        return tduckDataId;
    }

    public void setTduckDataId(String tduckDataId) {
        this.tduckDataId = tduckDataId;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
}
