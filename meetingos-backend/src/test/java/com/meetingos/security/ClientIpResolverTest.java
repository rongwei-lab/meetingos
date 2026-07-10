package com.meetingos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private PublicQueryProperties properties;
    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new PublicQueryProperties();
        resolver = new ClientIpResolver(properties);
    }

    @Test
    void ignoresForwardedHeadersWhenTrustIsDisabled() {
        MockHttpServletRequest request = request("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void usesAndNormalizesFirstValidForwardedAddressWhenTrusted() {
        properties.setTrustForwardHeaders(true);
        MockHttpServletRequest request = request("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.000.113.010, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void rejectsMalformedOrNonIpForwardedValues() {
        properties.setTrustForwardHeaders(true);
        MockHttpServletRequest request = request("10.0.0.8");
        request.addHeader("X-Forwarded-For", "attacker.example, 203.0.113.10");
        request.addHeader("X-Real-IP", "999.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void acceptsIpv6LiteralsWithoutResolvingHostnames() {
        properties.setTrustForwardHeaders(true);
        MockHttpServletRequest request = request("10.0.0.8");
        request.addHeader("X-Real-IP", "[2001:db8::1]");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
