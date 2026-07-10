package com.meetingos.integration;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.dinner.DinnerAssignRequest;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.dinner.DinnerService;
import com.meetingos.dinner.DinnerTableRepository;
import com.meetingos.dinner.DinnerTableRequest;
import com.meetingos.room.AssignRoomRequest;
import com.meetingos.room.RoomRepository;
import com.meetingos.room.RoomRequest;
import com.meetingos.room.RoomService;
import com.meetingos.room.RoomStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestDataConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResourceCapacityConcurrencyIntegrationTest {

    private static final Long MEETING_ID = 1L;

    @Autowired
    private RoomService roomService;
    @Autowired
    private DinnerService dinnerService;
    @Autowired
    private AttendeeRepository attendeeRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private DinnerTableRepository dinnerTableRepository;
    @Autowired
    private DinnerAssignmentRepository dinnerAssignmentRepository;

    @Test
    void concurrentRoomAssignmentsCannotExceedCapacity() throws Exception {
        String roomNo = "LOCK-ROOM-1";
        roomService.create(new RoomRequest(
                MEETING_ID,
                roomNo,
                "单人房",
                "并发测试酒店",
                null,
                1,
                RoomStatus.AVAILABLE
        ));
        List<Long> attendeeIds = testAttendeeIds();

        try {
            List<AssignmentOutcome> outcomes = runConcurrently(
                    () -> roomService.assign(new AssignRoomRequest(attendeeIds.get(0), roomNo, null)),
                    () -> roomService.assign(new AssignRoomRequest(attendeeIds.get(1), roomNo, null))
            );

            assertOneCapacityWinner(outcomes);
            assertThat(attendeeRepository.countByMeetingIdAndRoomNo(MEETING_ID, roomNo)).isEqualTo(1);
        } finally {
            for (Long attendeeId : attendeeIds) {
                attendeeRepository.findById(attendeeId)
                        .filter(attendee -> roomNo.equals(attendee.getRoomNo()))
                        .ifPresent(attendee -> roomService.clearAssignment(attendee.getId()));
            }
            roomRepository.findByMeetingIdAndRoomNo(MEETING_ID, roomNo).ifPresent(roomRepository::delete);
        }
    }

    @Test
    void concurrentDinnerAssignmentsCannotExceedCapacity() throws Exception {
        String tableNo = "LOCK-TABLE-1";
        Long tableId = dinnerService.createTable(new DinnerTableRequest(MEETING_ID, tableNo, 1, null)).id();
        List<Long> attendeeIds = testAttendeeIds();

        try {
            List<AssignmentOutcome> outcomes = runConcurrently(
                    () -> dinnerService.assign(new DinnerAssignRequest(attendeeIds.get(0), tableNo, null)),
                    () -> dinnerService.assign(new DinnerAssignRequest(attendeeIds.get(1), tableNo, null))
            );

            assertOneCapacityWinner(outcomes);
            assertThat(dinnerAssignmentRepository.countByMeetingIdAndTableNo(MEETING_ID, tableNo)).isEqualTo(1);
        } finally {
            for (Long attendeeId : attendeeIds) {
                if (dinnerAssignmentRepository.existsByAttendeeId(attendeeId)) {
                    dinnerService.clear(attendeeId);
                }
            }
            if (dinnerTableRepository.existsById(tableId)) {
                dinnerService.deleteTable(MEETING_ID, tableId);
            }
        }
    }

    private List<Long> testAttendeeIds() {
        return List.of(
                attendeeRepository.findByMeetingIdAndPhone(MEETING_ID, "13800000001").orElseThrow().getId(),
                attendeeRepository.findByMeetingIdAndPhone(MEETING_ID, "13800000002").orElseThrow().getId()
        );
    }

    private List<AssignmentOutcome> runConcurrently(ThrowingAction firstAction,
                                                    ThrowingAction secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AssignmentOutcome> first = executor.submit(() -> invokeAfterSignal(ready, start, firstAction));
            Future<AssignmentOutcome> second = executor.submit(() -> invokeAfterSignal(ready, start, secondAction));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private AssignmentOutcome invokeAfterSignal(CountDownLatch ready,
                                                CountDownLatch start,
                                                ThrowingAction action) throws Exception {
        ready.countDown();
        start.await();
        try {
            action.run();
            return new AssignmentOutcome(true, null);
        } catch (BusinessException ex) {
            return new AssignmentOutcome(false, ex.getMessage());
        }
    }

    private void assertOneCapacityWinner(List<AssignmentOutcome> outcomes) {
        assertThat(outcomes).extracting(AssignmentOutcome::success)
                .containsExactlyInAnyOrder(true, false);
        assertThat(outcomes).filteredOn(outcome -> !outcome.success())
                .singleElement()
                .extracting(AssignmentOutcome::message)
                .asString()
                .contains("上限");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }

    private record AssignmentOutcome(boolean success, String message) {
    }
}
