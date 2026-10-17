package com.meetingos.tduck;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.invitation.InvitationTrackingTokenService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 为 TDuck 填写页提供手机号重复预检查。
 *
 * <p>预检查只改善提交前的反馈，不替代 Webhook 同步时的事务和数据库唯一约束。
 * 令牌复用 MeetingOS 已有的 HMAC 令牌实现，但使用新的随机 clickPublicId，
 * 不会创建邀请函点击记录，也不会把手机号写入令牌。</p>
 */
@Service
public class TduckPrecheckService {

    private static final int TOKEN_TTL_SECONDS = 10 * 60;

    private final TduckFormBindingRepository bindingRepository;
    private final TduckFieldMappingRepository mappingRepository;
    private final AttendeeRepository attendeeRepository;
    private final InvitationTrackingTokenService tokenService;

    public TduckPrecheckService(TduckFormBindingRepository bindingRepository,
                                TduckFieldMappingRepository mappingRepository,
                                AttendeeRepository attendeeRepository,
                                InvitationTrackingTokenService tokenService) {
        this.bindingRepository = bindingRepository;
        this.mappingRepository = mappingRepository;
        this.attendeeRepository = attendeeRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public TduckPrecheckTokenResponse issueToken(String rawFormKey) {
        String formKey = normalizeFormKey(rawFormKey);
        if (!StringUtils.hasText(formKey)) {
            return TduckPrecheckTokenResponse.disabled("TDuck 表单未指定");
        }

        Optional<TduckFormBinding> bindingResult = bindingRepository.findByFormKey(formKey);
        if (bindingResult.isEmpty() || bindingResult.get().getMeeting() == null) {
            // 直接打开未绑定 MeetingOS 的 TDuck 表单时不阻断原有填写流程。
            return TduckPrecheckTokenResponse.disabled("当前表单未绑定会议");
        }

        TduckFormBinding binding = bindingResult.get();
        Meeting meeting = binding.getMeeting();
        if (meeting.getId() == null) {
            return TduckPrecheckTokenResponse.disabled("当前报名表未绑定会议");
        }
        String registrationClosedReason = registrationClosedReason(meeting, binding);
        if (registrationClosedReason != null) {
            return TduckPrecheckTokenResponse.disabled(registrationClosedReason);
        }

        Optional<TduckFieldMapping> phoneMapping = mappingRepository
                .findByMeetingIdAndAttendeeField(meeting.getId(), AttendeeField.PHONE);
        if (phoneMapping.isEmpty() || !StringUtils.hasText(phoneMapping.get().getTduckFieldKey())) {
            return TduckPrecheckTokenResponse.disabled("手机号字段映射尚未配置");
        }

        String token = tokenService.create(
                tokenService.newOpaqueId(),
                meeting.getId(),
                formKey,
                Instant.now().plusSeconds(TOKEN_TTL_SECONDS)
        );
        return TduckPrecheckTokenResponse.active(
                token,
                phoneMapping.get().getTduckFieldKey(),
                TOKEN_TTL_SECONDS
        );
    }

    @Transactional(readOnly = true)
    public TduckPhonePrecheckResponse checkPhone(TduckPhonePrecheckRequest request) {
        String formKey = normalizeFormKey(request.formKey());
        String phone = normalizePhone(request.phone());
        if (!StringUtils.hasText(formKey)) {
            return TduckPhonePrecheckResponse.unavailable("当前报名表暂不支持手机号重复校验");
        }
        if (phone == null) {
            return TduckPhonePrecheckResponse.invalidPhone();
        }

        Optional<TduckFormBinding> bindingResult = bindingRepository.findByFormKey(formKey);
        if (bindingResult.isEmpty() || bindingResult.get().getMeeting() == null) {
            return TduckPhonePrecheckResponse.unavailable("当前报名表暂不支持手机号重复校验");
        }

        TduckFormBinding binding = bindingResult.get();
        Meeting meeting = binding.getMeeting();
        if (meeting.getId() == null) {
            return TduckPhonePrecheckResponse.unavailable("当前报名表暂不支持手机号重复校验");
        }
        String registrationClosedReason = registrationClosedReason(meeting, binding);
        if (registrationClosedReason != null) {
            return TduckPhonePrecheckResponse.unavailable(registrationClosedReason);
        }
        var claims = tokenService.verify(request.token());
        if (claims.isEmpty()
                || claims.get().meetingId() != meeting.getId()
                || !tokenService.matchesFormKey(claims.get(), formKey)) {
            return TduckPhonePrecheckResponse.invalidToken();
        }

        Optional<TduckFieldMapping> phoneMapping = mappingRepository
                .findByMeetingIdAndAttendeeField(meeting.getId(), AttendeeField.PHONE);
        if (phoneMapping.isEmpty() || !StringUtils.hasText(phoneMapping.get().getTduckFieldKey())) {
            return TduckPhonePrecheckResponse.unavailable("手机号字段映射尚未配置");
        }

        return attendeeRepository.findByMeetingIdAndPhone(meeting.getId(), phone).isPresent()
                ? TduckPhonePrecheckResponse.duplicate()
                : TduckPhonePrecheckResponse.allowed();
    }

    private String registrationClosedReason(Meeting meeting, TduckFormBinding binding) {
        if (meeting == null || binding == null || !StringUtils.hasText(binding.getFormKey())) {
            return "当前报名表未绑定会议";
        }
        if (!binding.isRegistrationEnabled()) {
            return "报名入口已关闭";
        }
        if (meeting.getStatus() != MeetingStatus.REGISTRATION_OPEN
                && meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            return "会议当前不在报名开放状态";
        }
        LocalDateTime now = LocalDateTime.now();
        if (meeting.getRegistrationStartTime() != null && now.isBefore(meeting.getRegistrationStartTime())) {
            return "报名尚未开始";
        }
        if (meeting.getRegistrationEndTime() != null && now.isAfter(meeting.getRegistrationEndTime())) {
            return "报名已截止";
        }
        return null;
    }

    private String normalizeFormKey(String formKey) {
        if (formKey == null) {
            return null;
        }
        String normalized = formKey.trim();
        return normalized.length() <= 120 && !normalized.isBlank() ? normalized : null;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        return normalized.matches("^\\+?\\d{6,20}$") ? normalized : null;
    }
}
