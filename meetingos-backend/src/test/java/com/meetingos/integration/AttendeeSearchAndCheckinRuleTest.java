package com.meetingos.integration;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewRequest;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.checkin.CheckinRequest;
import com.meetingos.checkin.CheckinService;
import com.meetingos.checkin.CheckinType;
import com.meetingos.common.BusinessException;
import com.meetingos.dinner.DinnerAssignRequest;
import com.meetingos.dinner.DinnerService;
import com.meetingos.seating.ManualAssignSeatRequest;
import com.meetingos.seating.SeatRepository;
import com.meetingos.seating.SeatingService;
import com.meetingos.room.AssignRoomRequest;
import com.meetingos.room.RoomRequest;
import com.meetingos.room.RoomRepository;
import com.meetingos.room.RoomService;
import com.meetingos.room.RoomStatus;
import com.meetingos.tduck.SyncStatus;
import com.meetingos.tduck.TduckManualSyncRequest;
import com.meetingos.tduck.TduckClient;
import com.meetingos.tduck.TduckFormDetails;
import com.meetingos.tduck.TduckService;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.publicapi.PublicQueryHistoryService;
import com.meetingos.publicapi.PublicQueryField;
import com.meetingos.publicapi.PublicQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.Map;
import java.util.Optional;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestDataConfig.class)
class AttendeeSearchAndCheckinRuleTest {

    @Autowired
    private AttendeeService attendeeService;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private SeatingService seatingService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private DinnerService dinnerService;

    @Autowired
    private TduckService tduckService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private PublicQueryHistoryRepository publicQueryHistoryRepository;

    @Autowired
    private PublicQueryHistoryService publicQueryHistoryService;

    @MockBean
    private TduckClient tduckClient;

    @BeforeEach
    void setUpTduckClient() {
        /*
         * 公开报名现在会校验 TDuck 端表单是否真实可填写。集成测试只关心
         * MeetingOS 的开放/关闭规则，因此用测试替身固定外部 TDuck 状态，
         * 避免测试需要访问真实 TDuck 服务。
         */
        lenient().when(tduckClient.formDetails("ndef2026"))
                .thenReturn(Optional.of(new TduckFormDetails("ndef2026", "数字经济论坛报名表", "2", true, 5)));
        lenient().when(tduckClient.registrationUrlFor(anyString()))
                .thenAnswer(invocation -> "http://tduck.test/s/" + invocation.getArgument(0, String.class));
    }

