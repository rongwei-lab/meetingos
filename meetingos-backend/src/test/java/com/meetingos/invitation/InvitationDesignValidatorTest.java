package com.meetingos.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvitationDesignValidatorTest {
    private InvitationAssetRepository assetRepository;
    private InvitationDesignValidator validator;

    @BeforeEach
    void setUp() {
        assetRepository = mock(InvitationAssetRepository.class);
        validator = new InvitationDesignValidator(new ObjectMapper(), assetRepository);
    }

    @Test
    void acceptsMinimalDomainDocument() {
        validator.validate(1L, InvitationDesignDocumentV1.empty());
    }

    @Test
    void rejectsFabricLikeUnknownPropertiesAndInteractivePosterElements() {
        InvitationDesignDocumentV1.InvitationElement fabricElement = element(
                InvitationElementType.TEXT, Map.of("text", "会议邀请", "fabricType", "i-text")
        );
        InvitationDesignDocumentV1 invalidProps = document(List.of(fabricElement), List.of());
        assertThatThrownBy(() -> validator.validate(1L, invalidProps))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的属性");

        InvitationDesignDocumentV1.InvitationElement video = element(
                InvitationElementType.VIDEO,
                Map.of("url", "https://example.test/video.mp4", "coverAssetId", 1L)
        );
        InvitationDesignDocumentV1 invalidPoster = document(List.of(), List.of(video));
        assertThatThrownBy(() -> validator.validate(1L, invalidPoster))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("海报画布不支持互动元素");
    }

    @Test
    void rejectsCrossMeetingAssetsAndMoreThanTwoHundredElements() {
        Meeting anotherMeeting = new Meeting();
        anotherMeeting.setId(2L);
        InvitationAsset asset = new InvitationAsset();
        asset.setId(7L);
        asset.setMeeting(anotherMeeting);
        when(assetRepository.findByIdIn(java.util.Set.of(7L))).thenReturn(List.of(asset));
        InvitationDesignDocumentV1 withForeignAsset = document(
                List.of(element(InvitationElementType.IMAGE, Map.of("assetId", 7L))), List.of()
        );
        assertThatThrownBy(() -> validator.validate(1L, withForeignAsset))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("属于其他会议");

        List<InvitationDesignDocumentV1.InvitationElement> elements = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            elements.add(new InvitationDesignDocumentV1.InvitationElement(
                    "element_" + index, InvitationElementType.TEXT, 0, 0, 100, 30,
                    0, index, false, Map.of("text", "x")
            ));
        }
        assertThatThrownBy(() -> validator.validate(1L, document(elements, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多包含 200 个元素");
    }

    @Test
    void extractsOnlyAssetsReferencedByPublishedDocument() {
        InvitationDesignDocumentV1 document = new InvitationDesignDocumentV1(
                1,
                new InvitationDesignDocumentV1.InvitationCanvas(
                        375, 667, "#ffffff", 1L,
                        List.of(
                                new InvitationDesignDocumentV1.InvitationElement(
                                        "image", InvitationElementType.IMAGE, 0, 0, 100, 100, 0, 0, false,
                                        Map.of("assetId", 2L)
                                ),
                                new InvitationDesignDocumentV1.InvitationElement(
                                        "video", InvitationElementType.VIDEO, 0, 110, 100, 100, 0, 1, false,
                                        Map.of("url", "https://example.test/video.mp4", "coverAssetId", 3L)
                                ),
                                new InvitationDesignDocumentV1.InvitationElement(
                                        "carousel", InvitationElementType.CAROUSEL, 0, 220, 100, 100, 0, 2, false,
                                        Map.of("assetIds", List.of(4L, 5L))
                                )
                        )
                ),
                new InvitationDesignDocumentV1.InvitationCanvas(1080, 1920, "#ffffff", 6L, List.of())
        );

        assertThat(validator.referencedAssetIds(document)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(validator.referencedAssetIds(InvitationDesignDocumentV1.empty())).isEmpty();
    }

    @Test
    void validatesFontStackAgainstSafeAllowList() {
        InvitationDesignDocumentV1 safe = document(List.of(element(
                InvitationElementType.TEXT,
                Map.of("text", "会议", "fontFamily", "Inter, system-ui, sans-serif")
        )), List.of());
        validator.validate(1L, safe);

        InvitationDesignDocumentV1 unsafe = document(List.of(element(
                InvitationElementType.TEXT,
                Map.of("text", "会议", "fontFamily", "Inter; background:url(javascript:alert(1))")
        )), List.of());
        assertThatThrownBy(() -> validator.validate(1L, unsafe))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的字体");
    }

    @Test
    void rejectsUnsafeMapNavigationUrl() {
        InvitationDesignDocumentV1 unsafe = document(List.of(element(
                InvitationElementType.AMAP,
                Map.of(
                        "longitude", 121.49,
                        "latitude", 31.24,
                        "address", "会议中心",
                        "navigationUrl", "javascript:alert(1)"
                )
        )), List.of());

        assertThatThrownBy(() -> validator.validate(1L, unsafe))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("地图导航仅支持 HTTPS 直链");

        InvitationDesignDocumentV1 safe = document(List.of(element(
                InvitationElementType.AMAP,
                Map.of(
                        "longitude", 121.49,
                        "latitude", 31.24,
                        "address", "会议中心",
                        "navigationUrl", "https://uri.amap.com/marker?position=121.49,31.24"
                )
        )), List.of());
        validator.validate(1L, safe);
    }

    @Test
    void rejectsRotatedElementWhoseVisualBoundsLeaveCanvas() {
        InvitationDesignDocumentV1.InvitationElement element = new InvitationDesignDocumentV1.InvitationElement(
                "rotated", InvitationElementType.TEXT, 0, 0, 100, 100,
                45, 0, false, Map.of("text", "会议")
        );

        assertThatThrownBy(() -> validator.validate(1L, document(List.of(element), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出画布");
    }

    @Test
    void rejectsLowContrastQrCodeColors() {
        InvitationDesignDocumentV1.InvitationElement qrCode = element(
                InvitationElementType.CHANNEL_QR_CODE,
                Map.of("foregroundColor", "#777777", "backgroundColor", "#888888")
        );

        assertThatThrownBy(() -> validator.validate(1L, document(List.of(qrCode), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对比度至少为 4.5:1");
    }

    @Test
    void acceptsCheckinAndAllSupportedQueryButtonsOnH5() {
        validator.validate(1L, document(List.of(element(
                InvitationElementType.CHECKIN_BUTTON,
                Map.of("text", "立即签到", "fill", "#08784f", "color", "#ffffff")
        )), List.of()));

        for (String target : List.of("QUERY_ENTRY", "COMBINED_QUERY", "SEAT_QUERY", "ROOM_QUERY", "DINNER_QUERY")) {
            validator.validate(1L, document(List.of(element(
                    InvitationElementType.QUERY_BUTTON,
                    Map.of("text", "立即查询", "queryTarget", target)
            )), List.of()));
        }
    }

    @Test
    void rejectsInvalidButtonConfigurationAndInteractiveButtonsOnPoster() {
        assertThatThrownBy(() -> validator.validate(1L, document(List.of(element(
                InvitationElementType.QUERY_BUTTON,
                Map.of("text", "立即查询", "queryTarget", "CHECKIN")
        )), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的查询按钮目标");

        assertThatThrownBy(() -> validator.validate(1L, document(List.of(element(
                InvitationElementType.CHECKIN_BUTTON,
                Map.of("text", "签".repeat(41))
        )), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 40 个字符");

        assertThatThrownBy(() -> validator.validate(1L, document(List.of(), List.of(element(
                InvitationElementType.CHECKIN_BUTTON,
                Map.of("text", "立即签到")
        )))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("海报画布不支持互动元素");

        assertThatThrownBy(() -> validator.validate(1L, document(List.of(), List.of(element(
                InvitationElementType.QUERY_BUTTON,
                Map.of("text", "立即查询", "queryTarget", "QUERY_ENTRY")
        )))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("海报画布不支持互动元素");
    }

    @Test
    void acceptsSupportedQrPurposesAndLegacyMissingPurpose() {
        for (String purpose : List.of(
                "REGISTRATION", "QUERY_ENTRY", "COMBINED_QUERY", "SEAT_QUERY", "ROOM_QUERY",
                "DINNER_QUERY", "CHANNEL_INVITATION"
        )) {
            validator.validate(1L, document(List.of(element(
                    InvitationElementType.CHANNEL_QR_CODE,
                    Map.of(
                            "purpose", purpose,
                            "foregroundColor", "#000000",
                            "backgroundColor", "#ffffff",
                            "label", "扫码进入"
                    )
            )), List.of()));
        }

        validator.validate(1L, document(List.of(element(
                InvitationElementType.CHANNEL_QR_CODE,
                Map.of("foregroundColor", "#000000", "backgroundColor", "#ffffff")
        )), List.of()));
    }

    @Test
    void rejectsUnknownQrPurposeAndOverlongQrLabel() {
        assertThatThrownBy(() -> validator.validate(1L, document(List.of(element(
                InvitationElementType.CHANNEL_QR_CODE,
                Map.of("purpose", "SELF_CHECKIN")
        )), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的二维码用途");

        assertThatThrownBy(() -> validator.validate(1L, document(List.of(element(
                InvitationElementType.CHANNEL_QR_CODE,
                Map.of("purpose", "REGISTRATION", "label", "码".repeat(61))
        )), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("二维码说明最多 60 个字符");
    }

    @Test
    void detectsOnlyH5PublicQueryActions() {
        for (InvitationDesignDocumentV1.InvitationElement action : List.of(
                element(InvitationElementType.CHECKIN_BUTTON, Map.of("text", "立即签到")),
                element(InvitationElementType.QUERY_BUTTON, Map.of("text", "立即查询", "queryTarget", "QUERY_ENTRY")),
                element(InvitationElementType.CHANNEL_QR_CODE, Map.of("purpose", "SEAT_QUERY"))
        )) {
            assertThat(validator.containsPublicQueryAction(document(List.of(action), List.of()))).isTrue();
        }

        assertThat(validator.containsPublicQueryAction(document(List.of(element(
                InvitationElementType.CHANNEL_QR_CODE,
                Map.of("purpose", "REGISTRATION")
        )), List.of()))).isFalse();
        assertThat(validator.containsPublicQueryAction(document(List.of(element(
                InvitationElementType.CHANNEL_QR_CODE,
                Map.of("purpose", "CHANNEL_INVITATION")
        )), List.of()))).isFalse();
        assertThat(validator.containsPublicQueryAction(InvitationDesignDocumentV1.empty())).isFalse();
    }

    @Test
    void acceptsSupportedShapeTypesAndRejectsUnknownShape() {
        for (String shape : List.of("rectangle", "rounded-rectangle", "ellipse", "triangle", "diamond", "hexagon", "star", "circle")) {
            validator.validate(1L, document(List.of(element(InvitationElementType.SHAPE, Map.of("shape", shape))), List.of()));
        }
        validator.validate(1L, document(List.of(element(InvitationElementType.SHAPE, Map.of())), List.of()));

        assertThatThrownBy(() -> validator.validate(1L, document(
                List.of(element(InvitationElementType.SHAPE, Map.of("shape", "pentagon"))), List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的形状类型");
    }

    @Test
    void rejectsInvalidShapeGeometryNumbers() {
        for (Map<String, Object> props : List.of(
                Map.<String, Object>of("shape", "star", "strokeWidth", "wide"),
                Map.<String, Object>of("shape", "star", "borderRadius", 51),
                Map.<String, Object>of("shape", "star", "opacity", 1.1)
        )) {
            assertThatThrownBy(() -> validator.validate(1L, document(
                    List.of(element(InvitationElementType.SHAPE, props)), List.of()
            )))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("数值非法");
        }
    }

    private InvitationDesignDocumentV1 document(
            List<InvitationDesignDocumentV1.InvitationElement> h5,
            List<InvitationDesignDocumentV1.InvitationElement> poster) {
        return new InvitationDesignDocumentV1(
                1,
                new InvitationDesignDocumentV1.InvitationCanvas(375, 667, "#ffffff", null, h5),
                new InvitationDesignDocumentV1.InvitationCanvas(1080, 1920, "#ffffff", null, poster)
        );
    }

    private InvitationDesignDocumentV1.InvitationElement element(InvitationElementType type, Map<String, Object> props) {
        return new InvitationDesignDocumentV1.InvitationElement(
                "element_1", type, 0, 0, 100, 100, 0, 0, false, props
        );
    }
}
