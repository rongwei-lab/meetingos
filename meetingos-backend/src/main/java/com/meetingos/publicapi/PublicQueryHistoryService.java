package com.meetingos.publicapi;

import com.meetingos.attendee.Attendee;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicQueryHistoryService {

    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_IP_LENGTH = 45;

    private final PublicQueryHistoryRepository historyRepository;
    private final MeetingRepository meetingRepository;

    public PublicQueryHistoryService(PublicQueryHistoryRepository historyRepository,
                                     MeetingRepository meetingRepository) {
        this.historyRepository = historyRepository;
        this.meetingRepository = meetingRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Meeting meeting,
                              PublicQueryType queryType,
                              Map<PublicQueryField, String> values,
                              Attendee attendee,
                              String resultSummary,
                              String clientIp,
                              String userAgent) {
        save(meeting, queryType, PublicQueryStatus.SUCCESS, values, attendee, resultSummary, null, clientIp, userAgent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Meeting meeting,
                              PublicQueryType queryType,
                              Map<PublicQueryField, String> values,
                              Attendee attendee,
                              String errorMessage,
                              String clientIp,
                              String userAgent) {
        save(meeting, queryType, PublicQueryStatus.FAILED, values, attendee, null, errorMessage, clientIp, userAgent);
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicQueryHistoryResponse> history(Long meetingId,
                                                            PublicQueryType queryType,
                                                            PublicQueryStatus queryStatus,
                                                            String keyword,
                                                            int page,
                                                            int size) {
        ensureMeetingExists(meetingId);
        var pageRequest = PageRequests.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        var result = historyRepository.search(meetingId, queryType, queryStatus, blankToNull(keyword), pageRequest)
                .map(this::toResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public List<PublicQuerySummaryResponse> summary(Long meetingId) {
        ensureMeetingExists(meetingId);
        /*
         * 汇总页只需要“每个参会人/失败原因的一行统计”。旧逻辑会把全部历史投影到 JVM 再分组，
         * 日志量大时容易拖慢后台页面。这里让数据库用窗口函数完成分组计数和最近一条结果选择，
         * Java 层只做脱敏和类型转换，接口返回结构保持不变。
         */
        return historyRepository.findSummaryAggregatesByMeetingId(meetingId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    private PublicQuerySummaryResponse toSummaryResponse(Object[] row) {
        String groupType = stringAt(row, 1);
        String attendeePhone = "ATTENDEE".equals(groupType) ? SensitiveDataMasker.maskPhone(stringAt(row, 4)) : null;
        return new PublicQuerySummaryResponse(
                stringAt(row, 0),
                groupType,
                longAt(row, 2),
                stringAt(row, 3),
                attendeePhone,
                stringAt(row, 5),
                numberAt(row, 6),
                numberAt(row, 7),
                numberAt(row, 8),
                dateTimeAt(row, 9),
                stringAt(row, 10)
        );
    }

    private void save(Meeting meeting,
                      PublicQueryType queryType,
                      PublicQueryStatus queryStatus,
                      Map<PublicQueryField, String> values,
                      Attendee attendee,
                      String resultSummary,
                      String errorMessage,
                      String clientIp,
                      String userAgent) {
        if (meeting == null || meeting.getId() == null) {
            return;
        }
        PublicQueryHistory history = new PublicQueryHistory();
        history.setMeeting(meeting);
        history.setQueryType(queryType);
        history.setQueryStatus(queryStatus);
        history.setQueryValues(toQueryValues(values));
        history.setQueryText(truncate(toQueryText(values), MAX_TEXT_LENGTH));
        history.setAttendee(attendee);
        if (attendee != null) {
            history.setAttendeeName(attendee.getName());
            history.setAttendeePhone(attendee.getPhone());
        }
        history.setResultSummary(truncate(resultSummary, MAX_MESSAGE_LENGTH));
        history.setErrorMessage(truncate(errorMessage, MAX_MESSAGE_LENGTH));
        history.setClientIp(truncate(clientIp, MAX_IP_LENGTH));
        history.setUserAgent(truncate(userAgent, MAX_MESSAGE_LENGTH));
        historyRepository.save(history);
    }

    private PublicQueryHistoryResponse toResponse(PublicQueryHistory history) {
        Attendee attendee = history.getAttendee();
        return new PublicQueryHistoryResponse(
                history.getId(),
                history.getQueryType(),
                history.getQueryStatus(),
                SensitiveDataMasker.maskMap(history.getQueryValues()),
                history.getErrorMessage(),
                attendee == null ? null : attendee.getId(),
                firstNonBlank(history.getAttendeeName(), attendee == null ? null : attendee.getName()),
                SensitiveDataMasker.maskPhone(firstNonBlank(history.getAttendeePhone(), attendee == null ? null : attendee.getPhone())),
                history.getResultSummary(),
                maskIp(history.getClientIp()),
                maskUserAgent(history.getUserAgent()),
                history.getCreatedAt()
        );
    }

    private Map<String, Object> toQueryValues(Map<PublicQueryField, String> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey().name(), entry.getValue()));
        return result;
    }

    private String toQueryText(Map<PublicQueryField, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> parts.add(entry.getKey().label() + "=" + entry.getValue()));
        return String.join("; ", parts);
    }

    private void ensureMeetingExists(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw com.meetingos.common.BusinessException.notFound("会议不存在");
        }
    }

    private String blankToNull(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isBlank() ? null : text;
    }

    private String firstNonBlank(String primary, String fallback) {
        return blankToNull(primary) == null ? fallback : primary;
    }

    private String stringAt(Object[] row, int index) {
        Object value = valueAt(row, index);
        return value == null ? null : String.valueOf(value);
    }

    private Long longAt(Object[] row, int index) {
        Object value = valueAt(row, index);
        return value instanceof Number number ? number.longValue() : null;
    }

    private long numberAt(Object[] row, int index) {
        Object value = valueAt(row, index);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private LocalDateTime dateTimeAt(Object[] row, int index) {
        Object value = valueAt(row, index);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private Object valueAt(Object[] row, int index) {
        return row == null || index >= row.length ? null : row[index];
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String maskIp(String clientIp) {
        String value = blankToNull(clientIp);
        if (value == null) {
            return null;
        }
        int lastDot = value.lastIndexOf('.');
        if (lastDot > 0) {
            return value.substring(0, lastDot) + ".***";
        }
        int lastColon = value.lastIndexOf(':');
        return lastColon > 0 ? value.substring(0, lastColon) + ":****" : "***";
    }

    private String maskUserAgent(String userAgent) {
        String value = blankToNull(userAgent);
        if (value == null) {
            return null;
        }
        /*
         * User-Agent 常能和公开查询时间、输入组合成稳定指纹。
         * 管理端只展示浏览器大类，原始值留在服务端审计库中。
         */
        if (value.contains("Firefox")) {
            return "Firefox";
        }
        if (value.contains("Edg/")) {
            return "Edge";
        }
        if (value.contains("Chrome")) {
            return "Chrome";
        }
        if (value.contains("Safari")) {
            return "Safari";
        }
        return "Other";
    }

}
