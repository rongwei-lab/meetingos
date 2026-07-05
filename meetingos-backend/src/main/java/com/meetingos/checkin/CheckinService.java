package com.meetingos.checkin;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CheckinService {

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final CheckinRecordRepository checkinRecordRepository;
    private final CheckinCodeParser codeParser;

    public CheckinService(MeetingRepository meetingRepository,
                          AttendeeRepository attendeeRepository,
                          CheckinRecordRepository checkinRecordRepository,
                          CheckinCodeParser codeParser) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.checkinRecordRepository = checkinRecordRepository;
        this.codeParser = codeParser;
    }

    @Transactional
    public CheckinResponse checkin(CheckinRequest request) {
        ensureSupplementRemark(request);
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        Attendee attendee = resolveAttendeeForUpdate(request);
        if (attendee == null) {
            record(meeting, null, request, CheckinResultStatus.NOT_FOUND, "未找到参会人", null);
            return new CheckinResponse(CheckinResultStatus.NOT_FOUND, null, null, null, null, null, null, "未找到参会人");
        }
        if (!attendee.getMeeting().getId().equals(meeting.getId())) {
            record(meeting, attendee, request, CheckinResultStatus.NOT_FOUND, "参会人不属于当前会议", null);
            return response(CheckinResultStatus.NOT_FOUND, attendee, null, null, null, "参会人不属于当前会议");
        }
        if (attendee.getReviewStatus() != ReviewStatus.APPROVED) {
            record(meeting, attendee, request, CheckinResultStatus.NOT_APPROVED, "报名尚未审核通过", null);
            return response(CheckinResultStatus.NOT_APPROVED, attendee, null, null, null, "报名尚未审核通过");
        }
        if (attendee.getCheckinStatus() == CheckinStatus.CHECKED_IN) {
            record(meeting, attendee, request, CheckinResultStatus.DUPLICATE, "重复签到", attendee.getCheckedInAt());
            return response(
                    CheckinResultStatus.DUPLICATE,
                    attendee,
                    attendee.getCheckedInAt(),
                    attendee.getCheckinEntrance(),
                    attendee.getCheckinOperator(),
                    "重复签到，首次签到时间: " + attendee.getCheckedInAt()
            );
        }

        // 并发扫码时，resolveAttendeeForUpdate 使用悲观锁锁住参会人行，避免两个入口同时把未签到改成已签到。
        LocalDateTime now = LocalDateTime.now();
        attendee.setCheckinStatus(CheckinStatus.CHECKED_IN);
        attendee.setCheckedInAt(now);
        attendee.setCheckinEntrance(request.entrance());
        attendee.setCheckinOperator(SecurityUtils.currentUsernameOrSystem());
        record(meeting, attendee, request, CheckinResultStatus.SUCCESS, "签到成功", now);
        return response(CheckinResultStatus.SUCCESS, attendee, now, attendee.getCheckinEntrance(), attendee.getCheckinOperator(), "签到成功");
    }

    @Transactional(readOnly = true)
    public PageResponse<CheckinRecordResponse> records(Long meetingId, int page, int size) {
        var result = checkinRecordRepository.findPageWithAttendeeByMeetingId(meetingId, PageRequests.of(page, size));
        // open-in-view=false 时 Controller 序列化阶段已经离开事务，不能再触碰记录里的懒加载会议/参会人。
        // 在只读事务内提前组装脱敏 DTO，可以同时保证接口稳定和敏感签到快照不会原样外泄。
        return new PageResponse<>(
                result.getContent().stream().map(CheckinRecordResponse::masked).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private Attendee resolveAttendeeForUpdate(CheckinRequest request) {
        if (request.phone() != null && !request.phone().isBlank()) {
            return attendeeRepository.findByMeetingIdAndPhoneForUpdate(request.meetingId(), request.phone()).orElse(null);
        }
        if (request.name() != null && !request.name().isBlank()) {
            List<Attendee> matched = attendeeRepository.findByMeetingIdAndNameContainingIgnoreCase(request.meetingId(), request.name().trim());
            if (matched.size() == 1) {
                return attendeeRepository.findByMeetingIdAndAttendeeCodeForUpdate(request.meetingId(), matched.get(0).getAttendeeCode()).orElse(null);
            }
            if (matched.size() > 1) {
                throw BusinessException.badRequest("姓名匹配到多名参会人，请改用手机号或参会码");
            }
            return null;
        }
        CheckinCodePayload payload = codeParser.parse(request.meetingId(), request.qrCode(), request.attendeeCode());
        if (!payload.meetingId().equals(request.meetingId())) {
            throw BusinessException.badRequest("签到码会议与当前会议不一致");
        }
        return attendeeRepository.findByMeetingIdAndAttendeeCodeForUpdate(request.meetingId(), payload.attendeeCode()).orElse(null);
    }

    private void ensureSupplementRemark(CheckinRequest request) {
        // 补签会绕过现场扫码的实时证明，因此必须留下人工处理原因，便于后续审计和现场复核。
        if (request.checkinType() == CheckinType.SUPPLEMENT
                && (request.remark() == null || request.remark().isBlank())) {
            throw BusinessException.badRequest("补签必须填写原因");
        }
    }

    private void record(Meeting meeting, Attendee attendee, CheckinRequest request,
                        CheckinResultStatus status, String message, LocalDateTime checkedInAt) {
        CheckinRecord record = new CheckinRecord();
        record.setMeeting(meeting);
        record.setAttendee(attendee);
        record.setCheckinType(request.checkinType() == null ? CheckinType.SCAN : request.checkinType());
        record.setResultStatus(status);
        record.setEntrance(request.entrance());
        record.setOperator(SecurityUtils.currentUsernameOrSystem());
        record.setCheckedInAt(checkedInAt);
        record.setMessage(message);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("qrCode", request.qrCode());
        snapshot.put("attendeeCode", request.attendeeCode());
        snapshot.put("phone", request.phone());
        snapshot.put("name", request.name());
        snapshot.put("remark", request.remark());
        record.setRequestSnapshot(snapshot);
        checkinRecordRepository.save(record);
    }

    private CheckinResponse response(CheckinResultStatus status, Attendee attendee, LocalDateTime checkedInAt,
                                     String entrance, String operator, String message) {
        return new CheckinResponse(
                status,
                attendee.getId(),
                attendee.getName(),
                SensitiveDataMasker.maskPhone(attendee.getPhone()),
                checkedInAt,
                entrance,
                operator,
                message
        );
    }
}
