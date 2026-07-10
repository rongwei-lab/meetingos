package com.meetingos.auth;

import com.meetingos.common.BusinessException;
import com.meetingos.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginRateLimitServiceTest {

    private ClientIpResolver clientIpResolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        clientIpResolver = mock(ClientIpResolver.class);
        request = mock(HttpServletRequest.class);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.20");
    }

    @Test
    void locksSameUserAndClientAfterFiveFailures() {
        LoginRateLimitService service = new LoginRateLimitService(clientIpResolver, 10);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.recordFailure("admin", request, new BadCredentialsException("bad")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> service.assertAllowed("admin", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");
    }

    @Test
    void capsTrackedKeysAndReleasesExpiredEntries() {
        LoginRateLimitService service = new LoginRateLimitService(clientIpResolver, 2);

        recordFailure(service, "first");
        recordFailure(service, "second");
        recordFailure(service, "third");
        assertThat(service.trackedKeyCount()).isEqualTo(2);

        service.cleanupExpiredEntries(Long.MAX_VALUE);
        assertThat(service.trackedKeyCount()).isZero();

        recordFailure(service, "third");
        assertThat(service.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void boundsUsernamePartOfRateLimitKeyToDatabaseLength() {
        LoginRateLimitService service = new LoginRateLimitService(clientIpResolver, 10);
        String sharedPrefix = "x".repeat(64);

        recordFailure(service, sharedPrefix + "first-unbounded-suffix");
        recordFailure(service, sharedPrefix + "second-unbounded-suffix");

        assertThat(service.trackedKeyCount()).isEqualTo(1);
    }

    private void recordFailure(LoginRateLimitService service, String username) {
        assertThatThrownBy(() -> service.recordFailure(username, request, new BadCredentialsException("bad")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
