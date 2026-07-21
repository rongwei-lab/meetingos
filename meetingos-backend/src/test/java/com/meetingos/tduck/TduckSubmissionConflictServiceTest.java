package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TduckSubmissionConflictServiceTest {

    private static final Long MEETING_ID = 1L;
    private static final Long ATTENDEE_ID = 11L;
    private static final String PHONE = "13800000001";

    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private TduckFormBindingRepository bindingRepository;
    @Mock
    private TduckFieldMappingRepository mappingRepository;
    @Mock
    private TduckSyncLogRepository syncLogRepository;
    @Mock
    private TduckSubmissionRepository submissionRepository;
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
                submissionRepository,
                attendeeRepository,
                new TduckFieldMapper(),
                settingsService,
                tduckClient,
                syncLogService,
                webhookParser,
                transactionRunner,
                invitationAttributionService
        );

        lenient().when(settingsService.webhookToken()).thenReturn("test-webhook-token");
        lenient().when(syncLogRepository.findBySuccessEventId(anyString())).thenReturn(Optional.empty());
        lenient().when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(submissionRepository.save(any(TduckSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(syncLogService.success(any(), any(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> syncLog(101L));
        lenient().when(syncLogService.ignored(
                        any(TduckWebhookRequest.class),
                        any(Meeting.class),
                        anyString(),
                        org.mockito.ArgumentMatchers.<Attendee>any(),
                        anyString()
                ))
                .thenAnswer(invocation -> syncLog(102L));
        lenient().when(syncLogService.failed(any(), any(), anyString()))
                .thenAnswer(invocation -> syncLog(103L));
        lenient().when(transactionRunner.requiresNew(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });
    }

    @Test
    void olderSamePhoneSubmissionIsIgnoredWithoutOverwritingCurrentAttendee() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission active = submission(21L, meeting, attendee, "200", TduckSubmissionStatus.ACTIVE, "当前资料");
        active.setSubmittedAt(LocalDateTime.of(2026, 5, 2, 9, 0));
        stubBoundMeeting(meeting);
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE)).thenReturn(Optional.of(attendee));
        when(submissionRepository.findByAttendeeIdAndStatusForUpdate(ATTENDEE_ID, TduckSubmissionStatus.ACTIVE))
                .thenReturn(List.of(active));

        TduckWebhookResult result = sync("event-old", "form_data_add", "100", Map.of(
                "name", "历史资料",
                "phone", PHONE,
                "createTime", "2026-05-01 09:00:00"
        ));

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        assertThat(result.syncLogId()).isEqualTo(102L);
        assertThat(result.attendeeId()).isNull();
        assertThat(attendee.getName()).isEqualTo("当前资料");
        assertThat(attendee.getTduckDataId()).isEqualTo("200");

        ArgumentCaptor<TduckSubmission> submissionCaptor = ArgumentCaptor.forClass(TduckSubmission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        TduckSubmission ignored = submissionCaptor.getValue();
        assertThat(ignored.getStatus()).isEqualTo(TduckSubmissionStatus.IGNORED);
        assertThat(ignored.getTduckDataId()).isEqualTo("100");
        assertThat(ignored.getRawDataSnapshot()).doesNotContainKey("createTime");
        verify(attendeeRepository, never()).save(attendee);
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void newerSamePhoneSubmissionEntersPendingConflictWithoutOverwritingCurrentAttendee() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission active = submission(21L, meeting, attendee, "200", TduckSubmissionStatus.ACTIVE, "当前资料");
        active.setSubmittedAt(LocalDateTime.of(2026, 5, 1, 9, 0));
        stubBoundMeeting(meeting);
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE)).thenReturn(Optional.of(attendee));
        when(submissionRepository.findByAttendeeIdAndStatusForUpdate(ATTENDEE_ID, TduckSubmissionStatus.ACTIVE))
                .thenReturn(List.of(active));

        TduckWebhookResult result = sync("event-new", "form_data_add", "300", Map.of(
                "name", "新资料",
                "phone", PHONE,
                "createTime", "2026-05-02 09:00:00"
        ));

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        assertThat(result.message()).contains("等待管理员处理");
        assertThat(attendee.getName()).isEqualTo("当前资料");
        assertThat(attendee.getTduckDataId()).isEqualTo("200");

        ArgumentCaptor<TduckSubmission> submissionCaptor = ArgumentCaptor.forClass(TduckSubmission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        TduckSubmission pending = submissionCaptor.getValue();
        assertThat(pending.getStatus()).isEqualTo(TduckSubmissionStatus.PENDING_CONFLICT);
        assertThat(pending.getTduckDataId()).isEqualTo("300");
        verify(attendeeRepository, never()).save(attendee);
        verify(invitationAttributionService).preparePendingSubmissionAttribution(
                MEETING_ID,
                ATTENDEE_ID,
                "form-1",
                "300",
                null
        );
    }

    @Test
    void webhookRawSubmissionTimeKeepsNonNumericHistoricalDataIdOutOfManualQueue() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "current-data", "当前资料");
        TduckSubmission active = submission(
                21L,
                meeting,
                attendee,
                "current-data",
                TduckSubmissionStatus.ACTIVE,
                "当前资料"
        );
        active.setSubmittedAt(LocalDateTime.of(2026, 5, 2, 9, 0));
        stubBoundMeeting(meeting);
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE)).thenReturn(Optional.of(attendee));
        when(submissionRepository.findByAttendeeIdAndStatusForUpdate(ATTENDEE_ID, TduckSubmissionStatus.ACTIVE))
                .thenReturn(List.of(active));

        TduckWebhookResult result = tduckService.handleWebhook(
                new TduckWebhookRequest(
                        "event-raw-time",
                        "form_data_add",
                        "form-1",
                        "historical-data",
                        null,
                        Map.of("name", "历史资料", "phone", PHONE),
                        Map.of("createTime", "2026-05-01 09:00:00")
                ),
                null,
                "test-webhook-token",
                "{}"
        );

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        ArgumentCaptor<TduckSubmission> submissionCaptor = ArgumentCaptor.forClass(TduckSubmission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo(TduckSubmissionStatus.IGNORED);
        assertThat(submissionCaptor.getValue().getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 9, 0));
        verify(attendeeRepository, never()).save(attendee);
    }

    @Test
    void knownActiveSubmissionLocksFreshAttendeeBeforeUpdatingRegistrationFields() {
        Meeting meeting = meeting();
        Attendee staleAttendee = attendee(meeting, "200", "旧资料");
        Attendee lockedAttendee = attendee(meeting, "200", "旧资料");
        lockedAttendee.setCheckinStatus(CheckinStatus.CHECKED_IN);
        lockedAttendee.setRoomNo("1801");
        TduckSubmission active = submission(
                21L,
                meeting,
                staleAttendee,
                "200",
                TduckSubmissionStatus.ACTIVE,
                "旧资料"
        );
        stubBoundMeeting(meeting);
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "200"))
                .thenReturn(Optional.of(active));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE))
                .thenReturn(Optional.of(staleAttendee));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(lockedAttendee));
        when(attendeeRepository.save(lockedAttendee)).thenReturn(lockedAttendee);

        TduckWebhookResult result = sync("event-update", "form_data_update", "200", Map.of(
                "name", "新资料",
                "phone", PHONE
        ));

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(staleAttendee.getName()).isEqualTo("旧资料");
        assertThat(lockedAttendee.getName()).isEqualTo("新资料");
        assertThat(lockedAttendee.getCheckinStatus()).isEqualTo(CheckinStatus.CHECKED_IN);
        assertThat(lockedAttendee.getRoomNo()).isEqualTo("1801");
        verify(attendeeRepository).findByIdForUpdate(ATTENDEE_ID);
        verify(attendeeRepository).save(lockedAttendee);
    }

    @Test
    void knownPendingSubmissionRefreshesComparisonSnapshotWithoutOverwritingAttendee() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission pending = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.PENDING_CONFLICT, "首次新资料");
        pending.setSubmittedAt(LocalDateTime.of(2026, 5, 2, 9, 0));
        stubBoundMeeting(meeting);
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300"))
                .thenReturn(Optional.of(pending));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE)).thenReturn(Optional.of(attendee));

        TduckWebhookResult result = sync("event-pending-update", "form_data_update", "300", Map.of(
                "name", "更新后的新资料",
                "phone", PHONE,
                "organization", "更新单位",
                "createTime", "2026-05-03 10:00:00"
        ));

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        assertThat(result.message()).contains("等待管理员处理");
        assertThat(pending.getName()).isEqualTo("更新后的新资料");
        assertThat(pending.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 9, 0));
        assertThat(pending.getRawDataSnapshot())
                .containsEntry("organization", "更新单位")
                .doesNotContainKey("createTime");
        assertThat(attendee.getName()).isEqualTo("当前资料");
        verify(attendeeRepository, never()).save(attendee);
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void duplicatePullSyncRefreshesPendingComparisonSnapshotWithoutWritingAnotherLog() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission pending = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.PENDING_CONFLICT, "首次新资料");
        pending.setSubmittedAt(LocalDateTime.of(2026, 5, 2, 9, 0));
        TduckSyncLog existingLog = syncLog(104L);
        existingLog.setStatus(SyncStatus.DUPLICATE_IGNORED);
        existingLog.setAttendeeId(ATTENDEE_ID);
        existingLog.setMeeting(meeting);
        List<TduckFieldMapping> mappings = new ArrayList<>(requiredMappings(meeting));
        TduckFieldMapping organization = new TduckFieldMapping();
        organization.setMeeting(meeting);
        organization.setAttendeeField(AttendeeField.ORGANIZATION);
        organization.setTduckFieldKey("organization");
        mappings.add(organization);

        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-1");
        when(bindingRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(binding));
        when(tduckClient.formDataDetails("300")).thenReturn(new TduckFormDataDetails(
                "form-1",
                "300",
                "incoming-token",
                Map.of(
                        "name", "拉取后的新资料",
                        "phone", PHONE,
                        "organization", "拉取后的新单位",
                        "createTime", "2026-05-03 10:00:00"
                ),
                Map.of("formData", Map.of("id", "300"))
        ));
        when(syncLogRepository.findBySuccessEventId("pull-sync-form-1-300"))
                .thenReturn(Optional.of(existingLog));
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300"))
                .thenReturn(Optional.of(pending));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE)).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(attendee));
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(mappings);

        TduckWebhookResult result = tduckService.pullSync(new TduckPullSyncRequest(MEETING_ID, "300"));

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        assertThat(result.syncLogId()).isEqualTo(104L);
        assertThat(result.attendeeId()).isNull();
        assertThat(pending.getName()).isEqualTo("拉取后的新资料");
        assertThat(pending.getOrganization()).isEqualTo("拉取后的新单位");
        assertThat(pending.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 9, 0));
        assertThat(pending.getRawDataSnapshot()).doesNotContainKey("createTime");
        assertThat(attendee.getName()).isEqualTo("当前资料");
        assertThat(attendee.getTduckDataId()).isEqualTo("200");
        verify(meetingRepository).findByIdForUpdate(MEETING_ID);
        verify(submissionRepository).findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300");
        verify(attendeeRepository).findByMeetingIdAndPhoneForUpdate(MEETING_ID, PHONE);
        verify(attendeeRepository).findByIdForUpdate(ATTENDEE_ID);
        verify(attendeeRepository, never()).save(attendee);
        verify(submissionRepository, never()).save(any(TduckSubmission.class));
        verify(syncLogRepository, never()).save(any(TduckSyncLog.class));
        verifyNoSyncLogWrites();
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void duplicatePullSyncKeepsPendingSnapshotWhenIncomingPhoneBelongsToAnotherAttendee() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        Attendee anotherAttendee = attendee(meeting, "400", "另一位参会人");
        anotherAttendee.setId(12L);
        anotherAttendee.setPhone("13900000002");
        TduckSubmission pending = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.PENDING_CONFLICT, "首次新资料");
        pending.setRawDataSnapshot(Map.of("name", "首次新资料", "phone", PHONE));
        TduckSyncLog existingLog = syncLog(105L);
        existingLog.setStatus(SyncStatus.DUPLICATE_IGNORED);
        existingLog.setMeeting(meeting);

        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-1");
        when(bindingRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(binding));
        when(tduckClient.formDataDetails("300")).thenReturn(new TduckFormDataDetails(
                "form-1",
                "300",
                "incoming-token",
                Map.of("name", "另一位参会人", "phone", "13900000002"),
                Map.of("formData", Map.of("id", "300"))
        ));
        when(syncLogRepository.findBySuccessEventId("pull-sync-form-1-300"))
                .thenReturn(Optional.of(existingLog));
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300"))
                .thenReturn(Optional.of(pending));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(attendee));
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));
        when(attendeeRepository.findByMeetingIdAndPhoneForUpdate(MEETING_ID, "13900000002"))
                .thenReturn(Optional.of(anotherAttendee));

        TduckWebhookResult result = tduckService.pullSync(new TduckPullSyncRequest(MEETING_ID, "300"));

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.syncLogId()).isNull();
        assertThat(result.attendeeId()).isNull();
        assertThat(result.message()).contains("dataId 与手机号分别属于不同参会人");
        assertThat(pending.getName()).isEqualTo("首次新资料");
        assertThat(pending.getPhone()).isEqualTo(PHONE);
        assertThat(pending.getRawDataSnapshot()).containsExactlyEntriesOf(Map.of("name", "首次新资料", "phone", PHONE));
        verify(attendeeRepository, never()).save(attendee);
        verify(submissionRepository, never()).save(any(TduckSubmission.class));
        verify(syncLogRepository, never()).save(any(TduckSyncLog.class));
        verifyNoSyncLogWrites();
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void duplicatePullSyncKeepsPendingSnapshotWhenUpdatedDataCannotBeMapped() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission pending = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.PENDING_CONFLICT, "首次新资料");
        pending.setRawDataSnapshot(Map.of("name", "首次新资料", "phone", PHONE));
        TduckSyncLog existingLog = syncLog(106L);
        existingLog.setStatus(SyncStatus.DUPLICATE_IGNORED);
        existingLog.setMeeting(meeting);

        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-1");
        when(bindingRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(binding));
        when(tduckClient.formDataDetails("300")).thenReturn(new TduckFormDataDetails(
                "form-1",
                "300",
                "incoming-token",
                Map.of("phone", PHONE),
                Map.of("formData", Map.of("id", "300"))
        ));
        when(syncLogRepository.findBySuccessEventId("pull-sync-form-1-300"))
                .thenReturn(Optional.of(existingLog));
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300"))
                .thenReturn(Optional.of(pending));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(attendee));
        when(mappingRepository.findByMeetingId(MEETING_ID)).thenReturn(requiredMappings(meeting));

        TduckWebhookResult result = tduckService.pullSync(new TduckPullSyncRequest(MEETING_ID, "300"));

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(result.syncLogId()).isNull();
        assertThat(result.attendeeId()).isNull();
        assertThat(result.message()).contains("必填映射字段缺失");
        assertThat(pending.getName()).isEqualTo("首次新资料");
        assertThat(pending.getPhone()).isEqualTo(PHONE);
        assertThat(pending.getRawDataSnapshot()).containsExactlyEntriesOf(Map.of("name", "首次新资料", "phone", PHONE));
        verify(attendeeRepository, never()).findByMeetingIdAndPhoneForUpdate(anyLong(), anyString());
        verify(attendeeRepository, never()).save(attendee);
        verify(submissionRepository, never()).save(any(TduckSubmission.class));
        verify(syncLogRepository, never()).save(any(TduckSyncLog.class));
        verifyNoSyncLogWrites();
        verifyNoInteractions(invitationAttributionService);
    }

    @Test
    void duplicatePullSyncDoesNotRefreshResolvedSubmission() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission ignored = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.IGNORED, "已忽略资料");
        ignored.setRawDataSnapshot(Map.of("name", "已忽略资料", "phone", PHONE));
        TduckSyncLog existingLog = syncLog(107L);
        existingLog.setStatus(SyncStatus.DUPLICATE_IGNORED);
        existingLog.setMeeting(meeting);

        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-1");
        when(bindingRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(binding));
        when(tduckClient.formDataDetails("300")).thenReturn(new TduckFormDataDetails(
                "form-1",
                "300",
                "incoming-token",
                Map.of("name", "不应覆盖", "phone", PHONE),
                Map.of("formData", Map.of("id", "300"))
        ));
        when(syncLogRepository.findBySuccessEventId("pull-sync-form-1-300"))
                .thenReturn(Optional.of(existingLog));
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300"))
                .thenReturn(Optional.of(ignored));

        TduckWebhookResult result = tduckService.pullSync(new TduckPullSyncRequest(MEETING_ID, "300"));

        assertThat(result.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        assertThat(result.syncLogId()).isEqualTo(107L);
        assertThat(ignored.getName()).isEqualTo("已忽略资料");
        assertThat(ignored.getRawDataSnapshot()).containsExactlyEntriesOf(Map.of("name", "已忽略资料", "phone", PHONE));
        verifyNoInteractions(mappingRepository, invitationAttributionService);
        verify(attendeeRepository, never()).findByIdForUpdate(anyLong());
        verify(attendeeRepository, never()).findByMeetingIdAndPhoneForUpdate(anyLong(), anyString());
        verify(submissionRepository, never()).save(any(TduckSubmission.class));
        verify(syncLogRepository, never()).save(any(TduckSyncLog.class));
        verifyNoSyncLogWrites();
    }

    @Test
    void keepExistingMarksPendingSubmissionIgnoredWithoutChangingAttendee() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        TduckSubmission pending = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.PENDING_CONFLICT, "新资料");
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(submissionRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(pending));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(attendee));

        TduckSubmissionConflictResponse response = tduckService.resolveSubmissionConflict(
                31L,
                new TduckSubmissionResolveRequest(MEETING_ID, TduckSubmissionResolution.KEEP_EXISTING, " 已核实保留现有 ")
        );

        assertThat(response.status()).isEqualTo(TduckSubmissionStatus.IGNORED);
        assertThat(pending.getStatus()).isEqualTo(TduckSubmissionStatus.IGNORED);
        assertThat(pending.getResolutionNote()).isEqualTo("已核实保留现有");
        assertThat(pending.getResolvedAt()).isNotNull();
        assertThat(attendee.getName()).isEqualTo("当前资料");
        assertThat(attendee.getTduckDataId()).isEqualTo("200");
        verify(attendeeRepository, never()).save(attendee);
    }

    @Test
    void adoptIncomingReplacesOnlyRegistrationFieldsAndKeepsOperationalState() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "200", "当前资料");
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewReason("人工审核通过");
        attendee.setCheckinStatus(CheckinStatus.CHECKED_IN);
        attendee.setRoomNo("1801");
        attendee.setRoomRemark("保留住宿安排");
        attendee.setTags("VIP,接待");

        TduckSubmission active = submission(21L, meeting, attendee, "200", TduckSubmissionStatus.ACTIVE, "当前资料");
        TduckSubmission pending = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.PENDING_CONFLICT, "新资料");
        pending.setOrganization("新单位");
        pending.setTitle("新职务");
        pending.setRemark("新备注");
        pending.setRawDataSnapshot(Map.of("name", "新资料", "phone", PHONE));

        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(submissionRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(pending));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(attendee));
        when(submissionRepository.findByAttendeeIdAndStatusForUpdate(ATTENDEE_ID, TduckSubmissionStatus.ACTIVE))
                .thenReturn(List.of(active));
        when(attendeeRepository.save(attendee)).thenReturn(attendee);

        TduckSubmissionConflictResponse response = tduckService.resolveSubmissionConflict(
                31L,
                new TduckSubmissionResolveRequest(MEETING_ID, TduckSubmissionResolution.ADOPT_INCOMING, "采用最新报名")
        );

        assertThat(response.status()).isEqualTo(TduckSubmissionStatus.ACTIVE);
        assertThat(active.getStatus()).isEqualTo(TduckSubmissionStatus.SUPERSEDED);
        assertThat(pending.getStatus()).isEqualTo(TduckSubmissionStatus.ACTIVE);
        assertThat(pending.getActiveAttendeeId()).isEqualTo(ATTENDEE_ID);
        assertThat(attendee.getName()).isEqualTo("新资料");
        assertThat(attendee.getOrganization()).isEqualTo("新单位");
        assertThat(attendee.getTduckDataId()).isEqualTo("300");
        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(attendee.getReviewReason()).isEqualTo("人工审核通过");
        assertThat(attendee.getCheckinStatus()).isEqualTo(CheckinStatus.CHECKED_IN);
        assertThat(attendee.getRoomNo()).isEqualTo("1801");
        assertThat(attendee.getRoomRemark()).isEqualTo("保留住宿安排");
        assertThat(attendee.getTags()).isEqualTo("VIP,接待");
        verify(submissionRepository).flush();
        verify(attendeeRepository).save(attendee);
    }

    @Test
    void cancellationOfCurrentSubmissionRestoresMostRecentSupersededSubmission() {
        Meeting meeting = meeting();
        Attendee attendee = attendee(meeting, "300", "当前资料");
        attendee.setCheckinStatus(CheckinStatus.CHECKED_IN);
        attendee.setRoomNo("1801");

        TduckSubmission current = submission(31L, meeting, attendee, "300", TduckSubmissionStatus.ACTIVE, "当前资料");
        current.setSubmittedAt(LocalDateTime.of(2026, 5, 3, 9, 0));
        TduckSubmission fallback = submission(21L, meeting, attendee, "200", TduckSubmissionStatus.SUPERSEDED, "历史资料");
        fallback.setSubmittedAt(LocalDateTime.of(2026, 5, 2, 9, 0));
        fallback.setOrganization("历史单位");

        stubBoundMeeting(meeting);
        when(submissionRepository.findByMeetingIdAndTduckDataIdForUpdate(MEETING_ID, "300"))
                .thenReturn(Optional.of(current));
        when(attendeeRepository.findByIdForUpdate(ATTENDEE_ID)).thenReturn(Optional.of(attendee));
        when(submissionRepository.findByAttendeeIdAndStatusForUpdate(ATTENDEE_ID, TduckSubmissionStatus.ACTIVE))
                .thenReturn(List.of(current));
        when(submissionRepository.findByAttendeeIdAndStatusForUpdate(ATTENDEE_ID, TduckSubmissionStatus.SUPERSEDED))
                .thenReturn(List.of(fallback));
        when(attendeeRepository.save(attendee)).thenReturn(attendee);

        TduckWebhookResult result = sync("event-cancel", "form_data_delete", "300", Map.of());

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(result.attendeeId()).isEqualTo(ATTENDEE_ID);
        assertThat(current.getStatus()).isEqualTo(TduckSubmissionStatus.CANCELLED);
        assertThat(fallback.getStatus()).isEqualTo(TduckSubmissionStatus.ACTIVE);
        assertThat(attendee.getName()).isEqualTo("历史资料");
        assertThat(attendee.getOrganization()).isEqualTo("历史单位");
        assertThat(attendee.getTduckDataId()).isEqualTo("200");
        assertThat(attendee.getCheckinStatus()).isEqualTo(CheckinStatus.CHECKED_IN);
        assertThat(attendee.getRoomNo()).isEqualTo("1801");
        verify(attendeeRepository).findByIdForUpdate(ATTENDEE_ID);
        verify(attendeeRepository).save(attendee);
        verify(submissionRepository, times(0)).save(any(TduckSubmission.class));
    }

    private void verifyNoSyncLogWrites() {
        verify(syncLogService, never()).success(any(), any(), anyString(), any(), anyString());
        verify(syncLogService, never()).ignored(
                any(),
                any(),
                anyString(),
                org.mockito.ArgumentMatchers.<Attendee>any(),
                anyString()
        );
        verify(syncLogService, never()).ignored(
                any(),
                any(),
                anyString(),
                org.mockito.ArgumentMatchers.<Long>any(),
                anyString()
        );
        verify(syncLogService, never()).failed(any(), any(), anyString());
    }

    private void stubBoundMeeting(Meeting meeting) {
        TduckFormBinding binding = new TduckFormBinding();
        binding.setMeeting(meeting);
        binding.setFormKey("form-1");
        when(bindingRepository.findByFormKey("form-1")).thenReturn(Optional.of(binding));
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
    }

    private TduckWebhookResult sync(String eventId,
                                    String eventType,
                                    String dataId,
                                    Map<String, Object> data) {
        return tduckService.handleWebhook(
                new TduckWebhookRequest(eventId, eventType, "form-1", dataId, null, data, Map.of("source", "test")),
                null,
                "test-webhook-token",
                "{}"
        );
    }

    private Meeting meeting() {
        Meeting meeting = new Meeting();
        meeting.setId(MEETING_ID);
        meeting.setCode("M2026");
        meeting.setName("TDuck 重复报名测试会议");
        return meeting;
    }

    private Attendee attendee(Meeting meeting, String dataId, String name) {
        Attendee attendee = new Attendee();
        attendee.setId(ATTENDEE_ID);
        attendee.setMeeting(meeting);
        attendee.setName(name);
        attendee.setPhone(PHONE);
        attendee.setOrganization("当前单位");
        attendee.setTitle("当前职务");
        attendee.setTduckDataId(dataId);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        attendee.setAttendeeCode("TD2026-001");
        return attendee;
    }

    private TduckSubmission submission(Long id,
                                       Meeting meeting,
                                       Attendee attendee,
                                       String dataId,
                                       TduckSubmissionStatus status,
                                       String name) {
        TduckSubmission submission = new TduckSubmission();
        submission.setId(id);
        submission.setMeeting(meeting);
        submission.setAttendee(attendee);
        submission.setFormKey("form-1");
        submission.setTduckDataId(dataId);
        submission.setName(name);
        submission.setPhone(PHONE);
        submission.setOrganization("当前单位");
        submission.setTitle("当前职务");
        submission.setStatus(status);
        return submission;
    }

    private List<TduckFieldMapping> requiredMappings(Meeting meeting) {
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

    private TduckSyncLog syncLog(Long id) {
        TduckSyncLog log = new TduckSyncLog();
        log.setId(id);
        return log;
    }
}
