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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
        Map<String, SummaryAccumulator> groups = new LinkedHashMap<>();
        for (PublicQueryHistory history : historyRepository.findAllByMeetingIdForSummary(meetingId)) {
            String groupKey = history.getAttendee() != null
                    ? "ATTENDEE:" + history.getAttendee().getId()
                    : "UNRECOGNIZED:" + normalizeFailureReason(history.getErrorMessage());
            groups.computeIfAbsent(groupKey, key -> SummaryAccumulator.from(groupKey, history)).accept(history);
        }
        return groups.values().stream()
                .map(SummaryAccumulator::toResponse)
                .sorted(Comparator.comparing(PublicQuerySummaryResponse::latestQueryAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
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

    private String normalizeFailureReason(String errorMessage) {
        String reason = blankToNull(errorMessage);
        return reason == null ? "未知失败" : reason;
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

    private static final class SummaryAccumulator {
        private final String groupKey;
        private final String groupType;
        private final Long attendeeId;
        private final String attendeeName;
        private final String attendeePhone;
        private final String failureReason;
        private long totalCount;
        private long successCount;
        private long failedCount;
        private LocalDateTime latestQueryAt;
        private String latestResult;

        private SummaryAccumulator(String groupKey,
                                   String groupType,
                                   Long attendeeId,
                                   String attendeeName,
                                   String attendeePhone,
                                   String failureReason) {
            this.groupKey = groupKey;
            this.groupType = groupType;
            this.attendeeId = attendeeId;
            this.attendeeName = attendeeName;
            this.attendeePhone = attendeePhone;
            this.failureReason = failureReason;
        }

        static SummaryAccumulator from(String groupKey, PublicQueryHistory history) {
            Attendee attendee = history.getAttendee();
            if (attendee != null) {
                return new SummaryAccumulator(
                        groupKey,
                        "ATTENDEE",
                        attendee.getId(),
                        firstNonBlankStatic(history.getAttendeeName(), attendee.getName()),
                        SensitiveDataMasker.maskPhone(firstNonBlankStatic(history.getAttendeePhone(), attendee.getPhone())),
                        null
                );
            }
            return new SummaryAccumulator(
                    groupKey,
                    "UNRECOGNIZED",
                    null,
                    null,
                    null,
                    firstNonBlankStatic(history.getErrorMessage(), "未知失败")
            );
        }

        void accept(PublicQueryHistory history) {
            totalCount += 1;
            if (history.getQueryStatus() == PublicQueryStatus.SUCCESS) {
                successCount += 1;
            } else {
                failedCount += 1;
            }
            if (latestQueryAt == null || history.getCreatedAt().isAfter(latestQueryAt)) {
                latestQueryAt = history.getCreatedAt();
                latestResult = history.getQueryStatus() == PublicQueryStatus.SUCCESS
                        ? history.getResultSummary()
                        : history.getErrorMessage();
            }
        }

        PublicQuerySummaryResponse toResponse() {
            return new PublicQuerySummaryResponse(
                    groupKey,
                    groupType,
                    attendeeId,
                    attendeeName,
                    attendeePhone,
                    failureReason,
                    totalCount,
                    successCount,
                    failedCount,
                    latestQueryAt,
                    latestResult
            );
        }

        private static String firstNonBlankStatic(String primary, String fallback) {
            return primary == null || primary.isBlank() ? fallback : primary;
        }
    }
}
