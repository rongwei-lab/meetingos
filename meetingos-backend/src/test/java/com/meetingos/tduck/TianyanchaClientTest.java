package com.meetingos.tduck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TianyanchaClientTest {
    private MockRestServiceServer server;
    private RestClient restClient;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private TianyanchaClient client(String token, int daily, int perClient) {
        return new TianyanchaClient(new TianyanchaProperties(token, daily, perClient, Duration.ofSeconds(1)), restClient, clock);
    }

    @Test
    void usesHttpsEncodedWordAndHeaderTokenAndOnlyReturnsRelevantOrganizations() {
        server.expect(request -> {
            assertThat(request.getURI().getScheme()).isEqualTo("https");
            assertThat(request.getURI().getHost()).isEqualTo("open.api.tianyancha.com");
            assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                    .contains("word=北京百度", "pageNum=1", "pageSize=20");
        }).andExpect(header("Authorization", "secret-provider-token")).andRespond(withSuccess("""
                {"error_code":0,"result":{"items":[
                  {"type":1,"id":2,"name":"北京百度有限公司","base":"北京","regStatus":"存续","matchType":"公司名称匹配"},
                  {"type":1,"id":1,"name":"<em>北京百度</em>","base":"北京","regStatus":"存续","matchType":"公司名称匹配"},
                  {"type":1,"id":3,"name":"不相关的投资企业","matchType":"股东信息匹配"},
                  {"type":2,"id":4,"name":"北京百度","matchType":"公司名称匹配"},
                  {"type":1,"id":1,"name":"北京百度","matchType":"公司名称匹配"},
                  {"type":1,"id":5,"name":"示例事业单位","matchType":"曾用名匹配","companyType":5}
                ]}}
                """, MediaType.APPLICATION_JSON));
        var result = client("secret-provider-token", 100, 20).search("北京百度", "client-a");
        assertThat(result.available()).isTrue();
        assertThat(result.items()).extracting(TianyanchaClient.OrganizationSuggestion::name)
                .containsExactly("北京百度", "北京百度有限公司", "示例事业单位");
        assertThat(result.toString()).doesNotContain("secret-provider-token", "<em>", "不相关的投资企业");
        server.verify();
    }

    @Test
    void specialCharactersStayInOneEncodedWordParameter() {
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("%26", "%20").doesNotContain("word=\u5317"))
                .andRespond(withSuccess("{\"error_code\":300000}", MediaType.APPLICATION_JSON));
        assertThat(client("token", 100, 20).search("北京 A&B", "client").available()).isTrue();
        server.verify();
    }

    @Test
    void noDataIsAnEmptySuccessfulSearch() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://")))
                .andRespond(withSuccess("{\"error_code\":300000}", MediaType.APPLICATION_JSON));
        var result = client("token", 100, 20).search("不存在的单位", "client");
        assertThat(result.available()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.message()).contains("手工填写");
    }

    @Test
    void quotaAndMissingTokenNeverReachUpstream() {
        assertThat(client("", 100, 20).search("北京百度", "client").code()).isEqualTo("NOT_CONFIGURED");
        assertThat(client("token", 100, 20).search("北京", "client").code()).isEqualTo("INVALID_WORD");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://")))
                .andRespond(withSuccess("{\"error_code\":300000}", MediaType.APPLICATION_JSON));
        var limited = client("token", 1, 20);
        limited.search("北京百度", "client-a");
        assertThat(limited.search("北京大学", "client-b").code()).isEqualTo("RATE_LIMITED");
        server.verify();
    }

    @Test
    void clientAndGlobalRateLimitsAreSeparateFromRegistrationLimits() {
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(org.hamcrest.Matchers.startsWith("https://")))
                .andRespond(withSuccess("{\"error_code\":300000}", MediaType.APPLICATION_JSON));
        var limited = client("token", 100, 1);
        limited.search("北京百度", "client-a");
        assertThat(limited.search("北京大学", "client-a").code()).isEqualTo("RATE_LIMITED");
        limited.search("北京大学", "client-b");
        assertThat(limited.search("北京大学", "client-c").code()).isEqualTo("RATE_LIMITED");
        server.verify();
    }

    @Test
    void upstreamErrorResponsesDoNotExposeProviderDetailsOrCredentials() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://")))
                .andRespond(withSuccess("{\"error_code\":300006,\"reason\":\"secret-provider-token 余额不足\"}", MediaType.APPLICATION_JSON));
        var result = client("secret-provider-token", 100, 20).search("北京百度", "client");
        assertThat(result.available()).isFalse();
        assertThat(result.toString()).doesNotContain("secret-provider-token", "余额不足");
    }

    @Test
    void networkFailureFallsBackWithoutRetries() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://"))).andRespond(withServerError());
        var result = client("token", 100, 20).search("北京百度", "client");
        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("手工填写");
        server.verify();
    }
}
