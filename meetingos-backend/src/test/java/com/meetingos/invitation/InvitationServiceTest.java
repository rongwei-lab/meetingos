package com.meetingos.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationServiceTest {
    private MeetingRepository meetingRepository;
    private MeetingInvitationRepository invitationRepository;
    private MeetingInvitationVersionRepository versionRepository;
    private InvitationShareLinkRepository shareLinkRepository;
    private InvitationDesignValidator validator;
    private InvitationService service;
    private MeetingInvitation invitation;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        invitationRepository = mock(MeetingInvitationRepository.class);
        versionRepository = mock(MeetingInvitationVersionRepository.class);
        shareLinkRepository = mock(InvitationShareLinkRepository.class);
        InvitationAssetRepository assetRepository = mock(InvitationAssetRepository.class);
        validator = new InvitationDesignValidator(new ObjectMapper(), assetRepository);
        service = new InvitationService(
                meetingRepository, invitationRepository, versionRepository, shareLinkRepository, validator,
                "https://meeting.example.test", new InvitationProperties("strong-secret".repeat(4), 180, "amap-key")
        );

        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode("CONF2026");
        meeting.setName("原会议名称");
        meeting.setLocation("原会场");
        meeting.setRegistrationEndTime(LocalDateTime.now().plusDays(30));
        invitation = new MeetingInvitation();
        invitation.setId(10L);
        invitation.setMeeting(meeting);
        invitation.setName("邀请函 A");
        invitation.setStatus(InvitationStatus.DRAFT);
        invitation.setDraftRevision(2);
        invitation.setDraftDocument(InvitationDesignDocumentV1.empty());
        when(invitationRepository.findForUpdate(10L, 1L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void draftRevisionConflictReturns409SemanticWithoutOverwritingDocument() {
        var request = new InvitationApiModels.SaveDraftRequest(1, "新名称", InvitationDesignDocumentV1.empty());

        assertThatThrownBy(() -> service.saveDraft(1L, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新后重试");
        assertThat(invitation.getDraftRevision()).isEqualTo(2);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void publishCreatesImmutableSnapshotAndMovesAllChannelsToNewVersion() {
        when(versionRepository.findLatestVersionNo(10L)).thenReturn(3);
        when(versionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MeetingInvitationVersion value = invocation.getArgument(0);
            value.setId(99L);
            value.setCreatedAt(LocalDateTime.now());
            return value;
        });

        InvitationApiModels.InvitationDetailResponse response = service.publish(1L, 10L);

        ArgumentCaptor<MeetingInvitationVersion> versionCaptor = ArgumentCaptor.forClass(MeetingInvitationVersion.class);
        verify(versionRepository).saveAndFlush(versionCaptor.capture());
        MeetingInvitationVersion version = versionCaptor.getValue();
        assertThat(version.getVersionNo()).isEqualTo(4);
        assertThat(version.getMeetingSnapshot().name()).isEqualTo("原会议名称");
        assertThat(version.getMeetingSnapshot().location()).isEqualTo("原会场");
        assertThat(version.getDesignDocument()).isSameAs(invitation.getDraftDocument());
        assertThat(response.status()).isEqualTo(InvitationStatus.PUBLISHED);
        assertThat(response.latestPublishedVersion().versionNo()).isEqualTo(4);
        verify(shareLinkRepository).pointAllToVersion(10L, version);

        invitation.getMeeting().setName("会后修改名称");
        assertThat(version.getMeetingSnapshot().name()).isEqualTo("原会议名称");
    }

    @Test
    void creatingChannelUsesInvitationWriteLockAndPointsToCurrentPublishedVersion() {
        MeetingInvitationVersion version = new MeetingInvitationVersion();
        version.setId(91L);
        version.setInvitation(invitation);
        version.setVersionNo(2);
        invitation.setLatestPublishedVersion(version);
        invitation.setStatus(InvitationStatus.PUBLISHED);
        when(shareLinkRepository.save(any())).thenAnswer(invocation -> {
            InvitationShareLink link = invocation.getArgument(0);
            link.setId(92L);
            return link;
        });

        InvitationApiModels.ShareLinkResponse response = service.createShareLink(
                1L,
                10L,
                new InvitationApiModels.CreateShareLinkRequest(
                        InvitationChannelType.WECHAT_FRIENDS,
                        "合作方社群",
                        null
                )
        );

        verify(invitationRepository).findForUpdate(10L, 1L);
        assertThat(response.currentVersionNo()).isEqualTo(2);
    }
}
