package com.meetingos.invitation;

import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvitationEventRetentionServiceIntegrationTest {
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private MeetingInvitationRepository invitationRepository;
    @Autowired
    private MeetingInvitationVersionRepository versionRepository;
    @Autowired
    private InvitationShareLinkRepository shareLinkRepository;
    @Autowired
    private InvitationEventRepository eventRepository;
    @Autowired
    private RegistrationAttributionRepository attributionRepository;
    @Autowired
    private InvitationEventRetentionService retentionService;
    @Autowired
    private InvitationAnalyticsService analyticsService;
    @Autowired
    private EntityManager entityManager;

    @Test
    void deletesLateEventsForExpiredMeetingAndKeepsAttribution() {
        LocalDateTime now = LocalDateTime.now();
        Meeting meeting = saveExpiredMeeting(now);
        MeetingInvitation invitation = saveInvitation(meeting);
        MeetingInvitationVersion version = saveVersion(invitation);
        InvitationShareLink shareLink = saveShareLink(meeting, invitation, version);

        InvitationEvent pageView = saveEvent(
                meeting, invitation, version, shareLink, InvitationEventType.PAGE_VIEW,
                "late-page-view", now.plusDays(1), null);
        InvitationEvent click = saveEvent(
                meeting, invitation, version, shareLink, InvitationEventType.CTA_CLICK,
                "late-cta-click", now.plusDays(1), "late-click-public-id");
        RegistrationAttribution attribution = saveAttribution(
                meeting, invitation, version, shareLink, click, now);

        entityManager.flush();
        entityManager.clear();

        assertThat(retentionService.cleanupExpiredEvents()).isEqualTo(2);

        entityManager.flush();
        entityManager.clear();

        assertThat(eventRepository.findById(pageView.getId())).isEmpty();
        assertThat(eventRepository.findById(click.getId())).isEmpty();
        RegistrationAttribution retained = attributionRepository.findById(attribution.getId()).orElseThrow();
        assertThat(retained.getClickEvent()).isNull();
        assertThat(retained.getAttributionStatus()).isEqualTo(RegistrationAttributionStatus.ATTRIBUTED);
        assertThat(retained.getTduckDataId()).isEqualTo("retention-data-id");
    }

    @Test
    void analyticsExecutesDatabaseAggregatesAndBoundedDetailProjection() {
        LocalDateTime now = LocalDateTime.now();
        Meeting meeting = saveExpiredMeeting(now);
        MeetingInvitation invitation = saveInvitation(meeting);
        MeetingInvitationVersion version = saveVersion(invitation);
        InvitationShareLink shareLink = saveShareLink(meeting, invitation, version);
        saveEvent(meeting, invitation, version, shareLink, InvitationEventType.PAGE_VIEW,
                "analytics-page-view", now, null);
        InvitationEvent click = saveEvent(meeting, invitation, version, shareLink, InvitationEventType.CTA_CLICK,
                "analytics-cta-click", now.plusMinutes(1), "analytics-click-public-id");
        saveAttribution(meeting, invitation, version, shareLink, click, now.plusMinutes(2));
        entityManager.flush();
        entityManager.clear();

        InvitationApiModels.InvitationAnalyticsResponse response = analyticsService.analytics(
                meeting.getId(), invitation.getId(), now.toLocalDate(), now.toLocalDate()
        );

        assertThat(response.pageViews()).isEqualTo(1);
        assertThat(response.uniqueVisitors()).isEqualTo(1);
        assertThat(response.ctaClicks()).isEqualTo(1);
        assertThat(response.attributedRegistrations()).isEqualTo(1);
        assertThat(response.daily()).singleElement().satisfies(metric ->
                assertThat(metric.attributedRegistrations()).isEqualTo(1));
        assertThat(response.channels()).singleElement().satisfies(metric -> {
            assertThat(metric.pageViews()).isEqualTo(1);
            assertThat(metric.attributedRegistrations()).isEqualTo(1);
        });
        assertThat(analyticsService.attributions(meeting.getId(), invitation.getId()))
                .singleElement()
                .satisfies(attribution -> assertThat(attribution.tduckDataId()).isEqualTo("retention-data-id"));
    }

    private Meeting saveExpiredMeeting(LocalDateTime now) {
        Meeting meeting = new Meeting();
        meeting.setCode("INV-RETENTION-2026");
        meeting.setPublicQueryToken("inv_retention_public_token");
        meeting.setName("邀请事件保留期测试会议");
        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndTime(now.minusDays(181));
        return meetingRepository.saveAndFlush(meeting);
    }

    private MeetingInvitation saveInvitation(Meeting meeting) {
        MeetingInvitation invitation = new MeetingInvitation();
        invitation.setMeeting(meeting);
        invitation.setName("保留期测试邀请函");
        invitation.setStatus(InvitationStatus.PUBLISHED);
        invitation.setDraftDocument(InvitationDesignDocumentV1.empty());
        return invitationRepository.saveAndFlush(invitation);
    }

    private MeetingInvitationVersion saveVersion(MeetingInvitation invitation) {
        MeetingInvitationVersion version = new MeetingInvitationVersion();
        version.setInvitation(invitation);
        version.setVersionNo(1);
        version.setDesignDocument(InvitationDesignDocumentV1.empty());
        version.setMeetingSnapshot(InvitationMeetingSnapshot.from(invitation.getMeeting()));
        return versionRepository.saveAndFlush(version);
    }

    private InvitationShareLink saveShareLink(Meeting meeting,
                                               MeetingInvitation invitation,
                                               MeetingInvitationVersion version) {
        InvitationShareLink shareLink = new InvitationShareLink();
        shareLink.setMeeting(meeting);
        shareLink.setInvitation(invitation);
        shareLink.setCurrentVersion(version);
        shareLink.setChannelType(InvitationChannelType.EMAIL);
        shareLink.setChannelName("保留期测试渠道");
        shareLink.setShareToken("retention-share-token");
        return shareLinkRepository.saveAndFlush(shareLink);
    }

    private InvitationEvent saveEvent(Meeting meeting,
                                        MeetingInvitation invitation,
                                        MeetingInvitationVersion version,
                                        InvitationShareLink shareLink,
                                        InvitationEventType eventType,
                                        String clientEventId,
                                        LocalDateTime occurredAt,
                                        String clickPublicId) {
        InvitationEvent event = new InvitationEvent();
        event.setMeeting(meeting);
        event.setInvitation(invitation);
        event.setVersion(version);
        event.setShareLink(shareLink);
        event.setEventType(eventType);
        event.setClientEventId(clientEventId);
        event.setVisitorHash("a".repeat(64));
        event.setOccurredAt(occurredAt);
        event.setClickPublicId(clickPublicId);
        if (clickPublicId != null) {
            event.setClickExpiresAt(occurredAt.plusDays(1));
        }
        return eventRepository.saveAndFlush(event);
    }

    private RegistrationAttribution saveAttribution(Meeting meeting,
                                                     MeetingInvitation invitation,
                                                     MeetingInvitationVersion version,
                                                     InvitationShareLink shareLink,
                                                     InvitationEvent click,
                                                     LocalDateTime attributedAt) {
        RegistrationAttribution attribution = new RegistrationAttribution();
        attribution.setMeeting(meeting);
        attribution.setInvitation(invitation);
        attribution.setVersion(version);
        attribution.setShareLink(shareLink);
        attribution.setClickEvent(click);
        attribution.setAttributionStatus(RegistrationAttributionStatus.ATTRIBUTED);
        attribution.setFormKey("retention-form-key");
        attribution.setTduckDataId("retention-data-id");
        attribution.setActive(true);
        attribution.setAttributedAt(attributedAt);
        return attributionRepository.saveAndFlush(attribution);
    }
}
