package com.meetingos.integration;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeService;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewRequest;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.invitation.RegistrationAttribution;
import com.meetingos.invitation.RegistrationAttributionRepository;
import com.meetingos.invitation.RegistrationAttributionStatus;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.tduck.SyncStatus;
import com.meetingos.tduck.TduckService;
import com.meetingos.tduck.TduckSyncLog;
import com.meetingos.tduck.TduckSyncLogRepository;
import com.meetingos.tduck.TduckSyncLogService;
import com.meetingos.tduck.TduckTransactionRunner;
import com.meetingos.tduck.TduckWebhookRequest;
import com.meetingos.tduck.TduckWebhookResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestDataConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TduckTransactionIntegrationTest {

    @Autowired
    private TduckService tduckService;
    @Autowired
    private TduckSyncLogRepository syncLogRepository;
    @Autowired
    private AttendeeRepository attendeeRepository;
    @Autowired
    private AttendeeService attendeeService;
    @Autowired
    private RegistrationAttributionRepository attributionRepository;
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TduckTransactionRunner transactionRunner;
    @Autowired
    private TduckSyncLogService syncLogService;

    @Test
    void outerRollbackDoesNotRollbackIndependentWebhookTransaction() {
        String eventId = "event-rollback-transaction";
        String phone = "13900009001";
        String rawBody = payload(eventId, "data-rollback-1", phone);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                TduckWebhookResult result = tduckService.handleWebhook(rawBody, null, "test-webhook-secret");
                assertThat(result.status()).isEqualTo(SyncStatus.SUCCESS);
                throw new RollbackProbeException();
            })).isInstanceOf(RollbackProbeException.class);

            assertThat(syncLogRepository.findAllByEventId(eventId)).hasSize(1);
            assertThat(attendeeRepository.findByMeetingIdAndPhone(1L, phone)).isPresent();
        } finally {
            cleanup(eventId, phone);
        }
    }

    @Test
    void independentSyncRollbackRemovesAttendeeAndSuccessLogTogether() {
        String eventId = "event-inner-rollback";
        String phone = "13900009005";
        Meeting meeting = meetingRepository.findByCode("NDEF2026").orElseThrow();
        TduckWebhookRequest request = new TduckWebhookRequest(
                eventId,
                "form_data_add",
                "ndef2026",
                "data-inner-rollback",
                Map.of("name", "事务回滚测试", "phone", phone),
                Map.of("source", "test")
        );

        assertThatThrownBy(() -> transactionRunner.requiresNew(() -> {
            Attendee saved = attendeeRepository.saveAndFlush(
                    attendee(meeting, phone, "INNER-ROLLBACK-1", "data-inner-rollback")
            );
            syncLogService.success(request, meeting, eventId, saved, "测试事务回滚");
            throw new RollbackProbeException();
        })).isInstanceOf(RollbackProbeException.class);

        assertThat(syncLogRepository.findAllByEventId(eventId)).isEmpty();
        assertThat(attendeeRepository.findByMeetingIdAndPhone(meeting.getId(), phone)).isEmpty();
    }

    @Test
    void concurrentSameEventProducesOneSuccessAndOneDuplicate() throws Exception {
        String eventId = "event-concurrent-idempotency";
        String dataId = "data-concurrent-1";
        String phone = "13900009002";
        String rawBody = payload(eventId, dataId, phone);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TduckWebhookResult> first = executor.submit(() -> invokeAfter(start, rawBody));
            Future<TduckWebhookResult> second = executor.submit(() -> invokeAfter(start, rawBody));
            start.countDown();

            List<SyncStatus> statuses = List.of(first.get().status(), second.get().status());
            assertThat(statuses).containsExactlyInAnyOrder(SyncStatus.SUCCESS, SyncStatus.DUPLICATE_IGNORED);
        } finally {
            executor.shutdownNow();
            cleanup(eventId, phone);
        }
    }

    @Test
    void databaseRejectsDuplicateMeetingDataIdAndSuccessEventKey() {
        Meeting meeting = meetingRepository.findByCode("NDEF2026").orElseThrow();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Attendee first = transaction.execute(status -> attendeeRepository.saveAndFlush(
                attendee(meeting, "13900009003", "DB-UNIQUE-1", "data-db-unique")
        ));

        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> attendeeRepository.saveAndFlush(
                    attendee(meeting, "13900009004", "DB-UNIQUE-2", "data-db-unique")
            ))).isInstanceOf(DataIntegrityViolationException.class);

            TduckSyncLog firstLog = transaction.execute(status -> syncLogRepository.saveAndFlush(
                    successLog(meeting, "event-db-unique", "event-db-unique")
            ));
            try {
                assertThatThrownBy(() -> transaction.executeWithoutResult(status -> syncLogRepository.saveAndFlush(
                        successLog(meeting, "event-db-unique", "event-db-unique")
                ))).isInstanceOf(DataIntegrityViolationException.class);
            } finally {
                transaction.executeWithoutResult(status -> syncLogRepository.deleteById(firstLog.getId()));
            }
        } finally {
            transaction.executeWithoutResult(status -> attendeeRepository.deleteById(first.getId()));
        }
    }

    @Test
    void attendeeReviewAndAttributionActiveRollbackOrCommitTogether() {
        String phone = "13900009006";
        String dataId = "data-admin-review-atomic";
        Meeting meeting = meetingRepository.findByCode("NDEF2026").orElseThrow();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long attendeeId = transaction.execute(status -> {
            Attendee saved = attendeeRepository.saveAndFlush(
                    attendee(meeting, phone, "ADMIN-REVIEW-ATOMIC", dataId)
            );
            RegistrationAttribution attribution = new RegistrationAttribution();
            attribution.setMeeting(meeting);
            attribution.setAttendee(saved);
            attribution.setAttributionStatus(RegistrationAttributionStatus.DIRECT_UNATTRIBUTED);
            attribution.setFormKey("ndef2026");
            attribution.setTduckDataId(dataId);
            attribution.setActive(true);
            attribution.setAttributedAt(LocalDateTime.now());
            attributionRepository.saveAndFlush(attribution);
            return saved.getId();
        });

        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                attendeeService.review(
                        attendeeId,
                        new ReviewRequest(ReviewStatus.REJECTED, "事务回滚验证", null, null)
                );
                throw new RollbackProbeException();
            })).isInstanceOf(RollbackProbeException.class);

            transaction.executeWithoutResult(status -> {
                assertThat(attendeeRepository.findById(attendeeId).orElseThrow().getReviewStatus())
                        .isEqualTo(ReviewStatus.APPROVED);
                assertThat(attributionRepository.findForUpdate(meeting.getId(), dataId).orElseThrow().isActive())
                        .isTrue();
            });

            attendeeService.review(
                    attendeeId,
                    new ReviewRequest(ReviewStatus.REJECTED, "最终拒绝", null, null)
            );
            transaction.executeWithoutResult(status -> {
                assertThat(attendeeRepository.findById(attendeeId).orElseThrow().getReviewStatus())
                        .isEqualTo(ReviewStatus.REJECTED);
                assertThat(attributionRepository.findForUpdate(meeting.getId(), dataId).orElseThrow().isActive())
                        .isFalse();
            });
        } finally {
            cleanup("event-admin-review-atomic", phone);
        }
    }

    private TduckWebhookResult invokeAfter(CountDownLatch start, String rawBody) throws InterruptedException {
        start.await();
        return tduckService.handleWebhook(rawBody, null, "test-webhook-secret");
    }

    private String payload(String eventId, String dataId, String phone) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "form_data_add",
                  "formKey": "ndef2026",
                  "dataId": "%s",
                  "name": "事务测试参会人",
                  "phone": "%s"
                }
                """.formatted(eventId, dataId, phone);
    }

    private Attendee attendee(Meeting meeting, String phone, String attendeeCode, String dataId) {
        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        attendee.setName("唯一约束测试");
        attendee.setPhone(phone);
        attendee.setAttendeeType(AttendeeType.NORMAL);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        attendee.setAttendeeCode(attendeeCode);
        attendee.setTduckDataId(dataId);
        return attendee;
    }

    private TduckSyncLog successLog(Meeting meeting, String eventId, String successEventId) {
        TduckSyncLog log = new TduckSyncLog();
        log.setMeeting(meeting);
        log.setFormKey("ndef2026");
        log.setEventId(eventId);
        log.setSuccessEventId(successEventId);
        log.setEventType("form_data_add");
        log.setStatus(SyncStatus.SUCCESS);
        log.setMessage("测试");
        log.setRawPayload(Map.of());
        return log;
    }

    private void cleanup(String eventId, String phone) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            syncLogRepository.deleteAll(syncLogRepository.findAllByEventId(eventId));
            attendeeRepository.findByMeetingIdAndPhone(1L, phone).ifPresent(attendee -> {
                if (attendee.getTduckDataId() != null) {
                    attributionRepository.findForUpdate(1L, attendee.getTduckDataId())
                            .ifPresent(attributionRepository::delete);
                    attributionRepository.flush();
                }
                attendeeRepository.delete(attendee);
            });
        });
    }

    private static class RollbackProbeException extends RuntimeException {
    }
}
