package com.meetingos.tduck;

import com.meetingos.invitation.InvitationProperties;
import com.meetingos.invitation.InvitationTrackingTokenService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TduckOrganizationLookupServiceTest {
    @Mock private TduckFormBindingRepository bindings;
    @Mock private TianyanchaClient client;
    @Mock private TduckClient tduck;
    private final InvitationTrackingTokenService tokens = new InvitationTrackingTokenService(
            new InvitationProperties("test-organization-secret-at-least-32-bytes", 180, ""));
    private TduckOrganizationLookupService service;
    private Meeting meeting;
    private TduckFormBinding binding;

    @BeforeEach
    void setUp() {
        service = new TduckOrganizationLookupService(bindings, tokens, client, tduck);
        meeting = new Meeting();
        meeting.setId(7L);
        meeting.setStatus(MeetingStatus.REGISTRATION_OPEN);
        binding = new TduckFormBinding();
        binding.setFormKey("bound-form");
        binding.setMeeting(meeting);
        binding.setRegistrationEnabled(true);
    }

    private TduckOrganizationLookupService.LookupConfig enable() {
        when(client.configured()).thenReturn(true);
        when(bindings.findWithMeetingByFormKey("bound-form")).thenReturn(Optional.of(binding));
        when(tduck.organizationLookupEnabled("bound-form")).thenReturn(true);
        return service.issueToken("bound-form");
    }

    @Test
    void onlyConfiguredActiveBoundFormsWithEnabledOrganizationFieldReceiveScopedToken() {
        var config = enable();
        assertThat(config.enabled()).isTrue();
        var claims = tokens.verify(config.token()).orElseThrow();
        assertThat(claims.clickPublicId()).startsWith("org_");
        assertThat(claims.meetingId()).isEqualTo(7L);
        assertThat(tokens.matchesFormKey(claims, "bound-form")).isTrue();
        assertThat(config.expiresInSeconds()).isEqualTo(600);
        verify(client, never()).search(anyString(), anyString());
    }

    @Test
    void disabledProviderOrUnboundFormDoesNotReachTduckOrPaidSearch() {
        assertThat(service.issueToken("bound-form").enabled()).isFalse();
        verifyNoInteractions(bindings, tduck);
        when(client.configured()).thenReturn(true);
        assertThat(service.issueToken("unknown-form").enabled()).isFalse();
        verifyNoInteractions(tduck);
    }

    @Test
    void disabledFieldOrUnavailableTduckDoesNotIssueToken() {
        when(client.configured()).thenReturn(true);
        when(bindings.findWithMeetingByFormKey("bound-form")).thenReturn(Optional.of(binding));
        assertThat(service.issueToken("bound-form").enabled()).isFalse();
        when(tduck.organizationLookupEnabled("bound-form")).thenThrow(new RuntimeException("unavailable"));
        assertThat(service.issueToken("bound-form").enabled()).isFalse();
        verify(client, never()).search(anyString(), anyString());
    }

    @Test
    void invalidExpiredCrossFormAndOtherPurposeTokensDoNotReachPaidSearch() {
        var config = enable();
        for (String token : List.of("invalid", config.token() + "x",
                tokens.create("org_" + tokens.newOpaqueId(), 7L, "bound-form", Instant.now().minusSeconds(1)),
                tokens.create(tokens.newOpaqueId(), 7L, "bound-form", Instant.now().plusSeconds(60)))) {
            assertThat(service.search("bound-form", token, "北京百度", "client").code()).isEqualTo("INVALID_TOKEN");
        }
        assertThat(service.search("other-form", config.token(), "北京百度", "client").code()).isEqualTo("INVALID_TOKEN");
        verify(client, never()).search(anyString(), anyString());
    }

    @Test
    void closureAndRegistrationDeadlineAlsoInvalidatePreviouslyIssuedAccess() {
        var config = enable();
        binding.setRegistrationEnabled(false);
        assertThat(service.search("bound-form", config.token(), "北京百度", "client").available()).isFalse();
        binding.setRegistrationEnabled(true);
        meeting.setRegistrationEndTime(LocalDateTime.now().minusSeconds(1));
        assertThat(service.search("bound-form", config.token(), "北京百度", "client").available()).isFalse();
        verify(client, never()).search(anyString(), anyString());
    }

    @Test
    void validQueryOnlyPassesNameKeywordAndClientToProvider() {
        var config = enable();
        when(client.search("北京百度", "client")).thenReturn(TianyanchaClient.Suggestions.ok(List.of()));
        assertThat(service.search("bound-form", config.token(), "北京百度", "client").available()).isTrue();
        verify(client).search("北京百度", "client");
    }
}
