package com.meetingos.seating;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SeatingServiceLayoutLimitTest {

    @Test
    void rejectsLayoutBeyondExistingAdminUiBoundaryBeforeDatabaseWork() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        SeatingService service = new SeatingService(
                meetingRepository,
                mock(AttendeeRepository.class),
                mock(SeatLayoutRepository.class),
                mock(SeatRepository.class),
                mock(SeatAssignmentRepository.class)
        );
        SeatLayoutRequest request = new SeatLayoutRequest(
                1L,
                "主会场",
                "A 区",
                81,
                80,
                null,
                null,
                null,
                null,
                SeatType.NORMAL,
                null
        );

        assertThatThrownBy(() -> service.createLayout(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6400");
        verifyNoInteractions(meetingRepository);
    }
}
