package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.publicapi.RegistrationInfo;
import com.meetingos.tduck.TduckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static com.meetingos.invitation.InvitationApiModels.*;

@Service
public class PublicInvitationService {
    private final InvitationShareLinkRepository shareLinkRepository;
    private final InvitationAssetRepository assetRepository;
    private final InvitationEventRepository eventRepository;
    private final InvitationEventTransactionRunner transactionRunner;
    private final InvitationDesignValidator designValidator;
    private final InvitationTrackingTokenService tokenService;
    private final TduckService tduckService;
    private final String publicBaseUrl;

    public PublicInvitationService(InvitationShareLinkRepository shareLinkRepository,
                                   InvitationAssetRepository assetRepository,
                                   InvitationEventRepository eventRepository,
                                   InvitationEventTransactionRunner transactionRunner,
                                   InvitationDesignValidator designValidator,
                                   InvitationTrackingTokenService tokenService,
                                   TduckService tduckService,
                                   @Value("${meetingos.app.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.shareLinkRepository = shareLinkRepository;
        this.assetRepository = assetRepository;
        this.eventRepository = eventRepository;
        this.transactionRunner = transactionRunner;
        this.designValidator = designValidator;
        this.tokenService = tokenService;
        this.tduckService = tduckService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public PublicInvitationResponse detail(String shareToken) {
        InvitationShareLink link = requireAvailableLink(shareToken);
        MeetingInvitationVersion version = link.getCurrentVersion();
        RegistrationInfo registration = tduckService.registrationInfo(link.getMeeting().getCode());
        java.util.Set<Long> referencedAssets = designValidator.referencedAssetIds(version.getDesignDocument());
        List<AssetResponse> assets = assetRepository.findByIdIn(referencedAssets).stream()
                .filter(asset -> asset.getMeeting().getId().equals(link.getMeeting().getId()))
                .map(this::toAsset)
                .toList();
        return new PublicInvitationResponse(
                link.getInvitation().getId(), version.getVersionNo(), link.getChannelType(), link.getChannelName(),
                version.getDesignDocument(), version.getMeetingSnapshot(),
                link.getMeeting().getPublicQueryToken(), assets,
                registration.open(), registration.message()
        );
    }

    @Transactional
    public EventReceipt recordView(String shareToken,
                                   PublicEventRequest request,
                                   String rawVisitorId,
                                   String userAgent,
                                   String referrer) {
        InvitationShareLink link = requireAvailableLink(shareToken);
        String clientEventId = normalizeUuid(request.clientEventId());
        EventWriteResult result = saveEventIdempotently(
                link, InvitationEventType.PAGE_VIEW, clientEventId, rawVisitorId, userAgent, referrer,
                ignored -> {
                }
        );
        return new EventReceipt(result.recorded());
    }

    @Transactional
    public RegistrationIntentResponse registrationIntent(String shareToken,
                                                          PublicEventRequest request,
                                                          String rawVisitorId,
                                                          String userAgent,
                                                          String referrer) {
        InvitationShareLink link = requireAvailableLink(shareToken);
        RegistrationInfo registration = tduckService.registrationInfo(link.getMeeting().getCode());
        if (!registration.open() || !StringUtils.hasText(registration.formKey())) {
            throw BusinessException.badRequest(registration.message());
        }
        String clientEventId = normalizeUuid(request.clientEventId());
        LocalDateTime expiresAt = clickExpiry(link);
        EventWriteResult result = saveEventIdempotently(
                link, InvitationEventType.CTA_CLICK, clientEventId, rawVisitorId, userAgent, referrer,
                event -> {
                    event.setClickPublicId(tokenService.newOpaqueId());
                    event.setClickExpiresAt(expiresAt);
                }
        );
        InvitationEvent event = result.event();
        String token = tokenService.create(
                event.getClickPublicId(), link.getMeeting().getId(), registration.formKey(),
                event.getClickExpiresAt().atZone(ZoneId.systemDefault()).toInstant()
        );
        return new RegistrationIntentResponse(
                token,
                publicBaseUrl + "/r/" + link.getMeeting().getCode() + "#mos_at=" + token,
                event.getClickExpiresAt()
        );
    }

    private EventWriteResult saveEventIdempotently(InvitationShareLink link,
                                                    InvitationEventType eventType,
                                                    String clientEventId,
                                                    String rawVisitorId,
                                                    String userAgent,
                                                    String referrer,
                                                    Consumer<InvitationEvent> configure) {
        Optional<InvitationEvent> existing = eventRepository
                .findByShareLinkIdAndClientEventId(link.getId(), clientEventId);
        if (existing.isPresent()) {
            return existingEvent(existing.get(), eventType);
        }
        try {
            return transactionRunner.requiresNew(() -> {
                Optional<InvitationEvent> raced = eventRepository
                        .findByShareLinkIdAndClientEventId(link.getId(), clientEventId);
                if (raced.isPresent()) {
                    return existingEvent(raced.get(), eventType);
                }
                InvitationEvent event = newEvent(
                        link, eventType, clientEventId, rawVisitorId, userAgent, referrer
                );
                configure.accept(event);
                return new EventWriteResult(eventRepository.saveAndFlush(event), true);
            });
        } catch (DataIntegrityViolationException ex) {
            /*
             * 两个请求可能同时通过首次查询。后提交事务由唯一键完整回滚后，再在新事务中
             * 读取胜出事件；若冲突来自其他唯一键（如极小概率 click ID 碰撞），则保留原异常。
             */
            Optional<InvitationEvent> winner = transactionRunner.requiresNew(() -> eventRepository
                    .findByShareLinkIdAndClientEventId(link.getId(), clientEventId));
            if (winner.isEmpty()) {
                throw ex;
            }
            return existingEvent(winner.get(), eventType);
        }
    }

    private EventWriteResult existingEvent(InvitationEvent event, InvitationEventType expectedType) {
        if (event.getEventType() != expectedType) {
            throw BusinessException.conflict("clientEventId 已用于其他事件");
        }
        return new EventWriteResult(event, false);
    }

    private InvitationShareLink requireAvailableLink(String shareToken) {
        if (!StringUtils.hasText(shareToken) || shareToken.length() > 96) {
            throw BusinessException.notFound("邀请函不可用");
        }
        InvitationShareLink link = shareLinkRepository.findPublicLink(shareToken.trim())
                .orElseThrow(() -> BusinessException.notFound("邀请函不可用"));
        LocalDateTime now = LocalDateTime.now();
        boolean unavailable = !link.isEnabled()
                || (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(now))
                || link.getInvitation().getStatus() != InvitationStatus.PUBLISHED
                || link.getCurrentVersion() == null
                || link.getInvitation().getLatestPublishedVersion() == null
                || !link.getCurrentVersion().getId().equals(link.getInvitation().getLatestPublishedVersion().getId());
        if (unavailable) {
            // 无效、撤销、过期和未发布都使用同一响应，避免枚举会议信息。
            throw BusinessException.notFound("邀请函不可用");
        }
        return link;
    }

    private InvitationEvent newEvent(InvitationShareLink link,
                                     InvitationEventType type,
                                     String clientEventId,
                                     String rawVisitorId,
                                     String userAgent,
                                     String referrer) {
        InvitationEvent event = new InvitationEvent();
        event.setMeeting(link.getMeeting());
        event.setInvitation(link.getInvitation());
        event.setVersion(link.getCurrentVersion());
        event.setShareLink(link);
        event.setEventType(type);
        event.setClientEventId(clientEventId);
        event.setVisitorHash(tokenService.hashVisitor(rawVisitorId));
        event.setOccurredAt(LocalDateTime.now());
        event.setDeviceType(deviceType(userAgent));
        event.setReferrerHost(referrerHost(referrer));
        return event;
    }

    private LocalDateTime clickExpiry(InvitationShareLink link) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(7);
        LocalDateTime registrationEnd = link.getMeeting().getRegistrationEndTime();
        if (registrationEnd != null && registrationEnd.isBefore(expiry)) {
            expiry = registrationEnd;
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(expiry)) {
            expiry = link.getExpiresAt();
        }
        if (!expiry.isAfter(now)) {
            throw BusinessException.badRequest("报名已结束");
        }
        return expiry;
    }

    private String normalizeUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ex) {
            throw BusinessException.badRequest("clientEventId 必须为 UUID");
        }
    }

    private String deviceType(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "UNKNOWN";
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("ipad") || normalized.contains("tablet")) {
            return "TABLET";
        }
        if (normalized.contains("mobile") || normalized.contains("iphone") || normalized.contains("android")) {
            return "MOBILE";
        }
        return "DESKTOP";
    }

    private String referrerHost(String referrer) {
        if (!StringUtils.hasText(referrer)) {
            return null;
        }
        try {
            String host = new URI(referrer).getHost();
            if (!StringUtils.hasText(host)) {
                return null;
            }
            return host.length() <= 255 ? host : host.substring(0, 255);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private AssetResponse toAsset(InvitationAsset asset) {
        return new AssetResponse(asset.getId(), asset.getOriginalName(), asset.getPublicUrl(), asset.getContentType(),
                asset.getSizeBytes(), asset.getWidthPx(), asset.getHeightPx(), asset.getCreatedAt());
    }

    private record EventWriteResult(InvitationEvent event, boolean recorded) {
    }
}
