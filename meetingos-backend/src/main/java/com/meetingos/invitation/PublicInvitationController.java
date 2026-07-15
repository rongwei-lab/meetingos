package com.meetingos.invitation;

import com.meetingos.common.ApiResponse;
import com.meetingos.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

import static com.meetingos.invitation.InvitationApiModels.*;

@RestController
@RequestMapping("/api/public/invitations")
public class PublicInvitationController {
    private static final String VISITOR_COOKIE = "MOS_INV_VISITOR";

    private final PublicInvitationService invitationService;
    private final InvitationTrackingTokenService tokenService;
    private final SecurityProperties securityProperties;
    private final int visitorCookieDays;

    public PublicInvitationController(PublicInvitationService invitationService,
                                      InvitationTrackingTokenService tokenService,
                                      SecurityProperties securityProperties,
                                      InvitationProperties invitationProperties) {
        this.invitationService = invitationService;
        this.tokenService = tokenService;
        this.securityProperties = securityProperties;
        this.visitorCookieDays = invitationProperties.eventRetentionDays();
    }

    @GetMapping("/{shareToken}")
    public ApiResponse<PublicInvitationResponse> detail(@PathVariable String shareToken,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        ensureVisitor(request, response);
        return ApiResponse.ok(invitationService.detail(shareToken));
    }

    @PostMapping("/{shareToken}/views")
    public ApiResponse<EventReceipt> view(@PathVariable String shareToken,
                                          @Valid @RequestBody PublicEventRequest eventRequest,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        String visitor = ensureVisitor(request, response);
        return ApiResponse.ok(invitationService.recordView(
                shareToken, eventRequest, visitor,
                request.getHeader(HttpHeaders.USER_AGENT), request.getHeader(HttpHeaders.REFERER)
        ));
    }

    @PostMapping("/{shareToken}/registration-intents")
    public ApiResponse<RegistrationIntentResponse> registrationIntent(
            @PathVariable String shareToken,
            @Valid @RequestBody PublicEventRequest eventRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String visitor = ensureVisitor(request, response);
        return ApiResponse.ok(invitationService.registrationIntent(
                shareToken, eventRequest, visitor,
                request.getHeader(HttpHeaders.USER_AGENT), request.getHeader(HttpHeaders.REFERER)
        ));
    }

    private String ensureVisitor(HttpServletRequest request, HttpServletResponse response) {
        String value = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(cookie -> VISITOR_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(candidate -> candidate.matches("[A-Za-z0-9_-]{20,80}"))
                .findFirst()
                .orElse(null);
        if (value != null) {
            return value;
        }
        value = tokenService.newOpaqueId();
        ResponseCookie cookie = ResponseCookie.from(VISITOR_COOKIE, value)
                .httpOnly(true)
                .secure(securityProperties.secureCookies())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(visitorCookieDays))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return value;
    }
}
