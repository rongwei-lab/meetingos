package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.meetingos.invitation.InvitationApiModels.*;

@Service
public class InvitationAnalyticsService {
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
        if (invitationId != null) {
            invitationRepository.findByIdAndMeetingId(invitationId, meetingId)
                    .orElseThrow(() -> BusinessException.notFound("邀请函不存在"));
        }
        LocalDateTime from = fromDate == null
                ? (meeting.getCreatedAt() == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : meeting.getCreatedAt())
                : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? LocalDateTime.now() : toDate.atTime(LocalTime.MAX);
        if (to.isBefore(from)) {
            throw BusinessException.badRequest("统计结束日期不能早于开始日期");
        }

        List<InvitationEvent> events = eventRepository
                .findByMeetingIdAndOccurredAtBetweenOrderByOccurredAtAsc(meetingId, from, to).stream()
                .filter(event -> invitationId == null || event.getInvitation().getId().equals(invitationId))
                .toList();
        List<RegistrationAttribution> attributions = attributionRepository
                .findByMeetingIdAndAttributedAtBetweenOrderByAttributedAtAsc(meetingId, from, to).stream()
                .filter(attr -> invitationId == null
                        || (attr.getInvitation() != null && attr.getInvitation().getId().equals(invitationId)))
                .toList();

        long pageViews = events.stream().filter(event -> event.getEventType() == InvitationEventType.PAGE_VIEW).count();
        long ctaClicks = events.stream().filter(event -> event.getEventType() == InvitationEventType.CTA_CLICK).count();
        long uniqueVisitors = distinctVisitors(events, InvitationEventType.PAGE_VIEW);
        long uniqueClickVisitors = distinctVisitors(events, InvitationEventType.CTA_CLICK);
        long attributed = countStatus(attributions, RegistrationAttributionStatus.ATTRIBUTED, null);
        long activeAttributed = countStatus(attributions, RegistrationAttributionStatus.ATTRIBUTED, true);
        long direct = countStatus(attributions, RegistrationAttributionStatus.DIRECT_UNATTRIBUTED, null);
        long invalid = countStatus(attributions, RegistrationAttributionStatus.INVALID_TOKEN, null);
        long legacy = countStatus(attributions, RegistrationAttributionStatus.LEGACY_UNATTRIBUTED, null);
        long active = attributions.stream().filter(RegistrationAttribution::isActive).count();

