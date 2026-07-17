package com.meetingos.invitation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvitationAdminControllerAttributionTest {

    @Test
    void attributionListExposesTotalAndTruncationWithoutChangingBodyShape() {
        InvitationAnalyticsService analyticsService = mock(InvitationAnalyticsService.class);
        InvitationAdminController controller = new InvitationAdminController(
                mock(InvitationService.class),
                mock(InvitationAssetService.class),
                analyticsService
        );
        when(analyticsService.attributionPage(1L, 10L)).thenReturn(
                new InvitationAnalyticsService.AttributionPage(List.of(), 2_500, 2_000, true)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        var body = controller.attributions(1L, 10L, response);

        assertThat(body.data()).isEmpty();
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("2500");
        assertThat(response.getHeader("X-Result-Limit")).isEqualTo("2000");
        assertThat(response.getHeader("X-Result-Truncated")).isEqualTo("true");
    }
}
