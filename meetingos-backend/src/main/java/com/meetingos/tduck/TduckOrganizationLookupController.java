package com.meetingos.tduck;

import com.meetingos.common.ApiResponse;
import com.meetingos.publicapi.PublicRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/tduck")
public class TduckOrganizationLookupController {
    private final TduckOrganizationLookupService service;
    private final PublicRateLimitService publicRateLimit;

    public TduckOrganizationLookupController(TduckOrganizationLookupService service, PublicRateLimitService publicRateLimit) {
        this.service = service;
        this.publicRateLimit = publicRateLimit;
    }

    @GetMapping("/organization-lookup-token")
    public ApiResponse<TduckOrganizationLookupService.LookupConfig> token(@RequestParam String formKey,
            HttpServletRequest request, HttpServletResponse response) {
        publicRateLimit.check(request);
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(service.issueToken(formKey));
    }

    @PostMapping("/organization-suggestions")
    public ApiResponse<TianyanchaClient.Suggestions> suggestions(@Valid @RequestBody LookupRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        // 联想单独限流，避免连续输入消耗手机号预检查、签到等公开接口的共享额度。
        return ApiResponse.ok(service.search(body.formKey(), body.token(), body.word(), publicRateLimit.resolveClientIp(request)));
    }

    public record LookupRequest(@NotBlank @Size(max = 120) String formKey,
                                @NotBlank @Size(max = 255) String token,
                                @NotBlank @Size(max = 120) String word) { }
}
