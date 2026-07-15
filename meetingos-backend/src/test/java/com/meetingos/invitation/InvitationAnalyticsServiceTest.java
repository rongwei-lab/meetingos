package com.meetingos.invitation;

import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvitationAnalyticsServiceTest {
    private MeetingRepository meetingRepository;
    private InvitationEventRepository eventRepository;
    private RegistrationAttributionRepository attributionRepository;
    private InvitationShareLinkRepository shareLinkRepository;
    private InvitationAnalyticsService service;
    private Meeting meeting;
    private MeetingInvitation invitation;
    private InvitationShareLink shareLink;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        MeetingInvitationRepository invitationRepository = mock(MeetingInvitationRepository.class);
        shareLinkRepository = mock(InvitationShareLinkRepository.class);
        eventRepository = mock(InvitationEventRepository.class);
        attributionRepository = mock(RegistrationAttributionRepository.class);
        service = new InvitationAnalyticsService(
                meetingRepository, invitationRepository, shareLinkRepository, eventRepository, attributionRepository
        );

        meeting = new Meeting();
        meeting.setId(1L);
        invitation = new MeetingInvitation();
        invitation.setId(10L);
        invitation.setMeeting(meeting);
        shareLink = new InvitationShareLink();
        shareLink.setId(100L);
        shareLink.setMeeting(meeting);
        shareLink.setInvitation(invitation);

        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(attributionRepository.findByMeetingIdAndAttributedAtBetweenOrderByAttributedAtAsc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(shareLinkRepository.findByMeetingIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
    }

    @Test
    void clickThroughRateUsesUniqueClickVisitorsDividedByUniqueVisitors() {
        LocalDateTime occurredAt = LocalDate.of(2026, 7, 14).atTime(10, 0);
        List<InvitationEvent> events = List.of(
                event(InvitationEventType.PAGE_VIEW, "visitor-a", occurredAt),
                event(InvitationEventType.PAGE_VIEW, "visitor-a", occurredAt.plusMinutes(1)),
                event(InvitationEventType.PAGE_VIEW, "visitor-b", occurredAt.plusMinutes(2)),
                event(InvitationEventType.PAGE_VIEW, "visitor-b", occurredAt.plusMinutes(3)),
                event(InvitationEventType.PAGE_VIEW, "visitor-c", occurredAt.plusMinutes(4)),
                event(InvitationEventType.CTA_CLICK, "visitor-a", occurredAt.plusMinutes(5)),
                event(InvitationEventType.CTA_CLICK, "visitor-a", occurredAt.plusMinutes(6)),
                event(InvitationEventType.CTA_CLICK, "visitor-b", occurredAt.plusMinutes(7)),
                event(InvitationEventType.CTA_CLICK, "visitor-b", occurredAt.plusMinutes(8))
        );
        when(eventRepository.findByMeetingIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(events);

        InvitationApiModels.InvitationAnalyticsResponse response = service.analytics(
                1L, null, LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14)
        );

        assertThat(response.pageViews()).isEqualTo(5);
        assertThat(response.uniqueVisitors()).isEqualTo(3);
        assertThat(response.ctaClicks()).isEqualTo(4);
        assertThat(response.uniqueClickVisitors()).isEqualTo(2);
        assertThat(response.clickThroughRate()).isEqualTo(0.6667);
    }

    @Test
    void clickThroughRateIsZeroWhenThereAreNoUniqueVisitors() {
        when(eventRepository.findByMeetingIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(event(
                InvitationEventType.CTA_CLICK, "visitor-a", LocalDate.of(2026, 7, 14).atTime(10, 0)
        )));

        InvitationApiModels.InvitationAnalyticsResponse response = service.analytics(
                1L, null, LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14)
        );

        assertThat(response.uniqueVisitors()).isZero();
        assertThat(response.uniqueClickVisitors()).isEqualTo(1);
        assertThat(response.clickThroughRate()).isZero();
    }

    private InvitationEvent event(InvitationEventType type, String visitorHash, LocalDateTime occurredAt) {
        InvitationEvent event = new InvitationEvent();
        event.setMeeting(meeting);
        event.setInvitation(invitation);
        event.setShareLink(shareLink);
        event.setEventType(type);
        event.setVisitorHash(visitorHash);
        event.setOccurredAt(occurredAt);
        return event;
    }
}
