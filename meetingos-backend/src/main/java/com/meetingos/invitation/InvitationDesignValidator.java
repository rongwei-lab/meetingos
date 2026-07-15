package com.meetingos.invitation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class InvitationDesignValidator {
    static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    static final int MAX_ELEMENTS_PER_CANVAS = 200;
    private static final Pattern ELEMENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final Pattern COLOR = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern OPAQUE_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Set<String> ALLOWED_FONT_FAMILIES = Set.of(
            "Arial", "Helvetica", "Inter", "PingFang SC", "Microsoft YaHei", "Noto Sans SC",
            "Noto Sans CJK SC", "Source Han Sans SC", "SimHei", "SimSun", "Songti SC",
            "Segoe UI", "system-ui", "ui-sans-serif", "sans-serif", "serif"
    );
    private static final Set<InvitationElementType> POSTER_TYPES = Set.of(
            InvitationElementType.TEXT, InvitationElementType.IMAGE, InvitationElementType.SHAPE,
            InvitationElementType.DIVIDER, InvitationElementType.MEETING_FIELD,
            InvitationElementType.REGISTRATION_BUTTON, InvitationElementType.CHANNEL_QR_CODE,
            InvitationElementType.COUNTDOWN
    );
    private static final Set<String> COMMON_PROPS = Set.of("opacity");
    private static final Map<InvitationElementType, Set<String>> ALLOWED_PROPS = allowedProps();
    private static final Set<String> MEETING_FIELDS = Set.of(
            "NAME", "ORGANIZER", "CO_ORGANIZER", "START_TIME", "END_TIME", "LOCATION",
            "VENUE_NAME", "DESCRIPTION", "AGENDA", "COVER_IMAGE", "CONTACT_NAME", "CONTACT_PHONE"
    );
    private static final Set<String> SHAPE_TYPES = Set.of(
            "rectangle", "rounded-rectangle", "ellipse", "triangle", "diamond", "hexagon", "star", "circle"
    );
    private static final Set<String> QUERY_TARGETS = Set.of(
            "QUERY_ENTRY", "COMBINED_QUERY", "SEAT_QUERY", "ROOM_QUERY", "DINNER_QUERY"
    );
    private static final Set<String> QR_PURPOSES = Set.of(
            "REGISTRATION", "QUERY_ENTRY", "COMBINED_QUERY", "SEAT_QUERY", "ROOM_QUERY",
            "DINNER_QUERY", "CHANNEL_INVITATION"
    );

    private final ObjectMapper objectMapper;
    private final InvitationAssetRepository assetRepository;

    public InvitationDesignValidator(ObjectMapper objectMapper, InvitationAssetRepository assetRepository) {
        this.objectMapper = objectMapper;
        this.assetRepository = assetRepository;
    }

    public void validate(Long meetingId, InvitationDesignDocumentV1 document) {
        if (document == null || document.version() != 1) {
            throw BusinessException.badRequest("邀请函设计文档版本必须为 1");
        }
        ensureDocumentSize(document);
        Set<Long> assetIds = new HashSet<>();
        validateCanvas("H5", document.h5(), 375, 667, 6000, false, assetIds);
        validateCanvas("海报", document.poster(), 1080, 1920, 1920, true, assetIds);
        validateAssetOwnership(meetingId, assetIds);
    }

    public boolean containsMap(InvitationDesignDocumentV1 document) {
        return document != null && document.h5() != null && safeElements(document.h5()).stream()
                .anyMatch(element -> element != null && element.type() == InvitationElementType.AMAP);
    }

    public boolean containsPublicQueryAction(InvitationDesignDocumentV1 document) {
        if (document == null) {
            return false;
        }
        return containsPublicQueryAction(document.h5()) || containsPublicQueryAction(document.poster());
    }

    private boolean containsPublicQueryAction(InvitationDesignDocumentV1.InvitationCanvas canvas) {
        if (canvas == null) {
            return false;
        }
        return safeElements(canvas).stream().anyMatch(element -> {
            if (element == null) {
                return false;
            }
            if (element.type() == InvitationElementType.CHECKIN_BUTTON
                    || element.type() == InvitationElementType.QUERY_BUTTON) {
                return true;
            }
            if (element.type() != InvitationElementType.CHANNEL_QR_CODE || element.props() == null) {
                return false;
            }
            Object purpose = element.props().get("purpose");
            return purpose instanceof String value && QUERY_TARGETS.contains(value);
        });
    }

    public Set<Long> referencedAssetIds(InvitationDesignDocumentV1 document) {
        Set<Long> ids = new HashSet<>();
        if (document == null) {
            return ids;
        }
        collectAssetIds(document.h5(), ids);
        collectAssetIds(document.poster(), ids);
        return ids;
    }

    private void collectAssetIds(InvitationDesignDocumentV1.InvitationCanvas canvas, Set<Long> ids) {
        if (canvas == null) {
            return;
        }
        if (canvas.backgroundImageAssetId() != null) {
            ids.add(canvas.backgroundImageAssetId());
        }
        for (InvitationDesignDocumentV1.InvitationElement element : safeElements(canvas)) {
            if (element == null || element.props() == null) {
                continue;
            }
            for (String key : List.of("assetId", "coverAssetId")) {
                Object value = element.props().get(key);
                if (value instanceof Number number) {
                    ids.add(number.longValue());
                }
            }
            Object values = element.props().get("assetIds");
            if (values instanceof Collection<?> collection) {
                for (Object value : collection) {
                    if (value instanceof Number number) {
                        ids.add(number.longValue());
                    }
                }
            }
        }
    }

    private void ensureDocumentSize(InvitationDesignDocumentV1 document) {
        try {
            if (objectMapper.writeValueAsBytes(document).length > MAX_DOCUMENT_BYTES) {
                throw BusinessException.badRequest("邀请函设计文档不能超过 1 MiB");
            }
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest("邀请函设计文档无法序列化");
        }
    }

    private void validateCanvas(String label,
                                InvitationDesignDocumentV1.InvitationCanvas canvas,
                                int expectedWidth,
                                int minHeight,
                                int maxHeight,
                                boolean poster,
                                Set<Long> assetIds) {
        if (canvas == null || canvas.width() != expectedWidth
                || canvas.height() < minHeight || canvas.height() > maxHeight) {
            throw BusinessException.badRequest(label + "画布尺寸非法");
        }
        validateColor(canvas.backgroundColor(), label + "背景颜色");
        if (canvas.backgroundImageAssetId() != null) {
            requirePositiveId(canvas.backgroundImageAssetId(), label + "背景素材");
            assetIds.add(canvas.backgroundImageAssetId());
        }
        List<InvitationDesignDocumentV1.InvitationElement> elements = safeElements(canvas);
        if (elements.size() > MAX_ELEMENTS_PER_CANVAS) {
            throw BusinessException.badRequest(label + "画布最多包含 200 个元素");
        }
        Set<String> elementIds = new HashSet<>();
        for (InvitationDesignDocumentV1.InvitationElement element : elements) {
            validateElement(label, canvas, element, poster, elementIds, assetIds);
        }
    }

    private void validateElement(String canvasLabel,
                                 InvitationDesignDocumentV1.InvitationCanvas canvas,
                                 InvitationDesignDocumentV1.InvitationElement element,
                                 boolean poster,
                                 Set<String> elementIds,
                                 Set<Long> assetIds) {
        if (element == null || element.type() == null || element.id() == null
                || !ELEMENT_ID.matcher(element.id()).matches() || !elementIds.add(element.id())) {
            throw BusinessException.badRequest(canvasLabel + "画布存在非法或重复的元素 ID");
        }
        if (poster && !POSTER_TYPES.contains(element.type())) {
            throw BusinessException.badRequest("海报画布不支持互动元素 " + element.type());
        }
        if (!finite(element.x(), element.y(), element.width(), element.height(), element.rotation())
                || element.x() < 0 || element.y() < 0 || element.width() <= 0 || element.height() <= 0
                || element.x() + element.width() > canvas.width()
                || element.y() + element.height() > canvas.height()
                || Math.abs(element.rotation()) > 360
                || !rotatedBoundsWithinCanvas(canvas, element)) {
            throw BusinessException.badRequest(canvasLabel + "元素 " + element.id() + " 超出画布或尺寸非法");
        }
        Map<String, Object> props = element.props() == null ? Map.of() : element.props();
        Set<String> allowed = new HashSet<>(COMMON_PROPS);
        allowed.addAll(ALLOWED_PROPS.getOrDefault(element.type(), Set.of()));
        if (!allowed.containsAll(props.keySet())) {
            Set<String> unknown = new HashSet<>(props.keySet());
            unknown.removeAll(allowed);
            throw BusinessException.badRequest("元素 " + element.id() + " 包含不支持的属性: " + unknown);
        }
        props.forEach((key, value) -> validateSimpleValue(element.id(), key, value));
        validateOptionalNumber(props, "opacity", 0, 1);
        validateFontFamily(props.get("fontFamily"));
        validateTypedProps(element, props, assetIds);
    }

    private void validateTypedProps(InvitationDesignDocumentV1.InvitationElement element,
                                    Map<String, Object> props,
                                    Set<Long> assetIds) {
        switch (element.type()) {
            case TEXT -> requireText(props, "text", "文字元素内容不能为空");
            case IMAGE -> assetIds.add(requireAssetId(props, "assetId", "图片元素必须选择会议素材"));
            case SHAPE -> {
                validateShapeType(props.get("shape"));
                double maximumMetric = Math.min(element.width(), element.height()) / 2;
                validateOptionalNumber(props, "strokeWidth", 0, maximumMetric);
                validateOptionalNumber(props, "borderRadius", 0, maximumMetric);
            }
            case MEETING_FIELD -> {
                String field = requireText(props, "field", "会议字段类型不能为空");
                if (!MEETING_FIELDS.contains(field)) {
                    throw BusinessException.badRequest("不支持的会议绑定字段: " + field);
                }
            }
            case REGISTRATION_BUTTON -> requireText(props, "text", "报名按钮文字不能为空");
            case CHECKIN_BUTTON -> requireShortText(props, "text", 40, "签到按钮文字不能为空");
            case QUERY_BUTTON -> {
                requireShortText(props, "text", 40, "查询按钮文字不能为空");
                validateEnumProp(props, "queryTarget", QUERY_TARGETS, "不支持的查询按钮目标");
            }
            case CHANNEL_QR_CODE -> {
                validateOptionalEnumProp(props, "purpose", QR_PURPOSES, "不支持的二维码用途");
                validateOptionalTextLength(props, "label", 60, "二维码说明最多 60 个字符");
                validateQrContrast(props);
            }
            case BACKGROUND_MUSIC -> validateHttpsUrl(requireText(props, "url", "背景音乐必须配置 HTTPS 地址"), "背景音乐");
            case VIDEO -> {
                validateHttpsUrl(requireText(props, "url", "视频必须配置 HTTPS 地址"), "视频");
                assetIds.add(requireAssetId(props, "coverAssetId", "视频必须选择封面素材"));
            }
            case CAROUSEL -> {
                List<Long> ids = readAssetIds(props.get("assetIds"));
                if (ids.isEmpty() || ids.size() > 10) {
                    throw BusinessException.badRequest("轮播图必须包含 1 至 10 张会议素材");
                }
                assetIds.addAll(ids);
            }
            case AMAP -> {
                requireNumber(props, "longitude", -180, 180);
                requireNumber(props, "latitude", -90, 90);
                requireText(props, "address", "地图地址不能为空");
                Object navigationUrl = props.get("navigationUrl");
                if (navigationUrl != null) {
                    if (!(navigationUrl instanceof String url)) {
                        throw BusinessException.badRequest("地图导航地址格式非法");
                    }
                    if (StringUtils.hasText(url)) {
                        // 公开 H5 会把导航地址渲染为可点击链接，必须在入库前阻断危险协议。
                        validateHttpsUrl(url.trim(), "地图导航");
                    }
                }
            }
            default -> {
                // 其余静态元素仅需公共属性和白名单校验。
            }
        }
        validateOptionalColors(props);
    }

    private void validateShapeType(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof String shape) || !SHAPE_TYPES.contains(shape)) {
            throw BusinessException.badRequest("不支持的形状类型: " + value);
        }
    }

    private void validateSimpleValue(String elementId, String key, Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            if (value instanceof String text && text.length() > 5000) {
                throw BusinessException.badRequest("元素 " + elementId + " 的属性 " + key + " 过长");
            }
            return;
        }
        if ("assetIds".equals(key) && value instanceof Collection<?>) {
            return;
        }
        throw BusinessException.badRequest("元素 " + elementId + " 的属性 " + key + " 类型非法");
    }

    private void validateOptionalColors(Map<String, Object> props) {
        for (String key : List.of("color", "fill", "stroke", "foregroundColor", "backgroundColor")) {
            if (props.containsKey(key)) {
                validateColor(String.valueOf(props.get(key)), key);
            }
        }
    }

    private void validateQrContrast(Map<String, Object> props) {
        String foreground = String.valueOf(props.getOrDefault("foregroundColor", "#000000"));
        String background = String.valueOf(props.getOrDefault("backgroundColor", "#ffffff"));
        if (!OPAQUE_COLOR.matcher(foreground).matches() || !OPAQUE_COLOR.matcher(background).matches()) {
            throw BusinessException.badRequest("二维码前景色和背景色必须使用不透明六位十六进制颜色");
        }
        double lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
        double darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
        if ((lighter + 0.05) / (darker + 0.05) < 4.5) {
            throw BusinessException.badRequest("二维码前景色与背景色对比度至少为 4.5:1");
        }
    }

    private double relativeLuminance(String color) {
        int red = Integer.parseInt(color.substring(1, 3), 16);
        int green = Integer.parseInt(color.substring(3, 5), 16);
        int blue = Integer.parseInt(color.substring(5, 7), 16);
        return 0.2126 * linearColor(red) + 0.7152 * linearColor(green) + 0.0722 * linearColor(blue);
    }

    private double linearColor(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private void validateFontFamily(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof String fontStack) || !StringUtils.hasText(fontStack)) {
            throw BusinessException.badRequest("字体配置非法");
        }
        String[] families = fontStack.split(",");
        if (families.length > 8) {
            throw BusinessException.badRequest("字体回退列表过长");
        }
        for (String family : families) {
            String normalized = family.trim().replaceAll("^[\\\"']|[\\\"']$", "");
            if (!ALLOWED_FONT_FAMILIES.contains(normalized)) {
                throw BusinessException.badRequest("不支持的字体: " + normalized);
            }
        }
    }

    private String requireText(Map<String, Object> props, String key, String message) {
        Object value = props.get(key);
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            throw BusinessException.badRequest(message);
        }
        return text.trim();
    }

    private String requireShortText(Map<String, Object> props, String key, int maximumLength, String message) {
        String text = requireText(props, key, message);
        if (text.length() > maximumLength) {
            throw BusinessException.badRequest(key + " 最多 " + maximumLength + " 个字符");
        }
        return text;
    }

    private void validateOptionalTextLength(Map<String, Object> props,
                                            String key,
                                            int maximumLength,
                                            String message) {
        Object value = props.get(key);
        if (value == null) {
            return;
        }
        if (!(value instanceof String text) || text.length() > maximumLength) {
            throw BusinessException.badRequest(message);
        }
    }

    private void validateEnumProp(Map<String, Object> props,
                                  String key,
                                  Set<String> allowed,
                                  String message) {
        Object value = props.get(key);
        if (!(value instanceof String text) || !allowed.contains(text)) {
            throw BusinessException.badRequest(message + ": " + value);
        }
    }

    private void validateOptionalEnumProp(Map<String, Object> props,
                                          String key,
                                          Set<String> allowed,
                                          String message) {
        if (!props.containsKey(key) || props.get(key) == null) {
            return;
        }
        validateEnumProp(props, key, allowed, message);
    }

    private Long requireAssetId(Map<String, Object> props, String key, String message) {
        Object value = props.get(key);
        if (!(value instanceof Number number)) {
            throw BusinessException.badRequest(message);
        }
        long id = number.longValue();
        requirePositiveId(id, key);
        return id;
    }

    private List<Long> readAssetIds(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Number number) || number.longValue() <= 0) {
                throw BusinessException.badRequest("轮播图素材 ID 非法");
            }
            ids.add(number.longValue());
        }
        return ids;
    }

    private void requireNumber(Map<String, Object> props, String key, double min, double max) {
        Object value = props.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
            throw BusinessException.badRequest(key + " 数值非法");
        }
    }

    private void validateOptionalNumber(Map<String, Object> props, String key, double min, double max) {
        if (!props.containsKey(key) || props.get(key) == null) {
            return;
        }
        requireNumber(props, key, min, max);
    }

    private void validateAssetOwnership(Long meetingId, Set<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return;
        }
        List<InvitationAsset> assets = assetRepository.findByIdIn(assetIds);
        Set<Long> owned = new HashSet<>();
        for (InvitationAsset asset : assets) {
            if (asset.getMeeting().getId().equals(meetingId)) {
                owned.add(asset.getId());
            }
        }
        if (!owned.equals(assetIds)) {
            throw BusinessException.badRequest("设计文档引用了不存在或属于其他会议的素材");
        }
    }

    private void validateColor(String value, String label) {
        if (!StringUtils.hasText(value) || !COLOR.matcher(value).matches()) {
            throw BusinessException.badRequest(label + " 必须使用十六进制颜色");
        }
    }

    private void validateHttpsUrl(String value, String label) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw BusinessException.badRequest(label + "仅支持 HTTPS 直链");
            }
        } catch (URISyntaxException ex) {
            throw BusinessException.badRequest(label + "地址格式非法");
        }
    }

    private void requirePositiveId(long id, String label) {
        if (id <= 0) {
            throw BusinessException.badRequest(label + " ID 非法");
        }
    }

    private boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean rotatedBoundsWithinCanvas(InvitationDesignDocumentV1.InvitationCanvas canvas,
                                              InvitationDesignDocumentV1.InvitationElement element) {
        if (element.rotation() == 0) {
            return true;
        }
        double radians = Math.toRadians(element.rotation());
        double halfWidth = element.width() / 2.0;
        double halfHeight = element.height() / 2.0;
        double extentX = Math.abs(Math.cos(radians)) * halfWidth
                + Math.abs(Math.sin(radians)) * halfHeight;
        double extentY = Math.abs(Math.sin(radians)) * halfWidth
                + Math.abs(Math.cos(radians)) * halfHeight;
        double centerX = element.x() + halfWidth;
        double centerY = element.y() + halfHeight;
        double epsilon = 0.000001;
        return centerX - extentX >= -epsilon
                && centerY - extentY >= -epsilon
                && centerX + extentX <= canvas.width() + epsilon
                && centerY + extentY <= canvas.height() + epsilon;
    }

    private List<InvitationDesignDocumentV1.InvitationElement> safeElements(
            InvitationDesignDocumentV1.InvitationCanvas canvas) {
        return canvas.elements() == null ? List.of() : canvas.elements();
    }

    private static Map<InvitationElementType, Set<String>> allowedProps() {
        Map<InvitationElementType, Set<String>> result = new EnumMap<>(InvitationElementType.class);
        result.put(InvitationElementType.TEXT, Set.of("text", "fontSize", "fontFamily", "fontWeight", "fontStyle", "textAlign", "color", "lineHeight", "letterSpacing"));
        result.put(InvitationElementType.IMAGE, Set.of("assetId", "objectFit", "borderRadius", "alt"));
        result.put(InvitationElementType.SHAPE, Set.of("shape", "fill", "stroke", "strokeWidth", "borderRadius"));
        result.put(InvitationElementType.DIVIDER, Set.of("color", "thickness", "style"));
        result.put(InvitationElementType.MEETING_FIELD, Set.of("field", "labelPrefix", "fontSize", "fontFamily", "fontWeight", "textAlign", "color", "lineHeight"));
        result.put(InvitationElementType.REGISTRATION_BUTTON, Set.of("text", "fill", "color", "borderRadius", "fontSize", "fontWeight"));
        result.put(InvitationElementType.CHECKIN_BUTTON, Set.of("text", "fill", "color", "borderRadius", "fontSize", "fontWeight"));
        result.put(InvitationElementType.QUERY_BUTTON, Set.of("text", "queryTarget", "fill", "color", "borderRadius", "fontSize", "fontWeight"));
        result.put(InvitationElementType.CHANNEL_QR_CODE, Set.of("purpose", "foregroundColor", "backgroundColor", "label"));
        result.put(InvitationElementType.COUNTDOWN, Set.of("target", "format", "fontSize", "fontFamily", "fontWeight", "color"));
        result.put(InvitationElementType.BACKGROUND_MUSIC, Set.of("url", "title"));
        result.put(InvitationElementType.VIDEO, Set.of("url", "coverAssetId", "controls"));
        result.put(InvitationElementType.CAROUSEL, Set.of("assetIds", "intervalMs", "autoplay", "objectFit"));
        result.put(InvitationElementType.AMAP, Set.of("longitude", "latitude", "address", "zoom", "navigationUrl"));
        return result;
    }
}
