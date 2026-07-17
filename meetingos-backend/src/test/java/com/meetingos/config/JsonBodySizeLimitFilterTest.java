package com.meetingos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBodySizeLimitFilterTest {

    @Test
    void rejectsJsonBeforeDownstreamParsingWhenBodyExceedsLimit() throws Exception {
        JsonBodySizeLimitFilter filter = new JsonBodySizeLimitFilter(new ObjectMapper().findAndRegisterModules(), 10);
        MockHttpServletRequest request = jsonRequest("{\"value\":123}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized body must not reach downstream filters");
        });

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void replaysAcceptedJsonBodyToDownstreamParser() throws Exception {
        JsonBodySizeLimitFilter filter = new JsonBodySizeLimitFilter(new ObjectMapper().findAndRegisterModules(), 64);
        MockHttpServletRequest request = jsonRequest("{\"value\":1}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> received = new AtomicReference<>();
        FilterChain chain = (wrappedRequest, ignoredResponse) -> received.set(
                new String(wrappedRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
        );

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(received.get()).isEqualTo("{\"value\":1}");
    }

    @Test
    void rejectsOversizedPlainTextOnEveryWebhookCompatibilityPath() throws Exception {
        JsonBodySizeLimitFilter filter = new JsonBodySizeLimitFilter(new ObjectMapper().findAndRegisterModules(), 10);

        for (String path : new String[]{
                "/api/tduck/webhook",
                "/api/public/tduck/webhook",
                "/api/integrations/tduck/webhook"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setContentType(MediaType.TEXT_PLAIN_VALUE);
            request.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new AssertionError("oversized webhook body must not reach downstream filters");
            });

            assertThat(response.getStatus()).as(path).isEqualTo(413);
            assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        }
    }

    private MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/seat-query");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
