package com.meetingos.auth;

import com.meetingos.common.ApiResponse;
import com.meetingos.common.BusinessException;
import com.meetingos.operationlog.AuditLog;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.security.JwtCookieService;
import com.meetingos.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtCookieService jwtCookieService;
    private final OperationLogService operationLogService;

    public AuthController(AuthService authService,
                          JwtCookieService jwtCookieService,
                          OperationLogService operationLogService) {
        this.authService = authService;
        this.jwtCookieService = jwtCookieService;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse response) {
        LoginResponse login = authService.login(request, httpRequest);
        jwtCookieService.attachLoginCookie(response, login.accessToken());
        recordLoginSuccess(httpRequest, login);
        return ApiResponse.ok(login);
    }

    @PostMapping("/logout")
    @AuditLog(action = "AUTH_LOGOUT", targetType = "AUTH_SESSION")
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

    private void recordLoginSuccess(HttpServletRequest request, LoginResponse login) {
        try {
            /*
             * 登录失败入口可被未认证请求反复探测，不能把每次失败都写入操作日志。
             * 这里仅保留成功认证的账号与来源 IP；密码和签发出的 JWT 始终不进入审计数据。
             */
            operationLogService.recordForActor(
                    login.username(),
                    "AUTH_LOGIN",
                    "APP_USER",
                    login.username(),
                    true,
                    "登录成功",
                    request,
                    Map.of("authMethod", "PASSWORD")
            );
        } catch (RuntimeException ex) {
            // 审计是旁路能力，日志基础设施异常不能影响已完成的认证和 Cookie 下发。
            log.warn("Authentication audit log recording failed", ex);
        }
    }
}
