package com.meetingos.tduck;

import com.meetingos.security.TduckProperties;
import com.meetingos.settings.SystemSettingsService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TduckClient {

    private static final int LOGIN_RETRY_ATTEMPTS = 3;
    private static final long LOGIN_DUPLICATE_TOKEN_RETRY_DELAY_MS = 1_100L;

    private final TduckProperties properties;
    private final SystemSettingsService settingsService;
    private final RestClient restClient;
    private final Object tokenLock = new Object();
    private volatile String cachedToken;

    public TduckClient(TduckProperties properties,
                       SystemSettingsService settingsService,
                       RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.restClient = restClientBuilder.build();
    }

    public List<TduckFormOption> listForms(String keyword, int page, int size) {
        int current = Math.max(page, 0) + 1;
        int pageSize = Math.min(Math.max(size, 1), 100);
        String url = UriComponentsBuilder
                .fromHttpUrl(apiUrl("/user/form/page"))
                .queryParam("current", current)
                .queryParam("size", pageSize)
                .queryParam("name", keyword == null ? "" : keyword.trim())
                .queryParam("beginDateTime", "")
                .queryParam("endDateTime", "")
                .queryParam("status", "")
                .queryParam("folderId", 0)
                .build()
                .toUriString();
        Map<String, Object> response = withTokenRetry(token -> requestGet(url, token));
        Object data = unwrapTduckData(response);
        return extractRows(data).stream()
                .map(this::toFormOption)
                .map(this::withItemCount)
                .filter(option -> StringUtils.hasText(option.formKey()))
                .toList();
    }

    public TduckFormOption createForm(String name, String description) {
        String normalizedName = StringUtils.hasText(name) ? name.trim() : "MeetingOS 报名表";
        Map<String, Object> body = new LinkedHashMap<>();
        /*
         * TDuck 原生表单名支持富文本 HTML。这里仍传纯文本，避免 MeetingOS 生成的会议名里
         * 混入 HTML 后影响 TDuck 工作台展示，也便于后续在 MeetingOS 表单列表中清洗展示。
         */
        body.put("name", normalizedName);
        body.put("description", StringUtils.hasText(description) ? description.trim() : "MeetingOS 创建");
        body.put("type", 1);
        body.put("folderId", 0);
        Map<String, Object> response = withTokenRetry(token -> requestPost(apiUrl("/user/form/create"), body, token));
        Map<String, Object> form = objectMap(unwrapTduckData(response));
        if (form.isEmpty()) {
            throw new TduckClientException("TDuck 创建表单未返回表单信息");
        }
        String formKey = firstText(form, "formKey", "key", "id");
        if (!StringUtils.hasText(formKey)) {
            throw new TduckClientException("TDuck 创建表单未返回 formKey");
        }
        return toFormOption(form);
    }

    public Optional<TduckFormDetails> formDetails(String formKey) {
        if (!StringUtils.hasText(formKey)) {
            return Optional.empty();
        }
        String normalizedFormKey = formKey.trim();
        Map<String, Object> entityResponse = withTokenRetry(token -> requestGet(apiUrl("/user/form/" + normalizedFormKey), token));
        Map<String, Object> formEntity = objectMap(unwrapTduckData(entityResponse));
        if (formEntity.isEmpty()) {
            return Optional.empty();
        }

        /*
         * TDuck 的 details 接口服务公开填写页，部分版本不会携带 status；
         * 管理端 /user/form/{key} 才能准确判断是否处于“收集中”。因此这里拆成两次读取：
         * 先取实体校验发布态，再取 details 确认可填写字段真实存在。
         */
        Map<String, Object> detailsResponse = withTokenRetry(token -> requestGet(apiUrl("/user/form/details/" + normalizedFormKey), token));
        Object data = unwrapTduckData(detailsResponse);
        Map<?, ?> dataMap = data instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> detailsForm = objectMap(dataMap.get("form"));
        if (!detailsForm.isEmpty()) {
            mergeMissing(formEntity, detailsForm, "name", "formName", "title", "type", "typeDesc");
        }

        List<Map<String, Object>> items = firstList(dataMap, "formItems", "items", "itemList", "formItemList", "components");
        String resolvedFormKey = firstText(formEntity, "formKey", "key", "id");
        return Optional.of(new TduckFormDetails(
                firstNonBlank(resolvedFormKey, normalizedFormKey),
                cleanFormName(firstText(formEntity, "textName", "name", "formName", "title")),
                firstText(formEntity, "status", "state"),
                isPublished(formEntity),
                items.size()
        ));
    }

    public List<TduckFormFieldOption> formFields(String formKey) {
        if (!StringUtils.hasText(formKey)) {
            return List.of();
        }
        String normalizedFormKey = formKey.trim();
        Map<String, Object> detailsResponse = withTokenRetry(token -> requestGet(apiUrl("/user/form/details/" + normalizedFormKey), token));
        Object data = unwrapTduckData(detailsResponse);
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        return firstList(dataMap, "formItems", "items", "itemList", "formItemList", "components").stream()
                .map(this::toFieldOption)
                .filter(field -> StringUtils.hasText(field.fieldKey()))
                .toList();
    }

    public void configureWebhook(String formKey) {
        if (!StringUtils.hasText(formKey)) {
            throw new TduckClientException("TDuck formKey 为空，无法配置 Webhook");
        }
        String webhookUrl = webhookCallbackUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hookName", "MeetingOS callback " + formKey.trim());
        body.put("sourceType", "form");
        body.put("sourceId", formKey.trim());
        body.put("url", webhookUrl);
        body.put("requestType", "POST");
        body.put("enabled", true);
        body.put("otherOptions", Map.of(
                "eventTypes", List.of("form_data_add", "form_data_update", "form_data_delete")
        ));
        withTokenRetry(token -> {
            requestPost(apiUrl("/webhook/config/save"), body, token);
            return null;
        });
    }

    public void setFormPublished(String formKey, boolean published) {
        if (!StringUtils.hasText(formKey)) {
            throw new TduckClientException("TDuck formKey 为空，无法同步发布状态");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("formKey", formKey.trim());
        /*
         * TDuck 原生“发布/停止收集”控制的是 /tduck/s/{formKey} 原始填写页。
         * MeetingOS 报名开关必须调用这里，避免用户绕过 /r/{meetingCode} 中转页继续提交。
         */
        String path = published ? "/user/form/publish" : "/user/form/stop";
        withTokenRetry(token -> {
            requestPost(apiUrl(path), body, token);
            return null;
        });
    }

    public TduckFormDataDetails formDataDetails(String dataId) {
        if (!StringUtils.hasText(dataId)) {
            throw new TduckClientException("TDuck 数据 ID 不能为空");
        }
        Map<String, Object> response = withTokenRetry(token -> requestGet(apiUrl("/user/form/data/details/" + dataId.trim()), token));
        Map<String, Object> details = objectMap(unwrapTduckData(response));
        Map<String, Object> formData = objectMap(details.get("formData"));
        if (formData.isEmpty()) {
            throw new TduckClientException("TDuck 报名数据不存在或无填写内容");
        }
        String formKey = firstNonBlank(firstText(formData, "formKey"), firstText(details, "formKey"));
        String resolvedDataId = firstNonBlank(firstText(formData, "id", "dataId"), dataId.trim());
        return new TduckFormDataDetails(
                formKey,
                resolvedDataId,
                new LinkedHashMap<>(formData),
                details
        );
    }

    public List<String> listFormDataIds(String formKey, int limit) {
        if (!StringUtils.hasText(formKey)) {
            throw new TduckClientException("TDuck formKey 不能为空");
        }
        int maxRows = Math.min(Math.max(limit, 1), 1_000);
        int pageSize = Math.min(maxRows, 100);
        List<String> result = new ArrayList<>();
        /*
         * TDuck 管理端列表接口按 id 倒序返回，且 current 是 0 基页码。这里先只分页读取数据 ID，
         * 详情由调用方逐条拉取，确保单条异常不会让整批补偿同步直接中断。
         */
        for (int current = 0; result.size() < maxRows; current++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("formKey", formKey.trim());
            body.put("current", current);
            body.put("size", pageSize);
            Map<String, Object> response = withTokenRetry(token -> requestPost(apiUrl("/user/form/data/query"), body, token));
            Object data = unwrapTduckData(response);
            List<Map<String, Object>> rows = extractRows(data);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                if (result.size() >= maxRows) {
                    break;
                }
                String dataId = firstText(row, "id", "dataId");
                if (StringUtils.hasText(dataId)) {
                    result.add(dataId);
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
        }
        return result;
    }

    public TduckBrowserSessionResponse browserSession() {
        String token = adminToken();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        try {
            Map<String, Object> response = requestGet(apiUrl("/user/current/detail"), token);
            userInfo.putAll(objectMap(unwrapTduckData(response)));
        } catch (TduckUnauthorizedException ex) {
            clearToken();
            token = adminToken();
            Map<String, Object> response = requestGet(apiUrl("/user/current/detail"), token);
            userInfo.putAll(objectMap(unwrapTduckData(response)));
        } catch (TduckClientException ex) {
            // TDuck 前端判断登录态只依赖 token。用户详情接口异常时保留最小信息继续跳转，
            // 避免已成功获取 token 的情况下仍把用户挡在 TDuck 登录页。
        }
        /*
         * TDuck 前端只依赖 localStorage.token 判断是否已登录，userInfo 用于展示头像、名称和管理员菜单。
         * 部分 TDuck 版本的 /user/current/detail 不稳定时，用登录返回结构的最小字段兜底，
         * 避免按钮又被路由守卫打回登录页。
         */
        userInfo.putIfAbsent("token", token);
        userInfo.putIfAbsent("name", "TDuck 管理员");
        userInfo.putIfAbsent("admin", true);
        return new TduckBrowserSessionResponse(token, userInfo);
    }

    public String registrationUrlFor(String formKey) {
        String frontUrl = settingsService.tduckFrontUrl();
        return trimTrailingSlash(frontUrl) + "/s/" + formKey;
    }

    private Map<String, Object> requestPost(String url, Map<String, Object> body, String token) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("token", token)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            assertTduckOk(response);
            return response == null ? Map.of() : response;
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new TduckUnauthorizedException();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new TduckUnauthorizedException();
            }
            throw new TduckClientException("调用 TDuck 接口失败: HTTP " + ex.getStatusCode().value());
        } catch (TduckUnauthorizedException | TduckClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TduckClientException("调用 TDuck 接口失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> requestGet(String url, String token) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("token", token)
                    .retrieve()
                    .body(Map.class);
            assertTduckOk(response);
            return response == null ? Map.of() : response;
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new TduckUnauthorizedException();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new TduckUnauthorizedException();
            }
            throw new TduckClientException("调用 TDuck 接口失败: HTTP " + ex.getStatusCode().value());
        } catch (TduckUnauthorizedException | TduckClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TduckClientException("调用 TDuck 接口失败: " + ex.getMessage());
        }
    }

    private <T> T withTokenRetry(TduckTokenRequest<T> request) {
        try {
            return request.execute(adminToken());
        } catch (TduckUnauthorizedException ex) {
            clearToken();
            try {
                return request.execute(adminToken());
            } catch (TduckUnauthorizedException retryEx) {
                clearToken();
                throw new TduckClientException("TDuck 登录态失效，请检查管理员账号密码是否正确");
            }
        }
    }

    private String adminToken() {
        String existing = cachedToken;
        if (StringUtils.hasText(existing)) {
            return existing;
        }
        synchronized (tokenLock) {
            if (StringUtils.hasText(cachedToken)) {
                return cachedToken;
            }
            cachedToken = login();
            return cachedToken;
        }
    }

    private String login() {
        if (!StringUtils.hasText(properties.adminEmail()) || !StringUtils.hasText(properties.adminPassword())) {
            throw new TduckClientException("未配置 TDuck 管理员账号密码，请设置 TDUCK_ADMIN_EMAIL 和 TDUCK_ADMIN_PASSWORD");
        }
        TduckClientException lastDuplicateTokenFailure = null;
        for (int attempt = 1; attempt <= LOGIN_RETRY_ATTEMPTS; attempt++) {
            try {
                return doLogin();
            } catch (TduckClientException ex) {
                if (!isDuplicateTokenFailure(ex) || attempt == LOGIN_RETRY_ATTEMPTS) {
                    throw ex;
                }
                lastDuplicateTokenFailure = ex;
                waitBeforeRetryingDuplicateToken();
            }
        }
        throw lastDuplicateTokenFailure == null
                ? new TduckClientException("TDuck 管理员登录失败，请检查 TDUCK_ADMIN_EMAIL/TDUCK_ADMIN_PASSWORD")
                : lastDuplicateTokenFailure;
    }

    private String doLogin() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(apiUrl("/login/account"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "account", properties.adminEmail(),
                            "password", properties.adminPassword()
                    ))
                    .retrieve()
                    .body(Map.class);
            assertTduckOk(response);
            Object data = response == null ? null : response.get("data");
            String token = data instanceof Map<?, ?> map ? asText(map.get("token")) : null;
            if (!StringUtils.hasText(token)) {
                throw new TduckClientException("TDuck 管理员登录未返回 token");
            }
            return token;
        } catch (TduckUnauthorizedException | TduckClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TduckClientException("TDuck 管理员登录失败，请检查 TDUCK_ADMIN_EMAIL/TDUCK_ADMIN_PASSWORD");
        }
    }

    private boolean isDuplicateTokenFailure(TduckClientException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("数据库中已存在该记录");
    }

    private void waitBeforeRetryingDuplicateToken() {
        try {
            /*
             * TDuck 社区版登录 token 只包含用户和秒级时间戳，同一秒内外部脚本与
             * MeetingOS 后端同时登录同一管理员时，会生成完全相同的 JWT 并撞上
             * ac_user_token.token 唯一索引。等待跨过下一个秒级时间窗后重试即可恢复，
             * 同时保留其他登录错误的原始失败行为。
             */
            Thread.sleep(LOGIN_DUPLICATE_TOKEN_RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TduckClientException("TDuck 管理员登录重试被中断");
        }
    }

    private void clearToken() {
        synchronized (tokenLock) {
            cachedToken = null;
        }
    }

    private void assertTduckOk(Map<String, Object> response) {
        if (response == null || !response.containsKey("code")) {
            return;
        }
        String code = asText(response.get("code"));
        if ("200".equals(code)) {
            return;
        }
        if ("401".equals(code) || "403".equals(code)) {
            throw new TduckUnauthorizedException();
        }
        String message = firstNonBlank(asText(response.get("msg")), asText(response.get("message")));
        throw new TduckClientException("TDuck 接口返回异常: " + firstNonBlank(message, code));
    }

    private TduckFormOption toFormOption(Map<String, Object> row) {
        String formKey = firstText(row, "formKey", "key", "id");
        return new TduckFormOption(
                formKey,
                cleanFormName(firstText(row, "textName", "name", "formName", "title")),
                firstText(row, "status", "state"),
                isPublished(row),
                firstInt(row, "itemCount", "fieldCount", "itemsCount", "formItemCount"),
                StringUtils.hasText(formKey) ? registrationUrlFor(formKey) : ""
        );
    }

    private TduckFormOption withItemCount(TduckFormOption option) {
        if (!StringUtils.hasText(option.formKey()) || option.itemCount() > 0) {
            return option;
        }
        try {
            Optional<TduckFormDetails> details = formDetails(option.formKey());
            if (details.isEmpty()) {
                return option;
            }
            TduckFormDetails detail = details.get();
            return new TduckFormOption(
                    option.formKey(),
                    StringUtils.hasText(option.formName()) ? option.formName() : detail.formName(),
                    option.status(),
                    option.published(),
                    detail.itemCount(),
                    option.registrationUrl()
            );
        } catch (TduckClientException ex) {
            return option;
        }
    }

    private TduckFormFieldOption toFieldOption(Map<String, Object> row) {
        String fieldKey = firstText(row, "value", "formItemId", "fieldKey", "key", "vModel", "__vModel__", "id");
        String label = cleanFieldLabel(firstText(row, "textLabel", "label", "name", "title"));
        return new TduckFormFieldOption(
                fieldKey,
                label,
                firstText(row, "type", "tag", "componentType"),
                asBoolean(row.get("required"))
        );
    }

    private Object unwrapTduckData(Map<String, Object> response) {
        return response == null ? null : response.get("data");
    }

    private List<Map<String, Object>> extractRows(Object data) {
        if (data instanceof List<?> list) {
            return mapsFromList(list);
        }
        if (!(data instanceof Map<?, ?> map)) {
            return List.of();
        }
        for (String key : List.of("records", "content", "list", "rows", "data")) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return mapsFromList(list);
            }
            if (value instanceof Map<?, ?> nested) {
                List<Map<String, Object>> nestedRows = extractRows(nested);
                if (!nestedRows.isEmpty()) {
                    return nestedRows;
                }
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> mapsFromList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMap(item);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        return result;
    }

    private List<Map<String, Object>> firstList(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return mapsFromList(list);
            }
        }
        return List.of();
    }

    private void mergeMissing(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (!target.containsKey(key) && source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean isPublished(Map<String, Object> form) {
        Object published = firstValue(form, "published", "isPublished");
        if (published instanceof Boolean bool) {
            return bool;
        }
        String status = firstText(form, "status", "state");
        return "2".equals(status)
                || "PUBLISHED".equalsIgnoreCase(status)
                || "PUBLISH".equalsIgnoreCase(status)
                || "RELEASED".equalsIgnoreCase(status);
    }

    private int firstInt(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = asText(value);
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String firstText(Map<String, Object> map, String... keys) {
        return asText(firstValue(map, keys));
    }

    private Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(asText(value))) {
                return value;
            }
        }
        return null;
    }

    private String cleanFormName(String raw) {
        String text = HtmlUtils.htmlUnescape(firstNonBlank(raw, "未命名表单"))
                .replaceAll("<[^>]+>", "")
                .replace('\u00A0', ' ')
                .trim();
        return StringUtils.hasText(text) ? text : "未命名表单";
    }

    private String cleanFieldLabel(String raw) {
        String text = HtmlUtils.htmlUnescape(firstNonBlank(raw, "未命名字段"))
                .replaceAll("<[^>]+>", "")
                .replace('\u00A0', ' ')
                .trim();
        return StringUtils.hasText(text) ? text : "未命名字段";
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = asText(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String apiUrl(String path) {
        String configured = properties.adminApiBaseUrl();
        String baseUrl = StringUtils.hasText(configured) ? configured : settingsService.tduckApiUrl();
        return trimTrailingSlash(baseUrl) + (path.startsWith("/") ? path : "/" + path);
    }

    private String webhookCallbackUrl() {
        String base = properties.webhookInternalUrl();
        if (!StringUtils.hasText(base)) {
            base = trimTrailingSlash(settingsService.current().baseUrl()) + "/api/integrations/tduck/webhook";
        }
        /*
         * 默认回调地址不再自动拼接 token，避免密钥进入浏览器、代理和 Nginx 访问日志。
         * 若 TDuck 社区版部署确实无法设置自定义 header/HMAC，可在 MEETINGOS_WEBHOOK_INTERNAL_URL
         * 中显式配置仅内网可达的 ?token=... 兼容地址。
         */
        return UriComponentsBuilder
                .fromHttpUrl(base)
                .build()
                .encode()
                .toUriString();
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    @FunctionalInterface
    private interface TduckTokenRequest<T> {
        T execute(String token);
    }
}

class TduckClientException extends RuntimeException {
    TduckClientException(String message) {
        super(message);
    }
}

class TduckUnauthorizedException extends RuntimeException {
}
