package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.invitation.InvitationTrackingTokenService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TduckPrecheckServiceTest {

    @Mock
    private TduckFormBindingRepository bindingRepository;

    @Mock
    private TduckFieldMappingRepository mappingRepository;

    @Mock
    private AttendeeRepository attendeeRepository;

    @Mock
    private InvitationTrackingTokenService tokenService;

    private TduckPrecheckService service;
    private Meeting meeting;
    private TduckFormBinding binding;
    private TduckFieldMapping phoneMapping;

    @BeforeEach
    void setUp() {
        service = new TduckPrecheckService(bindingRepository, mappingRepository, attendeeRepository, tokenService);

        meeting = new Meeting();
        meeting.setId(7L);
        meeting.setCode("CONF2026");
        meeting.setStatus(MeetingStatus.REGISTRATION_OPEN);

        binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-key");
        binding.setRegistrationEnabled(true);

        phoneMapping = new TduckFieldMapping();
        phoneMapping.setMeeting(meeting);
        phoneMapping.setAttendeeField(AttendeeField.PHONE);
        phoneMapping.setTduckFieldKey("input-phone");

        when(bindingRepository.findByFormKey("form-key")).thenReturn(Optional.of(binding));
    }

    @Test
    void issueTokenContainsOnlyShortLivedFormBindingConfig() {
        when(mappingRepository.findByMeetingIdAndAttendeeField(7L, AttendeeField.PHONE))
                .thenReturn(Optional.of(phoneMapping));
        when(tokenService.newOpaqueId()).thenReturn("opaque-click-id");
        when(tokenService.create(eq("opaque-click-id"), eq(7L), eq("form-key"), any(Instant.class)))
                .thenReturn("signed-precheck-token");

        TduckPrecheckTokenResponse response = service.issueToken(" form-key ");

        assertThat(response.enabled()).isTrue();
        assertThat(response.token()).isEqualTo("signed-precheck-token");
        assertThat(response.phoneFieldKey()).isEqualTo("input-phone");
        assertThat(response.expiresInSeconds()).isEqualTo(600);
        assertThat(response.message()).isEqualTo("手机号重复校验已启用");
    }

    @Test
    void duplicatePhoneReturnsDuplicateWithoutExposingAttendeeData() {
        when(mappingRepository.findByMeetingIdAndAttendeeField(7L, AttendeeField.PHONE))
                .thenReturn(Optional.of(phoneMapping));
        when(tokenService.verify("signed-precheck-token"))
                .thenReturn(Optional.of(new InvitationTrackingTokenService.TrackingClaims(
                        "opaque-click-id", 7L, "form-fingerprint", Instant.now().plusSeconds(300)
                )));
        when(tokenService.matchesFormKey(any(), eq("form-key"))).thenReturn(true);
        when(attendeeRepository.findByMeetingIdAndPhone(7L, "13800138000"))
                .thenReturn(Optional.of(new Attendee()));

        TduckPhonePrecheckResponse response = service.checkPhone(
                new TduckPhonePrecheckRequest("form-key", "signed-precheck-token", "138-0013 8000")
        );

        assertThat(response.available()).isFalse();
        assertThat(response.code()).isEqualTo("DUPLICATE");
        assertThat(response.message()).isEqualTo("该手机号已报名，请勿重复提交");
    }

    @Test
    void availablePhoneIsNormalizedBeforeLookup() {
        when(mappingRepository.findByMeetingIdAndAttendeeField(7L, AttendeeField.PHONE))
                .thenReturn(Optional.of(phoneMapping));
        when(tokenService.verify(anyString()))
                .thenReturn(Optional.of(new InvitationTrackingTokenService.TrackingClaims(
                        "opaque-click-id", 7L, "form-fingerprint", Instant.now().plusSeconds(300)
                )));
        when(tokenService.matchesFormKey(any(), eq("form-key"))).thenReturn(true);
        when(attendeeRepository.findByMeetingIdAndPhone(7L, "13800138000"))
                .thenReturn(Optional.empty());

        TduckPhonePrecheckResponse response = service.checkPhone(
                new TduckPhonePrecheckRequest("form-key", "signed-precheck-token", "138-0013 8000")
        );

        assertThat(response.available()).isTrue();
        assertThat(response.code()).isEqualTo("AVAILABLE");
        verify(attendeeRepository).findByMeetingIdAndPhone(7L, "13800138000");
    }

    @Test
    void malformedPhoneAndInvalidTokenDoNotQueryAttendees() {
        TduckPhonePrecheckResponse malformed = service.checkPhone(
                new TduckPhonePrecheckRequest("form-key", "signed-precheck-token", "not-a-phone")
        );
        assertThat(malformed.code()).isEqualTo("INVALID_PHONE");

        when(tokenService.verify("bad-token")).thenReturn(Optional.empty());
        TduckPhonePrecheckResponse invalidToken = service.checkPhone(
                new TduckPhonePrecheckRequest("form-key", "bad-token", "13800138000")
        );
        assertThat(invalidToken.code()).isEqualTo("INVALID_TOKEN");
        verify(attendeeRepository, never()).findByMeetingIdAndPhone(any(), anyString());
    }

    @Test
    void missingMappingDisablesPrecheckWithoutBlockingForm() {
        when(mappingRepository.findByMeetingIdAndAttendeeField(7L, AttendeeField.PHONE))
                .thenReturn(Optional.empty());

        TduckPrecheckTokenResponse response = service.issueToken("form-key");

        assertThat(response.enabled()).isFalse();
        assertThat(response.token()).isNull();
        assertThat(response.message()).isEqualTo("手机号字段映射尚未配置");
    }

    @Test
    void closedMeetingDoesNotIssuePrecheckToken() {
        meeting.setStatus(MeetingStatus.REGISTRATION_CLOSED);

        TduckPrecheckTokenResponse response = service.issueToken("form-key");

        assertThat(response.enabled()).isFalse();
        assertThat(response.message()).isEqualTo("会议当前不在报名开放状态");
        verifyNoInteractions(mappingRepository, tokenService);
    }
}
