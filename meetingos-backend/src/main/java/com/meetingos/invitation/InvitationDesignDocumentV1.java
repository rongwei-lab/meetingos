package com.meetingos.invitation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 邀请函持久化使用稳定的领域结构，不直接保存 Fabric JSON。props 会按元素类型做白名单校验。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record InvitationDesignDocumentV1(
        int version,
        InvitationCanvas h5,
        InvitationCanvas poster
) {
    public static InvitationDesignDocumentV1 empty() {
        return new InvitationDesignDocumentV1(
                1,
                new InvitationCanvas(375, 667, "#ffffff", null, List.of()),
                new InvitationCanvas(1080, 1920, "#ffffff", null, List.of())
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InvitationCanvas(
            int width,
            int height,
            String backgroundColor,
            Long backgroundImageAssetId,
            List<InvitationElement> elements
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record InvitationElement(
            String id,
            InvitationElementType type,
            double x,
            double y,
            double width,
            double height,
            double rotation,
            int zIndex,
            boolean locked,
            Map<String, Object> props
    ) {
    }
}
