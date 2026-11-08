package com.meetingos.tduck;

import com.meetingos.invitation.InvitationTrackingTokenService;
import com.meetingos.meeting.MeetingStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TduckOrganizationLookupService {
    private static final int TOKEN_TTL_SECONDS = 600;
    private static final String TOKEN_SCOPE = "org_";
    private final TduckFormBindingRepository bindings;
    private final InvitationTrackingTokenService tokens;
    private final TianyanchaClient client;
    private final TduckClient tduckClient;

    public TduckOrganizationLookupService(TduckFormBindingRepository bindings, InvitationTrackingTokenService tokens,
                                           TianyanchaClient client, TduckClient tduckClient) {
        this.bindings = bindings;
        this.tokens = tokens;
        this.client = client;
        this.tduckClient = tduckClient;
    }

    public LookupConfig issueToken(String formKey) {
        if (!client.configured()) return LookupConfig.disabled();
        Optional<TduckFormBinding> binding = activeBinding(formKey);
        if (binding.isEmpty()) return LookupConfig.disabled();
        try {
            // 表单标识公开可读，必须从 TDuck 核实开关，避免为未启用此功能的表单提供付费代理。
            if (!tduckClient.organizationLookupEnabled(binding.get().getFormKey())) return LookupConfig.disabled();
        } catch (RuntimeException exception) {
            return LookupConfig.disabled();
        }
        String token = tokens.create(TOKEN_SCOPE + tokens.newOpaqueId(), binding.get().getMeeting().getId(),
                binding.get().getFormKey(), Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
        return new LookupConfig(true, token, TOKEN_TTL_SECONDS);
    }

    public TianyanchaClient.Suggestions search(String formKey, String token, String word, String clientIp) {
        var claims = tokens.verify(token);
        if (claims.isEmpty() || !claims.get().clickPublicId().startsWith(TOKEN_SCOPE)
                || !tokens.matchesFormKey(claims.get(), formKey)) {
            return TianyanchaClient.Suggestions.unavailable("INVALID_TOKEN");
        }
        Optional<TduckFormBinding> binding = activeBinding(formKey);
        if (binding.isEmpty() || binding.get().getMeeting().getId() != claims.get().meetingId()) {
            return TianyanchaClient.Suggestions.unavailable("UNAVAILABLE");
        }
        return client.search(word, clientIp);
    }

    private Optional<TduckFormBinding> activeBinding(String formKey) {
        if (formKey == null || !formKey.matches("[A-Za-z0-9_-]{1,120}")) return Optional.empty();
        return bindings.findWithMeetingByFormKey(formKey).filter(binding -> {
            var meeting = binding.getMeeting();
            if (meeting == null || meeting.getId() == null || !binding.isRegistrationEnabled()) return false;
            if (meeting.getStatus() != MeetingStatus.REGISTRATION_OPEN && meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            return (meeting.getRegistrationStartTime() == null || !now.isBefore(meeting.getRegistrationStartTime()))
                    && (meeting.getRegistrationEndTime() == null || !now.isAfter(meeting.getRegistrationEndTime()));
        });
    }

    public record LookupConfig(boolean enabled, String token, int expiresInSeconds) {
        static LookupConfig disabled() { return new LookupConfig(false, "", 0); }
    }
}
