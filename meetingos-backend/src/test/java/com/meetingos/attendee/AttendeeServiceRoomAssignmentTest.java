package com.meetingos.attendee;

import com.meetingos.common.BusinessException;
import com.meetingos.common.ImportResult;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.room.AssignRoomRequest;
import com.meetingos.room.RoomService;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.TduckFieldMappingRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendeeServiceRoomAssignmentTest {

    private AttendeeRepository attendeeRepository;
    private MeetingRepository meetingRepository;
    private RoomService roomService;
    private AttendeeService attendeeService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        attendeeRepository = mock(AttendeeRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        roomService = mock(RoomService.class);
        attendeeService = new AttendeeService(
                attendeeRepository,
                meetingRepository,
                mock(MeetingAccessService.class),
                mock(SystemSettingsService.class),
                mock(TduckFieldMappingRepository.class),
                mock(PublicQueryHistoryRepository.class),
                mock(SeatAssignmentRepository.class),
                mock(DinnerAssignmentRepository.class),
                roomService
        );
        meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode("FORUM");
        meeting.setName("数字经济论坛");
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
    }

    @Test
    void createPersistsWithoutRoomThenUsesRoomServiceAssignmentRules() {
        when(attendeeRepository.findByMeetingAndPhone(meeting, "13800000000")).thenReturn(Optional.empty());
        when(attendeeRepository.saveAndFlush(any(Attendee.class))).thenAnswer(invocation -> {
            Attendee attendee = invocation.getArgument(0);
            assertThat(attendee.getRoomNo()).isNull();
            attendee.setId(11L);
            return attendee;
        });
        when(roomService.assignWithinTransaction(any(AssignRoomRequest.class), eq("ATTENDEE_CREATE")))
                .thenAnswer(invocation -> applyAssignment(invocation.getArgument(0)));

        Attendee result = attendeeService.create(request("张三", "13800000000", true, "A101", "靠窗"));

        var ordered = inOrder(attendeeRepository, roomService);
        ordered.verify(attendeeRepository).saveAndFlush(any(Attendee.class));
        ordered.verify(roomService).assignWithinTransaction(
                new AssignRoomRequest(11L, "A101", "靠窗"),
                "ATTENDEE_CREATE"
        );
        assertThat(result.getRoomNo()).isEqualTo("A101");
        assertThat(result.isAccommodationRequired()).isTrue();
    }

    @Test
    void updateLocksAttendeeAndClearsRoomThroughRoomService() {
        Attendee attendee = attendee(11L, "旧姓名", "13800000000", "A101");
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByMeetingAndPhone(meeting, "13800000000")).thenReturn(Optional.of(attendee));
        when(roomService.clearAssignmentWithinTransaction(11L, "ATTENDEE_UPDATE_CLEAR"))
                .thenAnswer(invocation -> {
                    attendee.setRoomNo(null);
                    attendee.setRoomRemark(null);
                    attendee.setAccommodationRequired(false);
                    return attendee;
                });

        Attendee result = attendeeService.update(
                11L,
                request("新姓名", "13800000000", true, null, "等待排房")
        );

        verify(attendeeRepository).findByIdForUpdate(11L);
        verify(roomService).clearAssignmentWithinTransaction(11L, "ATTENDEE_UPDATE_CLEAR");
        assertThat(result.getName()).isEqualTo("新姓名");
        assertThat(result.getRoomNo()).isNull();
        assertThat(result.isAccommodationRequired()).isTrue();
        assertThat(result.getRoomRemark()).isEqualTo("等待排房");
    }

    @Test
    void importRoutesRoomNumberThroughRoomService() {
        when(attendeeRepository.findByMeetingAndPhone(meeting, "13800000000")).thenReturn(Optional.empty());
        when(attendeeRepository.saveAndFlush(any(Attendee.class))).thenAnswer(invocation -> {
            Attendee attendee = invocation.getArgument(0);
            assertThat(attendee.getRoomNo()).isNull();
            attendee.setId(11L);
            return attendee;
        });
        when(roomService.assignWithinTransaction(any(AssignRoomRequest.class), eq("ATTENDEE_IMPORT")))
                .thenAnswer(invocation -> applyAssignment(invocation.getArgument(0)));

        ImportResult result = attendeeService.importFile(1L, workbook(
                List.of("name", "phone", "roomNo", "accommodationRequired", "roomRemark"),
                List.of("张三", "13800000000", "A101", "true", "靠窗")
        ));

        assertThat(result.importedRows()).isEqualTo(1);
        verify(roomService).assignWithinTransaction(
                new AssignRoomRequest(11L, "A101", "靠窗"),
                "ATTENDEE_IMPORT"
        );
    }

    @Test
    void importDeletesNewAttendeeWhenRoomCapacityValidationFails() {
        when(attendeeRepository.findByMeetingAndPhone(meeting, "13800000000")).thenReturn(Optional.empty());
        Attendee saved = attendee(11L, "张三", "13800000000", null);
        when(attendeeRepository.saveAndFlush(any(Attendee.class))).thenReturn(saved);
        when(roomService.assignWithinTransaction(any(AssignRoomRequest.class), eq("ATTENDEE_IMPORT")))
                .thenThrow(BusinessException.badRequest("该房间已达到可入住人数上限"));

        ImportResult result = attendeeService.importFile(1L, workbook(
                List.of("name", "phone", "roomNo", "accommodationRequired"),
                List.of("张三", "13800000000", "A101", "true")
        ));

        assertThat(result.importedRows()).isZero();
        assertThat(result.failedRows()).singleElement()
                .extracting(failure -> failure.reason())
                .asString()
                .contains("上限");
        verify(attendeeRepository).delete(saved);
    }

    @Test
    void roomMutatingTransactionsUseReadCommittedIsolation() throws NoSuchMethodException {
        assertReadCommitted("create", AttendeeRequest.class);
        assertReadCommitted("update", Long.class, AttendeeRequest.class);
        assertReadCommitted("assignRoom", Long.class, String.class, String.class);
        assertReadCommitted("importFile", Long.class, MultipartFile.class);
    }

    @Test
    void batchReviewLocksAttendeesInStableIdOrder() {
        Attendee first = attendee(1L, "张三", "13800000001", null);
        Attendee second = attendee(2L, "李四", "13800000002", null);
        when(attendeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(attendeeRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));

        int updated = attendeeService.batchReview(new ReviewRequest(
                ReviewStatus.APPROVED,
                "资料完整",
                null,
                List.of(2L, 1L, 2L)
        ));

        var ordered = inOrder(attendeeRepository);
        ordered.verify(attendeeRepository).findByIdForUpdate(1L);
        ordered.verify(attendeeRepository).findByIdForUpdate(2L);
        assertThat(updated).isEqualTo(2);
        assertThat(first.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(second.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    private Attendee applyAssignment(AssignRoomRequest request) {
        Attendee attendee = attendee(11L, "张三", "13800000000", request.roomNo());
        attendee.setRoomRemark(request.roomRemark());
        attendee.setAccommodationRequired(true);
        return attendee;
    }

    private Attendee attendee(Long id, String name, String phone, String roomNo) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setMeeting(meeting);
        attendee.setName(name);
        attendee.setPhone(phone);
        attendee.setRoomNo(roomNo);
        attendee.setAccommodationRequired(roomNo != null);
        attendee.setReviewStatus(ReviewStatus.PENDING);
        return attendee;
    }

    private AttendeeRequest request(String name,
                                    String phone,
                                    boolean accommodationRequired,
                                    String roomNo,
                                    String roomRemark) {
        return new AttendeeRequest(
                1L,
                name,
                phone,
                "示例公司",
                "总监",
                null,
                null,
                AttendeeType.NORMAL,
                accommodationRequired,
                roomNo,
                roomRemark,
                null,
                null
        );
    }

    private MockMultipartFile workbook(List<String> headers, List<String> values) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("参会人");
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                headerRow.createCell(index).setCellValue(headers.get(index));
            }
            Row valueRow = sheet.createRow(1);
            for (int index = 0; index < values.size(); index++) {
                valueRow.createCell(index).setCellValue(values.get(index));
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "attendees.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("测试 XLSX 生成失败", ex);
        }
    }

    private void assertReadCommitted(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Transactional transactional = AttendeeService.class.getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }
}
