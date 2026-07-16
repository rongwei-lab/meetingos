package com.meetingos.auth;

import com.meetingos.operationlog.AuditLog;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.security.JwtCookieService;
import com.meetingos.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerAuditTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtCookieService jwtCookieService;

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse response;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, jwtCookieService, operationLogService);
    }

    @Test
    void successfulLoginAuditsOnlySafeAccountMetadata() {
        LoginRequest request = new LoginRequest("audit-admin", "never-record-this-password");
        LoginResponse login = new LoginResponse(
                "never-record-this-jwt",
                "Bearer",
                8L,
                "audit-admin",
                "审计管理员",
                Set.of(Role.SUPER_ADMIN)
        );
        when(authService.login(request, httpRequest)).thenReturn(login);

        controller.login(request, httpRequest, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operationLogService).recordForActor(
                eq("audit-admin"),
                eq("AUTH_LOGIN"),
                eq("APP_USER"),
                eq("audit-admin"),
                eq(true),
                eq("登录成功"),
                same(httpRequest),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).containsOnly(Map.entry("authMethod", "PASSWORD"));
        verify(jwtCookieService).attachLoginCookie(response, "never-record-this-jwt");
    }

    @Test
    void failedLoginDoesNotCreateAnOperationLogThatCanBeBruteForced() {
        LoginRequest request = new LoginRequest("audit-admin", "wrong-password");
        when(authService.login(request, httpRequest)).thenThrow(new BadCredentialsException("用户名或密码错误"));

        assertThatThrownBy(() -> controller.login(request, httpRequest, response))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtCookieService, operationLogService);
    }

    @Test
    void logoutUsesAuditAnnotation() throws Exception {
        Method method = AuthController.class.getMethod("logout", HttpServletResponse.class);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.action()).isEqualTo("AUTH_LOGOUT");
        assertThat(auditLog.targetType()).isEqualTo("AUTH_SESSION");
    }
}
