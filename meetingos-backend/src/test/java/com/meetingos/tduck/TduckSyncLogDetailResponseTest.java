package com.meetingos.tduck;

import com.meetingos.meeting.Meeting;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TduckSyncLogDetailResponseTest {

    @Test
    void returnsSavedFormValuesWithoutReturningRawPayloadMetadata() {
        Meeting meeting = new Meeting();
        meeting.setId(7L);
        TduckSyncLog log = new TduckSyncLog();
        log.setId(8L);
        log.setMeeting(meeting);
        log.setTduckDataId("211809");
        log.setRawPayload(new LinkedHashMap<>(Map.of(
                "data", Map.of(
                        "phone", "13800138000",
                        "identityNo", "110101199001011234",
                        "email", "zhangsan@example.com",
                        "company", "研发中心"
                ),
                "raw", Map.of("signature", "must-not-leak")
        )));

        TduckSyncLogDetailResponse response = TduckSyncLogDetailResponse.from(log);

        assertThat(response.formValues()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "phone", "13800138000",
                "identityNo", "110101199001011234",
                "email", "zhangsan@example.com",
                "company", "研发中心"
        ));
        assertThat(response.formValues()).doesNotContainKey("signature");
    }
}
