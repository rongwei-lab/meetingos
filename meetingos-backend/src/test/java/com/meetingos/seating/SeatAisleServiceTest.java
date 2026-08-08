package com.meetingos.seating;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeatAisleServiceTest {

    @Test
    void togglingAisleNormalizesStateAndRestoresUsableNormalSeat() {
        Fixture fixture = new Fixture();
        Seat seat = fixture.seat();
        seat.setSeatType(SeatType.VIP);
        seat.setLocked(true);
        seat.setStructuralBlank(true);
        when(fixture.seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(fixture.assignmentRepository.findBySeatId(10L)).thenReturn(Optional.empty());

        Seat aisle = fixture.service.updateSeatAisle(10L, new SeatAisleRequest(true));

        assertThat(aisle.isAisle()).isTrue();
        assertThat(aisle.isDisabled()).isTrue();
        assertThat(aisle.isLocked()).isFalse();
        assertThat(aisle.isStructuralBlank()).isFalse();
        assertThat(aisle.getSeatType()).isEqualTo(SeatType.NORMAL);
        when(fixture.assignmentRepository.findWithAttendeeAndSeatByMeetingId(1L)).thenReturn(List.of());
        when(fixture.seatRepository.findByMeetingIdOrderByLayoutSortOrderAscAreaNameAscRowNoAscSeatNoAsc(1L))
                .thenReturn(List.of(aisle));
        SeatView aisleView = fixture.service.seatMap(1L).get(0);
        assertThat(aisleView.status()).isEqualTo(SeatStatus.AISLE);
        assertThat(aisleView.aisle()).isTrue();

        Seat restored = fixture.service.updateSeatAisle(10L, new SeatAisleRequest(false));

        assertThat(restored.isAisle()).isFalse();
        assertThat(restored.isDisabled()).isFalse();
        assertThat(restored.isLocked()).isFalse();
        assertThat(restored.isStructuralBlank()).isFalse();
        assertThat(restored.getSeatType()).isEqualTo(SeatType.NORMAL);
    }

    @Test
    void assignedSeatCannotBecomeAisle() {
        Fixture fixture = new Fixture();
        Seat seat = fixture.seat();
        when(fixture.seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(fixture.assignmentRepository.findBySeatId(10L)).thenReturn(Optional.of(new SeatAssignment()));

        assertThatThrownBy(() -> fixture.service.updateSeatAisle(10L, new SeatAisleRequest(true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有人员入座");
    }

    @Test
    void aisleCannotBeAssignedLockedOrChangedToAnotherSeatState() {
        Fixture fixture = new Fixture();
        Seat seat = fixture.seat();
        seat.setAisle(true);
        seat.setDisabled(true);
        Attendee attendee = new Attendee();
        attendee.setId(8L);
        attendee.setMeeting(fixture.meeting);
        when(fixture.seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(fixture.attendeeRepository.findById(8L)).thenReturn(Optional.of(attendee));

        assertThatThrownBy(() -> fixture.service.manualAssign(new ManualAssignSeatRequest(8L, 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过道不能修改或分配");
        assertThatThrownBy(() -> fixture.service.lock(10L, new SeatLockRequest(true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过道不能修改或分配");
        assertThatThrownBy(() -> fixture.service.updateSeatType(10L, new SeatTypeRequest(SeatType.VIP)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过道不能修改或分配");
        assertThatThrownBy(() -> fixture.service.updateSeatDisabled(10L, new SeatDisabledRequest(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过道不能修改或分配");
    }

    @Test
    void layoutCannotTurnAssignedSeatIntoAisle() {
        Fixture fixture = new Fixture();
        Seat seat = fixture.seat();
        when(fixture.meetingRepository.findById(1L)).thenReturn(Optional.of(fixture.meeting));
        when(fixture.layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(1L, "主会场")).thenReturn(List.of());
        when(fixture.seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "主会场")).thenReturn(List.of(seat));
        when(fixture.assignmentRepository.existsBySeatIdIn(anySet())).thenReturn(true);

        SeatLayoutRequest request = new SeatLayoutRequest(
                1L,
                "主会场",
                "主会场",
                null,
                1,
                1,
                "row-seat",
                "",
                "北",
                0,
                SeatType.NORMAL,
                List.of(),
                List.of(),
                List.of("主会场-1-1")
        );

        assertThatThrownBy(() -> fixture.service.createLayout(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能改为过道");
    }

    private static final class Fixture {
        private final MeetingRepository meetingRepository = mock(MeetingRepository.class);
        private final AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        private final SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        private final SeatRepository seatRepository = mock(SeatRepository.class);
        private final SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
        private final SeatingService service = new SeatingService(
                meetingRepository,
                attendeeRepository,
                layoutRepository,
                seatRepository,
                assignmentRepository
        );
        private final Meeting meeting = new Meeting();

        private Fixture() {
            meeting.setId(1L);
        }

        private Seat seat() {
            Seat seat = new Seat();
            seat.setId(10L);
            seat.setMeeting(meeting);
            seat.setAreaName("主会场");
            seat.setRowNo(1);
            seat.setSeatNo(1);
            seat.setSeatCode("主会场-1-1");
            seat.setSeatType(SeatType.NORMAL);
            return seat;
        }
    }
}
