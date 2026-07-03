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
import java.util.List;

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
        lenient().when(mappingRepository.save(any(TduckFieldMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
    void differentFormAllowsReplacementWhenExistingBindingIsInvalid() {
        TduckFormBinding binding = existingBinding("missing-form");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDetails("missing-form")).thenReturn(Optional.empty());

        TduckBindingResponse response = tduckService.bindForm(request("new-form", true, "NDEF2026"));

        assertThat(response.formKey()).isEqualTo("new-form");
        assertThat(response.validationStatus()).isEqualTo(TduckBindingValidationStatus.VALID);
        verify(tduckClient).configureWebhook("new-form");
    }

    @Test
    void replacementRejectsFormKeyAlreadyBoundToAnotherMeeting() {
        TduckFormBinding binding = existingBinding("missing-form");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDetails("missing-form")).thenReturn(Optional.empty());

        Meeting anotherMeeting = new Meeting();
        anotherMeeting.setId(2L);
        anotherMeeting.setCode("OTHER2026");
        TduckFormBinding occupied = existingBinding("new-form");
        occupied.setMeeting(anotherMeeting);
        when(bindingRepository.findByFormKey("new-form")).thenReturn(Optional.of(occupied));

        assertThatThrownBy(() -> tduckService.bindForm(request("new-form", true, "NDEF2026")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已绑定其他会议");

        verify(tduckClient, never()).configureWebhook("new-form");
        verify(bindingRepository, never()).save(any());
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

    @Test
    void bindingResponseAllowsRebindingWhenCurrentFormNoLongerExists() {
        TduckFormBinding binding = existingBinding("missing-form");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDetails("missing-form")).thenReturn(Optional.empty());

        TduckBindingResponse response = tduckService.binding(1L);

        assertThat(response.validationStatus()).isEqualTo(TduckBindingValidationStatus.INVALID);
        assertThat(response.rebindingLocked()).isFalse();
        assertThat(response.rebindingLockedReason()).contains("重新选择");
    }

    @Test
    void disabledRegistrationDoesNotMarkStoppedTduckFormAsInvalidBinding() {
        TduckFormBinding binding = existingBinding("old-form");
        binding.setRegistrationEnabled(false);
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDetails("old-form"))
                .thenReturn(Optional.of(new TduckFormDetails("old-form", "旧表单", "3", false, 3)));

        TduckBindingResponse response = tduckService.binding(1L);

        assertThat(response.validationStatus()).isEqualTo(TduckBindingValidationStatus.VALID);
        assertThat(response.registrationEnabled()).isFalse();
        assertThat(response.tduckPublished()).isFalse();
        assertThat(response.validationMessage()).contains("报名入口已关闭");
        assertThat(response.rebindingLocked()).isTrue();
    }

    @Test
    void updateRegistrationEnabledSyncsTduckPublishStateBeforePersisting() {
        TduckFormBinding binding = existingBinding("old-form");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDetails("old-form"))
                .thenReturn(Optional.of(new TduckFormDetails("old-form", "旧表单", "3", false, 3)));

        TduckBindingResponse response = tduckService.updateRegistrationEnabled(1L, new TduckRegistrationStatusRequest(false));

        assertThat(binding.isRegistrationEnabled()).isFalse();
        assertThat(response.registrationEnabled()).isFalse();
        verify(tduckClient).setFormPublished("old-form", false);
        verify(bindingRepository).save(binding);
    }

    @Test
    void registrationInfoClosesWhenBoundFormNoLongerExists() {
        when(meetingRepository.findByCode("NDEF2026")).thenReturn(Optional.of(meeting));
        TduckFormBinding binding = existingBinding("missing-form");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDetails("missing-form")).thenReturn(Optional.empty());
        meeting.setStatus(com.meetingos.meeting.MeetingStatus.REGISTRATION_OPEN);

        var info = tduckService.registrationInfo("NDEF2026");

        assertThat(info.open()).isFalse();
        assertThat(info.message()).contains("报名表不可用");
        assertThat(info.registrationUrl()).isNull();
    }

    @Test
    void updateMappingsRejectsDuplicateAttendeeFieldBeforeDatabaseWrite() {
        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(List.of(
                mapping(AttendeeField.NAME, "name", true),
                mapping(AttendeeField.PHONE, "phone", true),
                mapping(AttendeeField.ORGANIZATION, "company", false),
                mapping(AttendeeField.ORGANIZATION, "department", false)
        ));

        assertThatThrownBy(() -> tduckService.updateMappings(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字段映射重复")
                .hasMessageContaining("ORGANIZATION");

        /*
         * 重复目标字段必须在服务层提前失败，不能继续 delete/insert；
         * 否则数据库唯一键异常会变成泛化 500，前端只能看到“系统异常”。
         */
        verify(mappingRepository, never()).deleteByMeetingId(any());
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void updateMappingsKeepsExplicitUnmappedFields() {
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(existingBinding("forum-registration")));
        UpdateFieldMappingsRequest request = new UpdateFieldMappingsRequest(List.of(
                mapping(AttendeeField.NAME, "name", true),
                mapping(AttendeeField.PHONE, "mobile", true),
                mapping(null, "gender", true),
                mapping(null, "arrivalDate", false)
        ));

        List<TduckFieldMapping> saved = tduckService.updateMappings(1L, request);

        assertThat(saved)
                .extracting(TduckFieldMapping::getAttendeeField)
                .containsExactly(AttendeeField.NAME, AttendeeField.PHONE, null, null);
        assertThat(saved)
                .filteredOn(mapping -> mapping.getAttendeeField() == null)
                /*
                 * 空目标字段表示“明确不映射”，不允许带 requiredMapping=true 进入后续 Webhook 校验，
                 * 否则未映射字段会被误当作必填业务字段。
                 */
                .allSatisfy(mapping -> assertThat(mapping.isRequiredMapping()).isFalse());
        verify(mappingRepository).deleteByMeetingId(1L);
        verify(mappingRepository).flush();
        verify(mappingRepository).save(anyMapping(null, "gender"));
        verify(mappingRepository).save(anyMapping(null, "arrivalDate"));
    }

    @Test
    void syncFieldsRecognizesExtendedAttendeeFields() {
        TduckFormBinding binding = existingBinding("forum-registration");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(List.of());
        when(tduckClient.formFields("forum-registration")).thenReturn(List.of(
                new TduckFormFieldOption("name", "姓名", "input", true),
                new TduckFormFieldOption("mobile", "手机号", "input", true),
                new TduckFormFieldOption("orgType", "单位类型", "select", false),
                new TduckFormFieldOption("arrivalNo", "抵达航班/车次", "input", false),
                new TduckFormFieldOption("returnTransport", "返程交通方式", "select", false),
                new TduckFormFieldOption("emergencyMobile", "紧急联系电话", "input", false),
                new TduckFormFieldOption("invoiceEmail", "发票邮箱", "input", false),
                new TduckFormFieldOption("photoConsent", "肖像授权", "radio", false)
        ));

        TduckFieldSyncResponse response = tduckService.syncFields(1L);

        assertThat(response.mappingStatus()).isEqualTo(MappingStatus.VALID);
        assertThat(response.mappedCount()).isEqualTo(8);
        assertThat(response.fields())
                .extracting(TduckFieldSyncItem::attendeeField)
                .containsExactly(
                        AttendeeField.NAME,
                        AttendeeField.PHONE,
                        AttendeeField.ORGANIZATION_TYPE,
                        AttendeeField.ARRIVAL_FLIGHT_TRAIN,
                        AttendeeField.DEPARTURE_TRANSPORT,
                        AttendeeField.EMERGENCY_PHONE,
                        AttendeeField.INVOICE_EMAIL,
                        AttendeeField.CONSENT_PHOTO
                );
        verify(mappingRepository).deleteByMeetingId(1L);
        verify(mappingRepository).flush();
    }

    @Test
    void syncFieldsKeepsSavedMappingsWhenRefreshingTduckFields() {
        TduckFormBinding binding = existingBinding("forum-registration");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(List.of(
                savedMapping(AttendeeField.NAME, "name", "姓名", true),
                savedMapping(AttendeeField.PHONE, "mobile", "手机号", true),
                savedMapping(AttendeeField.ORGANIZATION_TYPE, "orgType", "单位类型", false),
                savedMapping(AttendeeField.GENDER, "gender", "性别", false)
        ));
        when(tduckClient.formFields("forum-registration")).thenReturn(List.of(
                new TduckFormFieldOption("name", "姓名", "input", true),
                new TduckFormFieldOption("mobile", "手机号", "input", true),
                new TduckFormFieldOption("orgType", "单位类型", "select", true),
                new TduckFormFieldOption("gender", "性别", "radio", false),
                new TduckFormFieldOption("invoiceEmail", "发票邮箱", "input", false)
        ));

        TduckFieldSyncResponse response = tduckService.syncFields(1L);

        assertThat(response.mappingStatus()).isEqualTo(MappingStatus.VALID);
        assertThat(response.fields())
                .extracting(TduckFieldSyncItem::attendeeField)
                .containsExactly(
                        AttendeeField.NAME,
                        AttendeeField.PHONE,
                        AttendeeField.ORGANIZATION_TYPE,
                        AttendeeField.GENDER,
                        AttendeeField.INVOICE_EMAIL
                );
        /*
         * 已保存映射的 TDuck key 仍存在时必须保留，避免管理员点击“同步字段”
         * 后把刚保存的映射状态清掉；新字段仍可继续自动识别补上。
         */
        verify(mappingRepository).save(anyMapping(AttendeeField.ORGANIZATION_TYPE, "orgType"));
        verify(mappingRepository).save(anyMapping(AttendeeField.GENDER, "gender"));
        verify(mappingRepository).save(anyMapping(AttendeeField.INVOICE_EMAIL, "invoiceEmail"));
    }

    @Test
    void syncFieldsDoesNotReInferExplicitUnmappedFields() {
        TduckFormBinding binding = existingBinding("forum-registration");
        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(List.of(
                savedMapping(AttendeeField.NAME, "name", "姓名", true),
                savedMapping(AttendeeField.PHONE, "mobile", "手机号", true),
                savedMapping(null, "gender", "性别", false),
                savedMapping(null, "arrivalDate", "抵达日期", false)
        ));
        when(tduckClient.formFields("forum-registration")).thenReturn(List.of(
                new TduckFormFieldOption("name", "姓名", "input", true),
                new TduckFormFieldOption("mobile", "手机号", "input", true),
                new TduckFormFieldOption("gender", "性别", "radio", false),
                new TduckFormFieldOption("arrivalDate", "抵达日期", "datetime", false),
                new TduckFormFieldOption("invoiceEmail", "发票邮箱", "input", false)
        ));

        TduckFieldSyncResponse response = tduckService.syncFields(1L);

        assertThat(response.mappingStatus()).isEqualTo(MappingStatus.VALID);
        assertThat(response.mappedCount()).isEqualTo(3);
        assertThat(response.fields())
                .extracting(TduckFieldSyncItem::attendeeField)
                .containsExactly(
                        AttendeeField.NAME,
                        AttendeeField.PHONE,
                        null,
                        null,
                        AttendeeField.INVOICE_EMAIL
                );
        verify(mappingRepository).save(anyMapping(null, "gender"));
        verify(mappingRepository).save(anyMapping(null, "arrivalDate"));
        verify(mappingRepository).save(anyMapping(AttendeeField.INVOICE_EMAIL, "invoiceEmail"));
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

    private FieldMappingItem mapping(AttendeeField attendeeField, String tduckFieldKey, boolean requiredMapping) {
        return new FieldMappingItem(attendeeField, tduckFieldKey, tduckFieldKey, requiredMapping);
    }

    private TduckFieldMapping savedMapping(AttendeeField attendeeField, String tduckFieldKey, String tduckFieldLabel, boolean requiredMapping) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setMeeting(meeting);
        mapping.setAttendeeField(attendeeField);
        mapping.setTduckFieldKey(tduckFieldKey);
        mapping.setTduckFieldLabel(tduckFieldLabel);
        mapping.setRequiredMapping(requiredMapping);
        return mapping;
    }

    private TduckFieldMapping anyMapping(AttendeeField attendeeField, String tduckFieldKey) {
        return org.mockito.ArgumentMatchers.argThat(mapping ->
                mapping != null
                        && mapping.getAttendeeField() == attendeeField
                        && tduckFieldKey.equals(mapping.getTduckFieldKey())
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
