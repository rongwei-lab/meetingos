package com.meetingos.tduck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.security.TduckProperties;
import com.meetingos.settings.SystemSettingsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TduckClientFormListTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listFormsDoesNotFetchTwoDetailEndpointsPerRow() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger detailRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/user/form/") && !path.equals("/user/form/page")) {
                detailRequests.incrementAndGet();
            }
            if (path.equals("/login/account")) {
                respond(exchange, "{\"code\":200,\"data\":{\"token\":\"test-token\"}}");
                return;
            }
            if (path.equals("/user/form/page")) {
                respond(exchange, """
                        {"code":200,"data":{"records":[
                          {"formKey":"form-a","name":"表单 A","status":"2"},
                          {"formKey":"form-b","name":"表单 B","status":"2"}
                        ]}}
                        """);
                return;
            }
            respond(exchange, "{\"code\":404,\"msg\":\"unexpected\"}", 404);
        });
        server.start();

        String apiBase = "http://127.0.0.1:" + server.getAddress().getPort();
        TduckProperties properties = new TduckProperties(
                "secret", "", "http://tduck-front.test", apiBase, apiBase,
                "admin@example.test", "password", Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.tduckFrontUrl()).thenReturn("http://tduck-front.test");
        TduckClient client = new TduckClient(properties, settingsService, RestClient.builder());

        List<TduckFormOption> forms = client.listForms("", 0, 20);

        assertThat(forms).extracting(TduckFormOption::formKey).containsExactly("form-a", "form-b");
        assertThat(forms).extracting(TduckFormOption::registrationUrl)
                .containsExactly("http://tduck-front.test/s/form-a", "http://tduck-front.test/s/form-b");
        assertThat(requests).hasValue(2);
        assertThat(detailRequests).hasValue(0);
        verify(settingsService).tduckFrontUrl();
    }

    @Test
    void configureWebhookAddsEncodedDeploymentTokenToInternalCallbackUrl() throws IOException {
        AtomicReference<String> configuredWebhookUrl = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/login/account")) {
                respond(exchange, "{\"code\":200,\"data\":{\"token\":\"test-token\"}}");
                return;
            }
            if (path.equals("/webhook/config/save")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> request = new ObjectMapper().readValue(exchange.getRequestBody().readAllBytes(), Map.class);
                configuredWebhookUrl.set(String.valueOf(request.get("url")));
                respond(exchange, "{\"code\":200,\"data\":{}}");
                return;
            }
            respond(exchange, "{\"code\":404,\"msg\":\"unexpected\"}", 404);
        });
        server.start();

        String apiBase = "http://127.0.0.1:" + server.getAddress().getPort();
        TduckProperties properties = new TduckProperties(
                "secret", "http://nginx/api/integrations/tduck/webhook", "http://tduck-front.test", apiBase, apiBase,
                "admin@example.test", "password", Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.webhookToken()).thenReturn("local token +/=");
        TduckClient client = new TduckClient(properties, settingsService, RestClient.builder());

        client.configureWebhook("form-a");

        assertThat(configuredWebhookUrl.get())
                .isEqualTo("http://nginx/api/integrations/tduck/webhook?token=local+token+%2B%2F%3D");
    }

    @Test
    void listFormDataIdsWithUnlimitedMarkerReadsEveryPage() throws IOException {
        AtomicInteger queryRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/login/account")) {
                respond(exchange, "{\"code\":200,\"data\":{\"token\":\"test-token\"}}");
                return;
            }
            if (path.equals("/user/form/data/query")) {
                int current = new ObjectMapper().readValue(exchange.getRequestBody().readAllBytes(), Map.class)
                        .get("current") instanceof Number page ? page.intValue() : -1;
                queryRequests.incrementAndGet();
                List<String> rows = new ArrayList<>();
                if (current == 0) {
                    for (int id = 1; id <= 100; id++) rows.add("{\"id\":\"" + id + "\"}");
                } else if (current == 1) {
                    rows.add("{\"id\":\"101\"}");
                } else {
                    respond(exchange, "{\"code\":500,\"msg\":\"unexpected page\"}", 500);
                    return;
                }
                respond(exchange, "{\"code\":200,\"data\":{\"records\":[" + String.join(",", rows) + "]}}");
                return;
            }
            respond(exchange, "{\"code\":404,\"msg\":\"unexpected\"}", 404);
        });
        server.start();

        String apiBase = "http://127.0.0.1:" + server.getAddress().getPort();
        TduckProperties properties = new TduckProperties(
                "secret", "", "http://tduck-front.test", apiBase, apiBase,
                "admin@example.test", "password", Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        TduckClient client = new TduckClient(properties, settingsService, RestClient.builder());

        List<String> ids = client.listFormDataIds("form-all", 0);

        assertThat(ids).hasSize(101).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25",
                "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40",
                "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55",
                "56", "57", "58", "59", "60", "61", "62", "63", "64", "65", "66", "67", "68", "69", "70",
                "71", "72", "73", "74", "75", "76", "77", "78", "79", "80", "81", "82", "83", "84", "85",
                "86", "87", "88", "89", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "100", "101");
        assertThat(queryRequests).hasValue(2);
    }

    @Test
    void configureWebhookRejectsMissingDeploymentTokenBeforeCallingTduck() {
        TduckProperties properties = new TduckProperties(
                "", "http://nginx/api/integrations/tduck/webhook", "http://tduck-front.test", "http://tduck-api.test", "http://tduck-api.test",
                "admin@example.test", "password", Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.webhookToken()).thenReturn(" ");
        TduckClient client = new TduckClient(properties, settingsService, RestClient.builder());

        assertThatThrownBy(() -> client.configureWebhook("form-a"))
                .isInstanceOf(TduckClientException.class)
                .hasMessageContaining("TDUCK_WEBHOOK_TOKEN");
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, body, 200);
    }

    private void respond(HttpExchange exchange, String body, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
