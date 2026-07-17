package com.meetingos.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingStatus;
import com.meetingos.publicapi.RegistrationInfo;
import com.meetingos.tduck.TduckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicInvitationServiceTest {
    private InvitationShareLinkRepository shareLinkRepository;
    private InvitationAssetRepository assetRepository;
    private InvitationEventRepository eventRepository;
    private InvitationEventTransactionRunner transactionRunner;
    private TduckService tduckService;
    private PublicInvitationService service;
    private InvitationShareLink link;

    @BeforeEach
    void setUp() {
        shareLinkRepository = mock(InvitationShareLinkRepository.class);
        assetRepository = mock(InvitationAssetRepository.class);
        eventRepository = mock(InvitationEventRepository.class);
        transactionRunner = mock(InvitationEventTransactionRunner.class);
        tduckService = mock(TduckService.class);
        InvitationProperties properties = new InvitationProperties(
                "test-invitation-tracking-secret-that-is-long-enough", 180, ""
        );
        service = new PublicInvitationService(
                shareLinkRepository, assetRepository, eventRepository, transactionRunner,
                new InvitationDesignValidator(new ObjectMapper(), assetRepository),
                new InvitationTrackingTokenService(properties), tduckService, "https://meeting.example.test"
        );
        when(transactionRunner.requiresNew(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });
        when(transactionRunner.readOnly(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0, Supplier.class);
            return action.get();
        });
        link = publishedLink();
        when(shareLinkRepository.findPublicLink("share-token")).thenReturn(Optional.of(link));
    }

    @Test
    void publicDetailOnlyReturnsAssetsReferencedByPublishedVersion() {
        InvitationAsset referenced = asset(5L, link.getMeeting());
        InvitationAsset draftOnly = asset(6L, link.getMeeting());
        when(assetRepository.findByIdIn(Set.of(5L))).thenReturn(List.of(referenced));
        when(tduckService.registrationInfo("CONF2026")).thenReturn(new RegistrationInfo(
                1L, "CONF2026", "会议", MeetingStatus.REGISTRATION_OPEN, true, "报名开放中",
                "报名表", "form-key", "https://tduck.test/s/form-key"
        ));

        InvitationApiModels.PublicInvitationResponse response = service.detail("share-token");

        assertThat(response.assets()).extracting(InvitationApiModels.AssetResponse::id).containsExactly(5L);
        assertThat(response.publicQueryToken()).isEqualTo("pq_public-query-token");
        verify(assetRepository).findByIdIn(Set.of(5L));
        verify(assetRepository, never()).findByMeetingIdOrderByCreatedAtDesc(1L);
        assertThat(draftOnly.getId()).isEqualTo(6L);
    }

    @Test
    void publicMethodsSuspendCallerTransactionBeforeRemoteValidation() throws Exception {
        for (String methodName : List.of("detail", "recordView", "registrationIntent")) {
            Class<?>[] parameterTypes = switch (methodName) {
                case "detail" -> new Class<?>[]{String.class};
                default -> new Class<?>[]{
                        String.class, InvitationApiModels.PublicEventRequest.class,
                        String.class, String.class, String.class
                };
            };
            Transactional transactional = PublicInvitationService.class
                    .getMethod(methodName, parameterTypes)
                    .getAnnotation(Transactional.class);
            assertThat(transactional).isNotNull();
            assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        }
    }

    @Test
    void revokedAndUnpublishedLinksUseSameUnavailableError() {
        link.setEnabled(false);
        assertThatThrownBy(() -> service.detail("share-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("邀请函不可用");

        link.setEnabled(true);
        link.getInvitation().setStatus(InvitationStatus.UNPUBLISHED);
        assertThatThrownBy(() -> service.detail("share-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("邀请函不可用");
    }

    @Test
    void duplicateViewEventIsIdempotent() {
        String eventId = "01234567-89ab-cdef-0123-456789abcdef";
        InvitationEvent existing = new InvitationEvent();
        existing.setEventType(InvitationEventType.PAGE_VIEW);
        when(eventRepository.findByShareLinkIdAndClientEventId(4L, eventId)).thenReturn(Optional.of(existing));

        InvitationApiModels.EventReceipt receipt = service.recordView(
                "share-token", new InvitationApiModels.PublicEventRequest(eventId),
                "visitor-id-abcdefghijkl", "Mozilla/5.0", "https://source.example.test/path"
        );

        assertThat(receipt.recorded()).isFalse();
        verify(eventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void concurrentDuplicateViewReturnsIdempotentReceiptAfterLosingTransactionRollsBack() {
        String eventId = "01234567-89ab-cdef-0123-456789abcdef";
        InvitationEvent winner = new InvitationEvent();
        winner.setEventType(InvitationEventType.PAGE_VIEW);
        when(eventRepository.findByShareLinkIdAndClientEventId(4L, eventId))
                .thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("concurrent unique key"))
                .when(eventRepository).saveAndFlush(any());

        InvitationApiModels.EventReceipt receipt = service.recordView(
                "share-token", new InvitationApiModels.PublicEventRequest(eventId),
                "visitor-id-abcdefghijkl", "Mozilla/5.0", "https://source.example.test/path"
        );

        assertThat(receipt.recorded()).isFalse();
    }

    @Test
    void concurrentDuplicateClickReusesWinningEventAndTokenClaims() {
        String eventId = "11234567-89ab-cdef-0123-456789abcdef";
        InvitationEvent winner = new InvitationEvent();
        winner.setEventType(InvitationEventType.CTA_CLICK);
        winner.setClickPublicId("winning-click-public-id");
        winner.setClickExpiresAt(LocalDateTime.now().plusHours(2));
        when(eventRepository.findByShareLinkIdAndClientEventId(4L, eventId))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(tduckService.registrationInfo("CONF2026")).thenReturn(new RegistrationInfo(
                1L, "CONF2026", "会议", MeetingStatus.REGISTRATION_OPEN, true, "报名开放中",
                "报名表", "form-key", "https://tduck.test/s/form-key"
        ));
        doThrow(new DataIntegrityViolationException("concurrent unique key"))
                .when(eventRepository).saveAndFlush(any());

        InvitationApiModels.RegistrationIntentResponse response = service.registrationIntent(
                "share-token", new InvitationApiModels.PublicEventRequest(eventId),
                "visitor-id-abcdefghijkl", "Mozilla/5.0", "https://source.example.test/path"
        );

        assertThat(response.attributionToken()).isNotBlank();
        assertThat(response.redirectUrl()).contains("#mos_at=" + response.attributionToken());
        assertThat(response.expiresAt()).isEqualTo(winner.getClickExpiresAt());
    }

    private InvitationShareLink publishedLink() {
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode("CONF2026");
        meeting.setPublicQueryToken("pq_public-query-token");
        meeting.setName("会议");
        MeetingInvitation invitation = new MeetingInvitation();
        invitation.setId(2L);
        invitation.setMeeting(meeting);
        invitation.setName("邀请函");
        invitation.setStatus(InvitationStatus.PUBLISHED);
        InvitationDesignDocumentV1.InvitationElement image = new InvitationDesignDocumentV1.InvitationElement(
                "image", InvitationElementType.IMAGE, 0, 0, 100, 100, 0, 0, false, Map.of("assetId", 5L)
        );
        InvitationDesignDocumentV1 document = new InvitationDesignDocumentV1(
                1,
                new InvitationDesignDocumentV1.InvitationCanvas(375, 667, "#ffffff", null, List.of(image)),
                new InvitationDesignDocumentV1.InvitationCanvas(1080, 1920, "#ffffff", null, List.of())
        );
        MeetingInvitationVersion version = new MeetingInvitationVersion();
        version.setId(3L);
        version.setInvitation(invitation);
        version.setVersionNo(1);
        version.setDesignDocument(document);
        version.setMeetingSnapshot(InvitationMeetingSnapshot.from(meeting));
        invitation.setLatestPublishedVersion(version);
        InvitationShareLink value = new InvitationShareLink();
        value.setId(4L);
        value.setMeeting(meeting);
        value.setInvitation(invitation);
        value.setCurrentVersion(version);
        value.setChannelType(InvitationChannelType.EMAIL);
        value.setChannelName("邮件");
        value.setShareToken("share-token");
        value.setEnabled(true);
        value.setExpiresAt(LocalDateTime.now().plusDays(1));
        return value;
    }

    private InvitationAsset asset(Long id, Meeting meeting) {
        InvitationAsset asset = new InvitationAsset();
        asset.setId(id);
        asset.setMeeting(meeting);
        asset.setOriginalName("asset-" + id + ".png");
        asset.setPublicUrl("/uploads/images/" + id + ".png");
        asset.setContentType("image/png");
        asset.setSizeBytes(100);
        return asset;
    }
}
