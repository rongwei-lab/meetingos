package com.meetingos.integration;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.checkin.CheckinRequest;
import com.meetingos.checkin.CheckinService;
import com.meetingos.checkin.CheckinType;
import com.meetingos.common.BusinessException;
import com.meetingos.seating.ManualAssignSeatRequest;
import com.meetingos.seating.SeatRepository;
import com.meetingos.seating.SeatingService;
import com.meetingos.tduck.SyncStatus;
import com.meetingos.tduck.TduckManualSyncRequest;
import com.meetingos.tduck.TduckService;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

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
    private TduckService tduckService;

    @Autowired
    private MeetingRepository meetingRepository;

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
}
