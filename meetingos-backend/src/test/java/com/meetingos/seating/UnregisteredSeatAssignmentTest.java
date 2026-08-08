package com.meetingos.seating;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeCodeGenerator;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnregisteredSeatAssignmentTest {

    @Test
    void createsMarkedAttendeeAndReturnsSourceFlagOnSeat() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        SeatRepository seatRepository = mock(SeatRepository.class);
        SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
        AttendeeCodeGenerator attendeeCodeGenerator = mock(AttendeeCodeGenerator.class);
        SeatingService service = new SeatingService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository,
                attendeeCodeGenerator
        );

        Meeting meeting = mock(Meeting.class);
        Seat seat = new Seat();
        seat.setId(10L);
        seat.setMeeting(meeting);
        seat.setSeatType(SeatType.NORMAL);
        seat.setAreaName("主会场");
        seat.setRowNo(1);
        seat.setSeatNo(2);
        seat.setSeatCode("主会场-1-2");

        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(assignmentRepository.findBySeatId(10L)).thenReturn(Optional.empty());
        when(attendeeCodeGenerator.generate(meeting)).thenReturn("M-UNREGISTERED-001");
        when(attendeeRepository.saveAndFlush(any(Attendee.class))).thenAnswer(invocation -> {
            Attendee attendee = invocation.getArgument(0);
            attendee.setId(101L);
            return attendee;
        });
        when(assignmentRepository.save(any(SeatAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SeatAssignment assignment = service.assignUnregistered(new AssignUnregisteredSeatRequest(10L, "现场人员", null));

        ArgumentCaptor<Attendee> attendeeCaptor = ArgumentCaptor.forClass(Attendee.class);
        verify(attendeeRepository).saveAndFlush(attendeeCaptor.capture());
        Attendee attendee = attendeeCaptor.getValue();
        assertThat(attendee.getName()).isEqualTo("现场人员");
        assertThat(attendee.getPhone()).isNull();
        assertThat(attendee.isUnregistered()).isTrue();
        assertThat(assignment.getAttendee()).isSameAs(attendee);
        assertThat(assignment.getSeat()).isSameAs(seat);

        when(assignmentRepository.findWithAttendeeAndSeatByMeetingId(1L)).thenReturn(List.of(assignment));
        when(seatRepository.findByMeetingIdOrderByLayoutSortOrderAscAreaNameAscRowNoAscSeatNoAsc(1L)).thenReturn(List.of(seat));
        SeatView view = service.seatMap(1L).get(0);
        assertThat(view.status()).isEqualTo(SeatStatus.ASSIGNED);
        assertThat(view.unregistered()).isTrue();
        assertThat(view.attendeeId()).isEqualTo(101L);
        assertThat(view.attendeeName()).isEqualTo("现场人员");

        attendee.setCheckinStatus(CheckinStatus.CHECKED_IN);
        SeatView checkedInView = service.seatMap(1L).get(0);
        assertThat(checkedInView.status()).isEqualTo(SeatStatus.CHECKED_IN);
        assertThat(checkedInView.unregistered()).isTrue();
    }

    @Test
    void clearingUnregisteredSeatKeepsAttendeeAndDeletesOnlyAssignment() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        SeatRepository seatRepository = mock(SeatRepository.class);
        SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
        AttendeeCodeGenerator attendeeCodeGenerator = mock(AttendeeCodeGenerator.class);
        SeatingService service = new SeatingService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository,
                attendeeCodeGenerator
        );

        Seat seat = new Seat();
        seat.setId(10L);
        Attendee attendee = new Attendee();
        attendee.setUnregistered(true);
        SeatAssignment assignment = new SeatAssignment();
        assignment.setSeat(seat);
        assignment.setAttendee(attendee);

        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(assignmentRepository.findBySeatId(10L)).thenReturn(Optional.of(assignment));

        service.clearUnregistered(10L);

        verify(assignmentRepository).delete(eq(assignment));
    }

    @Test
    void normalizesOptionalPhoneBeforeCheckingForExistingAttendee() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        SeatRepository seatRepository = mock(SeatRepository.class);
        SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
        AttendeeCodeGenerator attendeeCodeGenerator = mock(AttendeeCodeGenerator.class);
        SeatingService service = new SeatingService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository,
                attendeeCodeGenerator
        );

        Meeting meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(1L);
        Seat seat = new Seat();
        seat.setId(10L);
        seat.setMeeting(meeting);
        seat.setSeatType(SeatType.NORMAL);
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(assignmentRepository.findBySeatId(10L)).thenReturn(Optional.empty());
        when(attendeeRepository.findByMeetingIdAndPhone(1L, "13800138000")).thenReturn(Optional.of(new Attendee()));

        assertThatThrownBy(() -> service.assignUnregistered(new AssignUnregisteredSeatRequest(10L, "现场人员", "138 0013-8000")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该手机号已存在参会人，请从参会人列表分配座位");
    }
}
