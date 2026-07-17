package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.meetingos.invitation.InvitationApiModels.*;

@Service
public class InvitationAnalyticsService {
    private static final int ATTRIBUTION_DETAIL_LIMIT = 2_000;

    private final MeetingRepository meetingRepository;
    private final MeetingInvitationRepository invitationRepository;
    private final InvitationShareLinkRepository shareLinkRepository;
    private final InvitationEventRepository eventRepository;
    private final RegistrationAttributionRepository attributionRepository;

    public InvitationAnalyticsService(MeetingRepository meetingRepository,
                                      MeetingInvitationRepository invitationRepository,
                                      InvitationShareLinkRepository shareLinkRepository,
                                      InvitationEventRepository eventRepository,
                                      RegistrationAttributionRepository attributionRepository) {
        this.meetingRepository = meetingRepository;
        this.invitationRepository = invitationRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.eventRepository = eventRepository;
        this.attributionRepository = attributionRepository;
    }

    @Transactional(readOnly = true)
    public InvitationAnalyticsResponse analytics(Long meetingId,
                                                  Long invitationId,
                                                  LocalDate fromDate,
                                                  LocalDate toDate) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        ensureInvitationExists(meetingId, invitationId);
        LocalDateTime from = fromDate == null
                ? (meeting.getCreatedAt() == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : meeting.getCreatedAt())
                : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? LocalDateTime.now() : toDate.atTime(LocalTime.MAX);
        if (to.isBefore(from)) {
            throw BusinessException.badRequest("统计结束日期不能早于开始日期");
        }

        Object[] eventTotals = firstRow(eventRepository.summarize(
                meetingId, invitationId, from, to, InvitationEventType.PAGE_VIEW, InvitationEventType.CTA_CLICK
        ), 4);
        Object[] attributionTotals = firstRow(attributionRepository.summarize(
                meetingId, invitationId, from, to,
                RegistrationAttributionStatus.ATTRIBUTED,
                RegistrationAttributionStatus.DIRECT_UNATTRIBUTED,
                RegistrationAttributionStatus.INVALID_TOKEN,
                RegistrationAttributionStatus.LEGACY_UNATTRIBUTED
        ), 6);

