package com.meetingos.meeting;

/**
 * 席签单个展示字段的打印样式。
 *
 * <p>fieldKey 对应前端可选字段；fontSizePt 为空时使用当前尺寸/字段数量的自动字号；
 * color 为空时使用黑色；letterSpacingEm 为空时使用前端自动字距；bold 为空时使用前端默认粗细。
 * 该对象会作为 JSON 保存在 meetings.nameplate_fields 中。</p>
 */
public record NameplateFieldSetting(
        String fieldKey,
        Integer fontSizePt,
        String color,
        Double letterSpacingEm,
        Boolean bold
) {
    public NameplateFieldSetting(String fieldKey, Integer fontSizePt, String color) {
        this(fieldKey, fontSizePt, color, null, null);
    }

    public NameplateFieldSetting(String fieldKey, Integer fontSizePt, String color, Double letterSpacingEm) {
        this(fieldKey, fontSizePt, color, letterSpacingEm, null);
    }
}
