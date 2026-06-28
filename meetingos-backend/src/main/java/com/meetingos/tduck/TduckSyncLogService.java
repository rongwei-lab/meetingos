package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.meeting.Meeting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TduckSyncLogService {

    private final TduckSyncLogRepository syncLogRepository;

    public TduckSyncLogService(TduckSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TduckSyncLog success(TduckWebhookRequest request, Meeting meeting, String eventId, Attendee attendee, String message) {
        TduckSyncLog log = baseLog(request, meeting, eventId);
        log.setStatus(SyncStatus.SUCCESS);
        log.setMessage(message);
        log.setAttendeeId(attendee.getId());
        return syncLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TduckSyncLog failed(TduckWebhookRequest request, Meeting meeting, String message) {
        TduckSyncLog log = baseLog(request, meeting, request == null ? null : request.eventId());
        log.setStatus(SyncStatus.FAILED);
        log.setMessage(message);
        return syncLogRepository.save(log);
    }

    private TduckSyncLog baseLog(TduckWebhookRequest request, Meeting meeting, String eventId) {
        TduckSyncLog log = new TduckSyncLog();
        log.setMeeting(meeting);
        log.setFormKey(firstNonBlank(request == null ? null : request.formKey(), "UNKNOWN"));
        log.setEventId(eventId);
        log.setEventType(firstNonBlank(request == null ? null : request.eventType(), "UNKNOWN"));
        log.setTduckDataId(request == null ? null : request.dataId());
        log.setRawPayload(toPayload(request));
        return log;
    }

    private Map<String, Object> toPayload(TduckWebhookRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request != null) {
            payload.put("eventId", request.eventId());
            payload.put("eventType", request.eventType());
            payload.put("formKey", request.formKey());
            payload.put("dataId", request.dataId());
            payload.put("data", request.data());
            payload.put("raw", request.raw());
        }
        return payload;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
