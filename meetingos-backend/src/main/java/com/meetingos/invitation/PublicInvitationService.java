package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.publicapi.RegistrationInfo;
import com.meetingos.tduck.TduckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PublicInvitationResponse detail(String shareToken) {
        PublicInvitationSnapshot snapshot = transactionRunner.readOnly(() -> {
            InvitationShareLink link = requireAvailableLink(shareToken);
            MeetingInvitationVersion version = link.getCurrentVersion();
            java.util.Set<Long> referencedAssets = designValidator.referencedAssetIds(version.getDesignDocument());
            List<AssetResponse> assets = assetRepository.findByIdIn(referencedAssets).stream()
                    .filter(asset -> asset.getMeeting().getId().equals(link.getMeeting().getId()))
                    .map(this::toAsset)
                    .toList();
            return new PublicInvitationSnapshot(
                    link.getInvitation().getId(), version.getVersionNo(), link.getChannelType(), link.getChannelName(),
                    version.getDesignDocument(), version.getMeetingSnapshot(), link.getMeeting().getPublicQueryToken(),
                    assets, link.getMeeting().getCode()
            );
        });
        // TDuck 是外部网络调用，必须在数据库快照事务提交并释放连接后执行。
        RegistrationInfo registration = tduckService.registrationInfo(snapshot.meetingCode());
        return new PublicInvitationResponse(
                snapshot.invitationId(), snapshot.versionNo(), snapshot.channelType(), snapshot.channelName(),
                snapshot.designDocument(), snapshot.meeting(), snapshot.publicQueryToken(), snapshot.assets(),
                registration.open(), registration.message()
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventReceipt recordView(String shareToken,
                                   PublicEventRequest request,
                                   String rawVisitorId,
                                   String userAgent,
                                   String referrer) {
        String clientEventId = normalizeUuid(request.clientEventId());
        EventWriteResult result = saveEventIdempotently(
                shareToken, InvitationEventType.PAGE_VIEW, clientEventId, rawVisitorId, userAgent, referrer
        );
        return new EventReceipt(result.recorded());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RegistrationIntentResponse registrationIntent(String shareToken,
                                                          PublicEventRequest request,
                                                          String rawVisitorId,
                                                          String userAgent,
                                                          String referrer) {
        RegistrationTarget target = transactionRunner.readOnly(() -> {
            InvitationShareLink link = requireAvailableLink(shareToken);
            return new RegistrationTarget(link.getMeeting().getCode());
        });
        // 远程表单状态校验不占用数据库连接；写点击事件时会再次校验分享链接状态。
        RegistrationInfo registration = tduckService.registrationInfo(target.meetingCode());
        if (!registration.open() || !StringUtils.hasText(registration.formKey())) {
            throw BusinessException.badRequest(registration.message());
        }
        String clientEventId = normalizeUuid(request.clientEventId());
        EventWriteResult result = saveEventIdempotently(
                shareToken, InvitationEventType.CTA_CLICK, clientEventId, rawVisitorId, userAgent, referrer
        );
        String token = tokenService.create(
                result.clickPublicId(), result.meetingId(), registration.formKey(),
                result.clickExpiresAt().atZone(ZoneId.systemDefault()).toInstant()
        );
        return new RegistrationIntentResponse(
                token,
                publicBaseUrl + "/r/" + target.meetingCode() + "#mos_at=" + token,
                result.clickExpiresAt()
        );
    }

    private EventWriteResult saveEventIdempotently(String shareToken,
                                                    InvitationEventType eventType,
                                                    String clientEventId,
                                                    String rawVisitorId,
                                                    String userAgent,
                                                    String referrer) {
        try {
            return transactionRunner.requiresNew(() -> {
                InvitationShareLink link = requireAvailableLink(shareToken);
                Optional<InvitationEvent> raced = eventRepository
                        .findByShareLinkIdAndClientEventId(link.getId(), clientEventId);
                if (raced.isPresent()) {
                    return existingEvent(raced.get(), eventType, link.getMeeting().getId());
                }
                InvitationEvent event = newEvent(
                        link, eventType, clientEventId, rawVisitorId, userAgent, referrer
                );
                if (eventType == InvitationEventType.CTA_CLICK) {
                    event.setClickPublicId(tokenService.newOpaqueId());
                    event.setClickExpiresAt(clickExpiry(link));
                }
                return eventWriteResult(eventRepository.saveAndFlush(event), true, link.getMeeting().getId());
            });
        } catch (DataIntegrityViolationException ex) {
            /*
             * 两个请求可能同时通过首次查询。后提交事务由唯一键完整回滚后，再在新事务中
             * 读取胜出事件；若冲突来自其他唯一键（如极小概率 click ID 碰撞），则保留原异常。
             */
            return transactionRunner.requiresNew(() -> {
                InvitationShareLink link = requireAvailableLink(shareToken);
                Optional<InvitationEvent> winner = eventRepository
                        .findByShareLinkIdAndClientEventId(link.getId(), clientEventId);
                if (winner.isEmpty()) {
                    throw ex;
                }
                return existingEvent(winner.get(), eventType, link.getMeeting().getId());
            });
        }
    }

    private EventWriteResult existingEvent(InvitationEvent event,
                                           InvitationEventType expectedType,
                                           Long meetingId) {
        if (event.getEventType() != expectedType) {
            throw BusinessException.conflict("clientEventId 已用于其他事件");
        }
        return eventWriteResult(event, false, meetingId);
    }

    private EventWriteResult eventWriteResult(InvitationEvent event, boolean recorded, Long meetingId) {
        return new EventWriteResult(
                recorded,
                event.getClickPublicId(),
                event.getClickExpiresAt(),
                meetingId
        );
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

    private record PublicInvitationSnapshot(
            Long invitationId,
            int versionNo,
            InvitationChannelType channelType,
            String channelName,
            InvitationDesignDocumentV1 designDocument,
            InvitationMeetingSnapshot meeting,
            String publicQueryToken,
            List<AssetResponse> assets,
            String meetingCode
    ) {
    }

    private record RegistrationTarget(String meetingCode) {
    }

    private record EventWriteResult(
            boolean recorded,
            String clickPublicId,
            LocalDateTime clickExpiresAt,
            Long meetingId
    ) {
    }
}
