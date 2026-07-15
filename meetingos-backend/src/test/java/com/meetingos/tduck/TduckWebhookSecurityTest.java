package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.invitation.InvitationAttributionService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TduckWebhookSecurityTest {

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
    private SystemSettingsService settingsService;
    @Mock
    private TduckClient tduckClient;
    @Mock
    private TduckSyncLogService syncLogService;
    @Mock
    private TduckWebhookParser webhookParser;
    @Mock
    private TduckTransactionRunner transactionRunner;
    @Mock
    private InvitationAttributionService invitationAttributionService;

    private TduckService tduckService;

    @BeforeEach
    void setUp() {
        tduckService = new TduckService(
                meetingRepository,
                bindingRepository,
                mappingRepository,
                syncLogRepository,
                attendeeRepository,
                new TduckFieldMapper(),
                settingsService,
                tduckClient,
                syncLogService,
                webhookParser,
                transactionRunner,
                invitationAttributionService
        );
    }

    @Test
    void invalidCredentialDoesNotParseOrPersistUntrustedPayload() {
        when(settingsService.webhookToken()).thenReturn("expected-secret");
        TduckSyncLog failedLog = new TduckSyncLog();
        failedLog.setId(7L);
        when(syncLogService.failed(isNull(), isNull(), anyString())).thenReturn(failedLog);

        assertThatThrownBy(() -> tduckService.handleWebhook("{not-json", null, "wrong-secret"))
                .hasMessageContaining("Webhook 签名校验失败");

        verify(webhookParser, never()).parse(anyString());
        verify(syncLogService).failed(null, null, "Webhook 签名校验失败");
    }

    @Test
    void failedLogWithoutTrustedRequestHasNoRawPayload() {
        TduckSyncLogRepository repository = org.mockito.Mockito.mock(TduckSyncLogRepository.class);
        when(repository.save(any(TduckSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TduckSyncLogService service = new TduckSyncLogService(repository);

        service.failed(null, null, "Webhook 签名校验失败");

        ArgumentCaptor<TduckSyncLog> captor = ArgumentCaptor.forClass(TduckSyncLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRawPayload()).isEmpty();
        assertThat(captor.getValue().getFormKey()).isEqualTo("UNKNOWN");
    }

    @Test
    void successAndFailureLogsUseExpectedTransactionPropagation() throws NoSuchMethodException {
        Transactional successTransaction = TduckSyncLogService.class
                .getMethod("success", TduckWebhookRequest.class, Meeting.class, String.class,
                        Attendee.class, String.class)
                .getAnnotation(Transactional.class);
        Transactional failedTransaction = TduckSyncLogService.class
                .getMethod("failed", TduckWebhookRequest.class, Meeting.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(successTransaction.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(failedTransaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void commitConstraintRaceResolvesByNormalizedSuccessKeyAfterRollback() {
        when(settingsService.webhookToken()).thenReturn("expected-secret");
        TduckSyncLog winningLog = new TduckSyncLog();
        winningLog.setId(41L);
        winningLog.setAttendeeId(51L);
        when(syncLogRepository.findBySuccessEventId("event-race"))
                .thenReturn(Optional.of(winningLog));
        when(transactionRunner.requiresNew(any()))
                .thenThrow(new DataIntegrityViolationException("concurrent unique key"))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(0, Supplier.class);
                    return action.get();
                });

        TduckWebhookResult result = tduckService.handleWebhook(
                request("  event-race  ", "data-race", "13800000003"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        assertThat(result.syncLogId()).isEqualTo(41L);
        assertThat(result.attendeeId()).isEqualTo(51L);
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void oldAddReplayAfterCancellationDoesNotRestoreAttribution() {
        when(settingsService.webhookToken()).thenReturn("expected-secret");
        TduckSyncLog oldAddLog = new TduckSyncLog();
        oldAddLog.setId(42L);
        oldAddLog.setAttendeeId(52L);
        when(syncLogRepository.findBySuccessEventId("old-add-event")).thenReturn(Optional.of(oldAddLog));
        when(transactionRunner.requiresNew(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });

        TduckWebhookResult result = tduckService.handleWebhook(
                request("old-add-event", "form_data_add", "data-replay", "old-token", "13800000004"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void oldCancellationReplayAfterResubmissionDoesNotDeactivateAttribution() {
        when(settingsService.webhookToken()).thenReturn("expected-secret");
        TduckSyncLog oldCancellationLog = new TduckSyncLog();
        oldCancellationLog.setId(43L);
        oldCancellationLog.setAttendeeId(53L);
        when(syncLogRepository.findBySuccessEventId("old-cancel-event"))
                .thenReturn(Optional.of(oldCancellationLog));
        when(transactionRunner.requiresNew(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });

        TduckWebhookResult result = tduckService.handleWebhook(
                request("old-cancel-event", "form_data_delete", "data-replay", null, "13800000005"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void sameDataIdWithChangedPhoneUpdatesOriginalAttendee() {
        stubSuccessfulTransaction();
        Meeting meeting = meeting();
        TduckFormBinding binding = binding(meeting);
        Attendee existing = attendee(meeting, 21L, "13800000001", "data-1");

        when(bindingRepository.findByFormKey("form-1")).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(mappings(meeting));
        when(attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(1L, "data-1"))
                .thenReturn(Optional.of(existing));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(1L, "13800000002"))
                .thenReturn(Optional.empty());
        when(attendeeRepository.save(existing)).thenReturn(existing);
        when(syncLogService.success(any(), any(), anyString(), any(), anyString())).thenAnswer(invocation -> {
            TduckSyncLog log = new TduckSyncLog();
            log.setId(31L);
            return log;
        });

        TduckWebhookResult result = tduckService.handleWebhook(
                request("event-1", "form_data_add", "data-1", "attribution-token", "13800000002"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.attendeeId()).isEqualTo(21L);
        assertThat(existing.getPhone()).isEqualTo("13800000002");
        assertThat(existing.getTduckDataId()).isEqualTo("data-1");
        verify(invitationAttributionService).attributeFirstSubmission(
                1L,
                21L,
                "form-1",
                "data-1",
                "attribution-token"
        );
    }

    @Test
    void attributionFailureDoesNotChangeSuccessfulWebhookResult() {
        stubSuccessfulTransaction();
        Meeting meeting = meeting();
        TduckFormBinding binding = binding(meeting);
        Attendee existing = attendee(meeting, 21L, "13800000001", "data-attr-failure");

        when(bindingRepository.findByFormKey("form-1")).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(mappings(meeting));
        when(attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(1L, "data-attr-failure"))
                .thenReturn(Optional.of(existing));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(1L, "13800000001"))
                .thenReturn(Optional.of(existing));
        when(attendeeRepository.save(existing)).thenReturn(existing);
        when(syncLogService.success(any(), any(), anyString(), any(), anyString())).thenAnswer(invocation -> {
            TduckSyncLog log = new TduckSyncLog();
            log.setId(35L);
            return log;
        });
        doThrow(new IllegalStateException("statistics unavailable"))
                .when(invitationAttributionService)
                .attributeFirstSubmission(1L, 21L, "form-1", "data-attr-failure", "bad-for-storage");

        TduckWebhookResult result = tduckService.handleWebhook(
                request("event-attr-failure", "form_data_add", "data-attr-failure", "bad-for-storage", "13800000001"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.syncLogId()).isEqualTo(35L);
        assertThat(result.attendeeId()).isEqualTo(21L);
    }

    @Test
    void cancellationMarksExistingAttributionInactiveWithoutReplacingIt() {
        stubSuccessfulTransaction();
        Meeting meeting = meeting();
        TduckFormBinding binding = binding(meeting);
        Attendee existing = attendee(meeting, 21L, "13800000001", "data-cancel");

        when(bindingRepository.findByFormKey("form-1")).thenReturn(Optional.of(binding));
        when(attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(1L, "data-cancel"))
                .thenReturn(Optional.of(existing));
        when(attendeeRepository.save(existing)).thenReturn(existing);
        when(syncLogService.success(any(), any(), anyString(), any(), anyString())).thenAnswer(invocation -> {
            TduckSyncLog log = new TduckSyncLog();
            log.setId(36L);
            return log;
        });

        TduckWebhookResult result = tduckService.handleWebhook(
                request("event-cancel", "form_data_delete", "data-cancel", "new-token-must-not-replace", "13800000001"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.attendeeId()).isEqualTo(21L);
        verify(invitationAttributionService).markInactive(1L, "data-cancel");
        verify(invitationAttributionService, never())
                .attributeFirstSubmission(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void duplicatePullSyncDoesNotReapplyPersistedAttribution() {
        Meeting meeting = meeting();
        TduckFormBinding binding = binding(meeting);
        TduckSyncLog existingLog = new TduckSyncLog();
        existingLog.setId(37L);
        existingLog.setAttendeeId(21L);

        when(bindingRepository.findByMeetingId(1L)).thenReturn(Optional.of(binding));
        when(tduckClient.formDataDetails("data-pull")).thenReturn(new TduckFormDataDetails(
                "form-1",
                "data-pull",
                "persisted-attribution-token",
                Map.of("name", "测试参会人", "phone", "13800000001"),
                Map.of("formData", Map.of("id", "data-pull"))
        ));
        when(syncLogRepository.findBySuccessEventId("pull-sync-form-1-data-pull"))
                .thenReturn(Optional.of(existingLog));
        when(transactionRunner.requiresNew(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });

        TduckWebhookResult result = tduckService.pullSync(new TduckPullSyncRequest(1L, "data-pull"));

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void missingDataIdCannotOverwritePhoneFallbackThatAlreadyHasBinding() {
        stubSuccessfulTransaction();
        Meeting meeting = meeting();
        TduckFormBinding binding = binding(meeting);
        Attendee existing = attendee(meeting, 21L, "13800000001", "bound-data-id");

        when(bindingRepository.findByFormKey("form-1")).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(mappings(meeting));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(1L, "13800000001"))
                .thenReturn(Optional.of(existing));
        TduckSyncLog failedLog = new TduckSyncLog();
        failedLog.setId(32L);
        when(syncLogService.failed(any(), any(), anyString())).thenReturn(failedLog);

        TduckWebhookResult result = tduckService.handleWebhook(
                request("event-2", null, "13800000001"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.message()).contains("本次请求缺少 dataId");
        verify(attendeeRepository, never()).save(any());
    }

    @Test
    void dataIdAndPhonePointingToDifferentAttendeesAreRejected() {
        stubSuccessfulTransaction();
        Meeting meeting = meeting();
        TduckFormBinding binding = binding(meeting);
        Attendee byDataId = attendee(meeting, 21L, "13800000001", "data-conflict");
        Attendee byPhone = attendee(meeting, 22L, "13800000002", "other-data");

        when(bindingRepository.findByFormKey("form-1")).thenReturn(Optional.of(binding));
        when(mappingRepository.findByMeetingId(1L)).thenReturn(mappings(meeting));
        when(attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(1L, "data-conflict"))
                .thenReturn(Optional.of(byDataId));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(1L, "13800000002"))
                .thenReturn(Optional.of(byPhone));
        TduckSyncLog failedLog = new TduckSyncLog();
        failedLog.setId(33L);
        when(syncLogService.failed(any(), any(), anyString())).thenReturn(failedLog);

        TduckWebhookResult result = tduckService.handleWebhook(
                request("event-conflict", "data-conflict", "13800000002"),
                null,
                "expected-secret",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.message()).contains("分别属于不同参会人");
        verify(attendeeRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    private void stubSuccessfulTransaction() {
        when(settingsService.webhookToken()).thenReturn("expected-secret");
        lenient().when(bindingRepository.findMeetingIdByFormKey("form-1")).thenReturn(Optional.of(1L));
        when(syncLogRepository.findBySuccessEventId(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRunner.requiresNew(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });
    }

    private Meeting meeting() {
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode("M2026");
        return meeting;
    }

    private TduckFormBinding binding(Meeting meeting) {
        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-1");
        return binding;
    }

    private Attendee attendee(Meeting meeting, Long id, String phone, String dataId) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setMeeting(meeting);
        attendee.setPhone(phone);
        attendee.setTduckDataId(dataId);
        return attendee;
    }

    private List<TduckFieldMapping> mappings(Meeting meeting) {
        TduckFieldMapping name = new TduckFieldMapping();
        name.setMeeting(meeting);
        name.setAttendeeField(AttendeeField.NAME);
        name.setTduckFieldKey("name");
        name.setRequiredMapping(true);

        TduckFieldMapping phone = new TduckFieldMapping();
        phone.setMeeting(meeting);
        phone.setAttendeeField(AttendeeField.PHONE);
        phone.setTduckFieldKey("phone");
        phone.setRequiredMapping(true);
        return List.of(name, phone);
    }

    private TduckWebhookRequest request(String eventId, String dataId, String phone) {
        return request(eventId, "form_data_add", dataId, null, phone);
    }

    private TduckWebhookRequest request(String eventId,
                                        String eventType,
                                        String dataId,
                                        String extValue,
                                        String phone) {
        return new TduckWebhookRequest(
                eventId,
                eventType,
                "form-1",
                dataId,
                extValue,
                Map.of("name", "测试参会人", "phone", phone),
                Map.of("source", "test")
        );
    }
}
