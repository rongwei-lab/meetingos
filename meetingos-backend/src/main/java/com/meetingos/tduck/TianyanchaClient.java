package com.meetingos.tduck;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TianyanchaClient {
    private static final String SEARCH_URL = "https://open.api.tianyancha.com/services/open/search/2.0";
    private static final Set<String> NAME_MATCHES = Set.of("公司名称匹配", "企业名称匹配", "名称匹配", "曾用名匹配", "简称匹配");
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    private final TianyanchaProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, ClientWindow> clients = new HashMap<>();
    private LocalDate quotaDate;
    private int dailyCalls;
    private long secondWindow = -1;
    private int secondCalls;

    @Autowired
    public TianyanchaClient(TianyanchaProperties properties, RestClient.Builder builder) {
        this(properties, createRestClient(properties, builder), Clock.systemUTC());
    }

    TianyanchaClient(TianyanchaProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    public boolean configured() {
        return StringUtils.hasText(properties.token()) && properties.maxCallsPerDay() > 0
                && properties.perClientPerMinute() > 0;
    }

    public Suggestions search(String word, String clientIp) {
        if (!configured()) return Suggestions.unavailable("NOT_CONFIGURED");
        if (word == null || word.strip().length() < 3 || word.length() > 120) {
            return Suggestions.unavailable("INVALID_WORD");
        }
        word = word.strip();
        if (!reserveCall(clientIp)) return Suggestions.unavailable("RATE_LIMITED");
        try {
            // 816 的参数名是 word；严格编码变量，避免中文、& 等字符改变查询参数。
            var uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL).queryParam("word", "{word}")
                    .queryParam("pageNum", 1).queryParam("pageSize", 20).encode().buildAndExpand(word).toUri();
            JsonNode response = restClient.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, properties.token()).retrieve().body(JsonNode.class);
            if (response == null) return Suggestions.unavailable("UNAVAILABLE");
            int code = response.path("error_code").asInt(-1);
            if (code == 300000) return Suggestions.ok(List.of());
            if (code != 0 || !response.path("result").path("items").isArray()) {
                return Suggestions.unavailable("UNAVAILABLE");
            }
            return Suggestions.ok(selectSuggestions(response.path("result").path("items"), word));
        } catch (RuntimeException exception) {
            // 联想失败时允许手填；异常和原始响应可能含凭据，不能透传到浏览器或日志。
            return Suggestions.unavailable("UNAVAILABLE");
        }
    }

    private List<OrganizationSuggestion> selectSuggestions(JsonNode items, String word) {
        String query = normalized(word);
        Map<String, OrganizationSuggestion> unique = new LinkedHashMap<>();
        for (JsonNode item : items) {
            if (item.path("type").asInt() != 1) continue;
            String name = plainText(item.path("name").asText(""));
            String reason = plainText(item.path("matchType").asText(""));
            // 通用搜索含“股东信息匹配”等关联公司，仅保留名称相关或明确的名称匹配结果。
            if (name.isBlank() || name.length() > 255
                    || (!normalized(name).contains(query) && !NAME_MATCHES.contains(reason))) continue;
            String id = item.path("id").asText("");
            unique.putIfAbsent(id.isBlank() ? name : id, new OrganizationSuggestion(id, name,
                    plainText(item.path("base").asText("")), plainText(item.path("regStatus").asText(""))));
        }
        List<OrganizationSuggestion> suggestions = new ArrayList<>(unique.values());
        suggestions.sort(Comparator.comparingInt((OrganizationSuggestion item) -> {
            String name = normalized(item.name());
            return name.equals(query) ? 0 : name.startsWith(query) ? 1 : name.contains(query) ? 2 : 3;
        }).thenComparingInt(item -> item.status().contains("注销") || item.status().contains("吊销") ? 1 : 0));
        return suggestions.stream().limit(10).toList();
    }

    private synchronized boolean reserveCall(String clientIp) {
        long now = clock.millis();
        long minute = now / 60_000;
        clients.entrySet().removeIf(entry -> entry.getValue().minute != minute);
        ClientWindow client = clients.get(clientIp);
        if ((client == null && clients.size() >= 10_000)
                || (client != null && client.calls >= properties.perClientPerMinute())) return false;
        LocalDate today = LocalDate.ofInstant(clock.instant(), QUOTA_ZONE);
        if (!today.equals(quotaDate)) {
            quotaDate = today;
            dailyCalls = 0;
        }
        if (dailyCalls >= properties.maxCallsPerDay()) return false;
        if (secondWindow != now / 1_000) {
            secondWindow = now / 1_000;
            secondCalls = 0;
        }
        if (secondCalls >= 2) return false;
        if (client == null) {
            client = new ClientWindow(minute);
            clients.put(clientIp, client);
        }
        client.calls++;
        secondCalls++;
        // 错误也占本地预算，不自动重试或翻页；进程内计数与当前单实例部署保持一致。
        dailyCalls++;
        return true;
    }

    private static RestClient createRestClient(TianyanchaProperties properties, RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        long configured = properties.timeout() == null ? 3_000 : properties.timeout().toMillis();
        int timeout = (int) Math.max(100, Math.min(configured, 5_000));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return builder.clone().requestFactory(factory).build();
    }

    private static String normalized(String value) {
        return value.replaceAll("\\s+", "").replace('（', '(').replace('）', ')').toLowerCase(Locale.ROOT);
    }

    private static String plainText(String value) {
        return HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>", "")).strip();
    }

    private static final class ClientWindow {
        final long minute;
        int calls;

        ClientWindow(long minute) { this.minute = minute; }
    }

    public record OrganizationSuggestion(String id, String name, String province, String status) { }

    public record Suggestions(boolean available, String code, List<OrganizationSuggestion> items, String message) {
        static Suggestions ok(List<OrganizationSuggestion> items) {
            return new Suggestions(true, "OK", items, items.isEmpty() ? "未找到相关单位，可继续输入或手工填写" : "");
        }

        static Suggestions unavailable(String code) {
            return new Suggestions(false, code, List.of(), "单位查询暂不可用，可继续手工填写");
        }
    }
}
