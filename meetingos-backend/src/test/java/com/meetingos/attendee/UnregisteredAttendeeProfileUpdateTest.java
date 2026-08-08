package com.meetingos.attendee;

import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.room.RoomService;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.AttendeeField;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnregisteredAttendeeProfileUpdateTest {

    private AttendeeRepository attendeeRepository;
    private MeetingAccessService meetingAccessService;
    private TduckFieldMappingRepository fieldMappingRepository;
    private SeatAssignmentRepository seatAssignmentRepository;
    private AttendeeService attendeeService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        attendeeRepository = mock(AttendeeRepository.class);
        meetingAccessService = mock(MeetingAccessService.class);
        fieldMappingRepository = mock(TduckFieldMappingRepository.class);
        seatAssignmentRepository = mock(SeatAssignmentRepository.class);
        attendeeService = new AttendeeService(
                attendeeRepository,
                mock(MeetingRepository.class),
                meetingAccessService,
                mock(SystemSettingsService.class),
                fieldMappingRepository,
                mock(PublicQueryHistoryRepository.class),
                seatAssignmentRepository,
                mock(DinnerAssignmentRepository.class),
                mock(RoomService.class)
        );
        meeting = new Meeting();
        meeting.setId(1L);
    }

    @Test
    void updatesWhitelistedFormValuesAndMappedProfileFieldsWhilePreservingRelatedState() {
        Attendee attendee = unregisteredAttendee();
        attendee.setOrganization("旧单位");
        attendee.setTitle("旧职务");
        attendee.setTags("临时入座");
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setCheckinStatus(CheckinStatus.CHECKED_IN);
        attendee.setCheckedInAt(LocalDateTime.of(2026, 7, 30, 9, 0));
        attendee.setAttendeeCode("MEETING-UNREGISTERED-001");
        attendee.setTduckDataId("not-a-tduck-submission");
        attendee.setRoomNo("A101");
        attendee.setRoomRemark("靠窗");
        attendee.setAccommodationRequired(true);
        attendee.setRawDataSnapshot(new LinkedHashMap<>(Map.of(
                "source", "onsite",
                "legacy_field", "保留",
                "input_type", "1",
                "input_typelabel", "普通参会人"
        )));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(1L)).thenReturn(List.of(
                mapping(AttendeeField.NAME, "input_name"),
                mapping(AttendeeField.PHONE, "input_phone"),
                mapping(AttendeeField.ORGANIZATION, "input_company"),
                mapping(AttendeeField.TITLE, "input_title"),
                mapping(AttendeeField.IDENTITY_NO, "input_identity"),
                mapping(AttendeeField.EMAIL, "input_email"),
                mapping(AttendeeField.ATTENDEE_TYPE, "input_type"),
                mapping(AttendeeField.REMARK, "textarea_remark"),
                mapping(null, "checkbox_lunch"),
                mapping(AttendeeField.ACCOMMODATION_REQUIRED, "radio_room"),
                mapping(AttendeeField.ROOM_NO, "input_room")
        ));
        when(attendeeRepository.findByMeetingAndPhone(meeting, "13800138000")).thenReturn(Optional.empty());

        Attendee updated = attendeeService.updateUnregisteredProfile(11L, request(Map.ofEntries(
                Map.entry("input_name", "  现场新人员  "),
                Map.entry("input_phone", "138 0013-8000"),
                Map.entry("input_company", "  新单位  "),
                Map.entry("input_title", "  嘉宾  "),
                Map.entry("input_identity", "  110101199001010011  "),
                Map.entry("input_email", "  person@example.com  "),
                Map.entry("input_type", "2"),
                Map.entry("input_typelabel", "嘉宾"),
                Map.entry("textarea_remark", "  现场补录  "),
                Map.entry("checkbox_lunch", "午餐"),
                Map.entry("radio_room", "否"),
                Map.entry("input_room", "B999")
        )));

        assertThat(updated).isSameAs(attendee);
        assertThat(attendee.getName()).isEqualTo("现场新人员");
        assertThat(attendee.getPhone()).isEqualTo("13800138000");
        assertThat(attendee.getOrganization()).isEqualTo("新单位");
        assertThat(attendee.getTitle()).isEqualTo("嘉宾");
        assertThat(attendee.getIdentityNo()).isEqualTo("110101199001010011");
        assertThat(attendee.getEmail()).isEqualTo("person@example.com");
        assertThat(attendee.getAttendeeType()).isEqualTo(AttendeeType.GUEST);
        assertThat(attendee.getRemark()).isEqualTo("现场补录");
        assertThat(attendee.getTags()).isEqualTo("临时入座");
        assertThat(attendee.getRawDataSnapshot()).containsEntry("source", "onsite")
                .containsEntry("legacy_field", "保留")
                .containsEntry("input_type", "2")
                .containsEntry("input_typelabel", "嘉宾")
                .containsEntry("checkbox_lunch", "午餐")
                .containsEntry("radio_room", "否")
                .containsEntry("input_room", "B999");
        assertThat(attendee.isUnregistered()).isTrue();
        assertThat(attendee.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(attendee.getCheckinStatus()).isEqualTo(CheckinStatus.CHECKED_IN);
        assertThat(attendee.getCheckedInAt()).isEqualTo(LocalDateTime.of(2026, 7, 30, 9, 0));
        assertThat(attendee.getAttendeeCode()).isEqualTo("MEETING-UNREGISTERED-001");
        assertThat(attendee.getTduckDataId()).isEqualTo("not-a-tduck-submission");
        assertThat(attendee.getRoomNo()).isEqualTo("A101");
        assertThat(attendee.getRoomRemark()).isEqualTo("靠窗");
        assertThat(attendee.isAccommodationRequired()).isTrue();
        verify(meetingAccessService).requireManage(1L);
        verifyNoInteractions(seatAssignmentRepository);
    }

    @Test
    void emptyValuesOverwriteBothValueAndLabelAndCanClearOptionalPhone() {
        Attendee attendee = unregisteredAttendee();
        attendee.setRawDataSnapshot(new LinkedHashMap<>(Map.of(
                "source", "onsite",
                "input_phone", "13800138000",
                "radio_lunch", "1",
                "radio_lunchlabel", "盒饭"
        )));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(1L)).thenReturn(List.of(
                mapping(AttendeeField.PHONE, "input_phone"),
                mapping(null, "radio_lunch")
        ));

        attendeeService.updateUnregisteredProfile(11L, request(Map.of(
                "input_phone", "",
                "radio_lunch", "",
                "radio_lunchlabel", ""
        )));

        assertThat(attendee.getPhone()).isNull();
        assertThat(attendee.getRawDataSnapshot()).containsEntry("source", "onsite")
                .containsEntry("input_phone", "")
                .containsEntry("radio_lunch", "")
                .containsEntry("radio_lunchlabel", "");
        verify(attendeeRepository, never()).findByMeetingAndPhone(meeting, "13800138000");
    }

    @Test
    void removesStaleCompanionLabelWhenOnlyTheMainValueIsSubmitted() {
        Attendee attendee = unregisteredAttendee();
        attendee.setRawDataSnapshot(new LinkedHashMap<>(Map.of(
                "input_type", "1",
                "input_typelabel", "嘉宾"
        )));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(1L)).thenReturn(List.of(
                mapping(AttendeeField.ATTENDEE_TYPE, "input_type")
        ));

        attendeeService.updateUnregisteredProfile(11L, request(Map.of("input_type", "VIP")));

        assertThat(attendee.getAttendeeType()).isEqualTo(AttendeeType.VIP);
        assertThat(attendee.getRawDataSnapshot()).containsEntry("input_type", "VIP")
                .doesNotContainKey("input_typelabel");
    }

    @Test
    void rejectsSystemOrUnknownFieldsWithoutChangingTheSnapshot() {
        Attendee attendee = unregisteredAttendee();
        attendee.setRawDataSnapshot(new LinkedHashMap<>(Map.of("source", "onsite", "input_name", "现场人员")));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(1L)).thenReturn(List.of(
                mapping(AttendeeField.NAME, "input_name")
        ));

        assertThatThrownBy(() -> attendeeService.updateUnregisteredProfile(11L, request(Map.of(
                "input_name", "不应更新",
                "source", "forged"
        )))).hasMessageContaining("仅支持编辑当前会议报名表中的字段");

        assertThat(attendee.getName()).isEqualTo("现场人员");
        assertThat(attendee.getRawDataSnapshot()).containsExactlyEntriesOf(Map.of(
                "source", "onsite",
                "input_name", "现场人员"
        ));
    }

    @Test
    void rejectsRegisteredAttendeeWithoutChangingItsData() {
        Attendee attendee = unregisteredAttendee();
        attendee.setUnregistered(false);
        attendee.setName("已报名人员");
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));

        assertThatThrownBy(() -> attendeeService.updateUnregisteredProfile(11L, request(Map.of("input_name", "不应更新"))))
                .hasMessage("仅支持编辑未报名入座人员");

        assertThat(attendee.getName()).isEqualTo("已报名人员");
        verifyNoInteractions(fieldMappingRepository);
    }

    @Test
    void rejectsPhoneThatBelongsToAnotherAttendeeWithoutWritingFormValues() {
        Attendee attendee = unregisteredAttendee();
        attendee.setRawDataSnapshot(new LinkedHashMap<>(Map.of("source", "onsite", "input_phone", "13800138000")));
        Attendee existing = unregisteredAttendee();
        existing.setId(12L);
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(fieldMappingRepository.findByMeetingIdOrderByIdAsc(1L)).thenReturn(List.of(
                mapping(AttendeeField.PHONE, "input_phone")
        ));
        when(attendeeRepository.findByMeetingAndPhone(meeting, "13800138001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> attendeeService.updateUnregisteredProfile(11L, request(Map.of("input_phone", "13800138001"))))
                .hasMessage("同一会议内手机号已存在报名记录");

        assertThat(attendee.getPhone()).isEqualTo("13800138000");
        assertThat(attendee.getRawDataSnapshot()).containsEntry("input_phone", "13800138000");
    }

    private TduckFieldMapping mapping(AttendeeField attendeeField, String fieldKey) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setAttendeeField(attendeeField);
        mapping.setTduckFieldKey(fieldKey);
        return mapping;
    }

    private Attendee unregisteredAttendee() {
        Attendee attendee = new Attendee();
        attendee.setId(11L);
        attendee.setMeeting(meeting);
        attendee.setName("现场人员");
        attendee.setPhone("13800138000");
        attendee.setUnregistered(true);
        attendee.setAttendeeType(AttendeeType.NORMAL);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        return attendee;
    }

    private UnregisteredAttendeeProfileRequest request(Map<String, Object> formValues) {
        return new UnregisteredAttendeeProfileRequest(formValues);
    }
}
