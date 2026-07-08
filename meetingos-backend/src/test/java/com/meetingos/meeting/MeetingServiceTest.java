package com.meetingos.meeting;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.common.BusinessException;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.permission.MeetingUserPermissionRepository;
import com.meetingos.security.AppUserRepository;
import com.meetingos.seating.SeatAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeetingServiceTest {

    private MeetingRepository meetingRepository;
    private MeetingService meetingService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        meetingService = new MeetingService(
                meetingRepository,
                mock(AttendeeRepository.class),
                mock(SeatAssignmentRepository.class),
                mock(DinnerAssignmentRepository.class),
                mock(MeetingUserPermissionRepository.class),
                mock(AppUserRepository.class)
        );

        meeting = new Meeting();
        meeting.setId(2L);
        meeting.setName("数字经济论坛");
        when(meetingRepository.findById(2L)).thenReturn(Optional.of(meeting));
    }

    @Test
    void printSettingsReturnsBlankTemplateWhenNotConfigured() {
        MeetingPrintSettingsResponse response = meetingService.printSettings(2L);

        assertThat(response.meetingId()).isEqualTo(2L);
        assertThat(response.nameplateTemplateUrl()).isNull();
        assertThat(response.nameplateFields()).containsExactly(new NameplateFieldSetting("name", null, null));
    }

    @Test
    void updatePrintSettingsStoresUploadedTemplateUrl() {
        MeetingPrintSettingsResponse response = meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest("/uploads/images/nameplate-template.png", null)
        );

        assertThat(response.meetingId()).isEqualTo(2L);
        assertThat(response.nameplateTemplateUrl()).isEqualTo("/uploads/images/nameplate-template.png");
        assertThat(meeting.getNameplateTemplateUrl()).isEqualTo("/uploads/images/nameplate-template.png");
    }

    @Test
    void updatePrintSettingsAllowsClearingTemplateUrl() {
        meeting.setNameplateTemplateUrl("/uploads/images/nameplate-template.png");

        MeetingPrintSettingsResponse response = meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(" ", null)
        );

        assertThat(response.nameplateTemplateUrl()).isNull();
        assertThat(meeting.getNameplateTemplateUrl()).isNull();
    }

    @Test
    void updatePrintSettingsRejectsUnsafeTemplateUrl() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest("javascript:alert(1)", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("席签模板仅允许");
    }

    @Test
    void updatePrintSettingsRejectsPathTraversalUnderUploads() {
        /*
         * 模板 URL 后续会写入打印页 background-image；即使是同源路径，也不能允许
         * ../ 或反斜杠这类路径穿越写入数据库，避免绕过上传目录边界。
         */
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest("/uploads/../private/secret.png", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("席签模板仅允许");
    }

    @Test
    void updatePrintSettingsStoresOrderedNameplateFields() {
        MeetingPrintSettingsResponse response = meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(
                        "/uploads/images/nameplate-template.png",
                        List.of(
                                new NameplateFieldSetting("name", 120, "#000000", 0.12, true),
                                new NameplateFieldSetting("company", 24, "#d03020", 0.04, false),
                                new NameplateFieldSetting("title", null, null)
                        )
                )
        );

        assertThat(response.nameplateTemplateUrl()).isEqualTo("/uploads/images/nameplate-template.png");
        assertThat(response.nameplateFields()).containsExactly(
                new NameplateFieldSetting("name", 120, "#000000", 0.12, true),
                new NameplateFieldSetting("company", 24, "#d03020", 0.04, false),
                new NameplateFieldSetting("title", null, null)
        );
        assertThat(meeting.getNameplateFields()).containsExactlyElementsOf(response.nameplateFields());
    }

    @Test
    void updatePrintSettingsRejectsEmptyNameplateFields() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(null, List.of())
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个");
    }

    @Test
    void updatePrintSettingsRejectsDuplicateNameplateFields() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(null, List.of(
                        new NameplateFieldSetting("name", null, null),
                        new NameplateFieldSetting("name", 40, "#000000")
                ))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复");
    }

    @Test
    void updatePrintSettingsRejectsInvalidNameplateFieldKey() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(null, List.of(new NameplateFieldSetting("unknown", null, null)))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void updatePrintSettingsRejectsInvalidNameplateColor() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(null, List.of(new NameplateFieldSetting("name", null, "red")))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hex");
    }

    @Test
    void updatePrintSettingsRejectsOutOfRangeNameplateFontSize() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(null, List.of(new NameplateFieldSetting("name", 121, "#000000")))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10-120pt");
    }

    @Test
    void updatePrintSettingsRejectsOutOfRangeNameplateLetterSpacing() {
        assertThatThrownBy(() -> meetingService.updatePrintSettings(
                2L,
                new MeetingPrintSettingsRequest(null, List.of(new NameplateFieldSetting("name", null, "#000000", 1.2)))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0-1em");
    }
}
