package com.meetingos.statistics.dashboard;

import com.meetingos.permission.MeetingAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    @Test
    void configReturnsCurrentMeetingManageCapabilityInsteadOfInferringFromGlobalRole() {
        DashboardService dashboardService = mock(DashboardService.class);
        MeetingAccessService meetingAccessService = mock(MeetingAccessService.class);
        DashboardController controller = new DashboardController(dashboardService, meetingAccessService);
        DashboardConfigResponse stored = new DashboardConfigResponse(7L, List.of(), null, false);
        when(dashboardService.config(7L)).thenReturn(stored);
        when(meetingAccessService.canManage(7L)).thenReturn(true);

        DashboardConfigResponse response = controller.config(7L).data();

        assertThat(response.canManage()).isTrue();
        assertThat(response.meetingId()).isEqualTo(7L);
    }

    @Test
    void fullValueFieldCatalogAndQueryRequireMeetingManagePermission() throws NoSuchMethodException {
        PreAuthorize fieldsAuthorization = DashboardController.class
                .getMethod("fields", Long.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize queryAuthorization = DashboardController.class
                .getMethod("query", DashboardQueryRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(fieldsAuthorization.value()).contains("canManage");
        assertThat(queryAuthorization.value()).contains("canManage");
    }
}
