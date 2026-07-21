package com.meetingos.invitation;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeAttributionStateSynchronizer;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class InvitationAttributionService {
    private final RegistrationAttributionRepository attributionRepository;
    private final InvitationEventRepository eventRepository;
    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final InvitationTrackingTokenService tokenService;
    private final AttendeeAttributionStateSynchronizer attributionStateSynchronizer;

    public InvitationAttributionService(RegistrationAttributionRepository attributionRepository,
                                        InvitationEventRepository eventRepository,
                                        MeetingRepository meetingRepository,
                                        AttendeeRepository attendeeRepository,
                                        InvitationTrackingTokenService tokenService,
                                        AttendeeAttributionStateSynchronizer attributionStateSynchronizer) {
        this.attributionRepository = attributionRepository;
        this.eventRepository = eventRepository;
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.tokenService = tokenService;
        this.attributionStateSynchronizer = attributionStateSynchronizer;
    }

    /**
     * TDuck 主事务完成后调用。无来源或坏令牌也会形成明确的未归因记录，但绝不因令牌问题抛错。
     * 同一 dataId 后续更新、撤回再提交都保留首次来源，只更新 active 和 attendee 关联。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attributeFirstSubmission(Long meetingId,
                                         Long attendeeId,
                                         String formKey,
                                         String tduckDataId,
                                         String extValue) {
        attributeFirstSubmissionInCurrentTransaction(meetingId, attendeeId, formKey, tduckDataId, extValue);
    }

    /**
     * 重复报名待处理记录必须和 tduck_submissions 一起提交。否则管理员在提交后、归因异步补写前
     * 采用新报名，会出现当前来源已经切换但该 dataId 永远没有归因记录的短暂竞态。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void preparePendingSubmissionAttribution(Long meetingId,
                                                    Long attendeeId,
                                                    String formKey,
                                                    String tduckDataId,
                                                    String extValue) {
        attributeFirstSubmissionInCurrentTransaction(meetingId, attendeeId, formKey, tduckDataId, extValue);
    }

    private void attributeFirstSubmissionInCurrentTransaction(Long meetingId,
                                                               Long attendeeId,
                                                               String formKey,
                                                               String tduckDataId,
                                                               String extValue) {
        String normalizedFormKey = normalize(formKey, 120);
        String normalizedDataId = normalize(tduckDataId, 120);
        if (meetingId == null || meetingId <= 0 || attendeeId == null || attendeeId <= 0
                || normalizedFormKey == null || normalizedDataId == null) {
            return;
        }

        /*
         * 管理端审核同样按 attendee -> attribution 的顺序加锁。这里保持相同锁顺序，既避免死锁，
         * 也保证“管理员拒绝/取消”和 TDuck 更新并发时，最终 active 与参会人审核状态一致。
         */
        Attendee attendee = attendeeRepository.findByIdForUpdate(attendeeId)
                .filter(value -> value.getMeeting().getId().equals(meetingId))
                .orElse(null);
        if (attendee == null) {
            return;
        }

        Optional<RegistrationAttribution> existing = attributionRepository.findForUpdate(meetingId, normalizedDataId);
        if (existing.isPresent()) {
            restoreExisting(existing.get(), attendee);
            synchronizeSubmissionAttributionState(meetingId, attendee);
            return;
        }

        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null) {
            return;
        }

        AttributionResolution resolution = resolve(meetingId, normalizedFormKey, extValue);
        RegistrationAttribution attribution = new RegistrationAttribution();
        attribution.setMeeting(meeting);
        attribution.setAttendee(attendee);
        attribution.setFormKey(normalizedFormKey);
        attribution.setTduckDataId(normalizedDataId);
        // active 只能由 submission 状态同步决定，不能因为这条 dataId 刚收到回调就先行生效。
        attribution.setActive(false);
        attribution.setAttributedAt(LocalDateTime.now());
        attribution.setAttributionStatus(resolution.status());
        if (resolution.event() != null) {
            InvitationEvent event = resolution.event();
            attribution.setClickEvent(event);
            attribution.setInvitation(event.getInvitation());
            attribution.setVersion(event.getVersion());
            attribution.setShareLink(event.getShareLink());
        }
        attributionRepository.save(attribution);
        synchronizeSubmissionAttributionState(meetingId, attendee);
    }

    /**
     * 供 TDuck 冲突采用、撤回恢复等状态切换在其当前事务内调用。
     * REQUIRED 使其既能加入 resolve/cancel 的原子提交，也能在补偿场景自行开启事务。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void synchronizeSubmissionAttributionState(Long meetingId, Long attendeeId) {
        if (meetingId == null || meetingId <= 0 || attendeeId == null || attendeeId <= 0) {
            return;
        }
        attendeeRepository.findByIdForUpdate(attendeeId)
                .filter(attendee -> attendee.getMeeting().getId().equals(meetingId))
                .ifPresent(attendee -> synchronizeSubmissionAttributionState(meetingId, attendee));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInactive(Long meetingId, String tduckDataId) {
        String normalizedDataId = normalize(tduckDataId, 120);
        if (meetingId == null || meetingId <= 0 || normalizedDataId == null) {
            return;
        }
        attributionRepository.findForUpdate(meetingId, normalizedDataId).ifPresent(attribution -> {
            attribution.setActive(false);
            attributionRepository.save(attribution);
        });
    }

    private void restoreExisting(RegistrationAttribution attribution, Attendee attendee) {
        // 同一 dataId 的再次拉取不能绕开重复报名状态检查而直接重新激活归因。
        attribution.setActive(false);
        if (attribution.getAttendee() == null || !attendee.getId().equals(attribution.getAttendee().getId())) {
            attribution.setAttendee(attendee);
        }
        attributionRepository.save(attribution);
    }

    private void synchronizeSubmissionAttributionState(Long meetingId, Attendee attendee) {
        if (attendee.getId() == null) {
            return;
        }
        attributionStateSynchronizer.synchronize(
                meetingId,
                attendee.getId(),
                isEffectiveRegistration(attendee)
        );
    }

    private boolean isEffectiveRegistration(Attendee attendee) {
        ReviewStatus status = attendee.getReviewStatus();
        return status != ReviewStatus.REJECTED && status != ReviewStatus.CANCELLED;
    }

    private AttributionResolution resolve(Long meetingId, String formKey, String extValue) {
        if (!StringUtils.hasText(extValue)) {
            return new AttributionResolution(RegistrationAttributionStatus.DIRECT_UNATTRIBUTED, null);
        }
        Optional<InvitationTrackingTokenService.TrackingClaims> claims = tokenService.verify(extValue.trim());
        if (claims.isEmpty() || claims.get().meetingId() != meetingId || !tokenService.matchesFormKey(claims.get(), formKey)) {
            return new AttributionResolution(RegistrationAttributionStatus.INVALID_TOKEN, null);
        }
        InvitationEvent event = eventRepository.findByClickPublicId(claims.get().clickPublicId()).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (event == null || event.getEventType() != InvitationEventType.CTA_CLICK
                || !event.getMeeting().getId().equals(meetingId)
                || event.getClickExpiresAt() == null || !event.getClickExpiresAt().isAfter(now)) {
            return new AttributionResolution(RegistrationAttributionStatus.INVALID_TOKEN, null);
        }
        return new AttributionResolution(RegistrationAttributionStatus.ATTRIBUTED, event);
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : null;
    }

    private record AttributionResolution(RegistrationAttributionStatus status, InvitationEvent event) {
    }
}
