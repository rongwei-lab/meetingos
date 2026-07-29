package com.meetingos.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeMatchCandidate;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.dinner.DinnerService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.room.RoomRepository;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicQueryCandidatePagingTest {

    @Test
    void continuesAfterDatabaseSupersetCandidatesAreRejectedBySemanticCheck() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        PublicQueryService service = new PublicQueryService(
                meetingRepository,
                attendeeRepository,
                mock(SeatAssignmentRepository.class),
                mock(SeatRepository.class),
                mock(RoomRepository.class),
                mock(DinnerService.class),
                mock(PublicQueryHistoryService.class),
                mock(PublicCaptchaService.class),
                mock(PublicQueryTransactionRunner.class)
        );
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setPublicQueryToken("public-token");
        meeting.setPublicQueryFields("NAME");
        meeting.setPublicQueryMatchMode(PublicQueryMatchMode.FUZZY.name());
        meeting.setSelfCheckinEnabled(true);
        when(meetingRepository.findByPublicQueryToken("public-token")).thenReturn(Optional.of(meeting));

        List<AttendeeMatchCandidate> databaseSuperset = new ArrayList<>();
        List<Attendee> firstPage = new ArrayList<>();
        for (long id = 1; id <= 21; id++) {
            databaseSuperset.add(new AttendeeMatchCandidate(id, ReviewStatus.APPROVED));
            firstPage.add(attendee(meeting, id, "José"));
        }
        Attendee semanticMatch = attendee(meeting, 22L, "Jose");
        when(attendeeRepository.findPublicQueryCandidates(any()))
                .thenReturn(databaseSuperset)
                .thenReturn(List.of(new AttendeeMatchCandidate(22L, ReviewStatus.APPROVED)));
        when(attendeeRepository.findAllById(databaseSuperset.stream().map(AttendeeMatchCandidate::id).toList()))
                .thenReturn(firstPage);
        when(attendeeRepository.findAllById(List.of(22L))).thenReturn(List.of(semanticMatch));

        PublicAttendeeMatch result = service.resolvePublicAttendee(new PublicQueryRequest(
                "public-token",
                Map.of(PublicQueryField.NAME, "Jose")
        ));

        assertThat(result.attendee()).isSameAs(semanticMatch);
        ArgumentCaptor<com.meetingos.attendee.AttendeePublicQueryCriteria> criteria =
                ArgumentCaptor.forClass(com.meetingos.attendee.AttendeePublicQueryCriteria.class);
        org.mockito.Mockito.verify(attendeeRepository, org.mockito.Mockito.times(2))
                .findPublicQueryCandidates(criteria.capture());
        assertThat(criteria.getAllValues()).extracting(com.meetingos.attendee.AttendeePublicQueryCriteria::offset)
                .containsExactly(0, 21);
    }

    @Test
    void combinedQueryDoesNotTurnInfrastructureExceptionIntoPublicBusinessMessage() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        PublicQueryTransactionRunner transactionRunner = mock(PublicQueryTransactionRunner.class);
        PublicQueryService service = new PublicQueryService(
                meetingRepository,
                attendeeRepository,
                mock(SeatAssignmentRepository.class),
                mock(SeatRepository.class),
                mock(RoomRepository.class),
                mock(DinnerService.class),
                mock(PublicQueryHistoryService.class),
                mock(PublicCaptchaService.class),
                transactionRunner
        );
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setPublicQueryToken("public-token");
        meeting.setPublicQueryFields("NAME");
        meeting.setPublicQueryMatchMode(PublicQueryMatchMode.EXACT.name());
        meeting.setSeatQueryEnabled(true);
        meeting.setPublicCombinedQueryTypes("SEAT");
        when(meetingRepository.findByPublicQueryToken("public-token")).thenReturn(Optional.of(meeting));
        when(transactionRunner.read(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });
        IllegalStateException databaseFailure = new IllegalStateException("internal SQL detail");
        when(attendeeRepository.findPublicQueryCandidates(any())).thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.publicCombinedQueryResponse(
                new PublicQueryRequest("public-token", Map.of(PublicQueryField.NAME, "张三")),
                null,
                null
        )).isSameAs(databaseFailure);
    }

    @Test
    void optionalConfiguredFieldMayBeBlankAndIsExcludedFromMatching() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        PublicQueryService service = new PublicQueryService(
                meetingRepository,
                attendeeRepository,
                mock(SeatAssignmentRepository.class),
                mock(SeatRepository.class),
                mock(RoomRepository.class),
                mock(DinnerService.class),
                mock(PublicQueryHistoryService.class),
                mock(PublicCaptchaService.class),
                mock(PublicQueryTransactionRunner.class)
        );
        Meeting meeting = publicMeeting("PHONE,NAME", "PHONE");
        Attendee attendee = attendee(meeting, 1L, "张三");
        attendee.setPhone("13800000000");
        when(meetingRepository.findByPublicQueryToken("public-token")).thenReturn(Optional.of(meeting));
        when(attendeeRepository.findPublicQueryCandidates(any()))
                .thenReturn(List.of(new AttendeeMatchCandidate(1L, ReviewStatus.APPROVED)));
        when(attendeeRepository.findAllById(List.of(1L))).thenReturn(List.of(attendee));

        PublicAttendeeMatch match = service.resolvePublicAttendee(new PublicQueryRequest(
                "public-token",
                Map.of(PublicQueryField.PHONE, "13800000000")
        ));

        assertThat(match.attendee()).isSameAs(attendee);
        ArgumentCaptor<com.meetingos.attendee.AttendeePublicQueryCriteria> criteria =
                ArgumentCaptor.forClass(com.meetingos.attendee.AttendeePublicQueryCriteria.class);
        org.mockito.Mockito.verify(attendeeRepository).findPublicQueryCandidates(criteria.capture());
        assertThat(criteria.getValue().phone()).isEqualTo("13800000000");
        assertThat(criteria.getValue().name()).isNull();
    }

    @Test
    void selfCheckinPreviewIncludesAttendeeOrganization() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        PublicQueryService service = new PublicQueryService(
                meetingRepository,
                attendeeRepository,
                mock(SeatAssignmentRepository.class),
                mock(SeatRepository.class),
                mock(RoomRepository.class),
                mock(DinnerService.class),
                mock(PublicQueryHistoryService.class),
                mock(PublicCaptchaService.class),
                mock(PublicQueryTransactionRunner.class)
        );
        Meeting meeting = publicMeeting("PHONE", "PHONE");
        Attendee attendee = attendee(meeting, 1L, "张晓平");
        attendee.setPhone("13800005888");
        attendee.setOrganization("会议示例单位");
        when(meetingRepository.findByPublicQueryToken("public-token")).thenReturn(Optional.of(meeting));
        when(attendeeRepository.findPublicQueryCandidates(any()))
                .thenReturn(List.of(new AttendeeMatchCandidate(1L, ReviewStatus.APPROVED)));
        when(attendeeRepository.findAllById(List.of(1L))).thenReturn(List.of(attendee));

        PublicCheckinPreviewResponse preview = service.previewSelfCheckin(new PublicQueryRequest(
                "public-token",
                Map.of(PublicQueryField.PHONE, "13800005888")
        ));

        assertThat(preview.ready()).isTrue();
        assertThat(preview.organization()).isEqualTo("会议示例单位");
    }

    @Test
    void missingRequiredFieldIsRejectedBeforeQueryingAttendees() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        AttendeeRepository attendeeRepository = mock(AttendeeRepository.class);
        PublicQueryService service = new PublicQueryService(
                meetingRepository,
                attendeeRepository,
                mock(SeatAssignmentRepository.class),
                mock(SeatRepository.class),
                mock(RoomRepository.class),
                mock(DinnerService.class),
                mock(PublicQueryHistoryService.class),
                mock(PublicCaptchaService.class),
                mock(PublicQueryTransactionRunner.class)
        );
        Meeting meeting = publicMeeting("PHONE,NAME", "PHONE");
        when(meetingRepository.findByPublicQueryToken("public-token")).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.resolvePublicAttendee(new PublicQueryRequest(
                "public-token",
                Map.of(PublicQueryField.NAME, "张三")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写手机号");
        verifyNoInteractions(attendeeRepository);
    }

    @Test
    void settingsRejectRequiredFieldOutsideConfiguredFields() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        PublicQueryService service = new PublicQueryService(
                meetingRepository,
                mock(AttendeeRepository.class),
                mock(SeatAssignmentRepository.class),
                mock(SeatRepository.class),
                mock(RoomRepository.class),
                mock(DinnerService.class),
                mock(PublicQueryHistoryService.class),
                mock(PublicCaptchaService.class),
                mock(PublicQueryTransactionRunner.class)
        );
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(new Meeting()));
        MeetingQuerySettingsRequest request = new ObjectMapper().convertValue(
                Map.of(
                        "seatQueryEnabled", true,
                        "roomQueryEnabled", true,
                        "fields", List.of("PHONE"),
                        "requiredFields", List.of("NAME"),
                        "matchMode", "FUZZY"
                ),
                MeetingQuerySettingsRequest.class
        );

        assertThatThrownBy(() -> service.updateSettings(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必填字段必须包含在查询字段中");
    }

    private Meeting publicMeeting(String fields, String requiredFields) {
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setPublicQueryToken("public-token");
        meeting.setPublicQueryFields(fields);
        meeting.setPublicQueryRequiredFields(requiredFields);
        meeting.setPublicQueryMatchMode(PublicQueryMatchMode.FUZZY.name());
        meeting.setSelfCheckinEnabled(true);
        return meeting;
    }

    private Attendee attendee(Meeting meeting, Long id, String name) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setMeeting(meeting);
        attendee.setName(name);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        return attendee;
    }
}
