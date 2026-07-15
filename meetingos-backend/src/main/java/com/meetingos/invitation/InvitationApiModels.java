package com.meetingos.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class InvitationApiModels {
    private InvitationApiModels() {
    }

    public record CreateInvitationRequest(
            @NotBlank @Size(max = 160) String name,
            InvitationDesignDocumentV1 document
    ) {
    }

    public record SaveDraftRequest(
            @PositiveOrZero long expectedRevision,
            @Size(max = 160) String name,
            @NotNull InvitationDesignDocumentV1 document
    ) {
    }

    public record CopyInvitationRequest(@Size(max = 160) String name) {
    }

    public record InvitationSummaryResponse(
            Long id,
            String name,
            InvitationStatus status,
            long draftRevision,
            Integer latestVersionNo,
            int shareLinkCount,
            LocalDateTime publishedAt,
            LocalDateTime updatedAt
    ) {
    }

    public record InvitationDetailResponse(
            Long id,
            Long meetingId,
            String name,
            InvitationStatus status,
            long draftRevision,
            InvitationDesignDocumentV1 draftDocument,
            PublishedVersionResponse latestPublishedVersion,
            LocalDateTime publishedAt,
            LocalDateTime unpublishedAt,
            LocalDateTime archivedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PublishedVersionResponse(
            Long id,
            int versionNo,
            InvitationDesignDocumentV1 designDocument,
            InvitationMeetingSnapshot meetingSnapshot,
            LocalDateTime publishedAt
    ) {
    }

    public record CreateShareLinkRequest(
            @NotNull InvitationChannelType channelType,
            @NotBlank @Size(max = 120) String channelName,
            LocalDateTime expiresAt
    ) {
    }

    public record UpdateShareLinkRequest(
            InvitationChannelType channelType,
            @Size(max = 120) String channelName,
            Boolean enabled,
            LocalDateTime expiresAt,
            boolean clearExpiresAt
    ) {
    }

    public record ShareLinkResponse(
            Long id,
            Long invitationId,
            InvitationChannelType channelType,
            String channelName,
            String shareToken,
            String shareUrl,
            boolean enabled,
            LocalDateTime expiresAt,
            Integer currentVersionNo,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record AssetResponse(
            Long id,
            String originalName,
            String url,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height,
            LocalDateTime createdAt
    ) {
    }

    public record PublicInvitationResponse(
            Long invitationId,
            int versionNo,
            InvitationChannelType channelType,
            String channelName,
            InvitationDesignDocumentV1 designDocument,
            InvitationMeetingSnapshot meeting,
            String publicQueryToken,
            List<AssetResponse> assets,
            boolean registrationOpen,
            String registrationMessage
    ) {
    }

    public record PublicEventRequest(
            @NotBlank
            @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "clientEventId 必须为 UUID")
            String clientEventId
    ) {
    }

    public record EventReceipt(boolean recorded) {
    }

    public record RegistrationIntentResponse(
            String attributionToken,
            String redirectUrl,
            LocalDateTime expiresAt
    ) {
    }

    public record InvitationAnalyticsResponse(
            long pageViews,
            long uniqueVisitors,
            long ctaClicks,
            long uniqueClickVisitors,
            long attributedRegistrations,
            long activeAttributedRegistrations,
            long directUnattributedRegistrations,
            long invalidTokenRegistrations,
            long legacyUnattributedRegistrations,
            long currentActiveRegistrations,
            double clickThroughRate,
            double registrationConversionRate,
            List<DailyMetricResponse> daily,
            List<ChannelMetricResponse> channels
    ) {
    }

    public record DailyMetricResponse(
            LocalDate date,
            long pageViews,
            long uniqueVisitors,
            long ctaClicks,
            long attributedRegistrations
    ) {
    }

    public record ChannelMetricResponse(
            Long shareLinkId,
            Long invitationId,
            String invitationName,
            InvitationChannelType channelType,
            String channelName,
            long pageViews,
            long uniqueVisitors,
            long ctaClicks,
            long attributedRegistrations,
            long activeAttributedRegistrations
    ) {
    }

    public record AttributionResponse(
            Long id,
            RegistrationAttributionStatus status,
            String tduckDataId,
            Long attendeeId,
            Long shareLinkId,
            String channelName,
            boolean active,
            LocalDateTime attributedAt
    ) {
    }
}
