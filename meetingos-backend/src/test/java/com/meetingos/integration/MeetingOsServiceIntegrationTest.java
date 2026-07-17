package com.meetingos.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRequest;
import com.meetingos.attendee.AttendeeResponse;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.attendee.AttendeeTagsRequest;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.checkin.CheckinRequest;
import com.meetingos.checkin.CheckinResponse;
import com.meetingos.checkin.CheckinResultStatus;
import com.meetingos.checkin.CheckinService;
import com.meetingos.checkin.CheckinType;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ImportResult;
import com.meetingos.exporttask.ExportFile;
import com.meetingos.exporttask.ExportTaskService;
import com.meetingos.exporttask.ExportTaskRepository;
import com.meetingos.exporttask.ExportTaskStatus;
import com.meetingos.meeting.MeetingService;
import com.meetingos.meeting.MeetingStatus;
import com.meetingos.operationlog.OperationLogRepository;
import com.meetingos.permission.MeetingPermissionRole;
import com.meetingos.permission.MeetingUserPermissionRequest;
import com.meetingos.permission.MeetingUserPermissionService;
import com.meetingos.publicapi.MeetingQuerySettingsRequest;
import com.meetingos.publicapi.PublicQueryField;
import com.meetingos.publicapi.PublicCombinedQueryResponse;
import com.meetingos.publicapi.PublicQueryHistory;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.publicapi.PublicQueryMatchMode;
import com.meetingos.publicapi.PublicQueryRequest;
import com.meetingos.publicapi.PublicQueryResultField;
import com.meetingos.publicapi.PublicQueryService;
import com.meetingos.publicapi.PublicQueryStatus;
import com.meetingos.publicapi.PublicQueryType;
import com.meetingos.room.AssignRoomRequest;
import com.meetingos.room.RoomRequest;
import com.meetingos.room.RoomRepository;
import com.meetingos.room.RoomService;
import com.meetingos.room.RoomStatus;
import com.meetingos.seating.AutoSeatingResult;
import com.meetingos.seating.ManualAssignSeatRequest;
import com.meetingos.seating.SeatDisabledRequest;
import com.meetingos.seating.SeatLayoutRequest;
import com.meetingos.seating.SeatRepository;
import com.meetingos.seating.SeatType;
import com.meetingos.seating.SeatingService;
import com.meetingos.seating.SwapSeatRequest;
import com.meetingos.security.Role;
import com.meetingos.security.CurrentUser;
import com.meetingos.security.UserStatus;
import com.meetingos.settings.SystemSettingsRequest;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.SyncStatus;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import com.meetingos.tduck.TduckService;
import com.meetingos.tduck.TduckTransactionRunner;
import com.meetingos.tduck.TduckWebhookRequest;
import com.meetingos.tduck.TduckWebhookResult;
import com.meetingos.user.CreateUserRequest;
import com.meetingos.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestDataConfig.class)
class MeetingOsServiceIntegrationTest {

    private static final String STRONG_TEST_PASSWORD = "MeetingOS@2026";
    private static final String TEST_PUBLIC_QUERY_TOKEN = "pq_test_ndef2026";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> COMMITTED_TDUCK_TEST_PHONES = List.of(
            "13800009999",
            "13800007777",
            "13800007778",
            "13800006666"
    );

    @Autowired
    private SeatingService seatingService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private TduckService tduckService;

    @Autowired
    private TduckTransactionRunner tduckTransactionRunner;

    @Autowired
    private TduckFieldMappingRepository tduckFieldMappingRepository;

    @Autowired
    private AttendeeService attendeeService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PublicQueryHistoryRepository publicQueryHistoryRepository;

    @Autowired
    private ExportTaskService exportTaskService;

    @Autowired
    private ExportTaskRepository exportTaskRepository;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private UserService userService;

    @Autowired
    private MeetingUserPermissionService meetingUserPermissionService;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private PublicQueryService publicQueryService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @AfterTransaction
    void cleanupCommittedTduckAttendees() {
        /*
         * Webhook 使用 REQUIRES_NEW，提交结果不会随测试方法的外层事务回滚。
         * 在外层测试事务结束后再用独立事务清理，避免固定手机号污染同类中的统计和更新用例。
         */
        tduckTransactionRunner.requiresNew(() -> {
            COMMITTED_TDUCK_TEST_PHONES.forEach(phone ->
                    attendeeRepository.findByMeetingIdAndPhone(1L, phone).ifPresent(attendeeRepository::delete)
            );
            return null;
        });
    }

