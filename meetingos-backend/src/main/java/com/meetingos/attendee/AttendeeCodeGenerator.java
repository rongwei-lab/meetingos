package com.meetingos.attendee;

import com.meetingos.common.BusinessException;
import com.meetingos.meeting.Meeting;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class AttendeeCodeGenerator {

    private static final int MAX_PREFIX_LENGTH = 47;
    private static final int MAX_ATTEMPTS = 5;

    private final AttendeeRepository attendeeRepository;

    public AttendeeCodeGenerator(AttendeeRepository attendeeRepository) {
        this.attendeeRepository = attendeeRepository;
    }

    public String generate(Meeting meeting) {
        String meetingCode = meeting.getCode();
        String prefix = meetingCode.length() <= MAX_PREFIX_LENGTH
                ? meetingCode
                : meetingCode.substring(0, MAX_PREFIX_LENGTH);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String random = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
            String attendeeCode = prefix + "-" + random;
            if (!attendeeRepository.existsByAttendeeCode(attendeeCode)) {
                return attendeeCode;
            }
        }
        throw BusinessException.conflict("参会码生成冲突，请稍后重试");
    }
}
