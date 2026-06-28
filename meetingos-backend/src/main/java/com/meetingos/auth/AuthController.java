package com.meetingos.auth;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.BusinessException;
import com.meetingos.security.JwtCookieService;
import com.meetingos.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtCookieService jwtCookieService;

    public AuthController(AuthService authService, JwtCookieService jwtCookieService) {
        this.authService = authService;
        this.jwtCookieService = jwtCookieService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse response) {
        LoginResponse login = authService.login(request, httpRequest);
        jwtCookieService.attachLoginCookie(response, login.accessToken());
        return ApiResponse.ok(login);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        jwtCookieService.clearLoginCookie(response);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok(SecurityUtils.currentUser()
                .map(user -> new CurrentUserResponse(user.id(), user.username(), user.displayName(), user.roles()))
                .orElseThrow(() -> BusinessException.unauthorized("未登录")));
    }
}