    @Test
    void autoAssignSeatsKeepsApprovedAttendeesAssignable() {
        AutoSeatingResult result = seatingService.autoAssign(1L);

        assertThat(result.assignedCount()).isGreaterThan(0);
        assertThat(result.skippedCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void seatLayoutCanGrowAndRejectsRemovingAssignedSeats() {
        SeatLayoutRequest expanded = new SeatLayoutRequest(
                1L,
                "主会场",
                "A区",
                3,
                5,
                "row-seat",
                "",
                "北",
                0,
                SeatType.NORMAL,
                null
        );

        seatingService.createLayout(expanded);

        assertThat(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区"))
                .hasSize(15);

        var expandedSeats = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区");
        Long outsideShrinkRangeSeatId = expandedSeats.get(expandedSeats.size() - 1).getId();
        seatingService.manualAssign(new ManualAssignSeatRequest(1L, outsideShrinkRangeSeatId));

        SeatLayoutRequest shrink = new SeatLayoutRequest(
                1L,
                "主会场",
                "A区",
                1,
                1,
                "row-seat",
                "",
                "北",
                0,
                SeatType.NORMAL,
                null
        );

        assertThatThrownBy(() -> seatingService.createLayout(shrink))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已分配座位");
    }

    @Test
    void seatAreaCanBeDeletedOnlyWhenNoAttendeeIsAssigned() {
        SeatLayoutRequest area = new SeatLayoutRequest(
                1L,
                "主会场",
                "临时区",
                2,
                2,
                "row-seat",
                "",
                "北",
                9,
                SeatType.NORMAL,
                null
        );
        seatingService.createLayout(area);

        assertThat(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "临时区"))
                .hasSize(4);

        Long seatId = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "临时区").get(0).getId();
        seatingService.manualAssign(new ManualAssignSeatRequest(1L, seatId));

        assertThatThrownBy(() -> seatingService.deleteArea(1L, "临时区"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有人员入座");

        seatingService.clear(1L);
        seatingService.deleteArea(1L, "临时区");

        assertThat(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "临时区"))
                .isEmpty();
    }

    @Test
    void assignedSeatsCanBeSwappedWithoutUniqueSeatCollision() {
        List<Long> seatIds = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区")
                .stream()
                .limit(2)
                .map(seat -> seat.getId())
                .toList();
        seatingService.manualAssign(new ManualAssignSeatRequest(1L, seatIds.get(0)));
        seatingService.manualAssign(new ManualAssignSeatRequest(2L, seatIds.get(1)));

        seatingService.swap(new SwapSeatRequest(1L, 2L));

        assertThat(seatingService.seatMap(1L))
                .filteredOn(view -> view.attendeeId() != null)
                .anySatisfy(view -> {
                    assertThat(view.attendeeId()).isEqualTo(1L);
                    assertThat(view.seatId()).isEqualTo(seatIds.get(1));
                })
                .anySatisfy(view -> {
                    assertThat(view.attendeeId()).isEqualTo(2L);
                    assertThat(view.seatId()).isEqualTo(seatIds.get(0));
                });
    }

    @Test
    void seatLayoutCanRenameExistingAreaAndKeepAssignments() {
        Long firstSeatId = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId();
        seatingService.manualAssign(new ManualAssignSeatRequest(1L, firstSeatId));

        seatingService.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "主会场区",
                "A区",
                2,
                4,
                "row-seat",
                "",
                "北",
                0,
                SeatType.NORMAL,
                null
        ));

        assertThat(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区"))
                .isEmpty();
        assertThat(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "主会场区"))
                .hasSize(8)
                .first()
                .satisfies(seat -> {
                    assertThat(seat.getId()).isEqualTo(firstSeatId);
                    assertThat(seat.getSeatCode()).isEqualTo("主会场区-1-1");
                });
        assertThat(seatingService.seatMap(1L))
                .filteredOn(view -> view.seatId().equals(firstSeatId))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.areaName()).isEqualTo("主会场区");
                    assertThat(view.attendeeId()).isNotNull();
                });
    }

    @Test
    void seatLayoutRenameRejectsExistingTargetArea() {
        seatingService.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "B区",
                1,
                2,
                "row-seat",
                "",
                "北",
                1,
                SeatType.NORMAL,
                null
        ));

        assertThatThrownBy(() -> seatingService.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "B区",
                "A区",
                2,
                4,
                "row-seat",
                "",
                "北",
                0,
                SeatType.NORMAL,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("区域名称已存在");
    }

    @Test
    void seatMapKeepsConfiguredAreaOrder() {
        seatingService.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "A区",
                2,
                4,
                "row-seat",
                "",
                "北",
                2,
                SeatType.NORMAL,
                null
        ));
        seatingService.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "B区",
                1,
                2,
                "row-seat",
                "",
                "北",
                1,
                SeatType.NORMAL,
                null
        ));

        // 区域拖拽后的顺序存放在 SeatLayout.sortOrder 中，座位图必须按该顺序返回，
        // 否则用户刷新页面后会看到区域又回到名称排序。
        assertThat(seatingService.seatMap(1L))
                .extracting(view -> view.areaName())
                .containsSubsequence("B区", "A区");
    }

    @Test
    void seatLayoutPersistsDefaultSeatTypeForExistingSeats() {
        seatingService.createLayout(new SeatLayoutRequest(
                1L,
                "主会场",
                "A区",
                2,
                4,
                "row-seat",
                "",
                "北",
                0,
                SeatType.VIP,
                null
        ));

        assertThat(seatingService.seatMap(1L))
                .filteredOn(view -> view.areaName().equals("A区"))
                .extracting(view -> view.seatType())
                .containsOnly(SeatType.VIP);
        assertThat(seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区"))
                .extracting(seat -> seat.getSeatType())
                .containsOnly(SeatType.VIP);
    }

    @Test
    void emptySeatCanBeDisabledAndAssignedSeatCannotBeDisabled() {
        var seats = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区");
        Long assignedSeatId = seats.get(0).getId();
        Long emptySeatId = seats.get(1).getId();

        seatingService.manualAssign(new ManualAssignSeatRequest(1L, assignedSeatId));

        // 停用座位会让它退出自动排座和手动分配候选；已有分配的座位必须先解除，
        // 否则会形成“座位不可用但仍被人员占用”的不一致状态。
        assertThatThrownBy(() -> seatingService.updateSeatDisabled(assignedSeatId, new SeatDisabledRequest(true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有人员入座");

        var disabledSeat = seatingService.updateSeatDisabled(emptySeatId, new SeatDisabledRequest(true));
        assertThat(disabledSeat.isDisabled()).isTrue();
        assertThat(disabledSeat.getSeatType()).isEqualTo(SeatType.VIP);
        assertThat(seatingService.seatMap(1L))
                .filteredOn(view -> view.seatId().equals(emptySeatId))
                .singleElement()
                .satisfies(view -> assertThat(view.status().name()).isEqualTo("DISABLED"));

        var enabledSeat = seatingService.updateSeatDisabled(emptySeatId, new SeatDisabledRequest(false));
        assertThat(enabledSeat.isDisabled()).isFalse();
        assertThat(enabledSeat.getSeatType()).isEqualTo(SeatType.VIP);
    }

    @Test
    void checkinRejectsDuplicateAfterFirstSuccess() {
        CheckinRequest request = new CheckinRequest(1L, "MEETINGOS-CHECKIN:1:NDEF2026-0001", null, null, null, "A入口", CheckinType.SCAN, null);

        CheckinResponse first = checkinService.checkin(request);
        CheckinResponse second = checkinService.checkin(request);

        assertThat(first.status()).isEqualTo(CheckinResultStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(CheckinResultStatus.DUPLICATE);
        assertThat(second.entrance()).isEqualTo("A入口");
        assertThat(second.operator()).isNotBlank();
        assertThat(attendeeRepository.findByMeetingIdAndAttendeeCode(1L, "NDEF2026-0001").orElseThrow().getCheckinStatus())
                .isEqualTo(CheckinStatus.CHECKED_IN);
    }

    @Test
    void checkinRecordsReturnDetachedMaskedDtos() {
        CheckinRequest request = new CheckinRequest(1L, null, null, "13800000001", null, "A入口", CheckinType.MANUAL, null);

        checkinService.checkin(request);
        checkinService.checkin(request);

        var records = checkinService.records(1L, 0, 10);

        assertThat(records.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(records.content()).anySatisfy(record -> {
            assertThat(record.meetingId()).isEqualTo(1L);
            assertThat(record.attendeeId()).isEqualTo(1L);
            assertThat(record.attendeeName()).isEqualTo("陈嘉宁");
            assertThat(record.requestSnapshot()).containsEntry("phone", "****0001");
        });
    }

    @Test
    void checkinCanResolveSingleAttendeeByNameSearch() {
        CheckinRequest request = new CheckinRequest(1L, null, null, null, "王子墨", "B入口", CheckinType.SEARCH, null);

        CheckinResponse response = checkinService.checkin(request);

        assertThat(response.status()).isEqualTo(CheckinResultStatus.SUCCESS);
        assertThat(response.attendeeName()).isEqualTo("王子墨");
        assertThat(response.entrance()).isEqualTo("B入口");
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void statisticsOverviewIncludesTrendsAndAccommodationMetrics() throws Exception {
        ensureRoom("1801");
        roomService.assign(new AssignRoomRequest(1L, "1801", "统计测试"));
        checkinService.checkin(new CheckinRequest(1L, null, null, "13800000001", null, "A入口", CheckinType.MANUAL, null));

        mockMvc.perform(get("/api/admin/statistics/overview")
                        .param("meetingId", "1")
                        .param("startDate", LocalDateTime.now().toLocalDate().toString())
                        .param("endDate", LocalDateTime.now().toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationCount").value(3))
                .andExpect(jsonPath("$.data.approvedCount").value(2))
                .andExpect(jsonPath("$.data.accommodationRequiredCount").value(1))
                .andExpect(jsonPath("$.data.roomAssignedRequiredCount").value(1))
                .andExpect(jsonPath("$.data.roomAssignedCount").value(1))
                .andExpect(jsonPath("$.data.roomUnassignedRequiredCount").value(0))
                .andExpect(jsonPath("$.data.rangeRegistrationCount").value(3))
                .andExpect(jsonPath("$.data.rangeCheckinCount").value(1))
                .andExpect(jsonPath("$.data.registrationTrend").isMap())
                .andExpect(jsonPath("$.data.checkinTrend").isMap())
                .andExpect(jsonPath("$.data.checkinEntranceDistribution.A入口").value(1));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void statisticsOverviewRejectsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/admin/statistics/overview")
                        .param("meetingId", "1")
                        .param("startDate", "2026-07-02")
                        .param("endDate", "2026-07-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tduckWebhookAcceptsConfiguredToken() {
        TduckWebhookRequest request = new TduckWebhookRequest(
                "event-token-1",
                "FORM_SUBMIT",
                "ndef2026",
                "td-token-1",
                Map.of(
                        "name", "赵新宇",
                        "phone", "13800009999",
                        "organization", "测试单位",
                        "title", "嘉宾",
                        "attendeeType", "GUEST"
                ),
                Map.of("eventId", "event-token-1")
        );

        TduckWebhookResult result = tduckService.handleWebhook(request, null, "test-webhook-secret", "{}");

        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
        var attendee = attendeeRepository.findByMeetingIdAndPhone(1L, "13800009999");
        assertThat(attendee).isPresent();
        assertThat(attendee.get().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void tduckWebhookEndpointAcceptsOfficialFlattenedPayload() throws Exception {
        mockMvc.perform(post("/api/integrations/tduck/webhook")
                        .queryParam("token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 90001,
                                  "formKey": "ndef2026",
                                  "name": "真实TDuck报名",
                                  "phone": "13800007777",
                                  "organization": "TDuck联调单位",
                                  "title": "测试嘉宾",
                                  "attendeeType": "GUEST",
                                  "eventType": "form_data_add",
                                  "eventTime": 1782259200000,
                                  "createTime": "2026-06-24 09:20:00"
                                }
                                """))
                .andExpect(status().isOk());

        var attendee = attendeeRepository.findByMeetingIdAndPhone(1L, "13800007777");
        assertThat(attendee).isPresent();
        assertThat(attendee.get().getName()).isEqualTo("真实TDuck报名");
        assertThat(attendee.get().getTduckDataId()).isEqualTo("90001");
        assertThat(attendee.get().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(attendee.get().getRawDataSnapshot()).containsEntry("organization", "TDuck联调单位");
    }

    @Test
    void tduckDeleteWebhookAutomaticallyCancelsAttendee() throws Exception {
        mockMvc.perform(post("/api/integrations/tduck/webhook")
                        .queryParam("token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "event-add-before-delete",
                                  "id": 90002,
                                  "formKey": "ndef2026",
                                  "name": "待撤回报名",
                                  "phone": "13800007778",
                                  "organization": "TDuck联调单位",
                                  "eventType": "form_data_add"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/integrations/tduck/webhook")
                        .queryParam("token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "event-delete-attendee",
                                  "dataId": 90002,
                                  "formKey": "ndef2026",
                                  "eventType": "form_data_delete"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.message").value("已自动取消参会"));

        var attendee = attendeeRepository.findByMeetingIdAndPhone(1L, "13800007778").orElseThrow();
        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.CANCELLED);
        assertThat(attendee.getReviewReason()).isEqualTo("TDuck 报名删除或撤回");
        assertThat(attendee.getReviewedBy()).isEqualTo("tduck-webhook");

        mockMvc.perform(post("/api/integrations/tduck/webhook")
                        .queryParam("token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "event-delete-attendee",
                                  "dataId": 90002,
                                  "formKey": "ndef2026",
                                  "eventType": "form_data_delete"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DUPLICATE_IGNORED"));
    }

    @Test
    void attendeeImportReturnsFailedRowsAndKeepsSuccessfulRows() {
        MockMultipartFile file = xlsx("attendees.xlsx", """
                姓名,手机号,单位,参会类型,审核状态
                导入嘉宾,13800008888,导入测试公司,VIP,APPROVED
                重复报名,13800000001,导入测试公司,NORMAL,PENDING
                """);

        ImportResult result = attendeeService.importFile(1L, file);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(result.failedRows().get(0).reason()).contains("手机号已存在");
        assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, "13800008888")).isPresent();
    }

    @Test
    void attendeeImportCommitsValidRowWhenAnotherRowFailsValidation() {
        MockMultipartFile file = xlsx("attendees-partial.xlsx", """
                姓名,手机号,单位
                %s,13800008886,字段过长
                独立提交成功,13800008887,逐行事务
                """.formatted("超".repeat(121)));

        ImportResult result = attendeeService.importFile(1L, file);

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.failedRows()).singleElement()
                .extracting(failure -> failure.rowNumber())
                .isEqualTo(2);
        assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, "13800008886")).isEmpty();
        assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, "13800008887")).isPresent();
    }

    @Test
    void meetingStatusTransitionRejectsInvalidJumpAfterArchive() {
        assertThat(meetingService.transition(1L, MeetingStatus.READY).getStatus()).isEqualTo(MeetingStatus.READY);
        assertThat(meetingService.transition(1L, MeetingStatus.IN_PROGRESS).getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(meetingService.transition(1L, MeetingStatus.ENDED).getStatus()).isEqualTo(MeetingStatus.ENDED);
        assertThat(meetingService.transition(1L, MeetingStatus.ARCHIVED).getStatus()).isEqualTo(MeetingStatus.ARCHIVED);

        assertThatThrownBy(() -> meetingService.transition(1L, MeetingStatus.REGISTRATION_OPEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从 ARCHIVED");
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void superAdminCanRestartEndedMeeting() throws Exception {
        meetingService.transition(1L, MeetingStatus.READY);
        meetingService.transition(1L, MeetingStatus.IN_PROGRESS);
        meetingService.transition(1L, MeetingStatus.ENDED);

        mockMvc.perform(post("/api/admin/meetings/1/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REGISTRATION_OPEN"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"MEETING_ADMIN"})
    void meetingAdminCannotRestartMeeting() throws Exception {
        mockMvc.perform(post("/api/admin/meetings/1/restart"))
                .andExpect(status().isForbidden());
    }

    @Test
    void attendeeUpdateTagsAndCancelArePersisted() {
        attendeeService.updateTags(1L, "VIP,接待");
        attendeeService.cancel(1L, "行程取消");

        var attendee = attendeeRepository.findById(1L).orElseThrow();
        assertThat(attendee.getTags()).isEqualTo("VIP,接待");
        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.CANCELLED);
        assertThat(attendee.getReviewReason()).isEqualTo("行程取消");

        ensureRoom("1802");
        var updated = attendeeService.update(2L, new AttendeeRequest(
                1L,
                "王子墨",
                "13800000002",
                "北方数据院",
                "高级研究员",
                null,
                "guest@example.com",
                AttendeeType.GUEST,
                true,
                "1802",
                "保留原房",
                "更新测试",
                "嘉宾"
        ));
        assertThat(updated.getTitle()).isEqualTo("高级研究员");
        assertThat(updated.getEmail()).isEqualTo("guest@example.com");
    }

    @Test
    void attendeeReviewOnlyAllowsPendingToApprovedOrRejected() {
        var approved = attendeeService.review(3L, new com.meetingos.attendee.ReviewRequest(
                ReviewStatus.APPROVED,
                "资料完整",
                null,
                java.util.List.of(3L)
        ));
        assertThat(approved.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        var firstReviewedAt = approved.getReviewedAt();

        /*
         * 重复点击同一个审核结果应该是幂等成功，但不能刷新审核时间，
         * 否则前端连点会制造出看似新的审核动作。
         */
        var repeated = attendeeService.review(3L, new com.meetingos.attendee.ReviewRequest(
                ReviewStatus.APPROVED,
                "重复点击",
                null,
                java.util.List.of(3L)
        ));
        assertThat(repeated.getReviewedAt()).isEqualTo(firstReviewedAt);
        assertThat(repeated.getReviewReason()).isEqualTo("资料完整");

        var rejected = attendeeService.review(3L, new com.meetingos.attendee.ReviewRequest(
                ReviewStatus.REJECTED,
                "改判拒绝",
                null,
                java.util.List.of(3L)
        ));
        assertThat(rejected.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(rejected.getReviewReason()).isEqualTo("改判拒绝");

        assertThatThrownBy(() -> attendeeService.review(3L, new com.meetingos.attendee.ReviewRequest(
                ReviewStatus.APPROVED,
                "拒绝后误点通过",
                null,
                java.util.List.of(3L)
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");

        var rejectedExistingApproved = attendeeService.review(1L, new com.meetingos.attendee.ReviewRequest(
                ReviewStatus.REJECTED,
                "已通过后确认拒绝",
                null,
                java.util.List.of(1L)
        ));
        assertThat(rejectedExistingApproved.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void roomImportRejectsDuplicateRoomNumberInsideSameMeeting() {
        MockMultipartFile file = xlsx("rooms.xlsx", """
                房间号,酒店,房型,备注
                2801,测试酒店,大床房,14点后入住
                2801,测试酒店,双床房,重复房间
                """);

        ImportResult result = roomService.importRooms(1L, file);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isEqualTo(1);
        assertThat(roomRepository.findByMeetingIdAndRoomNo(1L, "2801")).isPresent();
    }

    @Test
    void roomAssignmentImportCanMatchAttendeeByPhone() {
        roomService.importRooms(1L, xlsx("rooms.xlsx", """
                房间号,酒店,房型
                2901,测试酒店,双床房
                """));
        MockMultipartFile assignmentFile = xlsx("room-assignments.xlsx", """
                手机号,房间号,房间备注
                13800000002,2901,靠近电梯
                """);

        ImportResult result = roomService.importAssignments(1L, assignmentFile);

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, "13800000002").orElseThrow().getRoomNo())
                .isEqualTo("2901");
    }

    @Test
    void manualRoomAssignmentWritesChangeOperationLog() {
        ensureRoom("1803");
        roomService.assign(new AssignRoomRequest(2L, "1803", "调房测试"));

        assertThat(attendeeRepository.findById(2L).orElseThrow().getRoomNo()).isEqualTo("1803");
        assertThat(operationLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("ROOM_ASSIGNMENT_CHANGE");
            assertThat(log.getDetails()).containsEntry("oldRoomNo", null).containsEntry("newRoomNo", "1803");
        });
    }

    @Test
    void publicSeatAndRoomQueryRejectPendingAttendee() {
        assertThatThrownBy(() -> seatingService.querySeat(1L, "13800000003", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未审核通过");
        assertThatThrownBy(() -> roomService.query(1L, "13800000003"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未审核通过");
    }

    @Test
    void publicQuerySettingsGateSeatAndRoomQuery() {
        PublicQueryRequest request = new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "13800000001", PublicQueryField.NAME, "陈嘉宁")
        );

        assertThatThrownBy(() -> publicQueryService.publicSeatQuery(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("座位查询尚未开放");
        assertThatThrownBy(() -> publicQueryService.publicRoomQuery(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("住宿查询尚未开放");
    }

    @Test
    void publicSeatAndRoomQueryUseConfiguredFuzzyFields() {
        enablePublicQueries();
        seatingService.manualAssign(new ManualAssignSeatRequest(
                1L,
                seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId()
        ));
        ensureRoom("1801");
        roomService.assign(new AssignRoomRequest(1L, "1801", "前台领取房卡"));

        PublicQueryRequest request = new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "0001", PublicQueryField.NAME, "嘉宁")
        );

        assertThat(publicQueryService.publicSeatQuery(request).seatCode()).isEqualTo("A区-1-1");
        assertThat(publicQueryService.publicRoomQuery(request).hotelName()).isEqualTo("北京会议中心酒店");
    }

    @Test
    void combinedPublicQueryMatchesIdentityOnceAndKeepsIndependentResults() throws Exception {
        enablePublicQueries();
        seatingService.manualAssign(new ManualAssignSeatRequest(
                1L,
                seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId()
        ));
        ensureRoom("1801");
        roomService.assign(new AssignRoomRequest(1L, "1801", "前台领取房卡"));
        PublicQueryRequest request = new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "0001", PublicQueryField.NAME, "嘉宁")
        );

        PublicCombinedQueryResponse response = publicQueryService.publicCombinedQueryResponse(request, null, null);

        assertThat(response.seat()).isInstanceOf(com.meetingos.seating.SeatQueryResult.class);
        assertThat(response.seatError()).isNull();
        assertThat(response.room()).isInstanceOf(com.meetingos.room.RoomQueryResult.class);
        assertThat(response.roomError()).isNull();
        assertThat(response.dinner()).isNull();

        mockMvc.perform(post("/api/public/combined-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicQueryToken": "pq_test_ndef2026",
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seat.seatCode").value("A区-1-1"))
                .andExpect(jsonPath("$.data.room.roomNo").value("1801"));
    }

    @Test
    void publicFuzzyQueryRejectsMultipleMatchesWithoutLeakingCandidates() {
        publicQueryService.updateSettings(1L, new MeetingQuerySettingsRequest(
                true,
                true,
                java.util.List.of(PublicQueryField.ORGANIZATION),
                PublicQueryMatchMode.FUZZY
        ));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> publicQueryService.publicSeatQuery(new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.ORGANIZATION, "测试单位")
        )));
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessageContaining("匹配到多条报名记录");
        assertThat(thrown.getMessage()).doesNotContain("陈嘉宁");
    }

    @Test
    void publicConfiguredQueryReturnsExplicitStateErrors() {
        enablePublicQueries();
        seatingService.manualAssign(new ManualAssignSeatRequest(
                1L,
                seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId()
        ));

        assertThatThrownBy(() -> publicQueryService.publicSeatQuery(new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "13800000003", PublicQueryField.NAME, "李若彤")
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("报名尚未审核通过");

        assertThatThrownBy(() -> publicQueryService.publicSeatQuery(new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "13800000002", PublicQueryField.NAME, "王子墨")
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未排座");

        assertThatThrownBy(() -> publicQueryService.publicRoomQuery(new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "13800000002", PublicQueryField.NAME, "王子墨")
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未安排房间");
    }

    @Test
    void publicQueryPageSettingsAndWindowAreApplied() {
        publicQueryService.updateSettings(1L, new MeetingQuerySettingsRequest(
                true,
                true,
                true,
                List.of(PublicQueryField.PHONE, PublicQueryField.NAME),
                PublicQueryMatchMode.FUZZY,
                "论坛座位住宿查询",
                "请输入报名手机号和姓名",
                "请以现场指引为准",
                "会务组",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                "查询我的安排",
                "没有找到，请联系会务组",
                "请于 09:00 前到达会场，凭会议证件从主入口进入，对号入座。",
                "凭身份证办理入住，接待台位于酒店大堂。",
                "#16a34a",
                "https://example.com/logo.png",
                "https://example.com/bg.jpg",
                "LIST",
                List.of(PublicQueryResultField.ATTENDEE_NAME, PublicQueryResultField.SEAT_CODE, PublicQueryResultField.ROOM_NO),
                List.of(PublicQueryResultField.ATTENDEE_PHONE),
                List.of(PublicQueryResultField.SEAT_CODE, PublicQueryResultField.ROOM_NO)
        ));

        var settings = publicQueryService.adminSettings(1L);
        assertThat(settings.pageTitle()).isEqualTo("论坛座位住宿查询");
        assertThat(settings.buttonText()).isEqualTo("查询我的安排");
        assertThat(settings.seatNotice()).isEqualTo("请于 09:00 前到达会场，凭会议证件从主入口进入，对号入座。");
        assertThat(settings.roomNotice()).isEqualTo("凭身份证办理入住，接待台位于酒店大堂。");
        assertThat(settings.resultLayout()).isEqualTo("LIST");
        // 旧共享字段仍按合并结果返回，保证旧前端和旧调用方不会因为字段拆分而失效。
        assertThat(settings.visibleResultFields()).containsExactly(
                PublicQueryResultField.ATTENDEE_NAME,
                PublicQueryResultField.SEAT_CODE,
                PublicQueryResultField.ROOM_NO
        );
        // 新配置按入口拆分展示字段，避免座位入口出现住宿字段、住宿入口出现座位字段。
        assertThat(settings.seatVisibleResultFields()).containsExactly(
                PublicQueryResultField.ATTENDEE_NAME,
                PublicQueryResultField.SEAT_CODE
        );
        assertThat(settings.roomVisibleResultFields()).containsExactly(
                PublicQueryResultField.ATTENDEE_NAME,
                PublicQueryResultField.ROOM_NO
        );
        assertThat(settings.seatHighlightResultFields()).containsExactly(
                PublicQueryResultField.SEAT_CODE
        );
        assertThat(settings.roomHighlightResultFields()).containsExactly(
                PublicQueryResultField.ROOM_NO
        );

        PublicQueryRequest request = new PublicQueryRequest(
                TEST_PUBLIC_QUERY_TOKEN,
                Map.of(PublicQueryField.PHONE, "13800000001", PublicQueryField.NAME, "陈嘉宁")
        );
        assertThatThrownBy(() -> publicQueryService.publicSeatQuery(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询已暂停");
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void adminAndPublicQueryEndpointsUseConfiguredContract() throws Exception {
        enablePublicQueries();
        seatingService.manualAssign(new ManualAssignSeatRequest(
                1L,
                seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId()
        ));

        mockMvc.perform(post("/api/public/seat-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "meetingCode": "NDEF2026",
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/public/seat-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicQueryToken": "pq_test_ndef2026",
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendeeName").value("陈嘉宁"))
                .andExpect(jsonPath("$.data.seatCode").value("A区-1-1"))
                .andExpect(jsonPath("$.data.seatMap.areaName").value("A区"))
                .andExpect(jsonPath("$.data.seatMap.rowCount").value(2))
                .andExpect(jsonPath("$.data.seatMap.seatsPerRow").value(4))
                .andExpect(jsonPath("$.data.seatMap.currentRowNo").value(1))
                .andExpect(jsonPath("$.data.seatMap.currentSeatNo").value(1))
                .andExpect(jsonPath("$.data.seatMap.attendeeName").doesNotExist())
                .andExpect(jsonPath("$.data.seatMap.attendeePhone").doesNotExist())
                .andExpect(jsonPath("$.data.seatMap.status").doesNotExist())
                .andExpect(jsonPath("$.data.seatMap.seats").doesNotExist());

        mockMvc.perform(post("/api/admin/meetings/1/queries/seat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attendeeName").value("陈嘉宁"))
                .andExpect(jsonPath("$.data[0].seatLabel").value("A区 1排1座"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void publicQueryHistoryRecordsSuccessFailureAndBuildsSummary() throws Exception {
        mockMvc.perform(post("/api/public/seat-query")
                        .header("X-Forwarded-For", "203.0.113.11")
                        .header("User-Agent", "MeetingOS-History-Test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicQueryToken": "pq_test_ndef2026",
                                  "values": {
                                    "PHONE": "13800000001",
                                    "NAME": "未开放历史"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        enablePublicQueries();
        seatingService.manualAssign(new ManualAssignSeatRequest(
                1L,
                seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId()
        ));
        ensureRoom("1801");
        roomService.assign(new AssignRoomRequest(1L, "1801", "历史测试入住说明"));

        mockMvc.perform(post("/api/public/seat-query")
                        .header("X-Forwarded-For", "203.0.113.12")
                        .header("User-Agent", "MeetingOS-History-Test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicQueryToken": "pq_test_ndef2026",
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/room-query")
                        .header("X-Forwarded-For", "203.0.113.13")
                        .header("User-Agent", "MeetingOS-History-Test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicQueryToken": "pq_test_ndef2026",
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/seat-query")
                        .header("X-Forwarded-For", "203.0.113.14")
                        .header("User-Agent", "MeetingOS-History-Test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicQueryToken": "pq_test_ndef2026",
                                  "values": {
                                    "PHONE": "19900009999",
                                    "NAME": "历史无匹配"
                                  }
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/meetings/1/query-history")
                        .param("type", "SEAT")
                        .param("status", "SUCCESS")
                        .param("keyword", "嘉宁")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].attendeeName", hasItem("陈嘉宁")))
                .andExpect(jsonPath("$.data.content[*].queryValues.NAME", hasItem("嘉宁")))
                .andExpect(jsonPath("$.data.content[*].resultSummary", hasItem("A区 1排1座（A区-1-1）")));

        mockMvc.perform(get("/api/admin/meetings/1/query-history")
                        .param("type", "ROOM")
                        .param("status", "SUCCESS")
                        .param("keyword", "嘉宁")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].attendeeName", hasItem("陈嘉宁")))
                .andExpect(jsonPath("$.data.content[*].resultSummary", hasItem("北京会议中心酒店 1801 · 双床房")));

        mockMvc.perform(get("/api/admin/meetings/1/query-history")
                        .param("status", "FAILED")
                        .param("keyword", "历史无匹配")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].queryValues.NAME", hasItem("历史无匹配")))
                .andExpect(jsonPath("$.data.content[*].errorMessage", hasItem("未找到匹配报名记录")));

        mockMvc.perform(get("/api/admin/meetings/1/query-history")
                        .param("type", "SEAT")
                        .param("status", "FAILED")
                        .param("keyword", "未开放历史")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].errorMessage", hasItem("座位查询尚未开放")));

        mockMvc.perform(get("/api/admin/meetings/1/query-history/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.groupType == 'ATTENDEE' && @.attendeeName == '陈嘉宁')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.groupType == 'UNRECOGNIZED' && @.failureReason == '未找到匹配报名记录')]").isNotEmpty());
    }

    @Test
    void adminQueryEndpointRejectsUnassignedMeetingAdmin() throws Exception {
        var user = userService.create(new CreateUserRequest(
                "unassigned-query-admin",
                "未授权会议管理员",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                Set.of(Role.MEETING_ADMIN)
        ));
        CurrentUser currentUser = new CurrentUser(user.id(), "unassigned-query-admin", "未授权会议管理员", Set.of(Role.MEETING_ADMIN));
        var authorities = Set.of(new SimpleGrantedAuthority("ROLE_" + Role.MEETING_ADMIN.name()));

        mockMvc.perform(post("/api/admin/meetings/1/queries/seat")
                        .with(authentication(new UsernamePasswordAuthenticationToken(currentUser, null, authorities)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "values": {
                                    "PHONE": "0001",
                                    "NAME": "嘉宁"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/meetings/1/query-history")
                        .with(authentication(new UsernamePasswordAuthenticationToken(currentUser, null, authorities))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/meetings/1/query-history/summary")
                        .with(authentication(new UsernamePasswordAuthenticationToken(currentUser, null, authorities))))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportTaskDownloadStillSupportsExplicitCsvContent() {
        Attendee attendee = attendeeRepository.findById(1L).orElseThrow();
        attendee.setRawDataSnapshot(tduckSnapshot(attendee));
        attendeeRepository.saveAndFlush(attendee);

        ExportFile attendeeFile = exportTaskService.build(1L, "attendees", "csv", null, null);
        ExportFile seatFile = exportTaskService.build(1L, "seats", "csv", null, null);
        String attendeeCsv = new String(attendeeFile.content(), StandardCharsets.UTF_8);
        String seatCsv = new String(seatFile.content(), StandardCharsets.UTF_8);

        assertThat(attendeeFile.filename()).isEqualTo("meetingos-attendees.csv");
        assertThat(attendeeCsv).contains("姓名", "陈嘉宁", "****0001", "VIP", "已通过", "未签到");
        assertThat(attendeeCsv).doesNotContain("APPROVED", "NOT_CHECKED_IN");
        assertThat(attendeeCsv).doesNotContain("13800000001");
        assertThat(seatCsv).contains("座位码", "A区-1-1", "VIP", "嘉宾");
        assertThat(seatCsv).doesNotContain("GUEST");
    }

    @Test
    void exportTaskDownloadBuildsXlsxAndStatisticsContent() throws Exception {
        Attendee attendee = attendeeRepository.findById(1L).orElseThrow();
        attendee.setRawDataSnapshot(tduckSnapshot(attendee));
        attendeeRepository.saveAndFlush(attendee);

        ExportFile attendeeXlsx = exportTaskService.build(1L, "attendees");
        ExportFile statisticsXlsx = exportTaskService.build(
                1L,
                "statistics",
                "xlsx",
                LocalDate.now().minusDays(6),
                LocalDate.now()
        );

        assertThat(attendeeXlsx.filename()).isEqualTo("meetingos-attendees.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(attendeeXlsx.content()))) {
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("参会名单");
            assertThat(sheetText(workbook.getSheetAt(0)))
                    .contains(
                            "姓名", "手机号", "单位", "职务", "参会类型", "陈嘉宁",
                            "标签", "审核", "座位", "房间", "座位查询", "住宿查询", "签到", "参会人备注",
                            "已通过", "未签到"
                    )
                    .doesNotContain("会议ID", "会议编码", "晚宴桌号", "审核人", "签到时间");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(statisticsXlsx.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheet("统计概览").getRow(1).getCell(1).getStringCellValue()).isEqualTo("报名总人数");
            assertThat(workbook.getSheet("趋势").getRow(0).getCell(0).getStringCellValue()).isEqualTo("日期");
            assertThat(sheetText(workbook.getSheet("分布"))).contains("审核状态", "已通过", "参会类型", "嘉宾");
        }
    }

    @Test
    void exportTaskRejectsUnsupportedFormatAndInvalidStatisticsRange() {
        assertThatThrownBy(() -> exportTaskService.build(1L, "attendees", "pdf", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不支持的导出格式");

        assertThatThrownBy(() -> exportTaskService.build(1L, "statistics", "csv", LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计开始日期不能晚于结束日期");

        assertThatThrownBy(() -> exportTaskService.build(1L, "statistics", "csv", LocalDate.now(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同时提供开始日期和结束日期");
    }

    @Test
    void statisticsCsvKeepsMetricValuesInValueColumn() {
        ExportFile file = exportTaskService.build(
                1L,
                "statistics",
                "csv",
                LocalDate.now().minusDays(6),
                LocalDate.now()
        );
        String csv = new String(file.content(), StandardCharsets.UTF_8);

        assertThat(csv)
                .containsPattern("报名统计,报名总人数,\\d+,")
                .containsPattern("趋势,\\d{4}-\\d{2}-\\d{2} 报名新增,\\d+,")
                .doesNotContain("统计概览,报名统计,报名总人数");
    }

    @Test
    void exportTaskCreateGeneratesFixedDownloadWithVisibleProgress() throws Exception {
        Attendee attendee = attendeeRepository.findById(1L).orElseThrow();
        Map<String, Object> snapshot = tduckSnapshot(attendee);
        snapshot.put("customField", "TDuck扩展值");
        attendee.setRawDataSnapshot(snapshot);
        attendee.setTags("重点接待");
        attendee.setRemark("参会人导出备注");
        attendeeRepository.saveAndFlush(attendee);
        Long seatId = seatRepository.findByMeetingIdAndAreaNameOrderByRowNoAscSeatNoAsc(1L, "A区").get(0).getId();
        seatingService.manualAssign(new ManualAssignSeatRequest(attendee.getId(), seatId));
        ensureRoom("1908");
        roomService.assign(new AssignRoomRequest(attendee.getId(), "1908", "导出测试住宿备注"));
        for (PublicQueryType queryType : List.of(PublicQueryType.SEAT, PublicQueryType.ROOM)) {
            PublicQueryHistory history = new PublicQueryHistory();
            history.setMeeting(attendee.getMeeting());
            history.setAttendee(attendee);
            history.setQueryType(queryType);
            history.setQueryStatus(PublicQueryStatus.SUCCESS);
            history.setQueryValues(Map.of("PHONE", attendee.getPhone()));
            history.setAttendeeName(attendee.getName());
            history.setAttendeePhone(attendee.getPhone());
            history.setResultSummary("导出测试");
            publicQueryHistoryRepository.save(history);
        }

        var created = exportTaskService.create(1L, "attendees");
        var tasks = exportTaskService.list(1L);

        assertThat(created.status()).isEqualTo(ExportTaskStatus.PENDING);
        assertThat(created.format()).isEqualTo("xlsx");
        assertThat(created.progress()).isZero();
        assertThat(created.fileAvailable()).isFalse();
        assertThat(created.filename()).isEqualTo("meetingos-attendees-" + created.id() + ".xlsx");
        assertThatThrownBy(() -> exportTaskService.create(1L, "attendees"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有同类型导出任务");
        assertThat(tasks).anySatisfy(task -> {
            assertThat(task.id()).isEqualTo(created.id());
            assertThat(task.meetingId()).isEqualTo(1L);
            assertThat(task.type()).isEqualTo("attendees");
            assertThat(task.status()).isEqualTo(ExportTaskStatus.PENDING);
        });

        // 测试方法事务不会提交，因此 AFTER_COMMIT 监听器不会自动执行；这里直接驱动 worker 核心逻辑。
        exportTaskService.processTask(created.id());
        var completed = exportTaskService.get(1L, created.id());
        assertThat(completed.status()).isEqualTo(ExportTaskStatus.COMPLETED);
        assertThat(completed.progress()).isEqualTo(100);
        assertThat(completed.fileAvailable()).isTrue();
        assertThat(completed.fileSize()).isPositive();
        assertThat(completed.checksum()).hasSize(64);
        assertThat(completed.generatedAt()).isNotNull();
        assertThat(completed.expiresAt()).isAfter(completed.generatedAt());

        var download = exportTaskService.openDownload(1L, created.id());
        assertThat(download.filename()).isEqualTo(completed.filename());
        assertThat(download.contentLength()).isEqualTo(completed.fileSize());
        try (XSSFWorkbook workbook = new XSSFWorkbook(download.resource().getInputStream())) {
            assertThat(workbook.getSheet("参会名单")).isNotNull();
            assertThat(sheetText(workbook.getSheet("参会名单")))
                    .contains(
                            "姓名", "手机号", "TDuck扩展值",
                            "标签", "审核", "座位", "房间", "座位查询", "住宿查询", "签到", "参会人备注",
                            "重点接待", "已通过", "A区 1排1座", "1908", "已查询", "未签到", "参会人导出备注"
                    )
                    .doesNotContain("会议编码", "晚宴桌号", "酒店名称", "审核人", "签到时间");
        }
        var generatedPath = download.resource().getFile().toPath();
        var task = exportTaskRepository.findById(created.id()).orElseThrow();
        task.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        exportTaskService.cleanupExpiredTasks();
        assertThat(Files.exists(generatedPath)).isFalse();
        assertThat(exportTaskService.get(1L, created.id()).status()).isEqualTo(ExportTaskStatus.EXPIRED);
    }

    @Test
    void roomStaffCanCreateRoomExportButCannotCreateAttendeeExport() throws Exception {
        var user = userService.create(new CreateUserRequest(
                "room-exporter",
                "住宿导出员",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                Set.of(Role.ROOM_STAFF)
        ));
        meetingUserPermissionService.grant(new MeetingUserPermissionRequest(1L, user.id(), MeetingPermissionRole.ROOM));
        CurrentUser currentUser = new CurrentUser(user.id(), user.username(), user.displayName(), Set.of(Role.ROOM_STAFF));
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ROOM_STAFF"));
        var authentication = new UsernamePasswordAuthenticationToken(currentUser, null, authorities);

        mockMvc.perform(post("/api/admin/exports")
                        .param("meetingId", "1")
                        .param("type", "rooms")
                        .param("format", "xlsx")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/admin/exports")
                        .param("meetingId", "1")
                        .param("type", "attendees")
                        .param("format", "xlsx")
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/exports")
                        .param("meetingId", "1")
                        .param("page", "0")
                        .param("size", "20")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].type", hasItem("rooms")));
    }

    @Test
    void attendeeTaskStreamsAcrossRepositoryPages() throws Exception {
        var meeting = attendeeRepository.findById(1L).orElseThrow().getMeeting();
        List<Attendee> added = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            Attendee attendee = new Attendee();
            attendee.setMeeting(meeting);
            attendee.setName("分页参会人" + index);
            attendee.setPhone("139" + String.format("%08d", index));
            attendee.setOrganization("分页测试单位");
            attendee.setAttendeeType(AttendeeType.NORMAL);
            attendee.setReviewStatus(ReviewStatus.APPROVED);
            attendee.setReviewedAt(LocalDateTime.now());
            attendee.setRegisteredAt(LocalDateTime.now());
            attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
            attendee.setAttendeeCode("PAGE-" + String.format("%05d", index));
            added.add(attendee);
        }
        attendeeRepository.saveAll(added);

        var created = exportTaskService.create(1L, "approved", "xlsx", null, null);
        exportTaskService.processTask(created.id());
        var completed = exportTaskService.get(1L, created.id());
        assertThat(completed.status()).isEqualTo(ExportTaskStatus.COMPLETED);

        var download = exportTaskService.openDownload(1L, created.id());
        try (XSSFWorkbook workbook = new XSSFWorkbook(download.resource().getInputStream())) {
            // 测试种子有 2 名已审核人员，加上 501 行，验证至少跨过 500 行分页边界且没有漏行。
            assertThat(workbook.getSheet("审核通过名单").getLastRowNum()).isEqualTo(503);
        }
        var task = exportTaskRepository.findById(created.id()).orElseThrow();
        task.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        exportTaskService.cleanupExpiredTasks();
    }

    @Test
    void userCreationAndMeetingPermissionCanBeListed() {
        var user = userService.create(new CreateUserRequest(
                "checkin-user",
                "签到人员",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                java.util.Set.of(Role.CHECKIN_STAFF)
        ));

        var permission = meetingUserPermissionService.grant(
                new MeetingUserPermissionRequest(1L, user.id(), MeetingPermissionRole.CHECKIN)
        );
        var permissions = meetingUserPermissionService.list(1L, user.id());

        assertThat(permission.role()).isEqualTo(MeetingPermissionRole.CHECKIN);
        assertThat(permissions).singleElement().satisfies(item -> {
            assertThat(item.meetingId()).isEqualTo(1L);
            assertThat(item.userId()).isEqualTo(user.id());
            assertThat(item.username()).isEqualTo("checkin-user");
            assertThat(item.role()).isEqualTo(MeetingPermissionRole.CHECKIN);
        });
    }

    @Test
    void meetingScopedAuthorizationBlocksUnassignedMeetingAdmin() {
        var user = userService.create(new CreateUserRequest(
                "meeting-owner",
                "会议负责人",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                Set.of(Role.MEETING_ADMIN)
        ));

        withCurrentUser(user.id(), "meeting-owner", Set.of(Role.MEETING_ADMIN), () -> {
            assertThat(meetingService.list(null, null, 0, 20).content()).isEmpty();
            assertThatThrownBy(() -> attendeeService.review(1L, new com.meetingos.attendee.ReviewRequest(
                    ReviewStatus.APPROVED,
                    "授权测试",
                    null,
                    java.util.List.of(1L)
            ))).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未被授权");
        });
    }

    @Test
    void meetingScopedCheckinRoleCanCheckinButCannotManageAttendee() {
        var user = userService.create(new CreateUserRequest(
                "meeting-checkin",
                "签到授权人员",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                Set.of(Role.CHECKIN_STAFF)
        ));
        meetingUserPermissionService.grant(new MeetingUserPermissionRequest(1L, user.id(), MeetingPermissionRole.CHECKIN));

        withCurrentUser(user.id(), "meeting-checkin", Set.of(Role.CHECKIN_STAFF), () -> {
            CheckinResponse response = checkinService.checkin(new CheckinRequest(1L, null, null, "13800000001", null, "A入口", CheckinType.MANUAL, null));
            assertThat(response.status()).isEqualTo(CheckinResultStatus.SUCCESS);
            assertThat(response.phone()).isEqualTo("****0001");

            assertThatThrownBy(() -> attendeeService.updateTags(1L, "越权标签"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未被授权");
        });
    }

    @Test
    void attendeeResponseAndCsvExportFollowSensitiveMaskSetting() {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        attendee.setIdentityNo("110101199001010011");
        attendee.setEmail("chen@example.com");
        Map<String, Object> snapshot = tduckSnapshot(attendee);
        snapshot.put("identityNo", attendee.getIdentityNo());
        snapshot.put("email", attendee.getEmail());
        attendee.setRawDataSnapshot(snapshot);
        attendeeRepository.saveAndFlush(attendee);

        AttendeeResponse response = AttendeeResponse.masked(attendee);

        assertThat(response.phone()).isEqualTo("****0001");
        assertThat(response.identityNo()).isEqualTo("110******0011");
        assertThat(response.email()).isEqualTo("c***@example.com");
        assertThat(attendeeService.exportCsv(1L, "attendees"))
                .contains("****0001", "110******0011", "c***@example.com")
                .doesNotContain("13800000001", "110101199001010011", "chen@example.com");

        /*
         * 系统设置允许内网部署关闭导出脱敏；默认值仍保持脱敏，避免无意导出原始手机号、
         * 证件号和邮箱。这里单独覆盖关闭开关后的兼容路径。
         */
        systemSettingsService.update(new SystemSettingsRequest(
                "https://meeting.example.com",
                "https://meeting.example.com/tduck",
                "https://meeting.example.com/tduck-api",
                "",
                false,
                "会务控制台",
                "会务控制台",
                "会议全流程管理",
                "/favicon.svg",
                "/favicon.svg"
        ));
        assertThat(attendeeService.exportCsv(1L, "attendees")).contains("13800000001", "110101199001010011", "chen@example.com");
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void adminAttendeeListMasksContactAndDynamicFormValuesByDefault() throws Exception {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        attendee.setEmail("visitor@example.com");
        attendee.setRawDataSnapshot(Map.of(
                "input_phone_dynamic_key", "17757716617",
                "input_email_dynamic_key", "visitor@example.com"
        ));

        /*
         * 默认开启敏感信息脱敏时，后台列表也不直接暴露手机号、邮箱和 TDuck 动态字段原值。
         * 需要导出原始值的内网场景必须显式关闭系统脱敏配置，并由权限控制和操作日志审计兜底。
         */
        mockMvc.perform(get("/api/admin/attendees")
                        .param("meetingId", "1")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].phone", hasItem("****0001")))
                .andExpect(jsonPath("$.data.content[*].email", hasItem("v***@example.com")))
                .andExpect(jsonPath("$.data.content[*].formValues.input_phone_dynamic_key", hasItem("****6617")))
                .andExpect(jsonPath("$.data.content[*].formValues.input_email_dynamic_key", hasItem("v***@example.com")));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void attendeeRemarkEndpointCreatesEditsAndReturnsRemarkWithoutOverwritingSensitiveFields() throws Exception {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        assertThat(attendee.getRemark()).isNull();
        String originalPhone = attendee.getPhone();

        mockMvc.perform(put("/api/admin/attendees/1/remark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "  首次联系后等待确认  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value("首次联系后等待确认"))
                // 保存响应继续遵守脱敏开关，不能因编辑备注重新暴露原始手机号。
                .andExpect(jsonPath("$.data.phone").value("****0001"));

        attendeeRepository.flush();
        var created = attendeeRepository.findById(1L).orElseThrow();
        assertThat(created.getRemark()).isEqualTo("首次联系后等待确认");
        assertThat(created.getPhone()).isEqualTo(originalPhone);

        mockMvc.perform(get("/api/admin/attendees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value("首次联系后等待确认"));
        mockMvc.perform(get("/api/admin/attendees")
                        .param("meetingId", "1")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].remark", hasItem("首次联系后等待确认")));

        mockMvc.perform(put("/api/admin/attendees/1/remark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "已确认参会，需会前提醒"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value("已确认参会，需会前提醒"));

        attendeeRepository.flush();
        assertThat(attendeeRepository.findById(1L).orElseThrow().getRemark())
                .isEqualTo("已确认参会，需会前提醒");

        mockMvc.perform(get("/api/admin/attendees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value("已确认参会，需会前提醒"));

        mockMvc.perform(put("/api/admin/attendees/1/remark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value(nullValue()));
        attendeeRepository.flush();
        assertThat(attendeeRepository.findById(1L).orElseThrow().getRemark()).isNull();
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void attendeeRemarkEndpointRejectsValuesLongerThanDatabaseColumn() throws Exception {
        mockMvc.perform(put("/api/admin/attendees/1/remark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of("remark", "备".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(attendeeRepository.findById(1L).orElseThrow().getRemark()).isNull();
    }

    @Test
    void attendeeRemarkEndpointAllowsAssignedManagerAndRejectsUnassignedManager() throws Exception {
        var assigned = userService.create(new CreateUserRequest(
                "remark-manager",
                "备注管理员",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                Set.of(Role.MEETING_ADMIN)
        ));
        meetingUserPermissionService.grant(new MeetingUserPermissionRequest(
                1L,
                assigned.id(),
                MeetingPermissionRole.MANAGER
        ));
        CurrentUser assignedUser = new CurrentUser(
                assigned.id(),
                "remark-manager",
                "备注管理员",
                Set.of(Role.MEETING_ADMIN)
        );
        var managerAuthorities = Set.of(new SimpleGrantedAuthority("ROLE_" + Role.MEETING_ADMIN.name()));

        mockMvc.perform(put("/api/admin/attendees/1/remark")
                        .with(authentication(new UsernamePasswordAuthenticationToken(assignedUser, null, managerAuthorities)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "本会议管理员可维护"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value("本会议管理员可维护"));

        var unassigned = userService.create(new CreateUserRequest(
                "remark-outsider",
                "未授权备注管理员",
                STRONG_TEST_PASSWORD,
                UserStatus.ACTIVE,
                Set.of(Role.MEETING_ADMIN)
        ));
        CurrentUser unassignedUser = new CurrentUser(
                unassigned.id(),
                "remark-outsider",
                "未授权备注管理员",
                Set.of(Role.MEETING_ADMIN)
        );

        mockMvc.perform(put("/api/admin/attendees/1/remark")
                        .with(authentication(new UsernamePasswordAuthenticationToken(unassignedUser, null, managerAuthorities)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "remark": "越权修改"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(attendeeRepository.findById(1L).orElseThrow().getRemark())
                .isEqualTo("本会议管理员可维护");
    }

    @Test
    void attendeeCsvExportIncludesTduckRawFormFieldsAsMainData() {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        saveTduckFieldMapping("number1782317193399", "同行人数");
        saveTduckFieldMapping("radio1782317193403", "是否住宿");
        saveTduckFieldMapping("checkbox1782317193404", "用餐偏好");
        attendee.setRawDataSnapshot(Map.of(
                "number1782317193399", 3,
                "radio1782317193403", 1,
                "radio1782317193403label", "需要",
                "checkbox1782317193404", "a,b",
                "formKey", "ndef2026",
                "submitUa", "Mozilla/5.0",
                "params", Map.of("debug", true),
                "source", "pull-sync"
        ));

        String csv = attendeeService.exportCsv(1L, "attendees");

        assertThat(csv).contains("同行人数", "是否住宿", "用餐偏好");
        assertThat(csv).contains("3", "需要", "\"a,b\"");
        assertThat(csv).doesNotContain("number1782317193399", "radio1782317193403", "checkbox1782317193404");
        assertThat(csv).doesNotContain("radio1782317193403label", "formKey", "submitUa", "params", "source");
    }

    @Test
    void attendeeResponseMasksSensitiveValuesInsideTduckRawSnapshot() {
        var attendee = attendeeRepository.findById(1L).orElseThrow();
        attendee.setRawDataSnapshot(Map.of(
                "input_phone_dynamic_key", "17757716617",
                "input_email_dynamic_key", "visitor@example.com",
                "input_name_dynamic_key", "陈嘉宁"
        ));

        AttendeeResponse response = AttendeeResponse.masked(attendee);

        assertThat(response.formValues()).containsEntry("input_phone_dynamic_key", "****6617");
        assertThat(response.formValues()).containsEntry("input_email_dynamic_key", "v***@example.com");
        assertThat(response.formValues()).containsEntry("input_name_dynamic_key", "陈嘉宁");
    }

    @Test
    void systemSettingsIgnoreWebhookTokenFromRequestAndUseEnvironmentSecret() {
        var saved = systemSettingsService.update(new SystemSettingsRequest(
                "https://meeting.example.com",
                "https://meeting.example.com/tduck",
                "https://meeting.example.com/tduck-api",
                "rotated-webhook-secret",
                true,
                "会务控制台",
                "会务控制台",
                "会议全流程管理",
                "/favicon.svg",
                "/favicon.svg"
        ));

        assertThat(saved.webhookTokenConfigured()).isTrue();
        assertThat(saved.webhookTokenMasked()).isEqualTo("test****cret");
        assertThat(saved.brandName()).isEqualTo("会务控制台");
        assertThat(systemSettingsService.branding().browserTitle()).isEqualTo("会务控制台");

        var unchanged = systemSettingsService.update(new SystemSettingsRequest(
                "https://meeting.example.com",
                "https://meeting.example.com/tduck",
                "https://meeting.example.com/tduck-api",
                "",
                true,
                "会务控制台",
                "会务控制台",
                "会议全流程管理",
                "/favicon.svg",
                "/favicon.svg"
        ));
        assertThat(unchanged.webhookTokenMasked()).isEqualTo("test****cret");

        TduckWebhookRequest request = new TduckWebhookRequest(
                "event-rotated-token",
                "FORM_SUBMIT",
                "ndef2026",
                "td-rotated-token",
                Map.of("name", "轮换密钥参会人", "phone", "13800006666"),
                Map.of("eventId", "event-rotated-token")
        );

        assertThatThrownBy(() -> tduckService.handleWebhook(request, null, "rotated-webhook-secret", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Webhook 签名校验失败");

        TduckWebhookRequest retriedWithNewEvent = new TduckWebhookRequest(
                "event-rotated-token-success",
                request.eventType(),
                request.formKey(),
                request.dataId(),
                request.data(),
                Map.of("eventId", "event-rotated-token-success")
        );
        TduckWebhookResult result = tduckService.handleWebhook(retriedWithNewEvent, null, "test-webhook-secret", "{}");
        assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void createMeetingGeneratesCodeAndUpdateKeepsExistingCode() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "自动编号会议",
                                  "organizer": "会务组",
                                  "venueName": "测试会场",
                                  "capacity": 120,
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var createdMeeting = OBJECT_MAPPER.readTree(createResponse).path("data");
        String generatedCode = createdMeeting.path("code").asText();
        Long meetingId = createdMeeting.path("id").asLong();

        assertThat(generatedCode).startsWith("MEET");
        assertThat(generatedCode).doesNotContain("SHOULD-NOT-CHANGE");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/meetings/" + meetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SHOULD-NOT-CHANGE",
                                  "name": "自动编号会议更新",
                                  "organizer": "会务组",
                                  "venueName": "测试会场",
                                  "capacity": 150,
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(generatedCode))
                .andExpect(jsonPath("$.data.name").value("自动编号会议更新"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void deleteMeetingArchivesInsteadOfPhysicalDelete() throws Exception {
        mockMvc.perform(delete("/api/admin/meetings/1"))
                .andExpect(status().isOk());

        assertThat(meetingService.detail(1L).meeting().getStatus()).isEqualTo(MeetingStatus.ARCHIVED);
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    void adminTduckCompatibilityPrefixIsAvailable() throws Exception {
        mockMvc.perform(get("/api/admin/tduck/bindings/1"))
                .andExpect(status().isOk());
    }

    private MockMultipartFile csv(String filename, String content) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile xlsx(String filename, String content) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入模板");
            List<String> lines = content.strip().lines().toList();
            for (int rowIndex = 0; rowIndex < lines.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex);
                String[] cells = lines.get(rowIndex).split(",", -1);
                for (int colIndex = 0; colIndex < cells.length; colIndex++) {
                    row.createCell(colIndex).setCellValue(cells[colIndex].trim());
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("测试 XLSX 文件构造失败", ex);
        }
    }

    private void enablePublicQueries() {
        publicQueryService.updateSettings(1L, new MeetingQuerySettingsRequest(
                true,
                true,
                java.util.List.of(PublicQueryField.PHONE, PublicQueryField.NAME),
                PublicQueryMatchMode.FUZZY
        ));
    }

    private void ensureRoom(String roomNo) {
        if (roomRepository.findByMeetingIdAndRoomNo(1L, roomNo).isPresent()) {
            return;
        }
        roomService.create(new RoomRequest(1L, roomNo, "双床房", "北京会议中心酒店", "14:00 后可入住", 2, RoomStatus.AVAILABLE));
    }

    private String sheetText(org.apache.poi.ss.usermodel.Sheet sheet) {
        StringBuilder builder = new StringBuilder();
        sheet.forEach(row -> row.forEach(cell -> builder.append(cell).append('\n')));
        return builder.toString();
    }

    private void saveTduckFieldMapping(String fieldKey, String label) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setMeeting(attendeeRepository.findById(1L).orElseThrow().getMeeting());
        mapping.setAttendeeField(null);
        mapping.setTduckFieldKey(fieldKey);
        mapping.setTduckFieldLabel(label);
        mapping.setRequiredMapping(false);
        tduckFieldMappingRepository.save(mapping);
    }

    private Map<String, Object> tduckSnapshot(Attendee attendee) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", attendee.getName());
        snapshot.put("phone", attendee.getPhone());
        snapshot.put("organization", attendee.getOrganization());
        snapshot.put("title", attendee.getTitle());
        snapshot.put("attendeeType", attendee.getAttendeeType() == null ? null : attendee.getAttendeeType().name());
        return snapshot;
    }

    private void withCurrentUser(Long userId, String username, Set<Role> roles, Runnable action) {
        try {
            CurrentUser currentUser = new CurrentUser(userId, username, username, roles);
            var authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(currentUser, null, authorities));
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
