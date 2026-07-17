package com.meetingos.room;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.ImportRowTransactionRunner;
import com.meetingos.common.TableRow;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.settings.SystemSettingsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomServiceTest {

    private RoomRepository roomRepository;
    private AttendeeRepository attendeeRepository;
    private OperationLogService operationLogService;
    private RoomService roomService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        attendeeRepository = mock(AttendeeRepository.class);
        operationLogService = mock(OperationLogService.class);
        roomService = new RoomService(
                mock(MeetingRepository.class),
                roomRepository,
                attendeeRepository,
                mock(EntityManager.class),
                operationLogService,
                mock(SystemSettingsService.class)
        );
        meeting = new Meeting();
        meeting.setId(1L);
        meeting.setName("数字经济论坛");
    }

    @Test
    void reassignLocksTargetAndPreviousRoomsBeforeCapacityCheck() {
        Attendee attendee = attendee(11L, "B102");
        Room target = room("A101", 1, RoomStatus.AVAILABLE);
        Room previous = room("B102", 1, RoomStatus.ASSIGNED);
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(roomRepository.findByMeetingIdAndRoomNoInForUpdate(1L, List.of("A101", "B102")))
                .thenReturn(List.of(target, previous));
        when(attendeeRepository.countByMeetingIdAndRoomNo(1L, "A101")).thenReturn(0L);
        when(attendeeRepository.countByMeetingIdAndRoomNo(1L, "B102")).thenReturn(0L);

        roomService.assign(new AssignRoomRequest(11L, "A101", "靠窗"));

        InOrder ordered = inOrder(roomRepository, attendeeRepository);
        ordered.verify(attendeeRepository).findByIdForUpdate(11L);
        ordered.verify(roomRepository).findByMeetingIdAndRoomNoInForUpdate(1L, List.of("A101", "B102"));
        ordered.verify(attendeeRepository).countByMeetingIdAndRoomNo(1L, "A101");
        assertThat(attendee.getRoomNo()).isEqualTo("A101");
        assertThat(attendee.getRoomRemark()).isEqualTo("靠窗");
        assertThat(target.getStatus()).isEqualTo(RoomStatus.ASSIGNED);
        assertThat(previous.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        Map<String, Object> expectedLogDetails = Map.of(
                "meetingId", 1L,
                "attendeeId", 11L,
                "attendeeName", "张三",
                "oldRoomNo", "B102",
                "newRoomNo", "A101",
                "source", "MANUAL",
                "remark", "靠窗"
        );
        verify(operationLogService).recordWithinTransaction(
                "ROOM_ASSIGNMENT_CHANGE",
                "ROOM",
                "11",
                true,
                "房间分配变更",
                null,
                expectedLogDetails
        );
        verify(operationLogService, never()).record(
                "ROOM_ASSIGNMENT_CHANGE",
                "ROOM",
                "11",
                true,
                "房间分配变更",
                null,
                expectedLogDetails
        );
    }

    @Test
    void clearLocksPreviousRoomBeforeRefreshingStatus() {
        Attendee attendee = attendee(11L, "A101");
        Room previous = room("A101", 1, RoomStatus.ASSIGNED);
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(roomRepository.findByMeetingIdAndRoomNoInForUpdate(1L, List.of("A101")))
                .thenReturn(List.of(previous));
        when(attendeeRepository.countByMeetingIdAndRoomNo(1L, "A101")).thenReturn(0L);

        roomService.clearAssignment(11L);

        InOrder ordered = inOrder(roomRepository, attendeeRepository);
        ordered.verify(attendeeRepository).findByIdForUpdate(11L);
        ordered.verify(roomRepository).findByMeetingIdAndRoomNoInForUpdate(1L, List.of("A101"));
        ordered.verify(attendeeRepository).countByMeetingIdAndRoomNo(1L, "A101");
        assertThat(attendee.getRoomNo()).isNull();
        assertThat(attendee.getRoomRemark()).isNull();
        assertThat(attendee.isAccommodationRequired()).isFalse();
        assertThat(previous.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void assigningSameRoomDoesNotConsumeAnotherCapacitySlot() {
        Attendee attendee = attendee(11L, "A101");
        Room target = room("A101", 1, RoomStatus.ASSIGNED);
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(roomRepository.findByMeetingIdAndRoomNoInForUpdate(1L, List.of("A101")))
                .thenReturn(List.of(target));
        when(attendeeRepository.countByMeetingIdAndRoomNo(1L, "A101")).thenReturn(1L);

        roomService.assign(new AssignRoomRequest(11L, "A101", "更新备注"));

        assertThat(attendee.getRoomNo()).isEqualTo("A101");
        assertThat(attendee.getRoomRemark()).isEqualTo("更新备注");
    }

    @Test
    void capacityWriteTransactionsUseReadCommittedIsolation() throws NoSuchMethodException {
        assertReadCommitted("assign", AssignRoomRequest.class);
        assertReadCommitted("clearAssignment", Long.class);
    }

    @Test
    void cleanRoomImportUsesSingleTransactionForBatch() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        ImportRowTransactionRunner transactionRunner = mock(ImportRowTransactionRunner.class);
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(transactionRunner.supportsBatchTransactions()).thenReturn(true);
        when(transactionRunner.execute(org.mockito.ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        RoomService batchedService = new RoomService(
                meetingRepository,
                roomRepository,
                attendeeRepository,
                mock(EntityManager.class),
                mock(OperationLogService.class),
                mock(SystemSettingsService.class),
                transactionRunner
        );

        var result = batchedService.importRoomRows(1L, List.of(
                new TableRow(2, Map.of("房间号", "A101")),
                new TableRow(3, Map.of("房间号", "A102"))
        ), (processed, imported, failed) -> {
        });

        assertThat(result.importedRows()).isEqualTo(2);
        verify(transactionRunner, times(1)).execute(org.mockito.ArgumentMatchers.<Supplier<Object>>any());
    }

    private void assertReadCommitted(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Transactional transactional = RoomService.class.getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private Attendee attendee(Long id, String roomNo) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setMeeting(meeting);
        attendee.setName("张三");
        attendee.setRoomNo(roomNo);
        attendee.setRoomRemark("旧备注");
        attendee.setAccommodationRequired(true);
        return attendee;
    }

    private Room room(String roomNo, int capacity, RoomStatus status) {
        Room room = new Room();
        room.setId((long) roomNo.hashCode());
        room.setMeeting(meeting);
        room.setRoomNo(roomNo);
        room.setCapacity(capacity);
        room.setStatus(status);
        return room;
    }
}
