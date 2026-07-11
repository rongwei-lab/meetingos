package com.meetingos.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void recursivelyMasksCollectionsArraysAndNestedMaps() {
        Object masked = SensitiveDataMasker.maskValue(
                "input178001",
                null,
                Map.of(
                        "items", List.of("13800000000", "visitor@example.com"),
                        "array", new String[]{"13900001234"}
                )
        );

        assertThat(masked).isEqualTo(Map.of(
                "items", List.of("****0000", "v***@example.com"),
                "array", List.of("****1234")
        ));
    }

    @Test
    void usesDynamicFieldLabelToRecognizeIdentityValues() {
        assertThat(SensitiveDataMasker.maskValue("input178002", "护照号", "E12345678"))
                .isEqualTo("E12******5678");
        assertThat(SensitiveDataMasker.maskValue("input178003", "登录密码", 123456))
                .isEqualTo("***");
    }
}
