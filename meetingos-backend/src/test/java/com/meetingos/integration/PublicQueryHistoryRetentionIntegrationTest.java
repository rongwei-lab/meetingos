package com.meetingos.integration;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.publicapi.PublicQueryHistory;
import com.meetingos.publicapi.PublicQueryHistoryRepository;
import com.meetingos.publicapi.PublicQueryStatus;
import com.meetingos.publicapi.PublicQueryType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestDataConfig.class)
class PublicQueryHistoryRetentionIntegrationTest {

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private PublicQueryHistoryRepository historyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void keepsLatestBusinessFactPerAttendeeAndDeletesOnlyExpiredDetails() {
        Attendee attendee = attendeeRepository.findByMeetingIdAndPhone(1L, "13800000001").orElseThrow();
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);

        PublicQueryHistory olderSeat = save(attendee, PublicQueryType.SEAT);
        PublicQueryHistory latestSeat = save(attendee, PublicQueryType.SEAT);
        PublicQueryHistory onlyRoom = save(attendee, PublicQueryType.ROOM);
        PublicQueryHistory oldCheckin = save(attendee, PublicQueryType.CHECKIN);
        PublicQueryHistory unrecognized = save(null, PublicQueryType.SEAT, attendee);
        PublicQueryHistory recentDinner = save(attendee, PublicQueryType.DINNER);

        setCreatedAt(olderSeat, now.minusDays(400));
        setCreatedAt(latestSeat, now.minusDays(300));
        setCreatedAt(onlyRoom, now.minusDays(350));
        setCreatedAt(oldCheckin, now.minusDays(250));
        setCreatedAt(unrecognized, now.minusDays(240));
        setCreatedAt(recentDinner, now.minusDays(10));

        List<Long> deletable = historyRepository.findDeletableExpiredIds(
                now.minusDays(180),
                PageRequest.of(0, 100)
        );

        assertThat(deletable).containsExactlyInAnyOrder(
                olderSeat.getId(),
                oldCheckin.getId(),
                unrecognized.getId()
        );
        assertThat(deletable).doesNotContain(
                latestSeat.getId(),
                onlyRoom.getId(),
                recentDinner.getId()
        );
    }

    private PublicQueryHistory save(Attendee attendee, PublicQueryType queryType) {
        return save(attendee, queryType, attendee);
    }

    private PublicQueryHistory save(Attendee attendee,
                                    PublicQueryType queryType,
                                    Attendee meetingOwner) {
        PublicQueryHistory history = new PublicQueryHistory();
        history.setMeeting(meetingOwner.getMeeting());
        history.setAttendee(attendee);
        history.setQueryType(queryType);
        history.setQueryStatus(PublicQueryStatus.SUCCESS);
        history.setQueryValues(Map.of("PHONE", meetingOwner.getPhone()));
        return historyRepository.saveAndFlush(history);
    }

    private void setCreatedAt(PublicQueryHistory history, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update public_query_history set created_at = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                history.getId()
        );
    }
}
