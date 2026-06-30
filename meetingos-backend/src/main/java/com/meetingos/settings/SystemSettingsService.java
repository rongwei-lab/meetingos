package com.meetingos.settings;

import com.meetingos.common.BusinessException;
import com.meetingos.security.AppProperties;
import com.meetingos.security.TduckProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;

@Service
public class SystemSettingsService {

    private static final String BASE_URL = "baseUrl";
    private static final String TDUCK_FRONT_URL = "tduckFrontUrl";
    private static final String TDUCK_API_URL = "tduckApiUrl";
    private static final String MASK_SENSITIVE = "maskSensitive";
    private static final String EXPORT_APPROVAL = "exportApproval";
    private static final String BRAND_NAME = "brandName";
    private static final String BROWSER_TITLE = "browserTitle";
    private static final String META_DESCRIPTION = "metaDescription";
    private static final String LOGO_URL = "logoUrl";
    private static final String FAVICON_URL = "faviconUrl";
    private static final String DEFAULT_BRAND_NAME = "会议管理系统";
    private static final String DEFAULT_META_DESCRIPTION = "会议管理系统";
    private static final String DEFAULT_ICON_URL = "/favicon.svg";

    private final SystemSettingRepository settingRepository;
    private final AppProperties appProperties;
    private final TduckProperties tduckProperties;

    public SystemSettingsService(SystemSettingRepository settingRepository,
                                 AppProperties appProperties,
                                 TduckProperties tduckProperties) {
        this.settingRepository = settingRepository;
        this.appProperties = appProperties;
        this.tduckProperties = tduckProperties;
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse current() {
        String token = webhookToken();
        return new SystemSettingsResponse(
                get(BASE_URL, appProperties.publicBaseUrl()),
                get(TDUCK_FRONT_URL, tduckProperties.publicBaseUrl()),
                get(TDUCK_API_URL, tduckProperties.apiBaseUrl()),
                token != null && !token.isBlank(),
                maskSecret(token),
                Boolean.parseBoolean(get(MASK_SENSITIVE, "true")),
                Boolean.parseBoolean(get(EXPORT_APPROVAL, "false")),
                brandName(),
                browserTitle(),
                metaDescription(),
                logoUrl(),
                faviconUrl()
        );
    }

    @Transactional
    public SystemSettingsResponse update(SystemSettingsRequest request) {
        put(BASE_URL, normalizeUrl(request.baseUrl(), "会议系统域名"));
        put(TDUCK_FRONT_URL, normalizeUrl(request.tduckFrontUrl(), "TDuck 前端地址"));
        put(TDUCK_API_URL, normalizeUrl(request.tduckApiUrl(), "TDuck 后端地址"));
        // Webhook Token 属于部署密钥，只允许由 TDUCK_WEBHOOK_TOKEN 环境变量管理。
        // 这里故意忽略请求中的 webhookToken，避免后台页面或旧客户端把密钥写入数据库并覆盖 env。
        put(MASK_SENSITIVE, String.valueOf(request.maskSensitive()));
        put(EXPORT_APPROVAL, String.valueOf(request.exportApproval()));
        put(BRAND_NAME, normalizeText(request.brandName(), DEFAULT_BRAND_NAME));
        put(BROWSER_TITLE, normalizeText(request.browserTitle(), DEFAULT_BRAND_NAME));
        put(META_DESCRIPTION, normalizeText(request.metaDescription(), DEFAULT_META_DESCRIPTION));
        put(LOGO_URL, normalizeAssetUrl(request.logoUrl(), DEFAULT_ICON_URL, "Logo 地址"));
        put(FAVICON_URL, normalizeAssetUrl(request.faviconUrl(), logoUrl(), "favicon 地址"));
        return current();
    }

    @Transactional(readOnly = true)
    public BrandingResponse branding() {
        return new BrandingResponse(
                brandName(),
                browserTitle(),
                metaDescription(),
                logoUrl(),
                faviconUrl()
        );
    }

    @Transactional(readOnly = true)
    public String webhookToken() {
        return tduckProperties.webhookSecret();
    }

    @Transactional(readOnly = true)
    public String tduckFrontUrl() {
        return get(TDUCK_FRONT_URL, tduckProperties.publicBaseUrl());
    }

    @Transactional(readOnly = true)
    public String tduckApiUrl() {
        return get(TDUCK_API_URL, tduckProperties.apiBaseUrl());
    }

    @Transactional(readOnly = true)
    public boolean maskSensitiveEnabled() {
        return Boolean.parseBoolean(get(MASK_SENSITIVE, "true"));
    }

    @Transactional(readOnly = true)
    public boolean exportApprovalEnabled() {
        return Boolean.parseBoolean(get(EXPORT_APPROVAL, "false"));
    }

    private String brandName() {
        return get(BRAND_NAME, DEFAULT_BRAND_NAME);
    }

    private String browserTitle() {
        return get(BROWSER_TITLE, brandName());
    }

    private String metaDescription() {
        return get(META_DESCRIPTION, DEFAULT_META_DESCRIPTION);
    }

    private String logoUrl() {
        return get(LOGO_URL, DEFAULT_ICON_URL);
    }

    private String faviconUrl() {
        return get(FAVICON_URL, logoUrl());
    }

    private String get(String key, String fallback) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    private void put(String key, String value) {
        SystemSetting setting = settingRepository.findBySettingKey(key).orElseGet(SystemSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        settingRepository.save(setting);
    }

    private String normalizeUrl(String value, String label) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(label + "不能为空");
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw BusinessException.badRequest(label + "必须以 http:// 或 https:// 开头");
        }
        return trimmed;
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeAssetUrl(String value, String fallback, String label) {
        String text = normalizeText(value, fallback);
        if (DEFAULT_ICON_URL.equals(text) || text.startsWith("/uploads/")) {
            return text;
        }
        try {
            URI uri = new URI(text);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()) {
                return text;
            }
        } catch (URISyntaxException ignored) {
            // 统一落到下面的业务错误，避免把底层解析细节暴露给前端。
        }
        throw BusinessException.badRequest(label + "仅允许 /uploads/ 下的同源图片、/favicon.svg 或 https 图片地址");
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return "********";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
