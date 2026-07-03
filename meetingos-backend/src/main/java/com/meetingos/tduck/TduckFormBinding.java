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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tduck_form_bindings")
public class TduckFormBinding extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    @JsonIgnore
    private Meeting meeting;

    @Column(nullable = false, unique = true, length = 120)
    private String formKey;

    @Column(nullable = false, length = 160)
    private String formName;

    @Column(nullable = false, length = 500)
    private String registrationUrl;

    @Column(nullable = false)
    private boolean registrationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TduckBindingStatus bindingStatus = TduckBindingStatus.BOUND;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MappingStatus mappingStatus = MappingStatus.NOT_CONFIGURED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookStatus webhookStatus = WebhookStatus.NOT_CONFIGURED;

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

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getRegistrationUrl() {
        return registrationUrl;
    }

    public void setRegistrationUrl(String registrationUrl) {
        this.registrationUrl = registrationUrl;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public TduckBindingStatus getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(TduckBindingStatus bindingStatus) {
        this.bindingStatus = bindingStatus;
    }

    public MappingStatus getMappingStatus() {
        return mappingStatus;
    }

    public void setMappingStatus(MappingStatus mappingStatus) {
        this.mappingStatus = mappingStatus;
    }

    public WebhookStatus getWebhookStatus() {
        return webhookStatus;
    }

    public void setWebhookStatus(WebhookStatus webhookStatus) {
        this.webhookStatus = webhookStatus;
    }
}
