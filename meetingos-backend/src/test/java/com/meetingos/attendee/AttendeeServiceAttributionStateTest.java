package com.meetingos.attendee;

import com.meetingos.common.ImportRowTransactionRunner;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.invitation.RegistrationAttributionRepository;
import com.meetingos.invitation.RegistrationAttributionStateSynchronizer;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.room.RoomService;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.TduckFieldMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendeeServiceAttributionStateTest {
    @Mock
    private AttendeeRepository attendeeRepository;
    @Mock
    private MeetingAccessService meetingAccessService;
    @Mock
    private SeatAssignmentRepository seatAssignmentRepository;
    @Mock
    private DinnerAssignmentRepository dinnerAssignmentRepository;
    @Mock
    private PublicQueryHistoryRepository publicQueryHistoryRepository;
    @Mock
    private AttendeeAttributionStateSynchronizer attributionStateSynchronizer;

    private AttendeeService service;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meeting = new Meeting();
        meeting.setId(1L);
        service = new AttendeeService(
                attendeeRepository,
                org.mockito.Mockito.mock(MeetingRepository.class),
                meetingAccessService,
                org.mockito.Mockito.mock(SystemSettingsService.class),
                org.mockito.Mockito.mock(TduckFieldMappingRepository.class),
                publicQueryHistoryRepository,
                seatAssignmentRepository,
                dinnerAssignmentRepository,
                org.mockito.Mockito.mock(RoomService.class),
                ImportRowTransactionRunner.direct(),
                new AttendeeCodeGenerator(attendeeRepository),
                attributionStateSynchronizer
        );
    }

    @Test
    void rejectedReviewMarksAttributionInactive() {
        Attendee attendee = attendee(11L, ReviewStatus.APPROVED);
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));

        service.review(11L, new ReviewRequest(ReviewStatus.REJECTED, "资料不完整", null, null));

        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        verify(attributionStateSynchronizer).synchronize(1L, 11L, false);
    }

    @Test
    void approvedReviewKeepsAttributionActive() {
        Attendee attendee = attendee(12L, ReviewStatus.PENDING);
        when(attendeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(attendee));

        service.review(12L, new ReviewRequest(ReviewStatus.APPROVED, null, null, null));

        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        verify(attributionStateSynchronizer).synchronize(1L, 12L, true);
    }

    @Test
    void batchRejectedReviewMarksEveryAttributionInactiveInStableOrder() {
        Attendee first = attendee(13L, ReviewStatus.APPROVED);
        Attendee second = attendee(14L, ReviewStatus.APPROVED);
        when(attendeeRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(first));
        when(attendeeRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(second));

        int updated = service.batchReview(new ReviewRequest(
                ReviewStatus.REJECTED,
                "名额调整",
                null,
                List.of(14L, 13L)
        ));

        assertThat(updated).isEqualTo(2);
        verify(attributionStateSynchronizer).synchronize(1L, 13L, false);
        verify(attributionStateSynchronizer).synchronize(1L, 14L, false);
    }

    @Test
    void cancellationMarksAttributionInactive() {
        Attendee attendee = attendee(15L, ReviewStatus.APPROVED);
        when(attendeeRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(attendee));

        service.cancel(15L, "无法参会");

        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.CANCELLED);
        verify(attributionStateSynchronizer).synchronize(1L, 15L, false);
    }

    @Test
    void synchronizerFailureIsNotSwallowedByReviewTransaction() {
        Attendee attendee = attendee(16L, ReviewStatus.APPROVED);
        when(attendeeRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(attendee));
        doThrow(new IllegalStateException("attribution storage unavailable"))
                .when(attributionStateSynchronizer).synchronize(1L, 16L, false);

        assertThatThrownBy(() -> service.review(
                16L,
                new ReviewRequest(ReviewStatus.REJECTED, "资料不完整", null, null)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concreteSynchronizerMustJoinCallingTransaction() throws NoSuchMethodException {
        Transactional transactional = RegistrationAttributionStateSynchronizer.class
                .getMethod("synchronize", Long.class, Long.class, boolean.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);

        RegistrationAttributionRepository repository = org.mockito.Mockito.mock(RegistrationAttributionRepository.class);
        new RegistrationAttributionStateSynchronizer(repository).synchronize(1L, 17L, false);
        verify(repository).updateActiveByMeetingIdAndAttendeeId(1L, 17L, false);
    }

    private Attendee attendee(Long id, ReviewStatus reviewStatus) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setMeeting(meeting);
        attendee.setName("测试参会人");
        attendee.setPhone("13800000000");
        attendee.setReviewStatus(reviewStatus);
        return attendee;
    }
}
