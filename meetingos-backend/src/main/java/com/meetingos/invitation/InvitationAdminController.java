package com.meetingos.invitation;

import com.meetingos.common.ApiResponse;
import com.meetingos.operationlog.AuditLog;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static com.meetingos.invitation.InvitationApiModels.*;

@RestController
@RequestMapping("/api/admin/meetings/{meetingId}/invitations")
public class InvitationAdminController {
    private final InvitationService invitationService;
    private final InvitationAssetService assetService;
    private final InvitationAnalyticsService analyticsService;

    public InvitationAdminController(InvitationService invitationService,
                                     InvitationAssetService assetService,
                                     InvitationAnalyticsService analyticsService) {
        this.invitationService = invitationService;
        this.assetService = assetService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<InvitationSummaryResponse>> list(@PathVariable Long meetingId) {
        return ApiResponse.ok(invitationService.list(meetingId));
    }

    @PostMapping
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_CREATE", targetType = "MEETING_INVITATION")
    public ApiResponse<InvitationDetailResponse> create(@PathVariable Long meetingId,
                                                        @Valid @RequestBody CreateInvitationRequest request) {
        return ApiResponse.ok(invitationService.create(meetingId, request));
    }

    @GetMapping("/{invitationId}")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<InvitationDetailResponse> detail(@PathVariable Long meetingId,
                                                        @PathVariable Long invitationId) {
        return ApiResponse.ok(invitationService.detail(meetingId, invitationId));
    }

    @PostMapping("/{invitationId}/copy")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_COPY", targetType = "MEETING_INVITATION")
    public ApiResponse<InvitationDetailResponse> copy(@PathVariable Long meetingId,
                                                      @PathVariable Long invitationId,
                                                      @RequestBody(required = false) CopyInvitationRequest request) {
        return ApiResponse.ok(invitationService.copy(meetingId, invitationId, request));
    }

    @PutMapping("/{invitationId}/draft")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_DRAFT_SAVE", targetType = "MEETING_INVITATION")
    public ApiResponse<InvitationDetailResponse> saveDraft(@PathVariable Long meetingId,
                                                           @PathVariable Long invitationId,
                                                           @Valid @RequestBody SaveDraftRequest request) {
        return ApiResponse.ok(invitationService.saveDraft(meetingId, invitationId, request));
    }

    @PostMapping("/{invitationId}/publish")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_PUBLISH", targetType = "MEETING_INVITATION")
    public ApiResponse<InvitationDetailResponse> publish(@PathVariable Long meetingId,
                                                         @PathVariable Long invitationId) {
        return ApiResponse.ok(invitationService.publish(meetingId, invitationId));
    }

    @PostMapping("/{invitationId}/unpublish")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_UNPUBLISH", targetType = "MEETING_INVITATION")
    public ApiResponse<InvitationDetailResponse> unpublish(@PathVariable Long meetingId,
                                                           @PathVariable Long invitationId) {
        return ApiResponse.ok(invitationService.unpublish(meetingId, invitationId));
    }

    @PostMapping("/{invitationId}/archive")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_ARCHIVE", targetType = "MEETING_INVITATION")
    public ApiResponse<InvitationDetailResponse> archive(@PathVariable Long meetingId,
                                                         @PathVariable Long invitationId) {
        return ApiResponse.ok(invitationService.archive(meetingId, invitationId));
    }

    @GetMapping("/{invitationId}/share-links")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<ShareLinkResponse>> shareLinks(@PathVariable Long meetingId,
                                                           @PathVariable Long invitationId) {
        return ApiResponse.ok(invitationService.shareLinks(meetingId, invitationId));
    }

    @PostMapping("/{invitationId}/share-links")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_SHARE_LINK_CREATE", targetType = "INVITATION_SHARE_LINK")
    public ApiResponse<ShareLinkResponse> createShareLink(@PathVariable Long meetingId,
                                                          @PathVariable Long invitationId,
                                                          @Valid @RequestBody CreateShareLinkRequest request) {
        return ApiResponse.ok(invitationService.createShareLink(meetingId, invitationId, request));
    }

    @PutMapping("/{invitationId}/share-links/{shareLinkId}")
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_SHARE_LINK_UPDATE", targetType = "INVITATION_SHARE_LINK")
    public ApiResponse<ShareLinkResponse> updateShareLink(@PathVariable Long meetingId,
                                                          @PathVariable Long invitationId,
                                                          @PathVariable Long shareLinkId,
                                                          @Valid @RequestBody UpdateShareLinkRequest request) {
        return ApiResponse.ok(invitationService.updateShareLink(meetingId, invitationId, shareLinkId, request));
    }

    @GetMapping("/assets")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<AssetResponse>> assets(@PathVariable Long meetingId) {
        return ApiResponse.ok(assetService.list(meetingId));
    }

    @PostMapping(path = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@meetingAccess.canManage(#meetingId)")
    @AuditLog(action = "INVITATION_ASSET_UPLOAD", targetType = "INVITATION_ASSET")
    public ApiResponse<AssetResponse> uploadAsset(@PathVariable Long meetingId,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(assetService.upload(meetingId, file));
    }

    @GetMapping("/analytics")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<InvitationAnalyticsResponse> analytics(
            @PathVariable Long meetingId,
            @RequestParam(required = false) Long invitationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.analytics(meetingId, invitationId, from, to));
    }

    @GetMapping("/{invitationId}/attributions")
    @PreAuthorize("@meetingAccess.canView(#meetingId)")
    public ApiResponse<List<AttributionResponse>> attributions(@PathVariable Long meetingId,
                                                               @PathVariable Long invitationId) {
        return ApiResponse.ok(analyticsService.attributions(meetingId, invitationId));
    }
}