        long pageViews = longAt(eventTotals, 0);
        long uniqueVisitors = longAt(eventTotals, 1);
        long ctaClicks = longAt(eventTotals, 2);
        long uniqueClickVisitors = longAt(eventTotals, 3);
        long attributed = longAt(attributionTotals, 0);
        return new InvitationAnalyticsResponse(
                pageViews,
                uniqueVisitors,
                ctaClicks,
                uniqueClickVisitors,
                attributed,
                longAt(attributionTotals, 1),
                longAt(attributionTotals, 2),
                longAt(attributionTotals, 3),
                longAt(attributionTotals, 4),
                longAt(attributionTotals, 5),
                ratio(uniqueClickVisitors, uniqueVisitors),
                ratio(attributed, uniqueClickVisitors),
                daily(meetingId, invitationId, from, to),
                channels(meetingId, invitationId, from, to)
        );
    }

    @Transactional(readOnly = true)
    public List<AttributionResponse> attributions(Long meetingId, Long invitationId) {
        return attributionPage(meetingId, invitationId).items();
    }

    @Transactional(readOnly = true)
    public AttributionPage attributionPage(Long meetingId, Long invitationId) {
        ensureInvitationExists(meetingId, invitationId);
        /*
         * 接口结构保持 List 不变，但只投影响应字段并设置硬上限。极端历史数据不会再连同关联实体
         * 全量进入持久化上下文；超过上限时优先返回最近记录。
         */
        List<AttributionResponse> items = attributionRepository.findResponseRowsByInvitationId(
                        invitationId, PageRequest.of(0, ATTRIBUTION_DETAIL_LIMIT)
                ).stream()
                .map(row -> new AttributionResponse(
                        longObjectAt(row, 0),
                        (RegistrationAttributionStatus) row[1],
                        stringAt(row, 2),
                        longObjectAt(row, 3),
                        longObjectAt(row, 4),
                        stringAt(row, 5),
                        booleanAt(row, 6),
                        dateTimeAt(row, 7)
                ))
                .toList();
        long total = Math.max(items.size(), attributionRepository.countByInvitationId(invitationId));
        return new AttributionPage(items, total, ATTRIBUTION_DETAIL_LIMIT, total > items.size());
    }

    public record AttributionPage(
            List<AttributionResponse> items,
            long total,
            int limit,
            boolean truncated
    ) {
    }

    private List<DailyMetricResponse> daily(Long meetingId,
                                            Long invitationId,
                                            LocalDateTime from,
                                            LocalDateTime to) {
        Map<LocalDate, MutableDailyMetric> metrics = new LinkedHashMap<>();
        for (Object[] row : eventRepository.summarizeDaily(
                meetingId, invitationId, from, to, InvitationEventType.PAGE_VIEW, InvitationEventType.CTA_CLICK
        )) {
            MutableDailyMetric metric = metrics.computeIfAbsent(dateAt(row, 0), ignored -> new MutableDailyMetric());
            metric.pageViews = longAt(row, 1);
            metric.uniqueVisitors = longAt(row, 2);
            metric.ctaClicks = longAt(row, 3);
        }
        for (Object[] row : attributionRepository.summarizeAttributedDaily(
                meetingId, invitationId, from, to, RegistrationAttributionStatus.ATTRIBUTED
        )) {
            metrics.computeIfAbsent(dateAt(row, 0), ignored -> new MutableDailyMetric())
                    .attributedRegistrations = longAt(row, 1);
        }
        return metrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyMetricResponse(
                        entry.getKey(), entry.getValue().pageViews, entry.getValue().uniqueVisitors,
                        entry.getValue().ctaClicks, entry.getValue().attributedRegistrations
                ))
                .toList();
    }

    private List<ChannelMetricResponse> channels(Long meetingId,
                                                 Long invitationId,
                                                 LocalDateTime from,
                                                 LocalDateTime to) {
        Map<Long, MutableChannelMetric> metrics = new LinkedHashMap<>();
        for (InvitationShareLink link : shareLinkRepository.findAnalyticsLinks(meetingId, invitationId)) {
            metrics.put(link.getId(), new MutableChannelMetric(link));
        }
        for (Object[] row : eventRepository.summarizeByChannel(
                meetingId, invitationId, from, to, InvitationEventType.PAGE_VIEW, InvitationEventType.CTA_CLICK
        )) {
            MutableChannelMetric metric = metrics.get(longObjectAt(row, 0));
            if (metric != null) {
                metric.pageViews = longAt(row, 1);
                metric.uniqueVisitors = longAt(row, 2);
                metric.ctaClicks = longAt(row, 3);
            }
        }
        for (Object[] row : attributionRepository.summarizeAttributedByChannel(
                meetingId, invitationId, from, to, RegistrationAttributionStatus.ATTRIBUTED
        )) {
            MutableChannelMetric metric = metrics.get(longObjectAt(row, 0));
            if (metric != null) {
                metric.attributedRegistrations = longAt(row, 1);
                metric.activeAttributedRegistrations = longAt(row, 2);
            }
        }
        return metrics.values().stream()
                .map(value -> new ChannelMetricResponse(
                        value.link.getId(), value.link.getInvitation().getId(), value.link.getInvitation().getName(),
                        value.link.getChannelType(), value.link.getChannelName(), value.pageViews,
                        value.uniqueVisitors, value.ctaClicks, value.attributedRegistrations,
                        value.activeAttributedRegistrations
                ))
                .toList();
    }

    private void ensureInvitationExists(Long meetingId, Long invitationId) {
        if (invitationId != null) {
            invitationRepository.findByIdAndMeetingId(invitationId, meetingId)
                    .orElseThrow(() -> BusinessException.notFound("邀请函不存在"));
        }
    }

    private Object[] firstRow(List<Object[]> rows, int size) {
        return rows.isEmpty() ? new Object[size] : rows.get(0);
    }

    private long longAt(Object[] row, int index) {
        return row[index] instanceof Number number ? number.longValue() : 0L;
    }

    private Long longObjectAt(Object[] row, int index) {
        return row[index] instanceof Number number ? number.longValue() : null;
    }

    private boolean booleanAt(Object[] row, int index) {
        Object value = row[index];
        return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
    }

    private String stringAt(Object[] row, int index) {
        return row[index] == null ? null : row[index].toString();
    }

    private LocalDate dateAt(Object[] row, int index) {
        Object value = row[index];
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime dateTimeAt(Object[] row, int index) {
        Object value = row[index];
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value == null ? null : LocalDateTime.parse(value.toString());
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 10000.0;
    }

    private static final class MutableDailyMetric {
        private long pageViews;
        private long uniqueVisitors;
        private long ctaClicks;
        private long attributedRegistrations;
    }

    private static final class MutableChannelMetric {
        private final InvitationShareLink link;
        private long pageViews;
        private long uniqueVisitors;
        private long ctaClicks;
        private long attributedRegistrations;
        private long activeAttributedRegistrations;

        private MutableChannelMetric(InvitationShareLink link) {
            this.link = link;
        }
    }
}
