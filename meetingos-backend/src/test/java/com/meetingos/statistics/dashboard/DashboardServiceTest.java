package com.meetingos.statistics.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.tduck.AttendeeField;
import com.meetingos.tduck.MappingStatus;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import com.meetingos.tduck.TduckFieldMappingResponse;
import com.meetingos.tduck.TduckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final long MEETING_ID = 7L;

    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private AttendeeRepository attendeeRepository;
    @Mock
    private SeatAssignmentRepository seatAssignmentRepository;
    @Mock
    private DinnerAssignmentRepository dinnerAssignmentRepository;
    @Mock
    private TduckFieldMappingRepository fieldMappingRepository;
    @Mock
    private TduckService tduckService;
    @Mock
    private MeetingDashboardConfigRepository configRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                meetingRepository,
                attendeeRepository,
                seatAssignmentRepository,
                dinnerAssignmentRepository,
                fieldMappingRepository,
                tduckService,
                configRepository,
                new ObjectMapper()
        );
        lenient().when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of(
                        "inviter", "旧值",
                        "inviterlabel", "王老师",
                        "hobby", List.of("阅读", "运动"),
                        "checkbox178", "技术,公益",
                        "companion", "2",
                        "source", "admin-import"
                )),
                attendee(2L, Map.of(
                        "inviter", "李老师",
                        "hobby", List.of("运动"),
                        "companion", "3"
                ))
        ));
        lenient().when(seatAssignmentRepository.findAssignedAttendeeIdsByMeetingId(MEETING_ID)).thenReturn(List.of(1L));
        lenient().when(dinnerAssignmentRepository.findAssignedAttendeeIdsByMeetingId(MEETING_ID)).thenReturn(List.of());
        lenient().when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                mapping("inviter", "邀请人", AttendeeField.INVITER),
                mapping("companion", "随行人数", AttendeeField.COMPANION_COUNT)
        ));
    }

    @Test
    void fieldCatalogIncludesMappedAndSnapshotOnlyFieldsAndKeepsRawLabelPriority() {
        stubMeetingExists();
        DashboardFieldCatalogResponse response = dashboardService.fields(MEETING_ID);

        DashboardFieldDescriptor inviter = field(response, "tduck:inviter");
        assertThat(inviter.label()).isEqualTo("邀请人");
        assertThat(inviter.dataType()).isEqualTo(DashboardFieldDataType.TEXT);
        assertThat(inviter.coverageCount()).isEqualTo(2);
        assertThat(inviter.allowedAggregations()).contains(DashboardAggregation.DISTRIBUTION);
        assertThat(inviter.options()).extracting(DashboardFieldOption::value)
                .contains("王老师", "李老师");

        DashboardFieldDescriptor phone = field(response, "system:phone");
        assertThat(phone.allowedAggregations()).contains(DashboardAggregation.DISTRIBUTION);
        assertThat(phone.allowedVisualizations()).contains(DashboardVisualization.PIE);
        assertThat(phone.options()).extracting(DashboardFieldOption::value).contains("13800000001");
        assertThat(field(response, "system:roomNo").label()).isEqualTo("房间号");
        assertThat(field(response, "system:checkinEntrance").label()).isEqualTo("签到入口");
        assertThat(field(response, "system:reviewReason").label()).isEqualTo("审核原因");

        DashboardFieldDescriptor hobby = field(response, "tduck:hobby");
        assertThat(hobby.dataType()).isEqualTo(DashboardFieldDataType.MULTI_SELECT);
        assertThat(hobby.coverageCount()).isEqualTo(2);

        DashboardFieldDescriptor checkbox = field(response, "tduck:checkbox178");
        assertThat(checkbox.dataType()).isEqualTo(DashboardFieldDataType.MULTI_SELECT);
        assertThat(checkbox.options()).extracting(DashboardFieldOption::value).contains("技术", "公益");
        assertThat(response.fields()).extracting(DashboardFieldDescriptor::key).doesNotContain("tduck:source");

        DashboardFieldDescriptor companion = field(response, "tduck:companion");
        assertThat(companion.dataType()).isEqualTo(DashboardFieldDataType.NUMBER);
        assertThat(companion.allowedAggregations()).contains(
                DashboardAggregation.SUM,
                DashboardAggregation.AVERAGE
        );
    }

    @Test
    void fieldCatalogNormalizesLabelOnlySnapshotKeyAndUsesMappedTitleAndLabelValue() {
        when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of("radio1783663523296label", "高铁"))
        ));
        lenient().when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                mapping("radio1783663523296", "31日抵达方式", null)
        ));
        stubMeetingExists();

        DashboardFieldCatalogResponse response = dashboardService.fields(MEETING_ID);

        DashboardFieldDescriptor arrivalTransport = field(response, "tduck:radio1783663523296");
        assertThat(arrivalTransport.label()).isEqualTo("31日抵达方式");
        assertThat(arrivalTransport.coverageCount()).isEqualTo(1);
        assertThat(arrivalTransport.options()).containsExactly(new DashboardFieldOption("高铁", "高铁"));
        assertThat(response.fields()).extracting(DashboardFieldDescriptor::key)
                .doesNotContain("tduck:radio1783663523296label");
    }

    @Test
    void fieldCatalogKeepsRealFieldsEndingWithLabelSeparateFromTheirBaseField() {
        when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of(
                        "company", "会务公司",
                        "companylabel", "央企客户"
                ))
        ));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                mapping("company", "单位", null),
                mapping("companylabel", "客户标签", null)
        ));
        stubMeetingExists();

        DashboardFieldCatalogResponse response = dashboardService.fields(MEETING_ID);

        assertThat(field(response, "tduck:company").options())
                .containsExactly(new DashboardFieldOption("会务公司", "会务公司"));
        DashboardFieldDescriptor customerTag = field(response, "tduck:companylabel");
        assertThat(customerTag.label()).isEqualTo("客户标签");
        assertThat(customerTag.options()).containsExactly(new DashboardFieldOption("央企客户", "央企客户"));
    }

    @Test
    void fieldCatalogUsesCurrentTduckTitleForUnmappedSnapshotField() {
        when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of("radio1783663523286label", "非常满意"))
        ));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of());
        when(tduckService.mappings(MEETING_ID)).thenReturn(List.of(
                new TduckFieldMappingResponse(
                        null,
                        null,
                        "radio1783663523286",
                        "您对会务服务的满意度",
                        "radio",
                        false,
                        false,
                        MappingStatus.NOT_CONFIGURED
                )
        ));
        stubMeetingExists();

        DashboardFieldCatalogResponse response = dashboardService.fields(MEETING_ID);

        DashboardFieldDescriptor satisfaction = field(response, "tduck:radio1783663523286");
        assertThat(satisfaction.label()).isEqualTo("您对会务服务的满意度");
        assertThat(satisfaction.options()).containsExactly(new DashboardFieldOption("非常满意", "非常满意"));
        assertThat(response.fields()).extracting(DashboardFieldDescriptor::key)
                .doesNotContain("tduck:radio1783663523286label");
    }

    @Test
    void queryNormalizesLegacyTduckLabelFieldKey() {
        when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of("radio1783663523296label", "高铁"))
        ));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                mapping("radio1783663523296", "31日抵达方式", null)
        ));
        stubMeetingExists();

        DashboardQueryResponse response = dashboardService.query(new DashboardQueryRequest(
                MEETING_ID,
                List.of(new DashboardComponentRequest(
                        "arrival-transport",
                        "31日抵达方式",
                        "tduck:radio1783663523296label",
                        DashboardAggregation.DISTRIBUTION,
                        DashboardVisualization.TABLE,
                        10,
                        List.of()
                )),
                null,
                null
        ));

        DashboardComponentResult result = response.components().get(0);
        assertThat(result.status()).isEqualTo(DashboardComponentStatus.SUCCESS);
        assertThat(result.fieldKey()).isEqualTo("tduck:radio1783663523296");
        assertThat(result.items()).containsExactly(new DashboardDistributionItem("高铁", "高铁", 1L));
        verifyNoInteractions(tduckService);
    }

    @Test
    void queryNormalizesLegacyTduckLabelFilterKey() {
        when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of("radio1783663523296label", "高铁")),
                attendee(2L, Map.of("radio1783663523296label", "飞机"))
        ));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                mapping("radio1783663523296", "31日抵达方式", null)
        ));
        stubMeetingExists();

        DashboardQueryResponse response = dashboardService.query(new DashboardQueryRequest(
                MEETING_ID,
                List.of(new DashboardComponentRequest(
                        "arrivals-by-rail",
                        "高铁抵达人数",
                        "system:attendee",
                        DashboardAggregation.COUNT,
                        DashboardVisualization.NUMBER,
                        10,
                        List.of(new DashboardFilterRequest(
                                "tduck:radio1783663523296label",
                                DashboardFilterOperator.EQUALS,
                                List.of("高铁")
                        ))
                )),
                null,
                null
        ));

        DashboardComponentResult result = response.components().get(0);
        assertThat(result.status()).isEqualTo(DashboardComponentStatus.SUCCESS);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.value()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void configNormalizesLegacyTduckLabelComponentAndFilterKeys() {
        when(attendeeRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                attendee(1L, Map.of("radio1783663523296label", "高铁"))
        ));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(MEETING_ID)).thenReturn(List.of(
                mapping("radio1783663523296", "31日抵达方式", null)
        ));
        MeetingDashboardConfig config = new MeetingDashboardConfig();
        config.setConfiguration(Map.of("components", List.of(Map.of(
                "id", "legacy-arrival",
                "title", "31日抵达方式",
                "fieldKey", "tduck:radio1783663523296label",
                "aggregation", "DISTRIBUTION",
                "visualization", "TABLE",
                "limit", 10,
                "filters", List.of(Map.of(
                        "fieldKey", "tduck:radio1783663523296label",
                        "operator", "EQUALS",
                        "values", List.of("高铁")
                ))
        ))));
        when(configRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(config));
        stubMeetingExists();

        DashboardConfigResponse response = dashboardService.config(MEETING_ID);

        DashboardComponentRequest component = response.components().get(0);
        assertThat(component.fieldKey()).isEqualTo("tduck:radio1783663523296");
        assertThat(component.filters()).singleElement()
                .extracting(DashboardFilterRequest::fieldKey)
                .isEqualTo("tduck:radio1783663523296");
    }

    @Test
    void batchQueryReturnsIndependentInvalidFieldAndExpandsMultiSelectValues() {
        stubMeetingExists();
        DashboardQueryResponse response = dashboardService.query(new DashboardQueryRequest(
                MEETING_ID,
                List.of(
                        new DashboardComponentRequest(
                                "hobby", "兴趣分布", "tduck:hobby", DashboardAggregation.DISTRIBUTION,
                                DashboardVisualization.BAR, 10,
                                List.of(new DashboardFilterRequest(
                                        "tduck:inviter", DashboardFilterOperator.EQUALS, List.of("王老师")
                                ))
                        ),
                        new DashboardComponentRequest(
                                "missing", "失效字段", "tduck:removed", DashboardAggregation.DISTRIBUTION,
                                DashboardVisualization.PIE, 10, List.of()
                        ),
                        new DashboardComponentRequest(
                                "checkbox", "复选字段", "tduck:checkbox178", DashboardAggregation.DISTRIBUTION,
                                DashboardVisualization.BAR, 10,
                                List.of(new DashboardFilterRequest(
                                        "tduck:checkbox178", DashboardFilterOperator.EQUALS, List.of("技术")
                                ))
                        )
                ),
                null,
                null
        ));

        DashboardComponentResult hobby = response.components().get(0);
        assertThat(hobby.status()).isEqualTo(DashboardComponentStatus.SUCCESS);
        assertThat(hobby.total()).isEqualTo(1);
        assertThat(hobby.items()).extracting(DashboardDistributionItem::key)
                .containsExactlyInAnyOrder("阅读", "运动");

        DashboardComponentResult missing = response.components().get(1);
        assertThat(missing.status()).isEqualTo(DashboardComponentStatus.INVALID_FIELD);
        assertThat(missing.value()).isNull();
        assertThat(missing.items()).isEmpty();

        DashboardComponentResult checkbox = response.components().get(2);
        assertThat(checkbox.status()).isEqualTo(DashboardComponentStatus.SUCCESS);
        assertThat(checkbox.total()).isEqualTo(1);
        assertThat(checkbox.items()).extracting(DashboardDistributionItem::key)
                .containsExactlyInAnyOrder("技术", "公益");
    }

    @Test
    void numericMappedFieldSupportsStringNumbersOnlyBecauseItsMappingDeclaresNumber() {
        stubMeetingExists();
        DashboardQueryResponse response = dashboardService.query(new DashboardQueryRequest(
                MEETING_ID,
                List.of(new DashboardComponentRequest(
                        "companions", "随行人数", "tduck:companion", DashboardAggregation.SUM,
                        DashboardVisualization.NUMBER, 10, List.of()
                )),
                null,
                null
        ));

        DashboardComponentResult result = response.components().get(0);
        assertThat(result.status()).isEqualTo(DashboardComponentStatus.SUCCESS);
        assertThat(result.value()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void supportsOneAndOneHundredAsDashboardTopLimits() {
        stubMeetingExists();
        DashboardQueryResponse response = dashboardService.query(new DashboardQueryRequest(
                MEETING_ID,
                List.of(
                        new DashboardComponentRequest(
                                "top-one", "兴趣前一项", "tduck:hobby", DashboardAggregation.DISTRIBUTION,
                                DashboardVisualization.BAR, 1, List.of()
                        ),
                        new DashboardComponentRequest(
                                "top-one-hundred", "兴趣前一百项", "tduck:hobby", DashboardAggregation.DISTRIBUTION,
                                DashboardVisualization.TABLE, 100, List.of()
                        )
                ),
                null,
                null
        ));

        assertThat(response.components().get(0).items()).hasSize(1);
        assertThat(response.components().get(1).items()).hasSize(2);
    }

    @Test
    void rejectsDashboardTopLimitsAboveOneHundredWhenSavingConfiguration() {
        var meeting = new com.meetingos.meeting.Meeting();
        meeting.setId(MEETING_ID);
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> dashboardService.saveConfig(new DashboardConfigRequest(
                MEETING_ID,
                List.of(new DashboardComponentRequest(
                        "too-many", "超过范围", "tduck:inviter", DashboardAggregation.DISTRIBUTION,
                        DashboardVisualization.TABLE, 101, List.of()
                ))
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 到 100");

        verify(configRepository, never()).save(any());
    }

    @Test
    void savesOnlyValidatedCatalogComponentsAsMeetingConfiguration() {
        var meeting = new com.meetingos.meeting.Meeting();
        meeting.setId(MEETING_ID);
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(configRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.empty());
        when(configRepository.save(any(MeetingDashboardConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DashboardConfigResponse response = dashboardService.saveConfig(new DashboardConfigRequest(
                MEETING_ID,
                List.of(new DashboardComponentRequest(
                        "inviter", "邀请人分布", "tduck:inviter", DashboardAggregation.DISTRIBUTION,
                        DashboardVisualization.PIE, 10, List.of(), DashboardComponentWidth.FULL
                ))
        ));

        ArgumentCaptor<MeetingDashboardConfig> captured = ArgumentCaptor.forClass(MeetingDashboardConfig.class);
        verify(configRepository).save(captured.capture());
        assertThat(captured.getValue().getConfiguration()).containsKey("components");
        assertThat(response.components()).hasSize(1);
        assertThat(response.components().get(0).fieldKey()).isEqualTo("tduck:inviter");
        assertThat(response.components().get(0).width()).isEqualTo(DashboardComponentWidth.FULL);
    }

    @Test
    void rejectsUnknownFieldsWhenSavingConfiguration() {
        var meeting = new com.meetingos.meeting.Meeting();
        meeting.setId(MEETING_ID);
        when(meetingRepository.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> dashboardService.saveConfig(new DashboardConfigRequest(
                MEETING_ID,
                List.of(new DashboardComponentRequest(
                        "removed", "已删除字段", "tduck:removed", DashboardAggregation.DISTRIBUTION,
                        DashboardVisualization.PIE, 10, List.of()
                ))
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计字段已不存在");

        verify(configRepository, never()).save(any());
    }

    private DashboardFieldDescriptor field(DashboardFieldCatalogResponse response, String key) {
        return response.fields().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private void stubMeetingExists() {
        when(meetingRepository.existsById(MEETING_ID)).thenReturn(true);
    }

    private TduckFieldMapping mapping(String key, String label, AttendeeField attendeeField) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setTduckFieldKey(key);
        mapping.setTduckFieldLabel(label);
        mapping.setAttendeeField(attendeeField);
        return mapping;
    }

    private Attendee attendee(Long id, Map<String, Object> snapshot) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setName("参会人" + id);
        attendee.setPhone("1380000000" + id);
        attendee.setRegisteredAt(LocalDateTime.of(2026, 7, 25, 10, 0));
        attendee.setRawDataSnapshot(snapshot);
        return attendee;
    }
}
