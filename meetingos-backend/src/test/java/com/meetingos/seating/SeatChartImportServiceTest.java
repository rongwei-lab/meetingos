package com.meetingos.seating;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeatChartImportServiceTest {

    @Test
    void importsActualAndPaddedBlankCellsAsDisabledVisibleSeats() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        SeatRepository seatRepository = mock(SeatRepository.class);
        SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
        AtomicReference<List<Seat>> persistedSeats = new AtomicReference<>(List.of());

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        when(meetingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(meeting));
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(layoutRepository.findByMeetingId(1L)).thenReturn(List.of());
        when(layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(1L, "主会场")).thenReturn(List.of());
        when(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "主会场"))
                .thenAnswer(invocation -> persistedSeats.get());
        when(attendeeRepository.findByMeetingIdAndReviewStatus(1L, ReviewStatus.APPROVED)).thenReturn(List.of());
        doAnswer(invocation -> {
            Iterable<Seat> iterable = invocation.getArgument(0);
            List<Seat> seats = new ArrayList<>();
            iterable.forEach(seats::add);
            persistedSeats.set(List.copyOf(seats));
            return seats;
        }).when(seatRepository).saveAll(any());

        SeatingService seatingService = new SeatingService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository
        );
        SeatChartImportService service = new SeatChartImportService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository,
                seatingService
        );

        SeatChartImportResult result = service.importChart(1L, "主会场", "主会场", workbookWithBlankSeats());

        Map<String, Seat> seatsByCode = persistedSeats.get().stream()
                .collect(Collectors.toMap(Seat::getSeatCode, seat -> seat));
        assertThat(persistedSeats.get()).hasSize(8);
        assertThat(persistedSeats.get()).allMatch(seat -> !seat.isStructuralBlank());
        assertThat(seatsByCode.get("主会场-1-1").isDisabled()).isTrue();
        assertThat(seatsByCode.get("主会场-1-4").isDisabled()).isTrue();
        assertThat(seatsByCode.get("主会场-2-2").isDisabled()).isTrue();
        assertThat(seatsByCode.get("主会场-2-4").isDisabled()).isTrue();
        assertThat(seatsByCode.get("主会场-1-2").isDisabled()).isFalse();
        assertThat(seatsByCode.get("主会场-2-1").isDisabled()).isFalse();
        assertThat(seatsByCode.get("主会场-2-3").isDisabled()).isFalse();

        assertThat(result.totalSeatCount()).isEqualTo(8);
        assertThat(result.structuralBlankCount()).isZero();
        assertThat(result.namedSeatCount()).isEqualTo(3);
        assertThat(result.assignedCount()).isZero();
        assertThat(result.unassignedCount()).isEqualTo(3);
    }

    @Test
    void importsMarkedAisleColumnWithoutTreatingItOrRightRowLabelAsNames() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        SeatRepository seatRepository = mock(SeatRepository.class);
        SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
        AtomicReference<List<Seat>> persistedSeats = new AtomicReference<>(List.of());

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        when(meetingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(meeting));
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(layoutRepository.findByMeetingId(1L)).thenReturn(List.of());
        when(layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(1L, "主会场")).thenReturn(List.of());
        when(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "主会场"))
                .thenAnswer(invocation -> persistedSeats.get());
        when(attendeeRepository.findByMeetingIdAndReviewStatus(1L, ReviewStatus.APPROVED)).thenReturn(List.of());
        doAnswer(invocation -> {
            Iterable<Seat> iterable = invocation.getArgument(0);
            List<Seat> seats = new ArrayList<>();
            iterable.forEach(seats::add);
            persistedSeats.set(List.copyOf(seats));
            return seats;
        }).when(seatRepository).saveAll(any());

        SeatingService seatingService = new SeatingService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository
        );
        SeatChartImportService service = new SeatChartImportService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository,
                seatingService
        );

        SeatChartImportResult result = service.importChart(1L, "主会场", "主会场", workbookWithAisleAndRightRowLabels());

        Map<String, Seat> seatsByCode = persistedSeats.get().stream()
                .collect(Collectors.toMap(Seat::getSeatCode, seat -> seat));
        assertThat(persistedSeats.get()).hasSize(6);
        assertThat(seatsByCode.get("主会场-1-2"))
                .satisfies(seat -> {
                    assertThat(seat.isAisle()).isTrue();
                    assertThat(seat.isDisabled()).isTrue();
                    assertThat(seat.isLocked()).isFalse();
                    assertThat(seat.isStructuralBlank()).isFalse();
                    assertThat(seat.getSeatType()).isEqualTo(SeatType.NORMAL);
                });
        assertThat(seatsByCode.get("主会场-2-2").isAisle()).isTrue();
        assertThat(persistedSeats.get().stream().filter(Seat::isAisle).toList()).hasSize(2);
        assertThat(result.totalSeatCount()).isEqualTo(4);
        assertThat(result.aisleCount()).isEqualTo(2);
        assertThat(result.namedSeatCount()).isEqualTo(4);
        assertThat(result.unassignedCount()).isEqualTo(4);
        assertThat(result.issues()).noneMatch(issue -> issue.name().equals("通道") || issue.name().matches("第\\d+排"));
    }

    private MockMultipartFile workbookWithBlankSeats() {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("座位图");
            CellStyle blankSeatStyle = workbook.createCellStyle();
            blankSeatStyle.setBorderBottom(BorderStyle.THIN);

            Row firstRow = sheet.createRow(0);
            firstRow.createCell(0).setCellValue("第1排");
            blankCell(firstRow, 1, blankSeatStyle);
            firstRow.createCell(2).setCellValue("张三");
            blankCell(firstRow, 4, blankSeatStyle);

            Row secondRow = sheet.createRow(1);
            secondRow.createCell(0).setCellValue("第2排");
            secondRow.createCell(1).setCellValue("李四");
            blankCell(secondRow, 2, blankSeatStyle);
            secondRow.createCell(3).setCellValue("王五");

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "seat-chart.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MockMultipartFile workbookWithAisleAndRightRowLabels() {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("座位图");

            Row firstRow = sheet.createRow(0);
            firstRow.createCell(0).setCellValue("第1排");
            firstRow.createCell(1).setCellValue("张三");
            firstRow.createCell(2).setCellValue("过道");
            firstRow.createCell(3).setCellValue("李四");
            firstRow.createCell(4).setCellValue("第1排");

            Row secondRow = sheet.createRow(1);
            secondRow.createCell(0).setCellValue("第2排");
            secondRow.createCell(1).setCellValue("王五");
            secondRow.createCell(2).setCellValue("误填姓名");
            secondRow.createCell(3).setCellValue("钱七");
            secondRow.createCell(4).setCellValue("第2排");

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "seat-chart-aisle.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void blankCell(Row row, int columnIndex, CellStyle style) {
        var cell = row.createCell(columnIndex);
        cell.setBlank();
        cell.setCellStyle(style);
    }
}
