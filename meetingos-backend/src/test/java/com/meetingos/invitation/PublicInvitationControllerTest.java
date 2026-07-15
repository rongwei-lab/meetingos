package com.meetingos.invitation;

import com.meetingos.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicInvitationControllerTest {

    @Test
    void detailOnlyIssuesRetentionAlignedCookieAndDoesNotRecordPageView() {
        PublicInvitationService publicService = mock(PublicInvitationService.class);
        InvitationProperties properties = new InvitationProperties(
                "test-invitation-tracking-secret-that-is-long-enough", 180, ""
        );
        InvitationTrackingTokenService tokenService = new InvitationTrackingTokenService(properties);
        SecurityProperties securityProperties = new SecurityProperties(
                "test-jwt-secret-test-jwt-secret-123456", "test", 60, false
        );
        PublicInvitationController controller = new PublicInvitationController(
                publicService, tokenService, securityProperties, properties
        );
        when(publicService.detail("share-token")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.detail("share-token", request, response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("MOS_INV_VISITOR=")
                .contains("Max-Age=15552000")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        verify(publicService).detail("share-token");
        verify(publicService, never()).recordView(any(), any(), any(), any(), any());
    }
}
