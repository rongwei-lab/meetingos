package com.meetingos.meeting;

import com.meetingos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "meetings")
public class Meeting extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 160)
    private String organizer;

    @Column(length = 160)
    private String coOrganizer;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime registrationStartTime;

    private LocalDateTime registrationEndTime;

    @Column(length = 255)
    private String location;

    @Column(length = 160)
    private String venueName;

    private Integer capacity;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String agenda;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(length = 80)
    private String contactName;

    @Column(length = 40)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MeetingStatus status = MeetingStatus.DRAFT;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false)
    private boolean seatQueryEnabled;

    @Column(nullable = false)
    private boolean roomQueryEnabled;

    @Column(nullable = false)
    private boolean checkinEnabled = true;

    @Column(nullable = false, length = 255)
    private String publicQueryFields = "PHONE,NAME";

    @Column(nullable = false, length = 20)
    private String publicQueryMatchMode = "FUZZY";

    @Column(nullable = false)
    private boolean publicQueryPaused;

    @Column(length = 160)
    private String publicQueryTitle;

    @Column(length = 500)
    private String publicQueryDescription;

    @Column(columnDefinition = "text")
    private String publicQueryAnnouncement;

    @Column(length = 160)
    private String publicQueryPublisher;

    private LocalDateTime publicQueryStartTime;

    private LocalDateTime publicQueryEndTime;

    @Column(nullable = false, length = 40)
    private String publicQueryButtonText = "立即查询";

    @Column(nullable = false, length = 255)
    private String publicQueryEmptyMessage = "未查询到结果，请核对信息后重试";

    @Column(columnDefinition = "text")
    private String publicQuerySeatNotice = "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。";

    @Column(columnDefinition = "text")
    private String publicQueryRoomNotice = "凭会议证件及身份证办理入住，接待台位于酒店大堂。";

    @Column(nullable = false, length = 20)
    private String publicQueryThemeColor = "#2563eb";

    @Column(length = 500)
    private String publicQueryLogoUrl;

    @Column(length = 500)
    private String publicQueryBackgroundUrl;

    @Column(nullable = false, length = 20)
    private String publicQueryResultLayout = "CARD";

    @Column(nullable = false, length = 500)
    private String publicQueryVisibleResultFields = "ATTENDEE_NAME,SEAT_AREA,SEAT_ROW,SEAT_NO,SEAT_CODE,HOTEL_NAME,ROOM_TYPE,ROOM_NO,ROOM_REMARK,CHECKIN_TIPS";

    @Column(nullable = false, length = 500)
    private String publicQueryMaskedResultFields = "";

    @Column(nullable = false, length = 500)
    private String publicQueryHighlightResultFields = "SEAT_CODE,ROOM_NO";

    @Column(nullable = false, length = 500)
    private String publicQuerySeatVisibleResultFields = "ATTENDEE_NAME,SEAT_AREA,SEAT_ROW,SEAT_NO,SEAT_CODE";

    @Column(nullable = false, length = 500)
    private String publicQuerySeatMaskedResultFields = "";

    @Column(nullable = false, length = 500)
    private String publicQuerySeatHighlightResultFields = "SEAT_CODE";

    @Column(nullable = false, length = 500)
    private String publicQueryRoomVisibleResultFields = "ATTENDEE_NAME,HOTEL_NAME,ROOM_TYPE,ROOM_NO,ROOM_REMARK,CHECKIN_TIPS";

    @Column(nullable = false, length = 500)
    private String publicQueryRoomMaskedResultFields = "";

    @Column(nullable = false, length = 500)
    private String publicQueryRoomHighlightResultFields = "ROOM_NO";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getCoOrganizer() {
        return coOrganizer;
    }

    public void setCoOrganizer(String coOrganizer) {
        this.coOrganizer = coOrganizer;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getRegistrationStartTime() {
        return registrationStartTime;
    }

    public void setRegistrationStartTime(LocalDateTime registrationStartTime) {
        this.registrationStartTime = registrationStartTime;
    }

    public LocalDateTime getRegistrationEndTime() {
        return registrationEndTime;
    }

    public void setRegistrationEndTime(LocalDateTime registrationEndTime) {
        this.registrationEndTime = registrationEndTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAgenda() {
        return agenda;
    }

    public void setAgenda(String agenda) {
        this.agenda = agenda;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public void setStatus(MeetingStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isSeatQueryEnabled() {
        return seatQueryEnabled;
    }

    public void setSeatQueryEnabled(boolean seatQueryEnabled) {
        this.seatQueryEnabled = seatQueryEnabled;
    }

    public boolean isRoomQueryEnabled() {
        return roomQueryEnabled;
    }

    public void setRoomQueryEnabled(boolean roomQueryEnabled) {
        this.roomQueryEnabled = roomQueryEnabled;
    }

    public boolean isCheckinEnabled() {
        return checkinEnabled;
    }

    public void setCheckinEnabled(boolean checkinEnabled) {
        this.checkinEnabled = checkinEnabled;
    }

    public String getPublicQueryFields() {
        return publicQueryFields;
    }

    public void setPublicQueryFields(String publicQueryFields) {
        this.publicQueryFields = publicQueryFields;
    }

    public String getPublicQueryMatchMode() {
        return publicQueryMatchMode;
    }

    public void setPublicQueryMatchMode(String publicQueryMatchMode) {
        this.publicQueryMatchMode = publicQueryMatchMode;
    }

    public boolean isPublicQueryPaused() {
        return publicQueryPaused;
    }

    public void setPublicQueryPaused(boolean publicQueryPaused) {
        this.publicQueryPaused = publicQueryPaused;
    }

    public String getPublicQueryTitle() {
        return publicQueryTitle;
    }

    public void setPublicQueryTitle(String publicQueryTitle) {
        this.publicQueryTitle = publicQueryTitle;
    }

    public String getPublicQueryDescription() {
        return publicQueryDescription;
    }

    public void setPublicQueryDescription(String publicQueryDescription) {
        this.publicQueryDescription = publicQueryDescription;
    }

    public String getPublicQueryAnnouncement() {
        return publicQueryAnnouncement;
    }

    public void setPublicQueryAnnouncement(String publicQueryAnnouncement) {
        this.publicQueryAnnouncement = publicQueryAnnouncement;
    }

    public String getPublicQueryPublisher() {
        return publicQueryPublisher;
    }

    public void setPublicQueryPublisher(String publicQueryPublisher) {
        this.publicQueryPublisher = publicQueryPublisher;
    }

    public LocalDateTime getPublicQueryStartTime() {
        return publicQueryStartTime;
    }

    public void setPublicQueryStartTime(LocalDateTime publicQueryStartTime) {
        this.publicQueryStartTime = publicQueryStartTime;
    }

    public LocalDateTime getPublicQueryEndTime() {
        return publicQueryEndTime;
    }

    public void setPublicQueryEndTime(LocalDateTime publicQueryEndTime) {
        this.publicQueryEndTime = publicQueryEndTime;
    }

    public String getPublicQueryButtonText() {
        return publicQueryButtonText;
    }

    public void setPublicQueryButtonText(String publicQueryButtonText) {
        this.publicQueryButtonText = publicQueryButtonText;
    }

    public String getPublicQueryEmptyMessage() {
        return publicQueryEmptyMessage;
    }

    public void setPublicQueryEmptyMessage(String publicQueryEmptyMessage) {
        this.publicQueryEmptyMessage = publicQueryEmptyMessage;
    }

    public String getPublicQuerySeatNotice() {
        return publicQuerySeatNotice;
    }

    public void setPublicQuerySeatNotice(String publicQuerySeatNotice) {
        this.publicQuerySeatNotice = publicQuerySeatNotice;
    }

    public String getPublicQueryRoomNotice() {
        return publicQueryRoomNotice;
    }

    public void setPublicQueryRoomNotice(String publicQueryRoomNotice) {
        this.publicQueryRoomNotice = publicQueryRoomNotice;
    }

    public String getPublicQueryThemeColor() {
        return publicQueryThemeColor;
    }

    public void setPublicQueryThemeColor(String publicQueryThemeColor) {
        this.publicQueryThemeColor = publicQueryThemeColor;
    }

    public String getPublicQueryLogoUrl() {
        return publicQueryLogoUrl;
    }

    public void setPublicQueryLogoUrl(String publicQueryLogoUrl) {
        this.publicQueryLogoUrl = publicQueryLogoUrl;
    }

    public String getPublicQueryBackgroundUrl() {
        return publicQueryBackgroundUrl;
    }

    public void setPublicQueryBackgroundUrl(String publicQueryBackgroundUrl) {
        this.publicQueryBackgroundUrl = publicQueryBackgroundUrl;
    }

    public String getPublicQueryResultLayout() {
        return publicQueryResultLayout;
    }

    public void setPublicQueryResultLayout(String publicQueryResultLayout) {
        this.publicQueryResultLayout = publicQueryResultLayout;
    }

    public String getPublicQueryVisibleResultFields() {
        return publicQueryVisibleResultFields;
    }

    public void setPublicQueryVisibleResultFields(String publicQueryVisibleResultFields) {
        this.publicQueryVisibleResultFields = publicQueryVisibleResultFields;
    }

    public String getPublicQueryMaskedResultFields() {
        return publicQueryMaskedResultFields;
    }

    public void setPublicQueryMaskedResultFields(String publicQueryMaskedResultFields) {
        this.publicQueryMaskedResultFields = publicQueryMaskedResultFields;
    }

    public String getPublicQueryHighlightResultFields() {
        return publicQueryHighlightResultFields;
    }

    public void setPublicQueryHighlightResultFields(String publicQueryHighlightResultFields) {
        this.publicQueryHighlightResultFields = publicQueryHighlightResultFields;
    }

    public String getPublicQuerySeatVisibleResultFields() {
        return publicQuerySeatVisibleResultFields;
    }

    public void setPublicQuerySeatVisibleResultFields(String publicQuerySeatVisibleResultFields) {
        this.publicQuerySeatVisibleResultFields = publicQuerySeatVisibleResultFields;
    }

    public String getPublicQuerySeatMaskedResultFields() {
        return publicQuerySeatMaskedResultFields;
    }

    public void setPublicQuerySeatMaskedResultFields(String publicQuerySeatMaskedResultFields) {
        this.publicQuerySeatMaskedResultFields = publicQuerySeatMaskedResultFields;
    }

    public String getPublicQuerySeatHighlightResultFields() {
        return publicQuerySeatHighlightResultFields;
    }

    public void setPublicQuerySeatHighlightResultFields(String publicQuerySeatHighlightResultFields) {
        this.publicQuerySeatHighlightResultFields = publicQuerySeatHighlightResultFields;
    }

    public String getPublicQueryRoomVisibleResultFields() {
        return publicQueryRoomVisibleResultFields;
    }

    public void setPublicQueryRoomVisibleResultFields(String publicQueryRoomVisibleResultFields) {
        this.publicQueryRoomVisibleResultFields = publicQueryRoomVisibleResultFields;
    }

    public String getPublicQueryRoomMaskedResultFields() {
        return publicQueryRoomMaskedResultFields;
    }

    public void setPublicQueryRoomMaskedResultFields(String publicQueryRoomMaskedResultFields) {
        this.publicQueryRoomMaskedResultFields = publicQueryRoomMaskedResultFields;
    }

    public String getPublicQueryRoomHighlightResultFields() {
        return publicQueryRoomHighlightResultFields;
    }

    public void setPublicQueryRoomHighlightResultFields(String publicQueryRoomHighlightResultFields) {
        this.publicQueryRoomHighlightResultFields = publicQueryRoomHighlightResultFields;
    }
}
