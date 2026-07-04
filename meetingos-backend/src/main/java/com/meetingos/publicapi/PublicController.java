package com.meetingos.publicapi;

import com.meetingos.common.ApiResponse;
import com.meetingos.attendee.Attendee;
import com.meetingos.checkin.CheckinRequest;
import com.meetingos.checkin.CheckinResponse;
import com.meetingos.checkin.CheckinService;
import com.meetingos.checkin.CheckinType;
import com.meetingos.settings.BrandingResponse;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.TduckService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final TduckService tduckService;
    private final PublicRateLimitService publicRateLimitService;
    private final PublicQueryService publicQueryService;
    private final PublicCaptchaService publicCaptchaService;
    private final SystemSettingsService systemSettingsService;
    private final CheckinService checkinService;

    public PublicController(TduckService tduckService,
                            PublicRateLimitService publicRateLimitService,
                            PublicQueryService publicQueryService,
                            PublicCaptchaService publicCaptchaService,
                            SystemSettingsService systemSettingsService,
                            CheckinService checkinService) {
        this.tduckService = tduckService;
        this.publicRateLimitService = publicRateLimitService;
        this.publicQueryService = publicQueryService;
        this.publicCaptchaService = publicCaptchaService;
        this.systemSettingsService = systemSettingsService;
        this.checkinService = checkinService;
    }

    @GetMapping("/query-settings")
    public ApiResponse<MeetingQuerySettingsResponse> querySettings(@RequestParam(value = "token", required = false) String publicQueryToken) {
        /*
         * token 缺失时也交给业务层统一返回“公开查询链接无效”，避免旧 meetingCode 链接
         * 触发 Spring 参数绑定异常并在公网表现为 500。
         */
        return ApiResponse.ok(publicQueryService.publicSettings(publicQueryToken));
    }

    @GetMapping("/branding")
    public ApiResponse<BrandingResponse> branding() {
        return ApiResponse.ok(systemSettingsService.branding());
    }

    @GetMapping("/captcha")
    public ApiResponse<PublicCaptchaResponse> captcha(HttpServletRequest request) {
        publicRateLimitService.check(request);
        return ApiResponse.ok(publicCaptchaService.create());
    }

    @PostMapping("/seat-query")
    public ApiResponse<Object> seatQuery(@Valid @RequestBody PublicQueryRequest queryRequest,
                                         HttpServletRequest request) {
        publicRateLimitService.check(request);
        verifyCaptcha(queryRequest);
        return ApiResponse.ok(publicQueryService.publicSeatQueryResponse(queryRequest, clientIp(request), request.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/room-query")
    public ApiResponse<Object> roomQuery(@Valid @RequestBody PublicQueryRequest queryRequest,
                                         HttpServletRequest request) {
        publicRateLimitService.check(request);
        verifyCaptcha(queryRequest);
        return ApiResponse.ok(publicQueryService.publicRoomQueryResponse(queryRequest, clientIp(request), request.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/dinner-query")
    public ApiResponse<Object> dinnerQuery(@Valid @RequestBody PublicQueryRequest queryRequest,
                                           HttpServletRequest request) {
        publicRateLimitService.check(request);
        verifyCaptcha(queryRequest);
        return ApiResponse.ok(publicQueryService.publicDinnerQueryResponse(queryRequest, clientIp(request), request.getHeader(HttpHeaders.USER_AGENT)));
    }

    @GetMapping("/checkin-code")
    public ApiResponse<Map<String, String>> checkinCode(@RequestParam Long meetingId, @RequestParam String attendeeCode) {
        String compact = "MEETINGOS-CHECKIN:" + meetingId + ":" + attendeeCode;
        String full = "/public/checkin-code?type=checkin&meetingId=" + meetingId + "&attendeeCode=" + attendeeCode + "#" + compact;
        return ApiResponse.ok(Map.of("compactCode", compact, "fullCode", full));
    }

    @PostMapping("/checkin")
    public ApiResponse<CheckinResponse> selfCheckin(@Valid @RequestBody PublicQueryRequest queryRequest,
                                                    HttpServletRequest request) {
        publicRateLimitService.check(request);
        verifyCaptcha(queryRequest);
        PublicAttendeeMatch match = null;
        try {
            /*
             * H5 自助签到复用公开查询的身份字段和开放窗口，只在唯一匹配且已审核通过后
             * 转交给 CheckinService。真正写签到状态时仍使用原有悲观锁和重复签到拦截。
             */
            match = publicQueryService.resolvePublicAttendee(queryRequest);
            CheckinResponse response = checkinService.checkin(new CheckinRequest(
                    match.meeting().getId(),
                    null,
                    match.attendee().getAttendeeCode(),
                    null,
                    null,
                    "H5自助签到",
                    CheckinType.SEARCH,
                    null
            ));
            publicQueryService.recordPublicCheckin(
                    queryRequest,
                    match.attendee(),
                    response.message(),
                    null,
                    clientIp(request),
                    request.getHeader(HttpHeaders.USER_AGENT)
            );
            return ApiResponse.ok(response);
        } catch (RuntimeException ex) {
            recordPublicCheckinFailure(queryRequest, match, ex, request);
            throw ex;
        }
    }

    @GetMapping("/meetings/{meetingCode}/registration")
    public ApiResponse<RegistrationInfo> registration(@PathVariable String meetingCode) {
        return ApiResponse.ok(tduckService.registrationInfo(meetingCode));
    }

    @GetMapping("/registration/redirect/{meetingCode}")
    public void redirect(@PathVariable String meetingCode, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.sendRedirect(tduckService.resolveRedirectUrl(meetingCode));
    }

    private String clientIp(HttpServletRequest request) {
        return publicRateLimitService.resolveClientIp(request);
    }

    private void verifyCaptcha(PublicQueryRequest queryRequest) {
        publicCaptchaService.verify(queryRequest.captchaId(), queryRequest.captchaAnswer());
    }

    private void recordPublicCheckinFailure(PublicQueryRequest queryRequest,
                                            PublicAttendeeMatch match,
                                            RuntimeException error,
                                            HttpServletRequest request) {
        try {
            Attendee attendee = match == null ? publicQueryService.identifySingleMatchedAttendee(queryRequest) : match.attendee();
            publicQueryService.recordPublicCheckin(
                    queryRequest,
                    attendee,
                    null,
                    error.getMessage(),
                    clientIp(request),
                    request.getHeader(HttpHeaders.USER_AGENT)
            );
        } catch (RuntimeException ignored) {
            // 公开签到的历史记录不能影响主流程错误返回，尤其是会议码错误或查询字段缺失时。
        }
    }
}
