package com.meetingos.checkin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckinCodeParserTest {

    private final CheckinCodeParser parser = new CheckinCodeParser();

    @Test
    void parsesCompactCheckinCode() {
        CheckinCodePayload payload = parser.parse(1L, "MEETINGOS-CHECKIN:9:NDEF2026-0001", null);

        assertThat(payload.meetingId()).isEqualTo(9L);
        assertThat(payload.attendeeCode()).isEqualTo("NDEF2026-0001");
    }

    @Test
    void parsesFullUrlCode() {
        CheckinCodePayload payload = parser.parse(1L, "/public/checkin-code?type=checkin&meetingId=2&attendeeCode=NDEF2026-0002", null);

        assertThat(payload.meetingId()).isEqualTo(2L);
        assertThat(payload.attendeeCode()).isEqualTo("NDEF2026-0002");
    }
}
