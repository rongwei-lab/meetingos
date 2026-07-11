package com.meetingos.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFilesTest {

    @Test
    void protectsFormulaPrefixesHiddenBehindWhitespaceAndControlCharacters() {
        String csv = CsvFiles.toCsv(
                List.of("字段"),
                List.of(
                        List.of("\t=1+1"),
                        List.of("\r@SUM(A1:A2)"),
                        List.of("\u2003+1+1"),
                        List.of("普通文本")
                )
        );

        assertThat(csv)
                .contains("'\t=1+1")
                .contains("\"'\r@SUM(A1:A2)\"")
                .contains("'\u2003+1+1")
                .contains("普通文本");
    }
}
