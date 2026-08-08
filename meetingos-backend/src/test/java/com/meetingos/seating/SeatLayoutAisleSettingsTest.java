package com.meetingos.seating;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatLayoutAisleSettingsTest {

    @Test
    void seatMapReturnsParsedAislesForEachLayoutAndKeepsLegacyPhysicalAisle() {
        Fixture fixture = new Fixture();
        SeatLayout firstLayout = layout(8, 0, "v1:7,3,3,0,8,invalid");
        SeatLayout secondLayout = layout(4, 1, "3号座后过道，1号座后通道，3号座后过道，4号座后过道");
        Seat firstAreaSeat = seat(11L, "A 区", 1, firstLayout);
        Seat secondAreaSeat = seat(12L, "B 区", 2, secondLayout);
        Seat physicalAisle = seat(13L, "B 区", 3, secondLayout);
        physicalAisle.setAisle(true);
        physicalAisle.setDisabled(true);
        Seat withoutLayout = seat(14L, "旧区域", 1, null);

        when(fixture.assignmentRepository.findWithAttendeeAndSeatByMeetingId(1L)).thenReturn(List.of());
        when(fixture.seatRepository.findByMeetingIdOrderByLayoutSortOrderAscAreaNameAscRowNoAscSeatNoAsc(1L))
                .thenReturn(List.of(firstAreaSeat, secondAreaSeat, physicalAisle, withoutLayout));

        List<SeatView> views = fixture.service.seatMap(1L);

        assertThat(views.get(0).aisleAfterSeatNos()).containsExactly(3, 7);
        assertThat(views.get(1).aisleAfterSeatNos()).containsExactly(1, 3);
        assertThat(views.get(2).aisleAfterSeatNos()).containsExactly(1, 3);
        assertThat(views.get(2).aisle()).isTrue();
        assertThat(views.get(2).status()).isEqualTo(SeatStatus.AISLE);
        assertThat(views.get(3).aisleAfterSeatNos()).isEmpty();
    }

    @Test
    void createLayoutPersistsLayoutLevelAisleSettings() {
        Fixture fixture = new Fixture();
        when(fixture.meetingRepository.findById(1L)).thenReturn(Optional.of(fixture.meeting));
        when(fixture.layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(1L, "A 区")).thenReturn(List.of());
        when(fixture.seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A 区")).thenReturn(List.of());

        fixture.service.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "A 区",
                null,
                2,
                10,
                null,
                "v1:7,3,3,0,10",
                null,
                0,
                SeatType.NORMAL,
                List.of(),
                null,
                null
        ));

        ArgumentCaptor<SeatLayout> savedLayout = ArgumentCaptor.forClass(SeatLayout.class);
        verify(fixture.layoutRepository).save(savedLayout.capture());
        assertThat(savedLayout.getValue().getAisleSettings()).isEqualTo("v1:3,7");
    }

    @Test
    void layoutUpdateWithoutDefaultSeatTypeKeepsExistingSeatType() {
        Fixture fixture = new Fixture();
        SeatLayout existingLayout = layout(10, 0, null);
        Seat existingSeat = seat(11L, "A 区", 1, existingLayout);
        existingSeat.setSeatType(SeatType.VIP);
        when(fixture.meetingRepository.findById(1L)).thenReturn(Optional.of(fixture.meeting));
        when(fixture.layoutRepository.findByMeetingIdAndAreaNameOrderByIdAsc(1L, "A 区")).thenReturn(List.of(existingLayout));
        when(fixture.seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A 区"))
                .thenReturn(List.of(existingSeat));

        fixture.service.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "A 区",
                null,
                1,
                1,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null
        ));

        assertThat(existingSeat.getSeatType()).isEqualTo(SeatType.VIP);
    }

    private static SeatLayout layout(int seatsPerRow, int sortOrder, String aisleSettings) {
        SeatLayout layout = new SeatLayout();
        layout.setSeatsPerRow(seatsPerRow);
        layout.setSortOrder(sortOrder);
        layout.setAisleSettings(aisleSettings);
        return layout;
    }

    private static Seat seat(Long id, String areaName, int seatNo, SeatLayout layout) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setAreaName(areaName);
        seat.setRowNo(1);
        seat.setSeatNo(seatNo);
        seat.setSeatCode(areaName + "-1-" + seatNo);
        seat.setSeatType(SeatType.NORMAL);
        seat.setLayout(layout);
        return seat;
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
    }
}
