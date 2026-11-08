package com.meetingos.tduck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.security.TduckProperties;
import com.meetingos.settings.SystemSettingsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TduckOrganizationFieldConfigTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void detectsSavedOrganizationSwitchInObjectAndStringSchemes(boolean stringScheme) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> scheme = Map.of("typeId", "INPUT", "config", Map.of(
                "dataType", Map.of("type", "organization"), "meetingOsTianyanchaEnabled", true));
        Object saved = stringScheme ? mapper.writeValueAsString(scheme) : scheme;
        assertThat(checkField(mapper.writeValueAsString(Map.of("scheme", saved)))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"scheme\":{\"typeId\":\"INPUT\",\"config\":{\"dataType\":{\"type\":\"organization\"}}}}",
            "{\"scheme\":{\"typeId\":\"INPUT\",\"config\":{\"dataType\":{\"type\":\"organization\"},\"meetingOsTianyanchaEnabled\":false}}}",
            "{\"scheme\":{\"typeId\":\"INPUT\",\"config\":{\"dataType\":{\"type\":\"phone\"},\"meetingOsTianyanchaEnabled\":true}}}",
            "{\"scheme\":{\"typeId\":\"SELECT\",\"config\":{\"dataType\":{\"type\":\"organization\"},\"meetingOsTianyanchaEnabled\":true}}}"
    })
    void ordinaryFieldsAndDisabledSwitchDoNotEnablePaidLookup(String field) throws Exception {
        assertThat(checkField(field)).isFalse();
    }

    private boolean checkField(String field) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String payload = exchange.getRequestURI().getPath().equals("/login/account")
                    ? "{\"code\":200,\"data\":{\"token\":\"tduck-token\"}}"
                    : "{\"code\":200,\"data\":{\"formItems\":[" + field + "]}}";
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var properties = new TduckProperties("secret", "", "", url, url,
                    "test@example.test", "test-password", Duration.ofSeconds(1), Duration.ofSeconds(1));
            return new TduckClient(properties, mock(SystemSettingsService.class), RestClient.builder())
                    .organizationLookupEnabled("bound-form");
        } finally {
            server.stop(0);
        }
    }
}
