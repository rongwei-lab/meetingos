package com.meetingos.attendee;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendeeCodeGeneratorTest {

    @Test
    void keepsFullRandomValueWithinDatabaseLengthForLongMeetingCode() {
        AttendeeRepository repository = mock(AttendeeRepository.class);
        when(repository.existsByAttendeeCode(anyString())).thenReturn(false);
        Meeting meeting = meeting("M".repeat(64));

        String code = new AttendeeCodeGenerator(repository).generate(meeting);

        assertThat(code).hasSize(80);
        assertThat(code.substring(48)).matches("[0-9A-F]{32}");
    }

    @Test
    void retriesPreflightCollisionAndFailsAfterBoundedAttempts() {
        AttendeeRepository repository = mock(AttendeeRepository.class);
        when(repository.existsByAttendeeCode(anyString())).thenReturn(true, false);
        AttendeeCodeGenerator generator = new AttendeeCodeGenerator(repository);

        assertThat(generator.generate(meeting("FORUM"))).startsWith("FORUM-");
        verify(repository, times(2)).existsByAttendeeCode(anyString());

        AttendeeRepository exhausted = mock(AttendeeRepository.class);
        when(exhausted.existsByAttendeeCode(anyString())).thenReturn(true);
        assertThatThrownBy(() -> new AttendeeCodeGenerator(exhausted).generate(meeting("FORUM")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参会码生成冲突");
        verify(exhausted, times(5)).existsByAttendeeCode(anyString());
    }

    private Meeting meeting(String code) {
        Meeting meeting = new Meeting();
        meeting.setCode(code);
        return meeting;
    }
}
