package com.meetingos.publicapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.meetingos.attendee.Attendee;
import com.meetingos.common.BaseEntity;
import com.meetingos.common.JsonMapConverter;
import com.meetingos.meeting.Meeting;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "public_query_history")
public class PublicQueryHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicQueryType queryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicQueryStatus queryStatus;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> queryValues = new LinkedHashMap<>();

    @Column(length = 1000)
    private String queryText;

    @Column(length = 500)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id")
    @JsonIgnore
    private Attendee attendee;

    @Column(length = 120)
    private String attendeeName;

    @Column(length = 40)
    private String attendeePhone;

    @Column(length = 500)
    private String resultSummary;

    @Column(length = 45)
    private String clientIp;

    @Column(length = 500)
    private String userAgent;

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public PublicQueryType getQueryType() {
        return queryType;
    }

    public void setQueryType(PublicQueryType queryType) {
        this.queryType = queryType;
    }

    public PublicQueryStatus getQueryStatus() {
        return queryStatus;
    }

    public void setQueryStatus(PublicQueryStatus queryStatus) {
        this.queryStatus = queryStatus;
    }

    public Map<String, Object> getQueryValues() {
        return queryValues;
    }

    public void setQueryValues(Map<String, Object> queryValues) {
        this.queryValues = queryValues;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Attendee getAttendee() {
        return attendee;
    }

    public void setAttendee(Attendee attendee) {
        this.attendee = attendee;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public void setAttendeeName(String attendeeName) {
        this.attendeeName = attendeeName;
    }

    public String getAttendeePhone() {
        return attendeePhone;
    }

    public void setAttendeePhone(String attendeePhone) {
        this.attendeePhone = attendeePhone;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
