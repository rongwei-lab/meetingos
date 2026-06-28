package com.meetingos.notification;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final MeetingRepository meetingRepository;
    private final NotificationRecordRepository notificationRecordRepository;

    public NotificationService(MeetingRepository meetingRepository,
                               NotificationRecordRepository notificationRecordRepository) {
        this.meetingRepository = meetingRepository;
        this.notificationRecordRepository = notificationRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationRecord> list(Long meetingId) {
        if (meetingId == null) {
            return notificationRecordRepository.findAllByOrderByCreatedAtDesc();
        }
        return notificationRecordRepository.findByMeeting_IdOrderByCreatedAtDesc(meetingId);
    }

    @Transactional
    public NotificationRecord create(NotificationRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        NotificationRecord record = new NotificationRecord();
        record.setMeeting(meeting);
        record.setTitle(request.title());
        record.setContent(request.content());
        record.setChannel(request.channel());
        record.setScope(request.scope());
        record.setStatus(request.status() == null || request.status().isBlank() ? "DRAFT" : request.status());
        record.setExpectedCount(request.expectedCount());
        return notificationRecordRepository.save(record);
    }
}
