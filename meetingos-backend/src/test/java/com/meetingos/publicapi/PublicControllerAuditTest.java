package com.meetingos.publicapi;

import com.meetingos.attendee.Attendee;
import com.meetingos.checkin.CheckinResponse;
import com.meetingos.checkin.CheckinResultStatus;
import com.meetingos.checkin.CheckinService;
import com.meetingos.meeting.Meeting;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.TduckService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicControllerAuditTest {

    @Mock
    private TduckService tduckService;

    @Mock
    private PublicRateLimitService publicRateLimitService;

    @Mock
    private PublicQueryService publicQueryService;

    @Mock
    private PublicCaptchaService publicCaptchaService;

    @Mock
    private SystemSettingsService systemSettingsService;

    @Mock
    private CheckinService checkinService;

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private HttpServletRequest httpRequest;

    private PublicController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicController(
                tduckService,
                publicRateLimitService,
                publicQueryService,
                publicCaptchaService,
                systemSettingsService,
                checkinService,
                operationLogService
        );
        lenient().when(publicRateLimitService.resolveClientIp(httpRequest)).thenReturn("203.0.113.11");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulSelfCheckinAuditsOnlyObjectIdsAndResult() {
        PublicQueryRequest request = new PublicQueryRequest(
                "public-query-token",
                Map.of(PublicQueryField.PHONE, "13800000001"),
                "captcha-id",
                "captcha-answer"
        );
        Meeting meeting = new Meeting();
        meeting.setId(17L);
        Attendee attendee = new Attendee();
        attendee.setId(29L);
        attendee.setAttendeeCode("NDEF2026-0029");
        when(publicQueryService.resolvePublicAttendee(request)).thenReturn(new PublicAttendeeMatch(meeting, attendee));
        when(checkinService.checkin(any())).thenReturn(new CheckinResponse(
                CheckinResultStatus.SUCCESS,
                29L,
                "不应进入审计详情的姓名",
                "****0001",
                LocalDateTime.now(),
                "H5自助签到",
                "system",
                "签到成功"
        ));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin-in-browser", null, java.util.List.of())
        );

        controller.selfCheckin(request, httpRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operationLogService).recordForActor(
                eq("public"),
                eq("PUBLIC_SELF_CHECKIN"),
                eq("ATTENDEE"),
                eq("29"),
                eq(true),
                eq("自助签到成功"),
                isNull(),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).containsOnly(
                Map.entry("meetingId", 17L),
                Map.entry("attendeeId", 29L),
                Map.entry("resultStatus", "SUCCESS")
        );
    }

    @Test
    void duplicateSelfCheckinDoesNotExpandTheOperationLog() {
        PublicQueryRequest request = new PublicQueryRequest(
                "public-query-token",
                Map.of(PublicQueryField.PHONE, "13800000001")
        );
        Meeting meeting = new Meeting();
        meeting.setId(17L);
        Attendee attendee = new Attendee();
        attendee.setId(29L);
        attendee.setAttendeeCode("NDEF2026-0029");
        when(publicQueryService.resolvePublicAttendee(request)).thenReturn(new PublicAttendeeMatch(meeting, attendee));
        when(checkinService.checkin(any())).thenReturn(new CheckinResponse(
                CheckinResultStatus.DUPLICATE,
                29L,
                "参会人",
                "****0001",
                LocalDateTime.now(),
                "H5自助签到",
                "system",
                "重复签到"
        ));

        controller.selfCheckin(request, httpRequest);

        verifyNoInteractions(operationLogService);
    }

    @Test
    void checkinPreviewOnlyVerifiesIdentityWithoutWritingCheckin() {
        PublicQueryRequest request = new PublicQueryRequest(
                "public-query-token",
                Map.of(PublicQueryField.PHONE, "13800000001"),
                "captcha-id",
                "captcha-answer"
        );
        PublicCheckinPreviewResponse preview = new PublicCheckinPreviewResponse(
                true,
                29L,
                "参会人",
                "****0001",
                null,
                "身份核验通过，请确认签到。"
        );
        when(publicQueryService.previewSelfCheckin(request)).thenReturn(preview);

        var response = controller.previewSelfCheckin(request, httpRequest);

        assertThat(response.data()).isSameAs(preview);
        verify(publicRateLimitService).check(httpRequest);
        verify(publicCaptchaService).verify("captcha-id", "captcha-answer");
        verify(publicQueryService).previewSelfCheckin(request);
        verifyNoInteractions(checkinService, operationLogService);
    }

    @Test
    void combinedQueryAppliesRateLimitAndCaptchaOnlyOnce() {
        PublicQueryRequest request = new PublicQueryRequest(
                "public-query-token",
                Map.of(PublicQueryField.PHONE, "13800000001"),
                "captcha-id",
                "captcha-answer"
        );
        PublicCombinedQueryResponse combined = new PublicCombinedQueryResponse(
                null, "尚未排座", null, "尚未安排房间", null, "尚未安排晚宴"
        );
        when(publicQueryService.publicCombinedQueryResponse(request, "203.0.113.11", null)).thenReturn(combined);

        var response = controller.combinedQuery(request, httpRequest);

        assertThat(response.data()).isSameAs(combined);
        verify(publicRateLimitService).check(httpRequest);
        verify(publicCaptchaService).verifyAndConsume("captcha-id", "captcha-answer");
        verify(publicQueryService).publicCombinedQueryResponse(request, "203.0.113.11", null);
    }
}
