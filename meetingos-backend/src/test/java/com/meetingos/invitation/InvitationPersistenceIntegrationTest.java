package com.meetingos.invitation;

import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvitationPersistenceIntegrationTest {
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsDomainJsonPublishesSnapshotAndRepointsExistingChannel() {
        Meeting meeting = new Meeting();
        meeting.setCode("INV-PERSIST-2026");
        meeting.setPublicQueryToken("inv_persist_public_token");
        meeting.setName("发布前会议名称");
        meeting.setLocation("发布前会场");
        meeting.setStatus(MeetingStatus.REGISTRATION_OPEN);
        meeting = meetingRepository.saveAndFlush(meeting);

        InvitationApiModels.InvitationDetailResponse created = invitationService.create(
                meeting.getId(), new InvitationApiModels.CreateInvitationRequest("持久化邀请函", null)
        );
        InvitationApiModels.ShareLinkResponse channel = invitationService.createShareLink(
                meeting.getId(), created.id(),
                new InvitationApiModels.CreateShareLinkRequest(InvitationChannelType.EMAIL, "邮件渠道", null)
        );
        InvitationDesignDocumentV1 document = documentWithText("不可变发布内容");
        invitationService.saveDraft(
                meeting.getId(), created.id(),
                new InvitationApiModels.SaveDraftRequest(0, null, document)
        );
        InvitationApiModels.InvitationDetailResponse published = invitationService.publish(meeting.getId(), created.id());

        entityManager.flush();
        entityManager.clear();
        InvitationApiModels.InvitationDetailResponse reloaded = invitationService.detail(meeting.getId(), created.id());
        InvitationApiModels.ShareLinkResponse reloadedChannel = invitationService
                .shareLinks(meeting.getId(), created.id()).get(0);

        assertThat(reloaded.status()).isEqualTo(InvitationStatus.PUBLISHED);
        assertThat(reloaded.draftRevision()).isEqualTo(1);
        assertThat(reloaded.draftDocument().h5().elements().get(0).props().get("text"))
                .isEqualTo("不可变发布内容");
        assertThat(reloaded.latestPublishedVersion().meetingSnapshot().name()).isEqualTo("发布前会议名称");
        assertThat(reloaded.latestPublishedVersion().meetingSnapshot().location()).isEqualTo("发布前会场");
        assertThat(reloadedChannel.id()).isEqualTo(channel.id());
        assertThat(reloadedChannel.currentVersionNo()).isEqualTo(published.latestPublishedVersion().versionNo());
    }

    private InvitationDesignDocumentV1 documentWithText(String text) {
        InvitationDesignDocumentV1.InvitationElement element = new InvitationDesignDocumentV1.InvitationElement(
                "title", InvitationElementType.TEXT, 20, 20, 300, 60, 0, 0, false,
                Map.of("text", text, "fontFamily", "Inter, system-ui, sans-serif", "color", "#123456")
        );
        return new InvitationDesignDocumentV1(
                1,
                new InvitationDesignDocumentV1.InvitationCanvas(375, 667, "#ffffff", null, java.util.List.of(element)),
                new InvitationDesignDocumentV1.InvitationCanvas(1080, 1920, "#ffffff", null, java.util.List.of())
        );
    }
}
