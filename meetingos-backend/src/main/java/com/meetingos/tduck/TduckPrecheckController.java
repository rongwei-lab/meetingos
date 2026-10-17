package com.meetingos.tduck;

import com.meetingos.common.ApiResponse;
import com.meetingos.publicapi.PublicRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** TDuck 填写页使用的公开手机号重复预检查接口。 */
@RestController
@RequestMapping("/api/public/tduck")
public class TduckPrecheckController {

    private final TduckPrecheckService precheckService;
    private final PublicRateLimitService publicRateLimitService;

    public TduckPrecheckController(TduckPrecheckService precheckService,
                                   PublicRateLimitService publicRateLimitService) {
        this.precheckService = precheckService;
        this.publicRateLimitService = publicRateLimitService;
    }

    @GetMapping("/precheck-token")
    public ApiResponse<TduckPrecheckTokenResponse> token(@RequestParam String formKey,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        publicRateLimitService.check(request);
        noStore(response);
        return ApiResponse.ok(precheckService.issueToken(formKey));
    }

    @PostMapping("/phone-precheck")
    public ApiResponse<TduckPhonePrecheckResponse> check(@Valid @RequestBody TduckPhonePrecheckRequest request,
                                                         HttpServletRequest servletRequest,
                                                         HttpServletResponse response) {
        publicRateLimitService.check(servletRequest);
        noStore(response);
        return ApiResponse.ok(precheckService.checkPhone(request));
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
