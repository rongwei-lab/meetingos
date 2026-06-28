package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TduckServiceBindingGuardTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private TduckFormBindingRepository bindingRepository;

    @Mock
    private TduckFieldMappingRepository mappingRepository;

    @Mock
    private TduckSyncLogRepository syncLogRepository;

    @Mock
    private AttendeeRepository attendeeRepository;

    @Mock
    private TduckClient tduckClient;

    @Mock
    private TduckSyncLogService syncLogService;

    private TduckService tduckService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        tduckService = new TduckService(
                meetingRepository,
                bindingRepository,
                mappingRepository,
                syncLogRepository,
                attendeeRepository,
                new TduckFieldMapper(),
                org.mockito.Mockito.mock(SystemSettingsService.class),
                tduckClient,
                syncLogService
        );
        meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode("NDEF2026");
        meeting.setName("数字经济论坛");

        lenient().when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        lenient().when(tduckClient.formDetails(anyString())).thenAnswer(invocation -> {
            String formKey = invocation.getArgument(0, String.class);
            return Optional.of(new TduckFormDetails(formKey, "表单-" + formKey, "2", true, 3));
        });
        lenient().when(tduckClient.registrationUrlFor(anyString())).thenAnswer(invocation -> "http://tduck.test/s/" + invocation.getArgument(0, String.class));
        lenient().when(bindingRepository.save(any(TduckFormBinding.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstBindAllowsSavingWithoutConfirmation() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.empty());

        TduckBindingResponse response = tduckService.bindForm(request("new-form", null, null));

        assertThat(response.formKey()).isEqualTo("new-form");
        assertThat(response.registrationUrl()).isEqualTo("http://tduck.test/s/new-form");
        assertThat(response.webhookStatus()).isEqualTo(WebhookStatus.CONFIGURED);
        assertThat(response.rebindingLocked()).isTrue();
        assertThat(response.rebindingLockedReason()).contains("已绑定 TDuck 表单");
        verify(tduckClient).configureWebhook("new-form");
        verify(mappingRepository, never()).deleteByMeetingId(any());
    }

    @Test
    void sameFormSaveAllowsRefreshingWithoutConfirmation() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("old-form")));

        TduckBindingResponse response = tduckService.bindForm(request("old-form", null, null));

        assertThat(response.formKey()).isEqualTo("old-form");
        verify(tduckClient).configureWebhook("old-form");
        verify(mappingRepository, never()).deleteByMeetingId(any());
    }

    @Test
    void webhookConfigurationFailureRejectsBindingSave() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new TduckClientException("TDuck 不可达"))
                .when(tduckClient)
                .configureWebhook("new-form");

        assertThatThrownBy(() -> tduckService.bindForm(request("new-form", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TDuck Webhook 配置失败");

        verify(bindingRepository, never()).save(any());
    }

    @Test
    void differentFormWithoutConfirmationFailsAfterBindingExists() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("old-form")));

        assertThatThrownBy(() -> tduckService.bindForm(request("new-form", false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前会议已绑定 TDuck 表单，不能选择其他表单");

        verify(bindingRepository, never()).save(any());
        verify(mappingRepository, never()).deleteByMeetingId(any());
    }

    @Test
    void differentFormWithWrongMeetingCodeFailsAfterBindingExists() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("old-form")));

        assertThatThrownBy(() -> tduckService.bindForm(request("new-form", true, "WRONG")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前会议已绑定 TDuck 表单，不能选择其他表单");

        verify(bindingRepository, never()).save(any());
        verify(mappingRepository, never()).deleteByMeetingId(any());
    }

    @Test
    void differentFormWithCorrectMeetingCodeStillFailsAfterBindingExists() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("old-form")));

        assertThatThrownBy(() -> tduckService.bindForm(request("new-form", true, "NDEF2026")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前会议已绑定 TDuck 表单，不能选择其他表单");

        verify(bindingRepository, never()).save(any());
        verify(mappingRepository, never()).deleteByMeetingId(any());
    }

    @Test
    void differentFormRejectsEvenWithConfirmation() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("old-form")));

        assertThatThrownBy(() -> tduckService.bindForm(request("new-form", true, "NDEF2026")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前会议已绑定 TDuck 表单，不能选择其他表单");

        verify(bindingRepository, never()).save(any());
        verify(mappingRepository, never()).deleteByMeetingId(any());
    }

    @Test
    void bindingResponseMarksRebindingLockedAfterBindingExists() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("old-form")));

        TduckBindingResponse response = tduckService.binding(1L);

        assertThat(response.rebindingLocked()).isTrue();
        assertThat(response.rebindingLockedReason()).contains("已绑定 TDuck 表单");
    }

    @Test
    void bindingResponseUsesCurrentRegistrationUrlWhenStoredUrlIsStale() {
        TduckFormBinding binding = existingBinding("old-form");
        binding.setRegistrationUrl("http://stale.example.com/s/old-form");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));

        TduckBindingResponse response = tduckService.binding(1L);

        assertThat(response.validationStatus()).isEqualTo(TduckBindingValidationStatus.VALID);
        assertThat(response.registrationUrl()).isEqualTo("http://tduck.test/s/old-form");
    }

    private BindFormRequest request(String formKey, Boolean replaceExisting, String confirmMeetingCode) {
        return new BindFormRequest(
                1L,
                formKey,
                null,
                null,
                TduckBindingStatus.BOUND,
                WebhookStatus.CONFIGURED,
                replaceExisting,
                confirmMeetingCode,
                "测试换绑"
        );
    }

    private TduckFormBinding existingBinding(String formKey) {
        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey(formKey);
        binding.setFormName("旧表单");
        binding.setRegistrationUrl("http://tduck.test/s/" + formKey);
        binding.setBindingStatus(TduckBindingStatus.BOUND);
        binding.setWebhookStatus(WebhookStatus.CONFIGURED);
        binding.setMappingStatus(MappingStatus.VALID);
        return binding;
    }
}
