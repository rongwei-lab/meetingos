package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.PageResponse;
import com.meetingos.invitation.InvitationAttributionService;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.settings.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TduckSyncLogQueryTest {

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
    }

    @Test
    void filtersSyncLogsByMeetingAndStatus() {
        TduckSyncLog failedLog = new TduckSyncLog();
        failedLog.setStatus(SyncStatus.FAILED);
        when(syncLogRepository.findByMeetingIdAndStatus(
                7L,
                SyncStatus.FAILED,
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(failedLog)));

        PageResponse<TduckSyncLog> page = tduckService.syncLogs(7L, 0, 20, SyncStatus.FAILED);

        assertThat(page.content()).containsExactly(failedLog);
        assertThat(page.totalElements()).isEqualTo(1);
        verify(syncLogRepository).findByMeetingIdAndStatus(7L, SyncStatus.FAILED, any(Pageable.class));
        verify(syncLogRepository, never()).findByMeetingId(7L, any(Pageable.class));
    }
}
