package com.meetingos.invitation;

import com.meetingos.meeting.Meeting;
import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvitationEventRetentionService {
    private final EntityManager entityManager;
    private final InvitationEventRepository eventRepository;
    private final int retentionDays;

    public InvitationEventRetentionService(EntityManager entityManager,
                                           InvitationEventRepository eventRepository,
                                           InvitationProperties properties) {
        this.entityManager = entityManager;
        this.eventRepository = eventRepository;
        int retentionDays = properties.eventRetentionDays();
        if (retentionDays < 30 || retentionDays > 3650) {
            throw new IllegalStateException("邀请事件保留天数必须在 30 至 3650 之间");
        }
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${meetingos.invitation.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public int cleanupExpiredEvents() {
        LocalDateTime endedBefore = LocalDateTime.now().minusDays(retentionDays);
        List<Meeting> meetings = entityManager.createQuery(
                        "select m from Meeting m where m.endTime is not null and m.endTime <= :endedBefore", Meeting.class)
                .setParameter("endedBefore", endedBefore)
                .getResultList();
        int deleted = 0;
        for (Meeting meeting : meetings) {
            // 清理资格只由会议结束时间决定。达到保留截止日后必须删除该会议全部事件，
            // 否则截止后才到达的客户端重试事件会绕过 occurredAt 过滤并永久残留。
            deleted += eventRepository.deleteAllByMeetingId(meeting.getId());
        }
        return deleted;
    }
}
