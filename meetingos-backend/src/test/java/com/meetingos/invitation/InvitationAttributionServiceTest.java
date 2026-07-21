package com.meetingos.invitation;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeAttributionStateSynchronizer;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationAttributionServiceTest {
    private RegistrationAttributionRepository attributionRepository;
    private InvitationEventRepository eventRepository;
    private MeetingRepository meetingRepository;
    private AttendeeRepository attendeeRepository;
    private InvitationTrackingTokenService tokenService;
    private AttendeeAttributionStateSynchronizer attributionStateSynchronizer;
    private InvitationAttributionService service;
    private Meeting meeting;
    private Attendee attendee;

    @BeforeEach
    void setUp() {
        attributionRepository = mock(RegistrationAttributionRepository.class);
        eventRepository = mock(InvitationEventRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        attendeeRepository = mock(AttendeeRepository.class);
        attributionStateSynchronizer = mock(AttendeeAttributionStateSynchronizer.class);
        tokenService = new InvitationTrackingTokenService(new InvitationProperties(
                "test-invitation-tracking-secret-that-is-long-enough", 180, "test-amap-key"
        ));
        service = new InvitationAttributionService(
                attributionRepository,
                eventRepository,
                meetingRepository,
                attendeeRepository,
                tokenService,
                attributionStateSynchronizer
        );

        meeting = new Meeting();
        meeting.setId(1L);
        attendee = new Attendee();
        attendee.setId(9L);
        attendee.setMeeting(meeting);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        when(attributionRepository.findForUpdate(1L, "data-1")).thenReturn(Optional.empty());
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(attendeeRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(attendee));
    }

    @Test
    void recordsDirectAndInvalidTokenWithoutThrowing() {
        service.attributeFirstSubmission(1L, 9L, "form-1", "data-1", null);
        RegistrationAttribution direct = captureSaved();
        assertThat(direct.getAttributionStatus()).isEqualTo(RegistrationAttributionStatus.DIRECT_UNATTRIBUTED);
        assertThat(direct.getInvitation()).isNull();

        org.mockito.Mockito.reset(attributionRepository);
        when(attributionRepository.findForUpdate(1L, "data-1")).thenReturn(Optional.empty());
        service.attributeFirstSubmission(1L, 9L, "form-1", "data-1", "not-a-valid-token");
        assertThat(captureSaved().getAttributionStatus()).isEqualTo(RegistrationAttributionStatus.INVALID_TOKEN);
    }

    @Test
    void attributesValidClickAndPreservesFirstSourceOnUpdate() {
        InvitationEvent event = clickEvent();
        Instant expiry = event.getClickExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        String token = tokenService.create(event.getClickPublicId(), 1L, "form-1", expiry);
        when(eventRepository.findByClickPublicId(event.getClickPublicId())).thenReturn(Optional.of(event));

        service.attributeFirstSubmission(1L, 9L, "form-1", "data-1", token);
        RegistrationAttribution saved = captureSaved();
        assertThat(saved.getAttributionStatus()).isEqualTo(RegistrationAttributionStatus.ATTRIBUTED);
        assertThat(saved.getClickEvent()).isSameAs(event);

        RegistrationAttribution existing = new RegistrationAttribution();
        existing.setMeeting(meeting);
        existing.setAttributionStatus(RegistrationAttributionStatus.ATTRIBUTED);
        existing.setActive(false);
        when(attributionRepository.findForUpdate(1L, "data-1")).thenReturn(Optional.of(existing));
        service.attributeFirstSubmission(1L, 9L, "form-1", "data-1", "different-token");
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getAttributionStatus()).isEqualTo(RegistrationAttributionStatus.ATTRIBUTED);
        verify(attributionStateSynchronizer, times(2)).synchronize(1L, 9L, true);
    }

    @Test
    void invalidIdentifiersAreNoOpAndCancellationOnlyChangesActive() {
        service.attributeFirstSubmission(null, 9L, "form-1", "data-1", null);
        verify(attributionRepository, never()).save(any());

        RegistrationAttribution existing = new RegistrationAttribution();
        existing.setActive(true);
        when(attributionRepository.findForUpdate(1L, "data-1")).thenReturn(Optional.of(existing));
        service.markInactive(1L, "data-1");
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void laterTduckUpdateCannotReactivateAdministratorRejectedRegistration() {
        RegistrationAttribution existing = new RegistrationAttribution();
        existing.setMeeting(meeting);
        existing.setAttendee(attendee);
        existing.setAttributionStatus(RegistrationAttributionStatus.ATTRIBUTED);
        existing.setActive(false);
        attendee.setReviewStatus(ReviewStatus.REJECTED);
        when(attributionRepository.findForUpdate(1L, "data-1")).thenReturn(Optional.of(existing));

        service.attributeFirstSubmission(1L, 9L, "form-1", "data-1", "new-token");

        assertThat(existing.isActive()).isFalse();
        verify(attributionRepository).save(existing);
    }

    @Test
    void pendingRegistrationLeavesFinalActiveStateToSubmissionHistory() {
        RegistrationAttribution existing = new RegistrationAttribution();
        existing.setMeeting(meeting);
        existing.setAttendee(attendee);
        existing.setAttributionStatus(RegistrationAttributionStatus.DIRECT_UNATTRIBUTED);
        existing.setActive(false);
        attendee.setReviewStatus(ReviewStatus.PENDING);
        when(attributionRepository.findForUpdate(1L, "data-1")).thenReturn(Optional.of(existing));

        service.attributeFirstSubmission(1L, 9L, "form-1", "data-1", null);

        assertThat(existing.isActive()).isFalse();
        verify(attributionRepository).save(existing);
        verify(attributionStateSynchronizer).synchronize(1L, 9L, true);
    }

    @Test
    void synchronizesResolvedSubmissionUsingCurrentAttendeeReviewState() {
        service.synchronizeSubmissionAttributionState(1L, 9L);

        verify(attributionStateSynchronizer).synchronize(1L, 9L, true);

        attendee.setReviewStatus(ReviewStatus.REJECTED);
        service.synchronizeSubmissionAttributionState(1L, 9L);

        verify(attributionStateSynchronizer).synchronize(1L, 9L, false);
    }

    private RegistrationAttribution captureSaved() {
        ArgumentCaptor<RegistrationAttribution> captor = ArgumentCaptor.forClass(RegistrationAttribution.class);
        verify(attributionRepository).save(captor.capture());
        return captor.getValue();
    }

    private InvitationEvent clickEvent() {
        MeetingInvitation invitation = new MeetingInvitation();
        invitation.setId(2L);
        invitation.setMeeting(meeting);
        MeetingInvitationVersion version = new MeetingInvitationVersion();
        version.setId(3L);
        version.setInvitation(invitation);
        InvitationShareLink link = new InvitationShareLink();
        link.setId(4L);
        link.setMeeting(meeting);
        link.setInvitation(invitation);
        link.setCurrentVersion(version);
        InvitationEvent event = new InvitationEvent();
        event.setMeeting(meeting);
        event.setInvitation(invitation);
        event.setVersion(version);
        event.setShareLink(link);
        event.setEventType(InvitationEventType.CTA_CLICK);
        event.setClickPublicId("abcdefghijklmnopqrstuvwx");
        event.setClickExpiresAt(LocalDateTime.now().plusHours(1));
        return event;
    }
}