        return new InvitationAnalyticsResponse(
                pageViews, uniqueVisitors, ctaClicks, uniqueClickVisitors, attributed, activeAttributed,
                direct, invalid, legacy, active,
                ratio(uniqueClickVisitors, uniqueVisitors), ratio(attributed, uniqueClickVisitors),
                daily(events, attributions), channels(meetingId, invitationId, events, attributions)
        );
    }

    @Transactional(readOnly = true)
    public List<AttributionResponse> attributions(Long meetingId, Long invitationId) {
        invitationRepository.findByIdAndMeetingId(invitationId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("邀请函不存在"));
        return attributionRepository.findByInvitationIdOrderByAttributedAtDesc(invitationId).stream()
                .map(attribution -> new AttributionResponse(
                        attribution.getId(), attribution.getAttributionStatus(), attribution.getTduckDataId(),
                        attribution.getAttendee() == null ? null : attribution.getAttendee().getId(),
                        attribution.getShareLink() == null ? null : attribution.getShareLink().getId(),
                        attribution.getShareLink() == null ? null : attribution.getShareLink().getChannelName(),
                        attribution.isActive(), attribution.getAttributedAt()
                ))
                .toList();
    }

    private long distinctVisitors(List<InvitationEvent> events, InvitationEventType type) {
        return events.stream().filter(event -> event.getEventType() == type)
                .map(InvitationEvent::getVisitorHash).distinct().count();
    }

    private long countStatus(List<RegistrationAttribution> values,
                             RegistrationAttributionStatus status,
                             Boolean active) {
        return values.stream().filter(value -> value.getAttributionStatus() == status)
                .filter(value -> active == null || value.isActive() == active)
                .count();
    }

    private List<DailyMetricResponse> daily(List<InvitationEvent> events,
                                            List<RegistrationAttribution> attributions) {
        Map<LocalDate, MutableDailyMetric> metrics = new LinkedHashMap<>();
        for (InvitationEvent event : events) {
            MutableDailyMetric metric = metrics.computeIfAbsent(event.getOccurredAt().toLocalDate(), ignored -> new MutableDailyMetric());
            if (event.getEventType() == InvitationEventType.PAGE_VIEW) {
                metric.pageViews++;
                metric.visitors.add(event.getVisitorHash());
            } else if (event.getEventType() == InvitationEventType.CTA_CLICK) {
                metric.ctaClicks++;
            }
        }
        for (RegistrationAttribution attribution : attributions) {
            if (attribution.getAttributionStatus() == RegistrationAttributionStatus.ATTRIBUTED) {
                metrics.computeIfAbsent(attribution.getAttributedAt().toLocalDate(), ignored -> new MutableDailyMetric())
                        .attributedRegistrations++;
            }
        }
        return metrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyMetricResponse(entry.getKey(), entry.getValue().pageViews,
                        entry.getValue().visitors.size(), entry.getValue().ctaClicks,
                        entry.getValue().attributedRegistrations))
                .toList();
    }

    private List<ChannelMetricResponse> channels(Long meetingId,
                                                 Long invitationId,
                                                 List<InvitationEvent> events,
                                                 List<RegistrationAttribution> attributions) {
        List<InvitationShareLink> links = shareLinkRepository.findByMeetingIdOrderByCreatedAtAsc(meetingId).stream()
                .filter(link -> invitationId == null || link.getInvitation().getId().equals(invitationId))
                .toList();
        Map<Long, MutableChannelMetric> metrics = new HashMap<>();
        for (InvitationShareLink link : links) {
            metrics.put(link.getId(), new MutableChannelMetric(link));
        }
        for (InvitationEvent event : events) {
            MutableChannelMetric metric = metrics.get(event.getShareLink().getId());
            if (metric == null) {
                continue;
            }
            if (event.getEventType() == InvitationEventType.PAGE_VIEW) {
                metric.pageViews++;
                metric.visitors.add(event.getVisitorHash());
            } else {
                metric.ctaClicks++;
            }
        }
        for (RegistrationAttribution attribution : attributions) {
            if (attribution.getAttributionStatus() != RegistrationAttributionStatus.ATTRIBUTED
                    || attribution.getShareLink() == null) {
                continue;
            }
            MutableChannelMetric metric = metrics.get(attribution.getShareLink().getId());
            if (metric != null) {
                metric.attributedRegistrations++;
                if (attribution.isActive()) {
                    metric.activeAttributedRegistrations++;
                }
            }
        }
        return metrics.values().stream()
                .sorted(Comparator.comparing(value -> value.link.getCreatedAt()))
                .map(value -> new ChannelMetricResponse(
                        value.link.getId(), value.link.getInvitation().getId(), value.link.getInvitation().getName(),
                        value.link.getChannelType(), value.link.getChannelName(), value.pageViews,
                        value.visitors.size(), value.ctaClicks, value.attributedRegistrations,
                        value.activeAttributedRegistrations
                ))
                .toList();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 10000.0;
    }

    private static final class MutableDailyMetric {
        private long pageViews;
        private long ctaClicks;
        private long attributedRegistrations;
        private final Set<String> visitors = new HashSet<>();
    }

    private static final class MutableChannelMetric {
        private final InvitationShareLink link;
        private long pageViews;
        private long ctaClicks;
        private long attributedRegistrations;
        private long activeAttributedRegistrations;
        private final Set<String> visitors = new HashSet<>();

        private MutableChannelMetric(InvitationShareLink link) {
            this.link = link;
        }
    }
}
