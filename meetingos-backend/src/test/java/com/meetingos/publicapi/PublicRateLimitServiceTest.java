package com.meetingos.publicapi;

import com.meetingos.common.BusinessException;
import com.meetingos.security.ClientIpResolver;
import com.meetingos.security.PublicQueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicRateLimitServiceTest {

    @Test
    void preservesConfiguredPerMinuteThreshold() {
        PublicQueryProperties properties = properties(2);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(resolver.resolve(request)).thenReturn("203.0.113.30");
        PublicRateLimitService service = new PublicRateLimitService(properties, resolver, 10);

        service.check(request);
        service.check(request);

        assertThatThrownBy(() -> service.check(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公开查询过于频繁");
    }

    @Test
    void capsTrackedClientsAndReleasesExpiredEntries() {
        PublicQueryProperties properties = properties(30);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        HttpServletRequest first = request(resolver, "203.0.113.31");
        HttpServletRequest second = request(resolver, "203.0.113.32");
        HttpServletRequest third = request(resolver, "203.0.113.33");
        PublicRateLimitService service = new PublicRateLimitService(properties, resolver, 2);

        service.check(first);
        service.check(second);
        assertThatThrownBy(() -> service.check(third)).isInstanceOf(BusinessException.class);
        assertThat(service.trackedClientCount()).isEqualTo(2);

        service.cleanupExpiredEntries(Long.MAX_VALUE);
        assertThat(service.trackedClientCount()).isZero();

        service.check(third);
        assertThat(service.trackedClientCount()).isEqualTo(1);
    }

    private PublicQueryProperties properties(int rateLimit) {
        PublicQueryProperties properties = new PublicQueryProperties();
        properties.setRateLimitPerMinute(rateLimit);
        return properties;
    }

    private HttpServletRequest request(ClientIpResolver resolver, String clientIp) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(resolver.resolve(request)).thenReturn(clientIp);
        return request;
    }
}
