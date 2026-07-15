package com.meetingos.invitation;

import com.meetingos.attendee.Attendee;
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

    public InvitationAttributionService(RegistrationAttributionRepository attributionRepository,
                                        InvitationEventRepository eventRepository,
                                        MeetingRepository meetingRepository,
                                        AttendeeRepository attendeeRepository,
                                        InvitationTrackingTokenService tokenService) {
        this.attributionRepository = attributionRepository;
        this.eventRepository = eventRepository;
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.tokenService = tokenService;
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
        attribution.setActive(isEffectiveRegistration(attendee));
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
        attribution.setActive(isEffectiveRegistration(attendee));
        if (attribution.getAttendee() == null || !attendee.getId().equals(attribution.getAttendee().getId())) {
            attribution.setAttendee(attendee);
        }
        attributionRepository.save(attribution);
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
