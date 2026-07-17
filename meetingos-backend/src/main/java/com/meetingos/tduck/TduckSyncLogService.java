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

    private static final int FORM_KEY_MAX_LENGTH = 120;
    private static final int EVENT_ID_MAX_LENGTH = 120;
    private static final int EVENT_TYPE_MAX_LENGTH = 80;
    private static final int DATA_ID_MAX_LENGTH = 120;
    private static final int MESSAGE_MAX_LENGTH = 500;

    private final TduckSyncLogRepository syncLogRepository;

    public TduckSyncLogService(TduckSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TduckSyncLog success(TduckWebhookRequest request, Meeting meeting, String eventId, Attendee attendee, String message) {
        TduckSyncLog log = baseLog(request, meeting, eventId);
        log.setStatus(SyncStatus.SUCCESS);
        // 仅成功事件占用幂等键；失败事件保留 eventId 便于排障，但不会阻止修复后的重试。
        log.setSuccessEventId(eventId);
        log.setMessage(truncate(message, MESSAGE_MAX_LENGTH));
        log.setAttendeeId(attendee.getId());
        return syncLogRepository.save(log);
    }

    /*
     * Webhook 主事务内失败已被转换为 FAILED 结果，直接加入当前事务即可；鉴权/解析失败发生在
     * 事务外时 REQUIRED 仍会自行开启事务。这样避免失败洪峰下外层事务占一个连接、日志再等第二个连接。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public TduckSyncLog failed(TduckWebhookRequest request, Meeting meeting, String message) {
        TduckSyncLog log = baseLog(request, meeting, request == null ? null : request.eventId());
        log.setStatus(SyncStatus.FAILED);
        log.setMessage(truncate(message, MESSAGE_MAX_LENGTH));
        return syncLogRepository.save(log);
    }

    private TduckSyncLog baseLog(TduckWebhookRequest request, Meeting meeting, String eventId) {
        TduckSyncLog log = new TduckSyncLog();
        log.setMeeting(meeting);
        log.setFormKey(truncate(firstNonBlank(request == null ? null : request.formKey(), "UNKNOWN"), FORM_KEY_MAX_LENGTH));
        log.setEventId(truncate(eventId, EVENT_ID_MAX_LENGTH));
        log.setEventType(truncate(firstNonBlank(request == null ? null : request.eventType(), "UNKNOWN"), EVENT_TYPE_MAX_LENGTH));
        log.setTduckDataId(truncate(request == null ? null : request.dataId(), DATA_ID_MAX_LENGTH));
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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
