package com.meetingos.integration;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import com.meetingos.seating.Seat;
import com.meetingos.seating.SeatRepository;
import com.meetingos.seating.SeatType;
import com.meetingos.security.AppUser;
import com.meetingos.security.AppUserRepository;
import com.meetingos.security.Role;
import com.meetingos.security.UserStatus;
import com.meetingos.tduck.AttendeeField;
import com.meetingos.tduck.MappingStatus;
import com.meetingos.tduck.TduckBindingStatus;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import com.meetingos.tduck.TduckFormBinding;
import com.meetingos.tduck.TduckFormBindingRepository;
import com.meetingos.tduck.WebhookStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@TestConfiguration
class TestDataConfig {

    @Bean
    CommandLineRunner seedTestData(MeetingRepository meetingRepository,
                                   AttendeeRepository attendeeRepository,
                                   SeatRepository seatRepository,
                                   AppUserRepository userRepository,
                                   TduckFormBindingRepository bindingRepository,
                                   TduckFieldMappingRepository mappingRepository) {
        return args -> {
            AppUser user = new AppUser();
            user.setUsername("admin");
            user.setDisplayName("超级管理员");
            user.setPasswordHash("{noop}admin123");
            user.setStatus(UserStatus.ACTIVE);
            user.setRoles(new LinkedHashSet<>(List.of(Role.SUPER_ADMIN, Role.MEETING_ADMIN)));
            userRepository.save(user);

            Meeting meeting = new Meeting();
            meeting.setCode("NDEF2026");
            meeting.setName("2026年全国数字经济发展论坛");
            meeting.setLocation("北京国家会议中心");
            meeting.setVenueName("主会场");
            meeting.setStatus(MeetingStatus.REGISTRATION_OPEN);
            meeting.setStartTime(LocalDateTime.of(2026, 7, 15, 9, 0));
            meeting = meetingRepository.save(meeting);

            TduckFormBinding binding = new TduckFormBinding();
            binding.setMeeting(meeting);
            binding.setFormKey("ndef2026");
            binding.setFormName("数字经济论坛报名表");
            binding.setRegistrationUrl("http://tduck.test/s/ndef2026");
            binding.setBindingStatus(TduckBindingStatus.BOUND);
            binding.setMappingStatus(MappingStatus.VALID);
            binding.setWebhookStatus(WebhookStatus.CONFIGURED);
            bindingRepository.save(binding);

            mappingRepository.save(mapping(meeting, AttendeeField.NAME, "name", true));
            mappingRepository.save(mapping(meeting, AttendeeField.PHONE, "phone", true));
            mappingRepository.save(mapping(meeting, AttendeeField.ORGANIZATION, "organization", false));
            mappingRepository.save(mapping(meeting, AttendeeField.TITLE, "title", false));
            mappingRepository.save(mapping(meeting, AttendeeField.ATTENDEE_TYPE, "attendeeType", false));

            Attendee vip = attendee(meeting, "陈嘉宁", "13800000001", AttendeeType.VIP, "NDEF2026-0001");
            Attendee guest = attendee(meeting, "王子墨", "13800000002", AttendeeType.GUEST, "NDEF2026-0002");
            Attendee pending = attendee(meeting, "李若彤", "13800000003", AttendeeType.MEDIA, "NDEF2026-0003");
            pending.setReviewStatus(ReviewStatus.PENDING);
            pending.setReviewedAt(null);
            attendeeRepository.saveAll(List.of(vip, guest, pending));

            for (int row = 1; row <= 2; row++) {
                for (int no = 1; no <= 4; no++) {
                    Seat seat = new Seat();
                    seat.setMeeting(meeting);
                    seat.setAreaName("A区");
                    seat.setRowNo(row);
                    seat.setSeatNo(no);
                    seat.setSeatCode("A区-" + row + "-" + no);
                    seat.setSeatType(row == 1 ? SeatType.VIP : SeatType.GUEST);
                    seatRepository.save(seat);
                }
            }
        };
    }

    private TduckFieldMapping mapping(Meeting meeting, AttendeeField field, String tduckFieldKey, boolean required) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setMeeting(meeting);
        mapping.setAttendeeField(field);
        mapping.setTduckFieldKey(tduckFieldKey);
        mapping.setTduckFieldLabel(tduckFieldKey);
        mapping.setRequiredMapping(required);
        return mapping;
    }

    private Attendee attendee(Meeting meeting, String name, String phone, AttendeeType type, String code) {
        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        attendee.setName(name);
        attendee.setPhone(phone);
        attendee.setOrganization("测试单位");
        attendee.setAttendeeType(type);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        attendee.setAttendeeCode(code);
        return attendee;
    }
}
