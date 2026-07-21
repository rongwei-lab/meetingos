package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.meeting.Meeting;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TduckSubmissionConflictResponseTest {

    @Test
    void masksSensitiveFieldsWhileKeepingBusinessFormValuesForComparison() {
        Meeting meeting = new Meeting();
        meeting.setId(1L);

        Attendee attendee = new Attendee();
        attendee.setId(2L);
        attendee.setMeeting(meeting);
        attendee.setName("张三");
        attendee.setPhone("13800138000");
        attendee.setIdentityNo("110101199001011234");
        attendee.setEmail("zhangsan@example.com");
        attendee.setRawDataSnapshot(snapshot("张三", "原单位"));

        TduckSubmission submission = new TduckSubmission();
        submission.setId(3L);
        submission.setMeeting(meeting);
        submission.setAttendee(attendee);
        submission.setTduckDataId("211741");
        submission.setStatus(TduckSubmissionStatus.PENDING_CONFLICT);
        submission.setName("张三");
        submission.setPhone("13800138000");
        submission.setIdentityNo("110101199001015678");
        submission.setEmail("zhangsan@example.com");
        submission.setRawDataSnapshot(snapshot("张三", "新单位"));

        TduckSubmissionConflictResponse response = TduckSubmissionConflictResponse.from(submission);

        assertThat(response.existing().phone()).isEqualTo("****8000");
        assertThat(response.incoming().phone()).isEqualTo("****8000");
        assertThat(response.existing().identityNo()).isEqualTo("110******1234");
        assertThat(response.incoming().identityNo()).isEqualTo("110******5678");
        assertThat(response.existing().email()).isEqualTo("z***@example.com");
        assertThat(response.incoming().email()).isEqualTo("z***@example.com");
        assertThat(response.existing().formValues()).containsEntry("company", "原单位")
                .containsEntry("phone", "****8000")
                .containsEntry("idCard", "110******1234")
                .containsEntry("email", "z***@example.com")
                .doesNotContainKey("createTime");
        assertThat(response.incoming().formValues()).containsEntry("company", "新单位")
                .containsEntry("phone", "****8000")
                .containsEntry("idCard", "110******1234")
                .containsEntry("email", "z***@example.com")
                .doesNotContainKey("createTime");
    }

    private Map<String, Object> snapshot(String name, String company) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("phone", "13800138000");
        values.put("idCard", "110101199001011234");
        values.put("email", "zhangsan@example.com");
        values.put("company", company);
        values.put("createTime", "2026-07-21 17:37:09");
        return values;
    }
}