    @Test
    void supplementCheckinRequiresRemarkForAudit() {
        CheckinRequest request = new CheckinRequest(
                1L,
                null,
                null,
                "13800000001",
                null,
                "补签台",
                CheckinType.SUPPLEMENT,
                " "
        );

        assertThatThrownBy(() -> checkinService.checkin(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补签必须填写原因");
    }

    @Test
    void attendeeSearchCanFilterBySeatPresenceAndTags() {
        attendeeService.updateTags(1L, "VIP,接待");
        Long seatId = seatRepository.findByMeetingIdOrderByRowNoAscSeatNoAsc(1L).get(0).getId();
        seatingService.manualAssign(new ManualAssignSeatRequest(1L, seatId));

        var seated = attendeeService.search(1L, null, ReviewStatus.APPROVED, null, null, true, "接待", 0, 20);
        var unseated = attendeeService.search(1L, null, ReviewStatus.APPROVED, null, null, false, null, 0, 20);

        assertThat(seated.content())
                .extracting("phone")
                .containsExactly("13800000001");
        assertThat(unseated.content())
                .extracting("phone")
                .contains("13800000002")
                .doesNotContain("13800000001");
    }

    @Test
    void reviewedAttendeeWithPublicQueryHistoryCannotBeRejected() {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        publicQueryHistoryService.recordSuccess(
                attendee.getMeeting(),
                PublicQueryType.SEAT,
                Map.of(PublicQueryField.PHONE, attendee.getPhone(), PublicQueryField.NAME, attendee.getName()),
                attendee,
                "A区 1排1座",
                "127.0.0.1",
                "integration-test"
        );

        assertThat(publicQueryHistoryRepository.existsByAttendeeIdAndQueryTypeIn(1L, List.of(PublicQueryType.SEAT))).isTrue();
        assertThatThrownBy(() -> attendeeService.review(1L, new ReviewRequest(ReviewStatus.REJECTED, "资料不完整", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有查询记录，不能拒绝");
    }

    @Test
    void batchRejectStopsWhenSelectedAttendeeHasPublicQueryHistory() {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        publicQueryHistoryService.recordSuccess(
                attendee.getMeeting(),
                PublicQueryType.ROOM,
                Map.of(PublicQueryField.PHONE, attendee.getPhone(), PublicQueryField.NAME, attendee.getName()),
                attendee,
                "1801",
                "127.0.0.1",
                "integration-test"
        );

        assertThatThrownBy(() -> attendeeService.batchReview(new ReviewRequest(ReviewStatus.REJECTED, "批量拒绝", null, List.of(1L, 2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有查询记录，不能拒绝");
        assertThat(attendeeRepository.findById(1L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(attendeeRepository.findById(2L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void reviewedAttendeeWithSeatAssignmentCannotBeRejected() {
        Long seatId = seatRepository.findByMeetingIdOrderByRowNoAscSeatNoAsc(1L).get(0).getId();
        seatingService.manualAssign(new ManualAssignSeatRequest(1L, seatId));

        assertThatThrownBy(() -> attendeeService.review(1L, new ReviewRequest(ReviewStatus.REJECTED, "资料不完整", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已分配座位，不能拒绝");
        assertThat(attendeeRepository.findById(1L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void reviewedAttendeeWithRoomAssignmentCannotBeRejected() {
        ensureRoom("1801");
        roomService.assign(new AssignRoomRequest(1L, "1801", "拒绝规则测试"));

        assertThatThrownBy(() -> attendeeService.review(1L, new ReviewRequest(ReviewStatus.REJECTED, "资料不完整", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已分配住宿，不能拒绝");
        assertThat(attendeeRepository.findById(1L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void reviewedAttendeeWithDinnerAssignmentCannotBeRejected() {
        dinnerService.assign(new DinnerAssignRequest(1L, "A1", "拒绝规则测试"));

        assertThatThrownBy(() -> attendeeService.review(1L, new ReviewRequest(ReviewStatus.REJECTED, "资料不完整", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已分配晚宴，不能拒绝");
        assertThat(attendeeRepository.findById(1L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void batchRejectStopsWhenSelectedAttendeeHasAssignedResource() {
        ensureRoom("1802");
        roomService.assign(new AssignRoomRequest(1L, "1802", "批量拒绝规则测试"));

        assertThatThrownBy(() -> attendeeService.batchReview(new ReviewRequest(ReviewStatus.REJECTED, "批量拒绝", null, List.of(1L, 2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已分配住宿，不能拒绝");
        assertThat(attendeeRepository.findById(1L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(attendeeRepository.findById(2L).orElseThrow().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void manualTduckCompensationSyncIsIdempotent() {
        TduckManualSyncRequest request = new TduckManualSyncRequest(
                1L,
                "manual-td-1",
                Map.of(
                        "name", "手工补偿参会人",
                        "phone", "13800007777",
                        "organization", "补偿同步测试",
                        "attendeeType", "GUEST"
                ),
                null,
                null
        );

        var first = tduckService.manualSync(request);
        var second = tduckService.manualSync(request);

        assertThat(first.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        var attendee = attendeeRepository.findByMeetingIdAndPhone(1L, "13800007777");
        assertThat(attendee).isPresent();
        assertThat(attendee.get().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void manualTduckDeleteCompensationCanCancelByDataIdOnly() {
        TduckManualSyncRequest createRequest = new TduckManualSyncRequest(
                1L,
                "manual-delete-td-1",
                Map.of(
                        "name", "手工撤回参会人",
                        "phone", "13800007778",
                        "organization", "补偿同步测试"
                ),
                null,
                "form_data_add"
        );
        tduckService.manualSync(createRequest);

        TduckManualSyncRequest deleteRequest = new TduckManualSyncRequest(
                1L,
                "manual-delete-td-1",
                null,
                null,
                "form_data_delete"
        );

        var first = tduckService.manualSync(deleteRequest);
        var second = tduckService.manualSync(deleteRequest);

        assertThat(first.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(SyncStatus.DUPLICATE_IGNORED);
        var attendee = attendeeRepository.findByMeetingIdAndPhone(1L, "13800007778").orElseThrow();
        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.CANCELLED);
        assertThat(attendee.getReviewReason()).isEqualTo("TDuck 报名删除或撤回");
    }

    @Test
    void publicRegistrationInfoOnlyReturnsUrlWhenRegistrationIsOpen() {
        var openInfo = tduckService.registrationInfo("NDEF2026");
        assertThat(openInfo.open()).isTrue();
        assertThat(openInfo.formName()).isEqualTo("数字经济论坛报名表");
        assertThat(openInfo.registrationUrl()).isNotBlank();

        var meeting = meetingRepository.findByCode("NDEF2026").orElseThrow();
        meeting.setStatus(MeetingStatus.ARCHIVED);

        var closedInfo = tduckService.registrationInfo("NDEF2026");
        assertThat(closedInfo.open()).isFalse();
        assertThat(closedInfo.registrationUrl()).isNull();
        assertThat(closedInfo.message()).contains("不在报名开放状态");
    }

    private void ensureRoom(String roomNo) {
        if (roomRepository.findByMeetingIdAndRoomNo(1L, roomNo).isPresent()) {
            return;
        }
        roomService.create(new RoomRequest(1L, roomNo, "双床房", "北京会议中心酒店", "14:00 后可入住", 2, RoomStatus.AVAILABLE));
    }
}
