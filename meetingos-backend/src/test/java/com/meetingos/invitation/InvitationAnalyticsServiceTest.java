package com.meetingos.invitation;

import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

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
    private MeetingInvitationRepository invitationRepository;
    private InvitationEventRepository eventRepository;
    private RegistrationAttributionRepository attributionRepository;
    private InvitationShareLinkRepository shareLinkRepository;
    private InvitationAnalyticsService service;

    @BeforeEach
    void setUp() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        invitationRepository = mock(MeetingInvitationRepository.class);
        shareLinkRepository = mock(InvitationShareLinkRepository.class);
        eventRepository = mock(InvitationEventRepository.class);
        attributionRepository = mock(RegistrationAttributionRepository.class);
        service = new InvitationAnalyticsService(
                meetingRepository, invitationRepository, shareLinkRepository, eventRepository, attributionRepository
        );

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(eventRepository.summarizeDaily(
                eq(1L), any(), any(), any(), eq(InvitationEventType.PAGE_VIEW), eq(InvitationEventType.CTA_CLICK)
        )).thenReturn(List.of());
        when(eventRepository.summarizeByChannel(
                eq(1L), any(), any(), any(), eq(InvitationEventType.PAGE_VIEW), eq(InvitationEventType.CTA_CLICK)
        )).thenReturn(List.of());
        when(attributionRepository.summarizeAttributedDaily(
                eq(1L), any(), any(), any(), eq(RegistrationAttributionStatus.ATTRIBUTED)
        )).thenReturn(List.of());
        when(attributionRepository.summarizeAttributedByChannel(
                eq(1L), any(), any(), any(), eq(RegistrationAttributionStatus.ATTRIBUTED)
        )).thenReturn(List.of());
        when(shareLinkRepository.findAnalyticsLinks(eq(1L), any())).thenReturn(List.of());
        stubAttributionTotals();
    }

    @Test
    void clickThroughRateUsesDatabaseAggregates() {
        stubEventTotals(5L, 3L, 4L, 2L);

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
        stubEventTotals(0L, 0L, 1L, 1L);

        InvitationApiModels.InvitationAnalyticsResponse response = service.analytics(
                1L, null, LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 14)
        );

        assertThat(response.uniqueVisitors()).isZero();
        assertThat(response.uniqueClickVisitors()).isEqualTo(1);
        assertThat(response.clickThroughRate()).isZero();
    }

    @Test
    void attributionDetailsUseBoundedProjection() {
        MeetingInvitation invitation = new MeetingInvitation();
        invitation.setId(10L);
        when(invitationRepository.findByIdAndMeetingId(10L, 1L)).thenReturn(Optional.of(invitation));
        LocalDateTime attributedAt = LocalDateTime.of(2026, 7, 14, 10, 0);
        when(attributionRepository.findResponseRowsByInvitationId(eq(10L), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        99L, RegistrationAttributionStatus.ATTRIBUTED, "data-1", 8L, 7L,
                        "公众号", true, attributedAt
                }));
        when(attributionRepository.countByInvitationId(10L)).thenReturn(2_500L);

        InvitationAnalyticsService.AttributionPage page = service.attributionPage(1L, 10L);
        List<InvitationApiModels.AttributionResponse> response = page.items();

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(99L);
            assertThat(item.channelName()).isEqualTo("公众号");
            assertThat(item.attributedAt()).isEqualTo(attributedAt);
        });
        assertThat(page.total()).isEqualTo(2_500);
        assertThat(page.limit()).isEqualTo(2_000);
        assertThat(page.truncated()).isTrue();
    }

    private void stubEventTotals(long pageViews, long visitors, long clicks, long clickVisitors) {
        when(eventRepository.summarize(
                eq(1L), any(), any(), any(), eq(InvitationEventType.PAGE_VIEW), eq(InvitationEventType.CTA_CLICK)
        )).thenReturn(List.<Object[]>of(new Object[]{pageViews, visitors, clicks, clickVisitors}));
    }

    private void stubAttributionTotals() {
        when(attributionRepository.summarize(
                eq(1L), any(), any(), any(),
                eq(RegistrationAttributionStatus.ATTRIBUTED),
                eq(RegistrationAttributionStatus.DIRECT_UNATTRIBUTED),
                eq(RegistrationAttributionStatus.INVALID_TOKEN),
                eq(RegistrationAttributionStatus.LEGACY_UNATTRIBUTED)
        )).thenReturn(List.<Object[]>of(new Object[]{0L, 0L, 0L, 0L, 0L, 0L}));
    }
}
