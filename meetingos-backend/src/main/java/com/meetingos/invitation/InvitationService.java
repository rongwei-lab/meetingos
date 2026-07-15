package com.meetingos.invitation;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static com.meetingos.invitation.InvitationApiModels.*;

@Service
public class InvitationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MeetingRepository meetingRepository;
    private final MeetingInvitationRepository invitationRepository;
    private final MeetingInvitationVersionRepository versionRepository;
    private final InvitationShareLinkRepository shareLinkRepository;
    private final InvitationDesignValidator designValidator;
    private final String publicBaseUrl;
    private final String amapKey;

    public InvitationService(MeetingRepository meetingRepository,
                             MeetingInvitationRepository invitationRepository,
                             MeetingInvitationVersionRepository versionRepository,
                             InvitationShareLinkRepository shareLinkRepository,
                             InvitationDesignValidator designValidator,
                             @Value("${meetingos.app.public-base-url:http://localhost:5173}") String publicBaseUrl,
                             InvitationProperties invitationProperties) {
        this.meetingRepository = meetingRepository;
        this.invitationRepository = invitationRepository;
        this.versionRepository = versionRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.designValidator = designValidator;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.amapKey = invitationProperties.amapKey();
    }

    @Transactional(readOnly = true)
    public List<InvitationSummaryResponse> list(Long meetingId) {
        ensureMeeting(meetingId);
        return invitationRepository.findByMeetingIdOrderByUpdatedAtDesc(meetingId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvitationDetailResponse detail(Long meetingId, Long invitationId) {
        return toDetail(findInvitation(meetingId, invitationId));
    }

    @Transactional
    public InvitationDetailResponse create(Long meetingId, CreateInvitationRequest request) {
        Meeting meeting = ensureMeeting(meetingId);
        InvitationDesignDocumentV1 document = request.document() == null
                ? InvitationDesignDocumentV1.empty()
                : request.document();
        designValidator.validate(meetingId, document);
        MeetingInvitation invitation = new MeetingInvitation();
        invitation.setMeeting(meeting);
        invitation.setName(normalizeName(request.name()));
        invitation.setStatus(InvitationStatus.DRAFT);
        invitation.setDraftRevision(0);
        invitation.setDraftDocument(document);
        invitation.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
        return toDetail(invitationRepository.save(invitation));
    }

    @Transactional
    public InvitationDetailResponse copy(Long meetingId, Long invitationId, CopyInvitationRequest request) {
        MeetingInvitation source = findInvitation(meetingId, invitationId);
        MeetingInvitation copy = new MeetingInvitation();
        copy.setMeeting(source.getMeeting());
        copy.setName(normalizeName(request == null || !StringUtils.hasText(request.name())
                ? source.getName() + " 副本"
                : request.name()));
        copy.setStatus(InvitationStatus.DRAFT);
        copy.setDraftRevision(0);
        copy.setDraftDocument(source.getDraftDocument());
        copy.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
        designValidator.validate(meetingId, copy.getDraftDocument());
        return toDetail(invitationRepository.save(copy));
    }

    @Transactional
    public InvitationDetailResponse saveDraft(Long meetingId, Long invitationId, SaveDraftRequest request) {
        MeetingInvitation invitation = findForUpdate(meetingId, invitationId);
        ensureNotArchived(invitation);
        if (invitation.getDraftRevision() != request.expectedRevision()) {
            throw BusinessException.conflict("草稿已被其他操作更新，请刷新后重试");
        }
        designValidator.validate(meetingId, request.document());
        if (StringUtils.hasText(request.name())) {
            invitation.setName(normalizeName(request.name()));
        }
        invitation.setDraftDocument(request.document());
        invitation.setDraftRevision(invitation.getDraftRevision() + 1);
        return toDetail(invitationRepository.save(invitation));
    }

    @Transactional
    public InvitationDetailResponse publish(Long meetingId, Long invitationId) {
        MeetingInvitation invitation = findForUpdate(meetingId, invitationId);
        ensureNotArchived(invitation);
        designValidator.validate(meetingId, invitation.getDraftDocument());
        if (designValidator.containsMap(invitation.getDraftDocument()) && !StringUtils.hasText(amapKey)) {
            throw BusinessException.badRequest("当前环境未配置高德地图密钥，不能发布包含地图的邀请函");
        }
        if (designValidator.containsPublicQueryAction(invitation.getDraftDocument())
                && !StringUtils.hasText(invitation.getMeeting().getPublicQueryToken())) {
            throw BusinessException.badRequest("会议未配置公开查询令牌，不能发布查询或签到入口");
        }

        MeetingInvitationVersion version = new MeetingInvitationVersion();
        version.setInvitation(invitation);
        version.setVersionNo(versionRepository.findLatestVersionNo(invitationId) + 1);
        version.setDesignDocument(invitation.getDraftDocument());
        version.setMeetingSnapshot(InvitationMeetingSnapshot.from(invitation.getMeeting()));
        version.setPublishedBy(SecurityUtils.currentUsernameOrSystem());
        version = versionRepository.saveAndFlush(version);

        LocalDateTime now = LocalDateTime.now();
        invitation.setLatestPublishedVersion(version);
        invitation.setStatus(InvitationStatus.PUBLISHED);
        invitation.setPublishedAt(now);
        invitation.setUnpublishedAt(null);
        shareLinkRepository.pointAllToVersion(invitationId, version);
        return toDetail(invitationRepository.save(invitation));
    }

    @Transactional
    public InvitationDetailResponse unpublish(Long meetingId, Long invitationId) {
        MeetingInvitation invitation = findForUpdate(meetingId, invitationId);
        ensureNotArchived(invitation);
        if (invitation.getStatus() != InvitationStatus.PUBLISHED) {
            throw BusinessException.conflict("只有已发布邀请函可以下线");
        }
        invitation.setStatus(InvitationStatus.UNPUBLISHED);
        invitation.setUnpublishedAt(LocalDateTime.now());
        return toDetail(invitationRepository.save(invitation));
    }

    @Transactional
    public InvitationDetailResponse archive(Long meetingId, Long invitationId) {
        MeetingInvitation invitation = findForUpdate(meetingId, invitationId);
        if (invitation.getStatus() == InvitationStatus.ARCHIVED) {
            return toDetail(invitation);
        }
        invitation.setStatus(InvitationStatus.ARCHIVED);
        invitation.setArchivedAt(LocalDateTime.now());
        shareLinkRepository.disableAll(invitationId);
        return toDetail(invitationRepository.save(invitation));
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResponse> shareLinks(Long meetingId, Long invitationId) {
        findInvitation(meetingId, invitationId);
        return shareLinkRepository.findByInvitationIdOrderByCreatedAtDesc(invitationId).stream()
                .map(this::toShareLink)
                .toList();
    }

    @Transactional
    public ShareLinkResponse createShareLink(Long meetingId, Long invitationId, CreateShareLinkRequest request) {
        // 与发布、归档共用邀请函行锁，避免渠道漏指最新版本或在归档后被并发重新启用。
        MeetingInvitation invitation = findForUpdate(meetingId, invitationId);
        ensureNotArchived(invitation);
        InvitationShareLink link = new InvitationShareLink();
        link.setMeeting(invitation.getMeeting());
        link.setInvitation(invitation);
        link.setCurrentVersion(invitation.getLatestPublishedVersion());
        link.setChannelType(request.channelType());
        link.setChannelName(normalizeChannelName(request.channelName()));
        link.setShareToken(randomToken());
        link.setEnabled(true);
        link.setExpiresAt(request.expiresAt());
        link.setCreatedBy(SecurityUtils.currentUsernameOrSystem());
        return toShareLink(shareLinkRepository.save(link));
    }

    @Transactional
    public ShareLinkResponse updateShareLink(Long meetingId,
                                             Long invitationId,
                                             Long shareLinkId,
                                             UpdateShareLinkRequest request) {
        MeetingInvitation invitation = findForUpdate(meetingId, invitationId);
        ensureNotArchived(invitation);
        InvitationShareLink link = shareLinkRepository.findByIdAndInvitationId(shareLinkId, invitationId)
                .filter(value -> value.getMeeting().getId().equals(meetingId))
                .orElseThrow(() -> BusinessException.notFound("邀请渠道不存在"));
        if (request.channelType() != null) {
            link.setChannelType(request.channelType());
        }
        if (StringUtils.hasText(request.channelName())) {
            link.setChannelName(normalizeChannelName(request.channelName()));
        }
        if (request.enabled() != null) {
            link.setEnabled(request.enabled());
        }
        if (request.clearExpiresAt()) {
            link.setExpiresAt(null);
        } else if (request.expiresAt() != null) {
            link.setExpiresAt(request.expiresAt());
        }
        return toShareLink(shareLinkRepository.save(link));
    }

    private Meeting ensureMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
    }

    private MeetingInvitation findInvitation(Long meetingId, Long invitationId) {
        return invitationRepository.findByIdAndMeetingId(invitationId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("邀请函不存在"));
    }

    private MeetingInvitation findForUpdate(Long meetingId, Long invitationId) {
        return invitationRepository.findForUpdate(invitationId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("邀请函不存在"));
    }

    private void ensureNotArchived(MeetingInvitation invitation) {
        if (invitation.getStatus() == InvitationStatus.ARCHIVED) {
            throw BusinessException.conflict("邀请函已归档，不能继续修改");
        }
    }

    private InvitationSummaryResponse toSummary(MeetingInvitation invitation) {
        MeetingInvitationVersion latest = invitation.getLatestPublishedVersion();
        return new InvitationSummaryResponse(
                invitation.getId(), invitation.getName(), invitation.getStatus(), invitation.getDraftRevision(),
                latest == null ? null : latest.getVersionNo(), shareLinkRepository.countByInvitationId(invitation.getId()),
                invitation.getPublishedAt(), invitation.getUpdatedAt()
        );
    }

    private InvitationDetailResponse toDetail(MeetingInvitation invitation) {
        MeetingInvitationVersion latest = invitation.getLatestPublishedVersion();
        PublishedVersionResponse published = latest == null ? null : new PublishedVersionResponse(
                latest.getId(), latest.getVersionNo(), latest.getDesignDocument(), latest.getMeetingSnapshot(), latest.getCreatedAt()
        );
        return new InvitationDetailResponse(
                invitation.getId(), invitation.getMeeting().getId(), invitation.getName(), invitation.getStatus(),
                invitation.getDraftRevision(), invitation.getDraftDocument(), published, invitation.getPublishedAt(),
                invitation.getUnpublishedAt(), invitation.getArchivedAt(), invitation.getCreatedAt(), invitation.getUpdatedAt()
        );
    }

    private ShareLinkResponse toShareLink(InvitationShareLink link) {
        MeetingInvitationVersion version = link.getCurrentVersion();
        return new ShareLinkResponse(
                link.getId(), link.getInvitation().getId(), link.getChannelType(), link.getChannelName(),
                link.getShareToken(), publicBaseUrl + "/i/" + link.getShareToken(), link.isEnabled(), link.getExpiresAt(),
                version == null ? null : version.getVersionNo(), link.getCreatedAt(), link.getUpdatedAt()
        );
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw BusinessException.badRequest("邀请函名称不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 160) {
            throw BusinessException.badRequest("邀请函名称不能超过 160 个字符");
        }
        return normalized;
    }

    private String normalizeChannelName(String value) {
        if (!StringUtils.hasText(value)) {
            throw BusinessException.badRequest("渠道名称不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw BusinessException.badRequest("渠道名称不能超过 120 个字符");
        }
        return normalized;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
