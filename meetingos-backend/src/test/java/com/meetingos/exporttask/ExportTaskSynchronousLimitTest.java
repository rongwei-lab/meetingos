package com.meetingos.exporttask;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.checkin.CheckinRecordRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ExportTable;
import com.meetingos.dinner.DinnerService;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.room.RoomService;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.statistics.StatisticsOverview;
import com.meetingos.statistics.StatisticsService;
import com.meetingos.tduck.TduckFieldMappingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportTaskSynchronousLimitTest {

    @TempDir
    Path uploadRoot;

    @Test
    void explicitCsvRejectsMoreThanTenThousandRowsBeforeLoadingData() {
        Fixture fixture = fixture(10_001L);

        assertThatThrownBy(() -> fixture.service().build(1L, "attendees", "csv", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同步导出最多支持 10000 行")
                .hasMessageContaining("请使用异步导出任务");

        verify(fixture.attendeeService(), never()).exportTable(any(), anyString());
    }

    @Test
    void tenThousandRowsRemainCompatibleWithExplicitCsv() {
        Fixture fixture = fixture(10_000L);
        when(fixture.attendeeService().exportTable(1L, "attendees"))
                .thenReturn(new ExportTable("参会名单", List.of("姓名"), List.of()));

        ExportFile file = fixture.service().build(1L, "attendees", "csv", null, null);

        assertThat(file.filename()).isEqualTo("meetingos-attendees.csv");
        assertThat(file.contentType().toString()).contains("text/csv");
    }

    @Test
    void statisticsExportSkipsRawRowLimitQuery() {
        Fixture fixture = fixture(20_000L);
        StatisticsOverview overview = mock(StatisticsOverview.class);
        when(overview.registrationTrend()).thenReturn(Map.of());
        when(overview.checkinTrend()).thenReturn(Map.of());
        when(overview.reviewStatusDistribution()).thenReturn(Map.of());
        when(overview.attendeeTypeDistribution()).thenReturn(Map.of());
        when(overview.organizationDistribution()).thenReturn(Map.of());
        when(overview.hotelDistribution()).thenReturn(Map.of());
        when(overview.roomTypeDistribution()).thenReturn(Map.of());
        when(overview.checkinEntranceDistribution()).thenReturn(Map.of());
        when(overview.checkinTypeDistribution()).thenReturn(Map.of());
        LocalDate today = LocalDate.now();
        when(fixture.statisticsService().overview(1L, today, today)).thenReturn(overview);

        ExportFile file = fixture.service().build(1L, "statistics", "csv", today, today);

        assertThat(file.filename()).isEqualTo("meetingos-statistics.csv");
        verify(fixture.entityManager(), never()).createQuery(anyString(), eq(Long.class));
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(long rowCount) {
        AttendeeService attendeeService = mock(AttendeeService.class);
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        MeetingAccessService accessService = mock(MeetingAccessService.class);
        StatisticsService statisticsService = mock(StatisticsService.class);
        EntityManager entityManager = mock(EntityManager.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        when(meetingRepository.existsById(1L)).thenReturn(true);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(rowCount);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> mock(TransactionStatus.class));
        ExportTaskService service = new ExportTaskService(
                attendeeService,
                mock(AttendeeRepository.class),
                mock(RoomService.class),
                mock(DinnerService.class),
                mock(SeatRepository.class),
                mock(SeatAssignmentRepository.class),
                mock(CheckinRecordRepository.class),
                meetingRepository,
                accessService,
                mock(ExportTaskRepository.class),
                mock(SystemSettingsService.class),
                statisticsService,
                mock(TduckFieldMappingRepository.class),
                entityManager,
                mock(ApplicationEventPublisher.class),
                transactionManager,
                uploadRoot.toString(),
                7,
                120,
                2
        );
        return new Fixture(service, attendeeService, statisticsService, entityManager);
    }

    private record Fixture(
            ExportTaskService service,
            AttendeeService attendeeService,
            StatisticsService statisticsService,
            EntityManager entityManager
    ) {
    }
}
